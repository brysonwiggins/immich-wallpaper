package com.wiggyserver.immichwallpaper.render.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.render.RasterBackend
import com.wiggyserver.immichwallpaper.render.RenderMath
import java.io.File
import java.io.OutputStream

/** [RasterBackend] on android.graphics. Decodes only the needed region; every bitmap is recycled by the composer. */
class AndroidBackend : RasterBackend<Bitmap> {
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun imageSize(file: File): Size {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        require(opts.outWidth > 0 && opts.outHeight > 0) { "cannot read image size: ${file.name}" }
        return Size(opts.outWidth, opts.outHeight)
    }

    override fun decodeRegion(file: File, region: PixelRect, sampleSize: Int): Bitmap {
        val decoder = if (Build.VERSION.SDK_INT >= 31) {
            BitmapRegionDecoder.newInstance(file.path)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(file.path, false)
        }
        try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return decoder.decodeRegion(region.toRect(), opts) ?: error("cannot decode ${file.name}")
        } finally {
            decoder.recycle()
        }
    }

    override fun createCanvas(size: Size): Bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)

    override fun draw(canvas: Bitmap, image: Bitmap, dest: PixelRect) {
        Canvas(canvas).drawBitmap(image, null, dest.toRect(), filterPaint)
    }

    override fun fill(canvas: Bitmap, rect: PixelRect, argb: Int) {
        Canvas(canvas).drawRect(rect.toRect(), Paint().apply { color = argb })
    }

    override fun fillHorizontalGradient(canvas: Bitmap, rect: PixelRect, leftArgb: Int, rightArgb: Int) {
        val paint = Paint().apply {
            shader = LinearGradient(rect.left.toFloat(), 0f, rect.right.toFloat(), 0f, leftArgb, rightArgb, Shader.TileMode.CLAMP)
        }
        Canvas(canvas).drawRect(rect.toRect(), paint)
    }

    override fun averageColor(canvas: Bitmap, rect: PixelRect): Int {
        if (rect.width <= 0 || rect.height <= 0) return RenderMath.average(IntArray(0))
        val pixels = IntArray(rect.width * rect.height)
        canvas.getPixels(pixels, 0, rect.width, rect.left, rect.top, rect.width, rect.height)
        return RenderMath.average(pixels)
    }

    override fun encodeJpeg(canvas: Bitmap, out: OutputStream, quality: Int) {
        check(canvas.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encoding failed" }
    }

    override fun release(image: Bitmap) = image.recycle()

    private fun PixelRect.toRect() = Rect(left, top, right, bottom)
}
