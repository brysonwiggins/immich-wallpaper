package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Face
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * What to pick random photos from. Empty lists mean "no restriction". The `excluded…` lists are
 * never shown; they are applied on the phone, not sent to the server.
 */
data class SourceFilter(
    val albumIds: List<String> = emptyList(),
    val personIds: List<String> = emptyList(),
    val favoritesOnly: Boolean = false,
    val excludedAlbumIds: List<String> = emptyList(),
    val excludedPersonIds: List<String> = emptyList(),
    val excludedAssetIds: List<String> = emptyList(),
    /** Also include photos that are in no album at all ("acts like another album"; decided on the phone). */
    val notInAlbum: Boolean = false,
) {
    val hasExclusions: Boolean get() = excludedAlbumIds.isNotEmpty() || excludedPersonIds.isNotEmpty() || excludedAssetIds.isNotEmpty()
}

data class Album(val id: String, val name: String, val assetCount: Int, val thumbnailAssetId: String? = null)

data class Person(val id: String, val name: String)

/** An asset plus the fields needed to detect edits (offline-set re-sync). */
data class RemoteAsset(val asset: Asset, val updatedAt: String?)

sealed interface ConnectionResult {
    data class Ok(val serverVersion: String) : ConnectionResult
    data object Unauthorized : ConnectionResult
    /** The key works but lacks some of [REQUIRED_PERMISSIONS] (in that order). */
    data class MissingPermissions(val permissions: List<String>) : ConnectionResult
    data class Unreachable(val message: String) : ConnectionResult
    data class Error(val status: Int) : ConnectionResult
}

/**
 * The API key permissions the app uses (names as Immich's API key screen shows them): finding and
 * reading photos, loading their images, listing albums and people, and face boxes for cropping.
 */
val REQUIRED_PERMISSIONS = listOf("asset.read", "asset.view", "album.read", "person.read", "face.read")

sealed class ImmichException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** HTTP 401, or 403 ([forbidden]) when the key lacks a permission, named in [missingPermission] when Immich says which. */
    class Unauthorized(val missingPermission: String? = null, val forbidden: Boolean = missingPermission != null) :
        ImmichException(if (missingPermission == null) "API key rejected" else "API key lacks $missingPermission")
    class Unreachable(cause: IOException) : ImmichException("Server unreachable: ${cause.message}", cause)
    class Http(val status: Int) : ImmichException("HTTP $status")
}

/**
 * Blocking Immich API client (call from a background thread). Authenticates with `x-api-key`.
 * Random search uses the v3.2+ `filter` body and falls back to the legacy flat body on HTTP 400.
 * Never logs the key or URLs.
 */
