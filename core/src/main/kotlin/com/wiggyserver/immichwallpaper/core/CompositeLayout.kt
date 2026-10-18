package com.wiggyserver.immichwallpaper.core

/**
 * Canvas layout for the CROPS method: one zone per viewport, full resolution, left to right,
 * top-aligned. [cropHints] is the `SparseArray<Rect>` handed to `setStreamWithCrops`.
 */
data class CompositeLayout(val canvas: Size, val zones: Map<Viewport, PixelRect>) {
    val cropHints: Map<Int, PixelRect> get() = zones.entries.associate { (v, r) -> v.orientation.key to r }

    companion object {
        fun of(viewports: List<Viewport>): CompositeLayout {
            require(viewports.isNotEmpty()) { "need at least one viewport" }
            var x = 0
            val zones = viewports.associateWith { v ->
                PixelRect(x, 0, x + v.size.width, v.size.height).also { x += v.size.width }
            }
            return CompositeLayout(Size(x, viewports.maxOf { it.size.height }), zones)
        }
    }
}
