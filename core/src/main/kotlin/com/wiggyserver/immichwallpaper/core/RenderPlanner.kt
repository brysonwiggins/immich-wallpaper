package com.wiggyserver.immichwallpaper.core

/** Draw [crop] of [asset] into [dest] on the canvas. */
data class Region(val asset: Asset, val dest: PixelRect, val crop: PixelRect)

/**
 * Everything the renderer needs for one set call: canvas size, photo regions, seam rectangles
 * (filled with blurred edge / dominant color) and, for the CROPS method, the crop hints.
 */
data class ScreenRender(
    val job: ScreenJob,
    val canvas: Size,
    val regions: List<Region>,
    val seams: List<PixelRect>,
    val cropHints: Map<Int, PixelRect>?,
)

/**
 * Turns a [SlidePlan] into concrete render instructions for each [ScreenJob]. A pair is split at
 * the center of its zone (the Fold's hinge, occlusion NONE) with a [seamWidthPx] gap.
 */
class RenderPlanner(
    private val seamWidthPx: Int = 16,
    private val faceAware: Boolean = true,
    private val cropPlanner: CropPlanner = CropPlanner(),
) {
    fun plan(slide: SlidePlan, jobs: List<ScreenJob>): List<ScreenRender> = jobs.map { job ->
        // PLAIN jobs have a single viewport, so this is just the plain image size.
        val composite = CompositeLayout.of(job.viewports)
        val regions = mutableListOf<Region>()
        val seams = mutableListOf<PixelRect>()
        composite.zones.forEach { (viewport, zone) ->
            val safe = when (job.method) {
                ApplyMethod.CROPS -> job.screen.safeZone
                ApplyMethod.PLAIN -> effectiveSafeZone(job.screen, viewport.size)
            }
            when (val layout = slide.layouts.getValue(viewport)) {
                is Layout.Single -> regions += region(layout.asset, zone, safe)
                is Layout.Pair -> {
                    val mid = zone.left + zone.width / 2
                    val half = seamWidthPx / 2
                    val left = PixelRect(zone.left, zone.top, mid - half, zone.bottom)
                    val right = PixelRect(mid + (seamWidthPx - half), zone.top, zone.right, zone.bottom)
                    regions += region(layout.left, left, safe)
                    regions += region(layout.right, right, safe)
                    seams += PixelRect(left.right, zone.top, right.left, zone.bottom)
                }
            }
        }
        ScreenRender(job, composite.canvas, regions, seams, if (job.method == ApplyMethod.CROPS) composite.cropHints else null)
    }

    private fun region(asset: Asset, dest: PixelRect, safe: SafeZone): Region {
        val target = Size(dest.width, dest.height)
        val crop = if (faceAware) cropPlanner.crop(asset.size, target, asset.faces, safe) else cropPlanner.crop(asset.size, target)
        return Region(asset, dest, crop)
    }

    /**
     * For a plain [image] the system center-crops per orientation; map each orientation's safe zone
     * back into image coordinates and keep the most restrictive.
     */
    fun effectiveSafeZone(screen: Screen, image: Size): SafeZone {
        if (!screen.rotates) return screen.safeZone
        var top = 0.0
        var bottom = 0.0
        screen.viewports().forEach { v ->
            val scale = maxOf(v.size.width.toDouble() / image.width, v.size.height.toDouble() / image.height)
            val visibleH = v.size.height / scale
            val offset = (image.height - visibleH) / 2
            top = maxOf(top, (offset + screen.safeZone.top * visibleH) / image.height)
            bottom = maxOf(bottom, (offset + screen.safeZone.bottom * visibleH) / image.height)
        }
        return SafeZone(top, bottom)
    }
}
