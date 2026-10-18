package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.Box
import com.wiggyserver.immichwallpaper.core.Size
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ImmichClientTest {
    private val key = "test-key"
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var filterBodyStatus = 200
    private var legacyMetadataStatus = 200
    private var authorized = true
    private val routes = mutableMapOf<String, MockResponse>()
    /** When set, answers each album lookup (metadata search) by its request body. */
    private var metadataFor: ((String) -> String)? = null
    /** Paths the key may not use, with the permission Immich names in its 403. */
    private val denied = mutableMapOf<String, String>()

    private fun fixture(name: String) =
        javaClass.getResource("/fixtures/immich-3.2.0/$name")!!.readText()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                routes[path]?.let { return it }
                if (path == "/api/search/metadata") metadataFor?.let { return json(it(request.body!!.utf8())) }
                if (path == "/api/server/version") return json(fixture("server-version.json"))
                if (!authorized || request.headers["x-api-key"] != key) return json("""{"message":"Invalid API key"}""", 401)
                denied[path]?.let { return json("""{"message":"Missing required permission: $it","error":"Forbidden","statusCode":403}""", 403) }
                return when {
                    path == "/api/users/me" -> json(fixture("users-me.json"))
                    path == "/api/search/random" -> {
                        val isFilter = request.body!!.utf8().contains("\"filter\"")
                        if (isFilter && filterBodyStatus != 200) json("""{"message":"bad"}""", filterBodyStatus)
                        else json(fixture("search-random.json"))
                    }
                    path == "/api/search/metadata" -> {
                        val isFilter = request.body!!.utf8().contains("\"filter\"")
                        if (!isFilter && legacyMetadataStatus != 200) json("""{"message":"bad"}""", legacyMetadataStatus)
                        else json(fixture("search-metadata.json"))
                    }
                    path == "/api/albums" -> json(fixture("albums.json"))
                    path == "/api/people" -> json(fixture("people.json"))
                    path == "/api/faces" -> json(fixture("faces-2.json"))
                    path == "/api/assets/00000000-0000-4000-8000-000000000021" -> json(fixture("asset.json"))
                    path.startsWith("/api/assets/") && path.endsWith("/thumbnail") ->
                        MockResponse.Builder().code(200).addHeader("Content-Type", "image/jpeg").body(Buffer().write(ByteArray(1234) { 7 })).build()
                    path.startsWith("/api/assets/") -> json("""{"message":"Not found"}""", 404)
                    else -> json("""{"message":"?"}""", 404)
                }
            }
        }
        server.start()
    }

    @AfterEach
    fun tearDown() = server.close()

    private val logged = mutableListOf<String>()
    private fun client(apiKey: String = key) = ImmichClient(server.url("/").toString(), apiKey, log = { logged += it })

    /** Every album lookup answers with the five photos of search-metadata.json, on one page. */
    private fun albumsHoldFixturePhotos() {
        routes["/api/search/metadata"] = json(fixture("search-metadata.json").replace("\"nextPage\": \"2\"", "\"nextPage\": null"))
    }

    private fun randomBodies() = requests.filter { it.url.encodedPath == "/api/search/random" }.map { it.body!!.utf8() }

    @Test
    fun `connection test reports the server version`() {
        assertEquals(ConnectionResult.Ok("3.2.0"), client().testConnection())
    }

    @Test
    fun `connection test reports a rejected key`() {
        assertEquals(ConnectionResult.Unauthorized, client("wrong").testConnection())
    }

    @Test
    fun `connection test names every permission the key is missing`() {
        denied["/api/albums"] = "album.read"
        denied["/api/assets/00000000-0000-4000-8000-000000000021/thumbnail"] = "asset.view"
        denied["/api/faces"] = "face.read"
        assertEquals(ConnectionResult.MissingPermissions(listOf("asset.view", "album.read", "face.read")), client().testConnection())
    }

    @Test
    fun `connection test needs only the permissions the app uses`() {
        denied["/api/users/me"] = "user.read"
        assertEquals(ConnectionResult.Ok("3.2.0"), client().testConnection())
        assertEquals(REQUIRED_PERMISSIONS, listOf("asset.read", "asset.view", "album.read", "person.read", "face.read"))
    }

    @Test
    fun `without photo access the photo checks are reported, not skipped silently`() {
        denied["/api/search/random"] = "asset.read"
        assertEquals(ConnectionResult.MissingPermissions(listOf("asset.read")), client().testConnection())
    }

    @Test
    fun `a call the key may not make names the missing permission`() {
        denied["/api/albums"] = "album.read"
        val e = assertThrows<ImmichException.Unauthorized> { client().albums() }
        assertEquals("album.read", e.missingPermission)
        authorized = false
        assertNull(assertThrows<ImmichException.Unauthorized> { client().albums() }.missingPermission)
    }

    @Test
    fun `connection test reports an unreachable server`() {
        server.close()
        assertTrue(client().testConnection() is ConnectionResult.Unreachable)
    }

    @Test
    fun `random assets use the filter body with type IMAGE and the chosen people and favorites`() {
        client().randomAssets(SourceFilter(personIds = listOf("p1"), favoritesOnly = true), 25)
        val body = randomBodies().first()
        listOf("\"filter\"", "\"type\":{\"eq\":\"IMAGE\"}", "\"personIds\":{\"any\":[\"p1\"]}", "\"isFavorite\":{\"eq\":true}", "\"size\":25", "\"withPeople\":true")
            .forEach { assertTrue(body.contains(it), "body lacks $it: $body") }
    }

    private fun id(n: String) = "00000000-0000-4000-8000-00000000$n"
    private val albumPhotos = listOf("0021", "0023", "0025", "0026", "0028").map { id(it) }

    @Test
    fun `chosen albums are picked from their own photo lists, so photos others shared come too`() {
        // Immich's random search only covers your own (and partners') photos; album listings cover everyone's.
        albumsHoldFixturePhotos()
        val c = client()
        val assets = c.randomAssets(SourceFilter(albumIds = listOf("a1")), 25)
        assertEquals(albumPhotos.toSet(), assets.map { it.id }.toSet())
        assertTrue(randomBodies().isEmpty(), randomBodies().toString())
        val again = c.randomAssets(SourceFilter(albumIds = listOf("a1")), 3)
        assertEquals(3, again.size)
        assertEquals(1, requests.count { it.url.encodedPath == "/api/search/metadata" }) // the list is cached
    }

    @Test
    fun `album photos still follow the chosen people, favorites and hidden photos`() {
        albumsHoldFixturePhotos()
        val withPerson = client().randomAssets(SourceFilter(albumIds = listOf("a1"), personIds = listOf(id("0010"))), 25)
        assertTrue(withPerson.isNotEmpty() && withPerson.all { id("0010") in it.personIds }, withPerson.toString())
        assertTrue(client().randomAssets(SourceFilter(albumIds = listOf("a1"), favoritesOnly = true), 25).isEmpty()) // none are favorites
        val hidden = client().randomAssets(SourceFilter(albumIds = listOf("a1"), excludedAssetIds = listOf(id("0021"))), 25)
        assertEquals(albumPhotos.toSet() - id("0021"), hidden.map { it.id }.toSet())
    }

    @Test
    fun `an album that was deleted or unshared gives no photos instead of an error`() {
        legacyMetadataStatus = 400
        filterBodyStatus = 400
        routes["/api/search/metadata"] = json("""{"message":"Not found or no album.read access"}""", 400)
        val c = client()
        assertTrue(c.randomAssets(SourceFilter(albumIds = listOf("gone")), 25).isEmpty())
        assertTrue(logged.any { "unavailable" in it }, logged.toString())
        routes.remove("/api/search/metadata")
        legacyMetadataStatus = 200
        c.photos(SourceFilter(), 1, 5)
        // The album's 400 didn't make the client give up on the flat body for everything else.
        assertFalse(requests.last { it.url.encodedPath == "/api/search/metadata" }.body!!.utf8().contains("\"filter\""))
    }

    @Test
    fun `a server that ignores the filter is detected and the older format is used`() {
        // The fixture server answers the filter body with photos regardless of it, like Immich 3.0.1 did.
        val assets = client().randomAssets(SourceFilter(personIds = listOf(id("0010"))), 25)
        assertTrue(assets.all { id("0010") in it.personIds })
        assertTrue(logged.any { "ignored the filter" in it }, logged.toString())
        val later = randomBodies().drop(1)
        assertTrue(later.isNotEmpty() && later.all { "\"filter\"" !in it }, later.toString())
    }

    @Test
    fun `with the older format, several people are asked for one at a time`() {
        filterBodyStatus = 400
        client().randomAssets(SourceFilter(personIds = listOf(id("000d"), id("000e"))), 25)
        val legacy = randomBodies().filter { "\"filter\"" !in it }
        assertTrue(legacy.isNotEmpty())
        legacy.forEach { body -> assertEquals(1, Regex("\"personIds\":\\[\"[^\"]+\"\\]").findAll(body).count(), body) }
    }

    @Test
    fun `archived photos are left out of all photos, but not out of albums they were put in`() {
        // Sets the asset's own field (the first one after its id).
        fun String.set(field: String, from: String, to: String): String {
            val at = indexOf("\"$field\": $from", indexOf("\"id\": \"${id("0021")}\""))
            return replaceRange(at, at + "\"$field\": $from".length, "\"$field\": $to")
        }
        routes["/api/search/random"] = json(fixture("search-random.json").set("visibility", "\"timeline\"", "\"archive\""))
        val all = client().randomAssets(SourceFilter(), 25)
        assertTrue(all.isNotEmpty() && all.none { it.id == id("0021") })
        routes["/api/search/metadata"] = json(
            fixture("search-metadata.json").replace("\"nextPage\": \"2\"", "\"nextPage\": null").set("visibility", "\"timeline\"", "\"archive\""),
        )
        assertTrue(client().randomAssets(SourceFilter(albumIds = listOf("a1")), 25).any { it.id == id("0021") })
    }

    @Test
    fun `favorites only keeps favorites`() {
        val favorites = setOf(id("0021"), id("0023"))
        // Flip each chosen asset's own isFavorite (the first one after its id; people come later).
        val body = favorites.fold(fixture("search-random.json")) { acc, f ->
            val at = acc.indexOf("\"isFavorite\": false", acc.indexOf("\"id\": \"$f\""))
            acc.replaceRange(at, at + "\"isFavorite\": false".length, "\"isFavorite\": true")
        }
        routes["/api/search/random"] = json(body)
        val assets = client().randomAssets(SourceFilter(favoritesOnly = true), 25)
        assertEquals(favorites, assets.map { it.id }.toSet())
    }

    @Test
    fun `people are only checked when the server sends them`() {
        val noPeople = fixture("search-random.json").replace(Regex("\"people\": \\[[^\\]]*\\]"), "\"people\": []")
        routes["/api/search/random"] = json(noPeople)
        assertEquals(25, client().randomAssets(SourceFilter(personIds = listOf(id("000d"))), 25).size)
        assertTrue(logged.none { "ignored the filter" in it })
    }

    @Test
    fun `chosen people must appear in the photo`() {
        val assets = client().randomAssets(SourceFilter(personIds = listOf(id("0010"))), 25)
        assertTrue(assets.isNotEmpty() && assets.all { id("0010") in it.personIds })
    }

    @Test
    fun `hidden photos and people never come back from random search`() {
        val filter = SourceFilter(excludedAssetIds = listOf(id("0021")), excludedPersonIds = listOf(id("000d")))
        val assets = client().randomAssets(filter, 25)
        assertTrue(assets.isNotEmpty())
        assertTrue(assets.none { it.id == id("0021") || id("000d") in it.personIds }, assets.toString())
        assertTrue(assets.any { it.id == id("0026") }) // has other people only
        val body = requests.last { it.url.encodedPath == "/api/search/random" }.body!!.utf8()
        assertFalse(body.contains("excluded"), body) // exclusions are applied on the phone
    }

    @Test
    fun `hidden albums are resolved to their photos once and filtered out`() {
        routes["/api/search/metadata"] = json(fixture("search-metadata.json").replace("\"nextPage\": \"2\"", "\"nextPage\": null"))
        val c = client()
        val filter = SourceFilter(excludedAlbumIds = listOf("hidden"))
        val first = c.randomAssets(filter, 25)
        val second = c.randomAssets(filter, 25)
        val albumPhotos = listOf("0021", "0023", "0025", "0026", "0028").map(::id)
        assertTrue(first.isNotEmpty())
        assertTrue((first + second).none { it.id in albumPhotos })
        val lookups = requests.filter { it.url.encodedPath == "/api/search/metadata" }
        assertEquals(1, lookups.size) // cached
        assertTrue(lookups[0].body!!.utf8().contains("\"albumIds\":[\"hidden\"]"))
    }

    @Test
    fun `album photo lists are cached per album`() {
        routes["/api/search/metadata"] = json(fixture("search-metadata.json").replace("\"nextPage\": \"2\"", "\"nextPage\": null"))
        val c = client()
        assertEquals(5, c.albumPhotoIds(listOf("a")).size)
        c.albumPhotoIds(listOf("a", "b"))
        c.randomAssets(SourceFilter(excludedAlbumIds = listOf("b")), 25)
        val albums = requests.filter { it.url.encodedPath == "/api/search/metadata" }.map { it.body!!.utf8().substringAfter("albumIds\":[\"").take(1) }
        assertEquals(listOf("a", "b"), albums) // each album fetched once
        assertTrue(c.albumPhotoIds(emptyList()).isEmpty())
    }

    private fun page(vararg photos: Pair<String, Boolean>) =
        """{"assets":{"items":[${photos.joinToString(",") { (i, fav) -> """{"id":"${id(i)}","type":"IMAGE","width":3,"height":4,"isFavorite":$fav}""" }}],"nextPage":null}}"""

    @Test
    fun `favorite counts are the album's photos that really are favorites`() {
        metadataFor = { body ->
            when {
                body.contains("isFavorite") -> page("0021" to true, "0023" to true, "0099" to true, "0050" to false) // 0050: server ignored the filter
                body.contains("\"a1\"") -> page("0021" to true, "0023" to true, "0025" to false, "0050" to false)
                body.contains("\"a2\"") -> page("0026" to false)
                else -> emptyPage
            }
        }
        val c = client()
        assertEquals(2, c.favoriteCount("a1"))
        assertEquals(0, c.favoriteCount("a2"))
        assertEquals(3, c.favoriteCount(null)) // the whole library
        assertEquals(1, requests.count { it.url.encodedPath == "/api/search/metadata" && it.body!!.utf8().contains("isFavorite") }) // cached
    }

    private val emptyPage = """{"assets":{"items":[],"nextPage":null}}"""
    private val tripAlbum = id("0004") // its photos: 0021, 0023, 0025, 0026, 0028
    private fun albumsWithOnlyTripFilled() {
        val trip = fixture("search-metadata.json").replace("\"nextPage\": \"2\"", "\"nextPage\": null")
        metadataFor = { body -> if (body.contains(tripAlbum)) trip else emptyPage }
    }

    @Test
    fun `not in an album keeps only photos outside every album`() {
        albumsWithOnlyTripFilled()
        val assets = client().randomAssets(SourceFilter(notInAlbum = true), 25)
        val inTrip = listOf("0021", "0023", "0025", "0026", "0028").map(::id)
        assertEquals(20, assets.size)
        assertTrue(assets.none { it.id in inTrip })
        val body = requests.first { it.url.encodedPath == "/api/search/random" }.body!!.utf8()
        assertFalse(body.contains("albumIds"), body) // the album side is decided on the phone
        assertEquals(5, requests.count { it.url.encodedPath == "/api/search/metadata" }) // one lookup per album
    }

    @Test
    fun `not in an album plus an album keeps both`() {
        albumsWithOnlyTripFilled()
        val assets = client().randomAssets(SourceFilter(albumIds = listOf(tripAlbum), notInAlbum = true), 25)
        val inTrip = listOf("0021", "0023", "0025", "0026", "0028").map(::id)
        assertEquals(25, assets.size)
        assertTrue(assets.any { it.id in inTrip } && assets.any { it.id !in inTrip }, assets.map { it.id }.toString())
        assertFalse(requests.first { it.url.encodedPath == "/api/search/random" }.body!!.utf8().contains("albumIds"))
    }

    @Test
    fun `asks the server a few more times when hiding removes too many, then gives up`() {
        val everything = javaClass.getResource("/fixtures/immich-3.2.0/search-random.json")!!.readText()
            .let { Regex("\"id\": \"(00000000-0000-4000-8000-0000000000[0-9a-f]{2})\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
        val assets = client().randomAssets(SourceFilter(excludedAssetIds = everything), 25)
        assertTrue(assets.isEmpty())
        assertEquals(4, requests.count { it.url.encodedPath == "/api/search/random" })
    }

    @Test
    fun `without exclusions one random request is enough`() {
        client().randomAssets(SourceFilter(), 30)
        assertEquals(1, requests.count { it.url.encodedPath == "/api/search/random" })
    }

    @Test
    fun `an empty filter sends only the image type`() {
        client().randomAssets(SourceFilter(), 5)
        val body = requests.last().body!!.utf8()
        assertFalse(body.contains("albumIds") || body.contains("personIds") || body.contains("isFavorite"), body)
    }

    @Test
    fun `falls back to the legacy body for older servers and remembers it`() {
        filterBodyStatus = 400
        val c = client()
        assertEquals(25, c.randomAssets(SourceFilter(), 25).size)
        c.randomAssets(SourceFilter(), 25)
        val bodies = randomBodies()
        assertEquals(3, bodies.size) // filter (400), legacy, legacy
        assertTrue(bodies[1].contains("\"type\":\"IMAGE\""))
        assertFalse(bodies[2].contains("\"filter\""))
    }

    @Test
    fun `maps assets with orientation-corrected size, local time and people`() {
        val first = client().randomAssets(SourceFilter(), 25).first()
        assertEquals("00000000-0000-4000-8000-000000000021", first.id)
        assertEquals(Size(3000, 4000), first.size)
        assertEquals(LocalDateTime.parse("2026-03-26T21:34:19.207"), first.takenAt)
        val withPeople = client().randomAssets(SourceFilter(), 25).first { it.personIds.isNotEmpty() }
        assertTrue(withPeople.personIds.all { it.startsWith("00000000-") })
    }

    @Test
    fun `falls back to exif dimensions, swapped for rotated orientations, when width and height are missing`() {
        assertEquals(Size(3000, 4000), AssetDto(id = "x", type = "IMAGE", exifInfo = ExifDto(4000, 3000, "6")).toAsset()!!.size)
        assertEquals(Size(4000, 3000), AssetDto(id = "x", type = "IMAGE", exifInfo = ExifDto(4000, 3000, "1")).toAsset()!!.size)
        assertNull(AssetDto(id = "x", type = "IMAGE").toAsset())
        assertNull(AssetDto(id = "x", type = "VIDEO", width = 10, height = 10).toAsset())
        assertNull(AssetDto(id = "x", type = "IMAGE", width = 10, height = 10, isTrashed = true).toAsset())
    }

    @Test
    fun `faces are normalized to fractions of the reference image`() {
        val faces = client().faces("any")
        assertTrue(faces.isNotEmpty())
        val f = faces.first()
        assertEquals(Box(439 / 1440.0, 998 / 1920.0, 640 / 1440.0, 1244 / 1920.0), f.box)
        assertEquals("00000000-0000-4000-8000-00000000000d", f.personId)
    }

    @Test
    fun `asset details, or null when it was deleted`() {
        val remote = client().asset("00000000-0000-4000-8000-000000000021")!!
        assertEquals("2026-06-03T04:58:00.009Z", remote.updatedAt)
        assertEquals(Size(3000, 4000), remote.asset.size)
        assertNull(client().asset("00000000-0000-4000-8000-0000000000ff"))
    }

    @Test
    fun `lists albums and people`() {
        val albums = client().albums()
        assertTrue(albums.isNotEmpty())
        assertTrue(albums.all { it.id.isNotEmpty() && it.assetCount >= 0 })
        val people = client().people()
        assertTrue(people.isNotEmpty())
    }

    @Test
    fun `albums carry their cover asset`() {
        val album = client().albums().first { it.id == "00000000-0000-4000-8000-000000000004" }
        assertEquals("00000000-0000-4000-8000-000000000003", album.thumbnailAssetId)
        assertEquals(182, album.assetCount)
    }

    @Test
    fun `browses photos newest first, one page at a time`() {
        val page = client().photos(SourceFilter(albumIds = listOf("a1"), favoritesOnly = true), page = 1, size = 50)
        assertEquals(5, page.items.size)
        assertEquals("00000000-0000-4000-8000-000000000021", page.items.first().id)
        assertEquals(2, page.nextPage)
        val body = requests.last { it.url.encodedPath == "/api/search/metadata" }.body!!.utf8()
        assertTrue(body.contains("\"page\":1"), body)
        assertTrue(body.contains("\"size\":50"), body)
        assertTrue(body.contains("\"order\":\"desc\""), body)
        assertTrue(body.contains("\"type\":\"IMAGE\""), body)
        assertTrue(body.contains("\"albumIds\":[\"a1\"]"), body)
        assertTrue(body.contains("\"isFavorite\":true"), body)
    }

    @Test
    fun `photo browsing falls back to the filter body when the flat body is rejected`() {
        legacyMetadataStatus = 400
        val page = client().photos(SourceFilter(personIds = listOf("p1")), page = 2, size = 10)
        assertEquals(5, page.items.size)
        val body = requests.last { it.url.encodedPath == "/api/search/metadata" }.body!!.utf8()
        assertTrue(body.contains("\"filter\""), body)
        assertTrue(body.contains("\"personIds\":{\"any\":[\"p1\"]}"), body)
    }

    @Test
    fun `the last page has no next page`() {
        routes["/api/search/metadata"] = json("""{"assets":{"total":0,"count":0,"items":[],"nextPage":null}}""")
        val page = client().photos(SourceFilter(), page = 3, size = 10)
        assertTrue(page.items.isEmpty())
        assertNull(page.nextPage)
    }

    @Test
    fun `thumbnail urls point at the api`() {
        val c = client()
        assertEquals(server.url("/api/assets/x1/thumbnail?size=thumbnail").toString(), c.thumbnailUrl("x1"))
        assertEquals(server.url("/api/people/p1/thumbnail").toString(), c.personThumbnailUrl("p1"))
    }

    @Test
    fun `downloads the preview size into a stream`() {
        val out = ByteArrayOutputStream()
        assertEquals(1234L, client().downloadPreview("abc", out))
        assertEquals(1234, out.size())
        assertEquals("size=preview", requests.last().url.query)
    }

    @Test
    fun `sends the api key on every authenticated call`() {
        val c = client()
        c.albums(); c.people(); c.faces("x")
        requests.forEach { assertEquals(key, it.headers["x-api-key"]) }
    }

    @Test
    fun `a rejected key throws Unauthorized`() {
        authorized = false
        assertThrows<ImmichException.Unauthorized> { client().albums() }
    }

    @Test
    fun `other http errors throw Http with the status`() {
        routes["/api/albums"] = json("""{"message":"boom"}""", 500)
        val e = assertThrows<ImmichException.Http> { client().albums() }
        assertEquals(500, e.status)
    }

    @Test
    fun `network failures throw Unreachable`() {
        server.close()
        assertThrows<ImmichException.Unreachable> { client().albums() }
    }

    @Test
    fun `a slow server times out as Unreachable`() {
        routes["/api/albums"] = MockResponse.Builder().code(200).body("[]").headersDelay(2, TimeUnit.SECONDS).build()
        val fast = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        assertThrows<ImmichException.Unreachable> { ImmichClient(server.url("/").toString(), key, fast).albums() }
    }

    @Test
    fun `normalizes the server url`() {
        assertEquals("https://photos.example.com/api", ImmichClient.normalizeUrl(" https://photos.example.com/ "))
        assertEquals("http://nas:2283/immich/api", ImmichClient.normalizeUrl("http://nas:2283/immich/api/"))
        assertThrows<IllegalArgumentException> { ImmichClient.normalizeUrl("photos.example.com") }
    }
}