class ImmichClient(
    serverUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient = defaultHttp(),
    /** Diagnostics (no keys or addresses): e.g. when the server turns out to ignore the search filter. */
    private val log: (String) -> Unit = {},
) : ImmichApi, PhotoLibrary {
    private val api = normalizeUrl(serverUrl)
    private var legacySearch = false
    private var filterMetadata = false

    /**
     * Short-timeout reachability + key check (about 3 s). Tries one call per permission in
     * [REQUIRED_PERMISSIONS], so a key made with only those passes and a narrower one is named.
     */
    fun testConnection(): ConnectionResult {
        val quick = http.newBuilder().callTimeout(PING_TIMEOUT_S, TimeUnit.SECONDS).build()
        return try {
            val version = quick.execute(get("/server/version", auth = false)) { JSON.decodeFromString<ServerVersionDto>(it) }
            val missing = mutableSetOf<String>()
            fun check(permission: String, call: () -> Unit) {
                try {
                    call()
                } catch (e: ImmichException.Unauthorized) {
                    if (!e.forbidden) throw e
                    missing += e.missingPermission ?: permission
                }
            }
            check("album.read") { quick.execute(get("/albums")) { } }
            check("person.read") { quick.execute(get("/people?withHidden=false&size=1")) { } }
            var sample: String? = null
            check("asset.read") {
                sample = quick.execute(post("/search/random", legacyBody(SourceFilter(), 1))) { JSON.decodeFromString<List<AssetDto>>(it) }.firstOrNull()?.id
            }
            sample?.let { id ->
                check("asset.view") { quick.executeRaw(get("/assets/$id/thumbnail?size=thumbnail", accept = "image/*")) { } }
                check("face.read") { quick.execute(get("/faces?id=$id")) { } }
            }
            if (missing.isEmpty()) ConnectionResult.Ok("${version.major}.${version.minor}.${version.patch}")
            else ConnectionResult.MissingPermissions(missing.sortedBy { REQUIRED_PERMISSIONS.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } })
        } catch (e: ImmichException.Unauthorized) {
            ConnectionResult.Unauthorized
        } catch (e: ImmichException.Unreachable) {
            ConnectionResult.Unreachable(e.message ?: "unreachable")
        } catch (e: ImmichException.Http) {
            ConnectionResult.Error(e.status)
        }
    }

    /**
     * Random photos that really match [filter]. Every photo is checked on the phone: in a chosen
     * album (or in none, with "not in an album"), showing a chosen person, a favorite when asked,
     * and not hidden. Servers have answered the newer `filter` body by ignoring it (Immich 3.0.1);
     * photos outside the selection reveal that, and the client switches to the older format for good.
     * Asks again (up to [ROUNDS] requests) while filtering leaves fewer than [count].
     */
    override fun randomAssets(filter: SourceFilter, count: Int): List<Asset> {
        val rule = PoolRule(filter)
        if (filter.albumIds.isEmpty()) return searchRandom(filter, rule, count)
        // Chosen albums: picked from their own photo lists. Immich's random search only covers your own
        // and partners' photos, so photos others added to a shared album would never come up.
        val fromAlbums = filter.albumIds.distinct().flatMap(::albumAssets).distinctBy { it.id }
            .filter { rule.serverSide(it, checkPeople = filter.personIds.isNotEmpty()) && rule.clientSide(it) }
            .shuffled()
        if (!filter.notInAlbum) return fromAlbums.take(count)
        // "Not in an album" too: about half from each side, then mixed.
        val rest = searchRandom(filter.copy(albumIds = emptyList()), rule, count)
        val half = (count + 1) / 2
        val albumSide = fromAlbums.take(maxOf(half, count - rest.size))
        return (albumSide + rest.filter { a -> albumSide.none { it.id == a.id } }.take(count - albumSide.size)).shuffled()
    }

    private fun searchRandom(filter: SourceFilter, rule: PoolRule, count: Int): List<Asset> {
        val found = LinkedHashMap<String, Asset>()
        repeat(ROUNDS) {
            val batch = randomBatch(filter, count)
            if (batch.isEmpty()) return found.values.toList()
            val checkPeople = filter.personIds.isNotEmpty() && batch.any { it.personIds.isNotEmpty() }
            val outside = batch.count { !rule.serverSide(it, checkPeople) }
            if (!legacySearch && rule.askedServer && outside > 0) {
                legacySearch = true
                log("SEARCH server ignored the filter: $outside of ${batch.size} photos were outside the selection; using the older search format")
            }
            val kept = batch.filter { rule.serverSide(it, checkPeople) && rule.clientSide(it) }
            kept.forEach { found.putIfAbsent(it.id, it) }
            if (kept.size < batch.size) log("SEARCH kept ${kept.size} of ${batch.size} photos")
            // Nothing filtered out: the server's answer is the pool, asking again can't add to it.
            if (found.size >= count || kept.size == batch.size) return found.values.take(count)
        }
        return found.values.toList()
    }

    /** The selection's rules, split into what the server was asked to apply and what only the phone applies. */
    private inner class PoolRule(private val filter: SourceFilter) {
        private val chosenAlbums = albumPhotoIds(filter.albumIds)
        private val inAnyAlbum = if (filter.notInAlbum) albumPhotoIds(albums().map { it.id }) else emptySet()
        private val hiddenIds = filter.excludedAssetIds.toSet() + albumPhotoIds(filter.excludedAlbumIds)
        private val people = filter.personIds.toSet()

        /** Whether the request carried constraints the server should have applied. */
        val askedServer = people.isNotEmpty() || filter.favoritesOnly

        fun serverSide(a: Asset, checkPeople: Boolean): Boolean =
            (!checkPeople || a.personIds.any { it in people }) &&
                (!filter.favoritesOnly || a.favorite)

        fun clientSide(a: Asset): Boolean =
            a.id !in hiddenIds &&
                (!a.archived || a.id in chosenAlbums) &&
                a.personIds.none { it in filter.excludedPersonIds } &&
                (!filter.notInAlbum || a.id !in inAnyAlbum || a.id in chosenAlbums)
    }

    // Album photo lists, looked up page by page and kept for an hour each (albums change rarely).
    private val albumCache = HashMap<String, Pair<Long, List<Asset>>>()

    /** Every photo id in any of [albumIds] (hidden albums; the "included photos" browser). */
    override fun albumPhotoIds(albumIds: List<String>): Set<String> = albumIds.toSet().flatMap { albumAssets(it) }.map { it.id }.toSet()

    /** The album's photos; none (logged) when it was deleted or is no longer shared with you. */
    @Synchronized
    private fun albumAssets(album: String): List<Asset> =
        albumCache[album]?.takeIf { System.nanoTime() - it.first < ALBUM_CACHE_NANOS }?.second ?: run {
            val out = LinkedHashMap<String, Asset>()
            var page: Int? = 1
            var pages = 0
            try {
                while (page != null && pages++ < MAX_ALBUM_PAGES) {
                    val result = photos(SourceFilter(albumIds = listOf(album)), page, ALBUM_PAGE_SIZE)
                    result.items.forEach { out.putIfAbsent(it.id, it) }
                    page = result.nextPage
                }
            } catch (e: ImmichException.Http) {
                // Immich answers 400 "Not found or no album.read access" for albums you can't see any more.
                if (e.status != 400 && e.status != 404) throw e
                log("ALBUM unavailable (HTTP ${e.status}): deleted or no longer shared")
            }
            out.values.toList().also { albumCache[album] = System.nanoTime() to it }
        }

    private var favoritesCache: Pair<Long, Set<String>>? = null

    /**
     * Counted on the phone: the album's photo list (cached, also used to check random photos) against
     * every favorite, keeping only photos marked favorite, so a server that ignores filters can't inflate it.
     */
    @Synchronized
    override fun favoriteCount(albumId: String?): Int {
        val favorites = favoritesCache?.takeIf { System.nanoTime() - it.first < ALBUM_CACHE_NANOS }?.second ?: run {
            val out = mutableSetOf<String>()
            var page: Int? = 1
            var pages = 0
            while (page != null && pages++ < MAX_ALBUM_PAGES) {
                val result = photos(SourceFilter(favoritesOnly = true), page, ALBUM_PAGE_SIZE)
                out += result.items.filter { it.favorite }.map { it.id }
                page = result.nextPage
            }
            out.also { favoritesCache = System.nanoTime() to it }
        }
        return if (albumId == null) favorites.size else albumPhotoIds(listOf(albumId)).count { it in favorites }
    }

    private fun randomBatch(filter: SourceFilter, count: Int): List<Asset> {
        if (!legacySearch) {
            try {
                return search(filterBody(filter, count))
            } catch (e: ImmichException.Http) {
                if (e.status != 400) throw e
                legacySearch = true
            }
        }
        // The older format reads several ids as "all of them"; one album and one person per request
        // keeps "any of them" (the phone checks the rest).
        val single = filter.copy(albumIds = listOfNotNull(filter.albumIds.randomOrNull()), personIds = listOfNotNull(filter.personIds.randomOrNull()))
        return search(legacyBody(single, count))
    }

    private fun search(body: JsonObject): List<Asset> =
        http.execute(post("/search/random", body)) { JSON.decodeFromString<List<AssetDto>>(it) }.mapNotNull { it.toAsset() }

    /** Null when the asset no longer exists (deleted on the server). */
    override fun asset(id: String): RemoteAsset? = try {
        http.execute(get("/assets/$id")) { JSON.decodeFromString<AssetDto>(it) }
            .let { dto -> dto.toAsset()?.let { RemoteAsset(it, dto.updatedAt) } }
    } catch (e: ImmichException.Http) {
        if (e.status == 404 || e.status == 400) null else throw e
    }

    override fun faces(assetId: String): List<Face> =
        http.execute(get("/faces?id=$assetId")) { JSON.decodeFromString<List<FaceDto>>(it) }.mapNotNull { it.toFace() }

    override fun albums(): List<Album> =
        http.execute(get("/albums")) { JSON.decodeFromString<List<AlbumDto>>(it) }
            .map { Album(it.id, it.albumName, it.assetCount, it.albumThumbnailAssetId) }

    /**
     * One page of photos, newest first. Sends the flat body (accepted by 3.2.0 for random search) and
     * switches to the `filter` body if the server rejects it with HTTP 400.
     */
    override fun photos(filter: SourceFilter, page: Int, size: Int): PhotoPage {
        if (!filterMetadata) {
            try {
                return metadata(legacyBody(filter, size, page))
            } catch (e: ImmichException.Http) {
                if (e.status != 400) throw e
            }
            // Rejected: either this server wants the filter body, or the request itself is bad (an album
            // that's gone). Only a filter body that works settles the format.
            return metadata(filterBody(filter, size, page)).also { filterMetadata = true }
        }
        return metadata(filterBody(filter, size, page))
    }

    private fun metadata(body: JsonObject): PhotoPage =
        http.execute(post("/search/metadata", body)) { JSON.decodeFromString<MetadataSearchDto>(it) }.assets.let { a ->
            PhotoPage(a.items.mapNotNull { it.toAsset() }, a.nextPage?.toIntOrNull())
        }

    override fun thumbnailUrl(assetId: String) = "$api/assets/$assetId/thumbnail?size=thumbnail"

    override fun personThumbnailUrl(personId: String) = "$api/people/$personId/thumbnail"

    override fun people(): List<Person> =
        http.execute(get("/people?withHidden=false&size=1000")) { JSON.decodeFromString<PeoplePageDto>(it) }
            .people.filterNot { it.isHidden }.map { Person(it.id, it.name) }

    /** Streams the `preview` rendition (JPEG, 1440 px short side on the owner's server). Returns bytes written. */
    override fun downloadPreview(assetId: String, out: OutputStream): Long =
        http.executeRaw(get("/assets/$assetId/thumbnail?size=preview", accept = "image/*")) { it.body.byteStream().copyTo(out) }

    private fun filterBody(filter: SourceFilter, count: Int, page: Int? = null) = buildJsonObject {
        put("size", count)
        page?.let { put("page", it); put("order", "desc") }
        put("withExif", true)
        put("withPeople", true)
        putJsonObject("filter") {
            putJsonObject("type") { put("eq", "IMAGE") }
            if (filter.albumIds.isNotEmpty()) putJsonObject("albumIds") { put("any", filter.albumIds.toJsonArray()) }
            if (filter.personIds.isNotEmpty()) putJsonObject("personIds") { put("any", filter.personIds.toJsonArray()) }
            if (filter.favoritesOnly) putJsonObject("isFavorite") { put("eq", true) }
        }
    }

    private fun legacyBody(filter: SourceFilter, count: Int, page: Int? = null) = buildJsonObject {
        put("size", count)
        page?.let { put("page", it); put("order", "desc") }
        put("withExif", true)
        put("withPeople", true)
        put("type", "IMAGE")
        if (filter.albumIds.isNotEmpty()) put("albumIds", filter.albumIds.toJsonArray())
        if (filter.personIds.isNotEmpty()) put("personIds", filter.personIds.toJsonArray())
        if (filter.favoritesOnly) put("isFavorite", true)
    }

    private fun List<String>.toJsonArray() = buildJsonArray { forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }

    private fun get(path: String, auth: Boolean = true, accept: String = "application/json") =
        request(path, auth, accept).get().build()

    private fun post(path: String, body: JsonObject) =
        request(path, auth = true, accept = "application/json").post(body.toString().toRequestBody(JSON_MEDIA)).build()

    private fun request(path: String, auth: Boolean, accept: String) = Request.Builder().url(api + path).header("Accept", accept).apply {
        if (auth) header("x-api-key", apiKey)
    }

    private fun <T> OkHttpClient.execute(request: Request, parse: (String) -> T): T = executeRaw(request) { parse(it.body.string()) }

    private fun <T> OkHttpClient.executeRaw(request: Request, read: (Response) -> T): T {
        val response = try {
            newCall(request).execute()
        } catch (e: IOException) {
            throw ImmichException.Unreachable(e)
        }
        return response.use {
            when {
                it.code == 401 -> throw ImmichException.Unauthorized()
                it.code == 403 -> throw ImmichException.Unauthorized(missingPermission(it), forbidden = true)
                !it.isSuccessful -> throw ImmichException.Http(it.code)
                else -> try {
                    read(it)
                } catch (e: IOException) {
                    throw ImmichException.Unreachable(e)
                }
            }
        }
    }

    /** Immich answers "Missing required permission: asset.view" when a key lacks one. */
    private fun missingPermission(response: Response): String? = try {
        MISSING_PERMISSION.find(response.body.string())?.groupValues?.get(1)
    } catch (e: IOException) {
        null
    }

    companion object {
        private val MISSING_PERMISSION = Regex("""Missing required permission: ([A-Za-z.]+)""")
        private const val PING_TIMEOUT_S = 3L
        private const val ROUNDS = 4
        private const val ALBUM_PAGE_SIZE = 1000
        private const val MAX_ALBUM_PAGES = 50
        private const val ALBUM_CACHE_NANOS = 3_600_000_000_000L
        private val JSON_MEDIA = "application/json".toMediaType()
        internal val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** Normalizes user input to the API base (always ending in `/api`). */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "Server URL must start with http:// or https://" }
            return trimmed.removeSuffix("/api").trimEnd('/') + "/api"
        }
    }
}
