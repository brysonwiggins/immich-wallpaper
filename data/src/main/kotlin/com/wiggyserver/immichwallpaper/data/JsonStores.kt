package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyMethod
import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Box
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.core.SlideFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

// Small JSON files, written atomically (temp + rename). These replace Room/DataStore —
// at most 3 slides and 10 slots, and plain JVM keeps them unit-testable.

private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Reads [file] as [T], or null if it is missing or unreadable. Writes atomically. */
private class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>) {
    fun read(): T? = runCatching { if (file.isFile) JSON.decodeFromString(serializer, file.readText()) else null }.getOrNull()

    fun write(value: T) {
        file.parentFile?.mkdirs()
        FileStore(file.parentFile ?: File(".")).write(file.name) { it.write(JSON.encodeToString(serializer, value).toByteArray()) }
    }
}

// ---- DTOs (the :core types stay free of serialization) ----

@Serializable
private data class RectDto(val l: Int, val t: Int, val r: Int, val b: Int)

@Serializable
private data class SlideFileDto(val which: Int, val method: String, val fileName: String, val cropHints: Map<Int, RectDto>? = null)

@Serializable
private data class SlideDto(val id: String, val assetIds: List<String>, val files: List<SlideFileDto>)

@Serializable
private data class FaceBoxDto(val l: Double, val t: Double, val r: Double, val b: Double, val personId: String? = null)

@Serializable
private data class StoredAssetDto(
    val id: String,
    val width: Int,
    val height: Int,
    val takenAt: String? = null,
    val personIds: Set<String> = emptySet(),
    val faces: List<FaceBoxDto> = emptyList(),
)

@Serializable
private data class SlotDto(
    val index: Int,
    val assetId: String,
    val status: String,
    val updatedAt: String? = null,
    val asset: StoredAssetDto? = null,
    val slide: SlideDto? = null,
)

@Serializable
private data class OfflineSetDto(val slots: List<SlotDto> = emptyList(), val rotationIndex: Int = 0)

private fun PixelRect.dto() = RectDto(left, top, right, bottom)
private fun RectDto.model() = PixelRect(l, t, r, b)
private fun PreparedSlide.dto() = SlideDto(id, assetIds, files.map { f -> SlideFileDto(f.which, f.method.name, f.fileName, f.cropHints?.mapValues { it.value.dto() }) })
private fun SlideDto.model() = PreparedSlide(id, assetIds, files.map { f -> SlideFile(f.which, ApplyMethod.valueOf(f.method), f.fileName, f.cropHints?.mapValues { it.value.model() }) })
private fun Asset.dto() = StoredAssetDto(id, size.width, size.height, takenAt?.toString(), personIds, faces.map { FaceBoxDto(it.box.left, it.box.top, it.box.right, it.box.bottom, it.personId) })
private fun StoredAssetDto.model() = Asset(id, Size(width, height), takenAt?.let(LocalDateTime::parse), personIds, faces.map { Face(Box(it.l, it.t, it.r, it.b), it.personId) })
private fun OfflineSlot.dto() = SlotDto(index, assetId, status.name, updatedAt, asset?.dto(), slide?.dto())
private fun SlotDto.model() = OfflineSlot(index, assetId, SlotStatus.valueOf(status), updatedAt, asset?.model(), slide?.model())

class JsonQueueStore(file: File) : QueueStore {
    private val json = JsonFile(file, ListSerializer(SlideDto.serializer()))
    override fun load(): List<PreparedSlide> = runCatching { json.read()?.map { it.model() } }.getOrNull().orEmpty()
    override fun save(slides: List<PreparedSlide>) = json.write(slides.map { it.dto() })
}

class JsonOfflineSetStore(file: File) : OfflineSetStore {
    private val json = JsonFile(file, OfflineSetDto.serializer())
    private var state: OfflineSetDto = runCatching { json.read() }.getOrNull() ?: OfflineSetDto()
    private var slots: List<OfflineSlot> = runCatching { state.slots.map { it.model() } }.getOrDefault(emptyList())

    @Synchronized override fun all() = slots.sortedBy { it.index }

    @Synchronized override fun put(slot: OfflineSlot) = save(slots.filter { it.index != slot.index } + slot, rotationIndex)

    @Synchronized override fun remove(index: Int) = save(slots.filter { it.index != index }, rotationIndex)

    override var rotationIndex: Int
        @Synchronized get() = state.rotationIndex
        @Synchronized set(value) = save(slots, value)

    private fun save(newSlots: List<OfflineSlot>, rotation: Int) {
        slots = newSlots
        state = OfflineSetDto(newSlots.map { it.dto() }, rotation)
        json.write(state)
    }
}

/** Everything the user configures except the API key (kept encrypted by a [CredentialStore]). */
data class AppConfig(
    val serverUrl: String = "",
    val filter: SourceFilter = SourceFilter(),
    val settings: Settings = Settings(),
    /** Set when the first-run setup finishes; until then the app opens on setup. */
    val onboarded: Boolean = false,
)

/** Keystore-backed in the app; never stored in plain files. */
interface CredentialStore {
    fun apiKey(): String?
    fun setApiKey(key: String?)
}

@Serializable
private data class ConfigDto(
    val serverUrl: String = "",
    val albumIds: List<String> = emptyList(),
    val personIds: List<String> = emptyList(),
    val favoritesOnly: Boolean = false,
    val excludedAlbumIds: List<String> = emptyList(),
    val excludedPersonIds: List<String> = emptyList(),
    val excludedAssetIds: List<String> = emptyList(),
    val notInAlbum: Boolean = false,
    val settings: SettingsDto = SettingsDto(),
    val onboarded: Boolean = false,
)

@Serializable
private data class SettingsDto(
    val applyTarget: String = ApplyTarget.LOCK_AND_HOME.name,
    val sideBySide: Boolean = false,
    val cooldownMillis: Long = 2_000,
    val faceAwareCrop: Boolean = true,
    val wifiOnlyRefill: Boolean = false,
)

/** Observable app configuration persisted as JSON. Invalid content falls back to defaults. */
class ConfigRepository(file: File) {
    private val json = JsonFile(file, ConfigDto.serializer())
    private val state = MutableStateFlow(runCatching { json.read()?.model() }.getOrNull() ?: AppConfig())
    val config: StateFlow<AppConfig> = state.asStateFlow()

    @Synchronized
    fun update(change: (AppConfig) -> AppConfig) {
        val next = change(state.value)
        json.write(next.dto())
        state.value = next
    }

    private fun ConfigDto.model() = AppConfig(
        serverUrl,
        SourceFilter(albumIds, personIds, favoritesOnly, excludedAlbumIds, excludedPersonIds, excludedAssetIds, notInAlbum),
        Settings(ApplyTarget.valueOf(settings.applyTarget), settings.sideBySide, settings.cooldownMillis, settings.faceAwareCrop, settings.wifiOnlyRefill),
        onboarded,
    )

    private fun AppConfig.dto() = ConfigDto(
        serverUrl, filter.albumIds, filter.personIds, filter.favoritesOnly,
        filter.excludedAlbumIds, filter.excludedPersonIds, filter.excludedAssetIds, filter.notInAlbum,
        SettingsDto(settings.applyTarget.name, settings.sideBySide, settings.cooldownMillis, settings.faceAwareCrop, settings.wifiOnlyRefill),
        onboarded,
    )
}
