package com.wiggyserver.immichwallpaper

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.data.Album
import com.wiggyserver.immichwallpaper.data.ImmichApi
import com.wiggyserver.immichwallpaper.data.ImmichClient
import com.wiggyserver.immichwallpaper.data.ImmichException
import com.wiggyserver.immichwallpaper.data.Person
import com.wiggyserver.immichwallpaper.data.PhotoLibrary
import com.wiggyserver.immichwallpaper.data.PhotoPage
import com.wiggyserver.immichwallpaper.data.SourceFilter
import java.io.OutputStream

/**
 * [ImmichApi] over whatever server URL and key are configured right now. Rebuilds the client when
 * either changes; with nothing configured every call fails as [ImmichException.Unauthorized], which
 * the repositories already treat as "stop and report".
 */
class ConfiguredImmichApi(
    private val serverUrl: () -> String,
    private val apiKey: () -> String?,
    private val build: (String, String) -> ImmichClient = { url, key -> ImmichClient(url, key, log = { AppLog.log("IMMICH", it) }) },
) : ImmichApi, PhotoLibrary {
    private var cached: Triple<String, String, ImmichClient>? = null

    @Synchronized
    fun client(): ImmichClient {
        val url = serverUrl()
        val key = apiKey()
        if (url.isBlank() || key.isNullOrBlank()) throw ImmichException.Unauthorized()
        cached?.let { (u, k, c) -> if (u == url && k == key) return c }
        val client = try {
            build(url, key)
        } catch (e: IllegalArgumentException) {
            throw ImmichException.Unauthorized() // invalid URL: same outcome as "not configured"
        }
        cached = Triple(url, key, client)
        return client
    }

    override fun randomAssets(filter: SourceFilter, count: Int): List<Asset> = client().randomAssets(filter, count)
    override fun asset(id: String) = client().asset(id)
    override fun faces(assetId: String): List<Face> = client().faces(assetId)
    override fun downloadPreview(assetId: String, out: OutputStream): Long = client().downloadPreview(assetId, out)

    override fun albums(): List<Album> = client().albums()
    override fun people(): List<Person> = client().people()
    override fun photos(filter: SourceFilter, page: Int, size: Int): PhotoPage = client().photos(filter, page, size)
    override fun albumPhotoIds(albumIds: List<String>): Set<String> = client().albumPhotoIds(albumIds)
    override fun favoriteCount(albumId: String?): Int = client().favoriteCount(albumId)

    // UI image URLs: empty (nothing loads) while no server is configured.
    override fun thumbnailUrl(assetId: String) = runCatching { client().thumbnailUrl(assetId) }.getOrDefault("")
    override fun personThumbnailUrl(personId: String) = runCatching { client().personThumbnailUrl(personId) }.getOrDefault("")
}
