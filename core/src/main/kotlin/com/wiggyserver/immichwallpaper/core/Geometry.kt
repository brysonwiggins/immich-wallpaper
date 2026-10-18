package com.wiggyserver.immichwallpaper.core

/** Pixel dimensions. */
data class Size(val width: Int, val height: Int) {
    val ratio: Double get() = if (height == 0) 1.0 else width.toDouble() / height
    val isLandscape: Boolean get() = width > height
    fun rotated() = Size(height, width)
}

/** A rectangle in pixels, [left, right) x [top, bottom). */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** A rectangle in fractions (0..1) of some image, e.g. a face box. */
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val area: Double get() = width * height

    fun union(other: Box) = Box(
        minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom),
    )
}
