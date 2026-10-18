package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Size
import java.io.File
import java.io.OutputStream

/**
 * The few raster operations the composer needs. [I] is the backend's image type
 * (java.awt BufferedImage for tests, android.graphics.Bitmap on the device).
 */
interface RasterBackend<I : Any> {
    /** Pixel size of an encoded image, read from its header (no full decode). */
    fun imageSize(file: File): Size

    /** Decodes only [region] of [file], subsampled by [sampleSize] (a power of two). */
    fun decodeRegion(file: File, region: PixelRect, sampleSize: Int): I

    fun createCanvas(size: Size): I

    /** Draws all of [image] scaled into [dest] with bilinear filtering. */
    fun draw(canvas: I, image: I, dest: PixelRect)

    fun fill(canvas: I, rect: PixelRect, argb: Int)

    fun fillHorizontalGradient(canvas: I, rect: PixelRect, leftArgb: Int, rightArgb: Int)

    fun averageColor(canvas: I, rect: PixelRect): Int

    fun encodeJpeg(canvas: I, out: OutputStream, quality: Int)

    /** Frees native memory (Bitmap.recycle); never hold images between swaps. */
    fun release(image: I)
}
