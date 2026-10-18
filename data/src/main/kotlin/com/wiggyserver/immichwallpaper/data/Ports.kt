package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.Screen
import com.wiggyserver.immichwallpaper.core.ScreenRender
import com.wiggyserver.immichwallpaper.core.Settings
import java.io.File
import java.io.OutputStream

/** The Immich calls the repositories need ([ImmichClient] implements it; tests use fakes). */
interface ImmichApi {
    fun randomAssets(filter: SourceFilter, count: Int): List<Asset>
    fun asset(id: String): RemoteAsset?
    fun faces(assetId: String): List<Face>
    fun downloadPreview(assetId: String, out: OutputStream): Long
}

/** One page of a photo listing; [nextPage] is null on the last page. */
data class PhotoPage(val items: List<Asset>, val nextPage: Int?)

/** Browsing the library for the UI: sources and the offline-set picker ([ImmichClient] implements it). */
interface PhotoLibrary {
    fun albums(): List<Album>
    fun people(): List<Person>
    fun photos(filter: SourceFilter, page: Int, size: Int): PhotoPage
    fun albumPhotoIds(albumIds: List<String>): Set<String>

    /** Favorite photos in the album, or in the whole library when [albumId] is null. Blocking, cached. */
    fun favoriteCount(albumId: String?): Int
    fun thumbnailUrl(assetId: String): String
    fun personThumbnailUrl(personId: String): String
}

/** The offline-set operations the UI needs ([OfflineSetRepository] implements it). */
interface OfflineSlots {
    fun slots(): List<OfflineSlot>
    fun setSlot(index: Int, assetId: String): SlotResult
    fun clearSlot(index: Int)
}

/** Draws one [ScreenRender] from source images (assetId → file) as a JPEG (`:render`). */
fun interface SlideRenderer {
    fun render(plan: ScreenRender, sources: Map<String, File>, out: OutputStream)
}

/** Everything about "now" that affects how slides are built. */
data class RenderContext(
    val screens: List<Screen>,
    val active: Screen,
    val capabilities: Capabilities,
    val settings: Settings,
    val filter: SourceFilter,
)

interface NetworkMonitor {
    val isOnline: Boolean
    val isUnmetered: Boolean
}

/** Persists the prepared queue across restarts (Room in the app). */
interface QueueStore {
    fun load(): List<PreparedSlide>
    fun save(slides: List<PreparedSlide>)
}

class InMemoryQueueStore : QueueStore {
    @Volatile private var slides = emptyList<PreparedSlide>()
    override fun load() = slides
    override fun save(slides: List<PreparedSlide>) { this.slides = slides.toList() }
}

enum class SlotStatus { PENDING, READY, MISSING }

/**
 * One of the up-to-10 offline photos. [asset] (with faces) and its kept source preview allow
 * re-rendering without the network; [slide] is the ready-to-apply result.
 */
data class OfflineSlot(
    val index: Int,
    val assetId: String,
    val status: SlotStatus,
    val updatedAt: String? = null,
    val asset: com.wiggyserver.immichwallpaper.core.Asset? = null,
    val slide: PreparedSlide? = null,
)

/** Persists the offline set and its rotation position (Room + DataStore in the app). */
interface OfflineSetStore {
    fun all(): List<OfflineSlot>
    fun put(slot: OfflineSlot)
    fun remove(index: Int)
    var rotationIndex: Int
}

class InMemoryOfflineSetStore : OfflineSetStore {
    private val slots = sortedMapOf<Int, OfflineSlot>()
    @Synchronized override fun all() = slots.values.toList()
    @Synchronized override fun put(slot: OfflineSlot) { slots[slot.index] = slot }
    @Synchronized override fun remove(index: Int) { slots.remove(index) }
    @Volatile override var rotationIndex = 0
}
