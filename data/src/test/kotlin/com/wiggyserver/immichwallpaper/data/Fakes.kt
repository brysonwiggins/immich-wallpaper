package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.core.Screens
import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.core.Size
import java.io.OutputStream
import java.time.LocalDateTime

internal fun portraitAsset(id: String, day: Int = 1) = Asset(id, Size(3000, 4000), LocalDateTime.of(2024, 5, day, 10, 0))
internal fun landscapeAsset(id: String) = Asset(id, Size(4000, 3000), LocalDateTime.of(2024, 5, 1, 10, 0))

internal class FakeApi(var pool: List<Asset> = (1..40).map { portraitAsset("p$it", day = it % 28 + 1) }) : ImmichApi {
    var randomCalls = 0
    var faceCalls = mutableListOf<String>()
    var downloads = mutableListOf<String>()
    var failWith: Exception? = null
    var failFacesWith: Exception? = null
    var failDownloadWith: Exception? = null
    var remote = mutableMapOf<String, RemoteAsset?>()
    private var cursor = 0

    override fun randomAssets(filter: SourceFilter, count: Int): List<Asset> {
        failWith?.let { throw it }
        randomCalls++
        if (pool.isEmpty()) return emptyList()
        return List(count) { pool[(cursor + it) % pool.size] }.distinct().also { cursor += count }
    }

    override fun asset(id: String): RemoteAsset? {
        failWith?.let { throw it }
        return if (remote.containsKey(id)) remote[id] else pool.firstOrNull { it.id == id }?.let { RemoteAsset(it, "v1") }
    }

    override fun faces(assetId: String): List<Face> {
        failFacesWith?.let { throw it }
        faceCalls += assetId
        return emptyList()
    }

    override fun downloadPreview(assetId: String, out: OutputStream): Long {
        failWith?.let { throw it }
        failDownloadWith?.let { throw it }
        downloads += assetId
        val bytes = "img:$assetId".toByteArray()
        out.write(bytes)
        return bytes.size.toLong()
    }
}

/** Writes a text description of what it was asked to draw, and checks every source file exists. */
internal class FakeRenderer : SlideRenderer {
    var failNext = false
    /** Runs (once) inside the first render, to act while a refill or sync is in progress. */
    var duringRender: (() -> Unit)? = null
    override fun render(plan: com.wiggyserver.immichwallpaper.core.ScreenRender, sources: Map<String, java.io.File>, out: OutputStream) {
        duringRender?.let { duringRender = null; it() }
        if (failNext) { failNext = false; error("render failed") }
        plan.regions.forEach { check(sources.getValue(it.asset.id).isFile) { "missing source ${it.asset.id}" } }
        out.write("which=${plan.job.which} regions=${plan.regions.map { it.asset.id }}".toByteArray())
    }
}

internal class FakeNetwork(override var isOnline: Boolean = true, override var isUnmetered: Boolean = true) : NetworkMonitor

internal fun foldContext(settings: Settings = Settings(), target: ApplyTarget = settings.applyTarget) = RenderContext(
    screens = listOf(Screens.FOLD8_COVER, Screens.FOLD8_INNER),
    active = Screens.FOLD8_COVER,
    capabilities = Capabilities(perScreenFlags = true, cropsApi = true),
    settings = settings.copy(applyTarget = target),
    filter = SourceFilter(),
)
