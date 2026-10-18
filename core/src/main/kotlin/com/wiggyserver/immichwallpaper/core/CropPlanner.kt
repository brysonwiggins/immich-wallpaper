package com.wiggyserver.immichwallpaper.core

import kotlin.math.roundToInt

/**
 * Plans a fill crop of [source] with the aspect of [target]. Faces stay inside the crop
 * (all of them if they fit, otherwise the largest) and, vertically, inside the [SafeZone] band,
 * clear of the lock screen clock and controls. Without faces it center-crops.
 */
class CropPlanner {
    fun crop(source: Size, target: Size, faces: List<Face> = emptyList(), safe: SafeZone = SafeZone.NONE): PixelRect {
        val (cropW, cropH) = cropSize(source, target)
        val focus = focusBox(faces, source, cropW, cropH)

        val left = if (focus == null) {
            (source.width - cropW) / 2.0
        } else {
            focus.centerX - cropW / 2.0
        }
        val top = if (focus == null) {
            (source.height - cropH) / 2.0
        } else {
            // Put the focus center at the middle of the safe band.
            val anchor = (safe.top + (1 - safe.bottom)) / 2
            focus.centerY - anchor * cropH
        }
        val l = left.roundToInt().coerceIn(0, source.width - cropW)
        val t = top.roundToInt().coerceIn(0, source.height - cropH)
        return PixelRect(l, t, l + cropW, t + cropH)
    }

    private fun cropSize(source: Size, target: Size): kotlin.Pair<Int, Int> =
        if (source.ratio > target.ratio) {
            (source.height * target.ratio).roundToInt().coerceAtMost(source.width) to source.height
        } else {
            source.width to (source.width / target.ratio).roundToInt().coerceAtMost(source.height)
        }

    private class PixelBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
    }

    private fun focusBox(faces: List<Face>, source: Size, cropW: Int, cropH: Int): PixelBox? {
        if (faces.isEmpty()) return null
        fun Box.toPixels() = PixelBox(left * source.width, top * source.height, right * source.width, bottom * source.height)
        val union = faces.map { it.box }.reduce(Box::union).toPixels()
        val fits = union.right - union.left <= cropW && union.bottom - union.top <= cropH
        return if (fits) union else faces.maxBy { it.box.area }.box.toPixels()
    }
}
