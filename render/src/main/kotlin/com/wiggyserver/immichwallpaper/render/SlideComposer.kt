package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.ScreenRender
import com.wiggyserver.immichwallpaper.data.SlideRenderer
import java.io.File
import java.io.OutputStream

/**
 * Draws a [ScreenRender]: black canvas → each photo region (decoding only its crop, subsampled to
 * just cover the destination) → seams filled with a gradient between the darkened colors at the
 * two photo edges → JPEG. Images are released as soon as they are drawn.
 */
class SlideComposer<I : Any>(
    private val backend: RasterBackend<I>,
    private val quality: Int = 92,
    private val seamSampleWidth: Int = 8,
    private val seamDarken: Double = 0.25,
) : SlideRenderer {

    override fun render(plan: ScreenRender, sources: Map<String, File>, out: OutputStream) {
        val canvas = backend.createCanvas(plan.canvas)
        try {
            backend.fill(canvas, PixelRect(0, 0, plan.canvas.width, plan.canvas.height), BLACK)
            plan.regions.forEach { region ->
                val file = sources[region.asset.id] ?: error("no source image for ${region.asset.id}")
                val sourceSize = backend.imageSize(file)
                val crop = RenderMath.scaleRect(region.crop, region.asset.size, sourceSize)
                val image = backend.decodeRegion(file, crop, RenderMath.sampleSize(crop, region.dest))
                try {
                    backend.draw(canvas, image, region.dest)
                } finally {
                    backend.release(image)
                }
            }
            plan.seams.forEach { seam ->
                val left = backend.averageColor(canvas, RenderMath.leftStrip(seam, seamSampleWidth))
                val right = backend.averageColor(canvas, RenderMath.rightStrip(seam, seamSampleWidth, plan.canvas.width))
                backend.fillHorizontalGradient(canvas, seam, RenderMath.darken(left, seamDarken), RenderMath.darken(right, seamDarken))
            }
            backend.encodeJpeg(canvas, out, quality)
        } finally {
            backend.release(canvas)
        }
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
    }
}
