package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Size
import kotlin.math.roundToInt

/** Backend-independent pixel math for the composer. */
object RenderMath {
    /** Maps [rect] in an image of size [from] onto the same region of an image of size [to] (e.g. original → preview). */
    fun scaleRect(rect: PixelRect, from: Size, to: Size): PixelRect {
        val sx = to.width.toDouble() / from.width
        val sy = to.height.toDouble() / from.height
        return PixelRect(
            (rect.left * sx).roundToInt().coerceIn(0, to.width),
            (rect.top * sy).roundToInt().coerceIn(0, to.height),
            (rect.right * sx).roundToInt().coerceIn(0, to.width),
            (rect.bottom * sy).roundToInt().coerceIn(0, to.height),
        )
    }

    /** Largest power-of-two subsampling of [crop] that still has at least [dest]'s pixels (memory: decode no more than needed). */
    fun sampleSize(crop: PixelRect, dest: PixelRect): Int {
        var s = 1
        while (crop.width / (s * 2) >= dest.width && crop.height / (s * 2) >= dest.height) s *= 2
        return s
    }

    fun average(argb: IntArray): Int {
        if (argb.isEmpty()) return 0xFF000000.toInt()
        var r = 0L
        var g = 0L
        var b = 0L
        argb.forEach { r += (it shr 16) and 0xFF; g += (it shr 8) and 0xFF; b += it and 0xFF }
        val n = argb.size
        fun avg(sum: Long) = ((sum + n / 2) / n).toInt()
        return (0xFF shl 24) or (avg(r) shl 16) or (avg(g) shl 8) or avg(b)
    }

    fun darken(argb: Int, amount: Double): Int {
        fun ch(shift: Int) = (((argb shr shift) and 0xFF) * (1 - amount)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun leftStrip(seam: PixelRect, width: Int) = PixelRect((seam.left - width).coerceAtLeast(0), seam.top, seam.left, seam.bottom)

    fun rightStrip(seam: PixelRect, width: Int, canvasWidth: Int) =
        PixelRect(seam.right, seam.top, (seam.right + width).coerceAtMost(canvasWidth), seam.bottom)
}
