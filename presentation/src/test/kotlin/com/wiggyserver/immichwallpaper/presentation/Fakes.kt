package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.data.Album
import com.wiggyserver.immichwallpaper.data.AppConfig
import com.wiggyserver.immichwallpaper.data.ConnectionResult
import com.wiggyserver.immichwallpaper.data.ImmichException
import com.wiggyserver.immichwallpaper.data.OfflineSlot
import com.wiggyserver.immichwallpaper.data.OfflineSlots
import com.wiggyserver.immichwallpaper.data.Person
import com.wiggyserver.immichwallpaper.data.PhotoLibrary
import com.wiggyserver.immichwallpaper.data.PhotoPage
import com.wiggyserver.immichwallpaper.data.SlotResult
import com.wiggyserver.immichwallpaper.data.SlotStatus
import com.wiggyserver.immichwallpaper.data.SourceFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

internal fun snapshotOf(
    configured: Boolean = true,
    serviceOn: Boolean = true,
    batteryExempt: Boolean = true,
    queued: Int = 3,
    offlineReady: Int = 10,
    offlineTotal: Int = 10,
    lastRefillFailure: com.wiggyserver.immichwallpaper.data.FailReason? = null,
    localNetworkAllowed: Boolean = true,
    poolSize: Int? = null,
) = AppSnapshot(
    configured, serviceOn, batteryExempt, queued, 3, emptyList(), offlineReady, offlineTotal,
    lastSwap = null, nowShowing = listOf("shown-1"), lastRefillFailure = lastRefillFailure, screens = listOf("COVER 1248x1972"), capabilities = Capabilities(true, true), localNetworkAllowed = localNetworkAllowed, poolSize = poolSize,
)

internal class FakeApp(config: AppConfig = AppConfig()) : AppPort {
    private val state = MutableStateFlow(config)
    override val config: StateFlow<AppConfig> = state
    var savedKey: String? = null
    var connection: ConnectionResult = ConnectionResult.Ok("3.2.0")
    val tested = mutableListOf<Pair<String, String?>>()
    val saved = mutableListOf<Pair<String, String?>>()
    var random = (1..10).map { "r$it" }
    var randomFailure: Exception? = null
    var snap: AppSnapshot = snapshotOf()
    var sideBySide = true
    var swaps = 0
    var rebuilds = 0
    var resets = 0
    var nowShowing = listOf<String>()
    var localAllowed = true
    var localServer = false

    override fun localNetworkAllowed() = localAllowed
    override fun isLocalServer(url: String) = localServer

    override fun hidePhotos(ids: List<String>) = updateConfig { it.copy(filter = it.filter.copy(excludedAssetIds = (it.filter.excludedAssetIds + ids).distinct())) }
    override fun unhidePhoto(id: String) = updateConfig { it.copy(filter = it.filter.copy(excludedAssetIds = it.filter.excludedAssetIds - id)) }

    override fun updateConfig(change: (AppConfig) -> AppConfig) { state.value = change(state.value) }
    override fun hasApiKey() = savedKey != null
    override fun testConnection(url: String, key: String?): ConnectionResult { tested += url to key; return connection }
    override fun saveServer(url: String, key: String?) {
        saved += url to key
        key?.let { savedKey = it }
        updateConfig { it.copy(serverUrl = url) }
    }
    override fun randomPhotoIds(count: Int): List<String> { randomFailure?.let { throw it }; return random.take(count) }
    override fun snapshot() = snap
    override fun sideBySideAvailable() = sideBySide
    override fun swapNow() { swaps++ }
    val turnedOff = mutableListOf<Boolean>()
    override fun turnOff(resetWallpaper: Boolean) { turnedOff += resetWallpaper; snap = snap.copy(serviceOn = false) }
    override fun rebuildQueue() { rebuilds++ }
    override fun resetCapabilities() { resets++ }
}

internal fun photo(id: String) = Asset(id, Size(3, 4), null)

internal class FakeLibrary : PhotoLibrary {
    var albums = listOf(Album("a1", "Trip", 12, "t1"), Album("a2", "Family", 40, null))
    var people = listOf(Person("p1", "Ana"), Person("p2", ""))
    var failure: Exception? = null
    /** Total photos per source; pages are cut from it. */
    var total = 120
    val pageRequests = mutableListOf<Triple<SourceFilter, Int, Int>>()
    /** People in photo number i of any listing. */
    var peopleOf: (Int) -> Set<String> = { emptySet() }
    /** Which photo ids belong to the albums asked about. */
    var inAlbums: (String) -> Boolean = { false }
    val albumLookups = mutableListOf<List<String>>()

    override fun albums(): List<Album> { failure?.let { throw it }; return albums }
    override fun people(): List<Person> { failure?.let { throw it }; return people }
    override fun photos(filter: SourceFilter, page: Int, size: Int): PhotoPage {
        failure?.let { throw it }
        pageRequests += Triple(filter, page, size)
        val start = (page - 1) * size
        val prefix = filter.albumIds.firstOrNull() ?: filter.personIds.firstOrNull() ?: "all"
        val ids = (start until minOf(start + size, total)).map { photo("$prefix-$it").copy(personIds = peopleOf(it)) }
        return PhotoPage(ids, if (start + size < total) page + 1 else null)
    }
    override fun albumPhotoIds(albumIds: List<String>): Set<String> {
        albumLookups += albumIds
        return if (albumIds.isEmpty()) emptySet() else (0 until total).flatMap { i -> listOf("p1-$i", "p2-$i", "all-$i") }.filter(inAlbums).toSet()
    }
    /** Favorite photos per album id (null: the whole library). */
    var favorites: (String?) -> Int = { 0 }
    val favoriteLookups = mutableListOf<String?>()
    override fun favoriteCount(albumId: String?): Int { favoriteLookups += albumId; failure?.let { throw it }; return favorites(albumId) }
    override fun thumbnailUrl(assetId: String) = "https://x/api/assets/$assetId/thumbnail"
    override fun personThumbnailUrl(personId: String) = "https://x/api/people/$personId/thumbnail"
}

internal class FakeSlots : OfflineSlots {
    val map = sortedMapOf<Int, OfflineSlot>()
    var result = SlotResult.Ready
    var failure: Exception? = null

    override fun slots() = map.values.toList()
    override fun setSlot(index: Int, assetId: String): SlotResult {
        failure?.let { throw it }
        if (result != SlotResult.NotFound) map[index] = OfflineSlot(index, assetId, if (result == SlotResult.Ready) SlotStatus.READY else SlotStatus.PENDING)
        return result
    }
    override fun clearSlot(index: Int) { map.remove(index) }
}

internal val unreachable = ImmichException.Unreachable(IOException("no route"))
