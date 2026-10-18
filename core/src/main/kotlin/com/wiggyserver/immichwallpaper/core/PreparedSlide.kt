package com.wiggyserver.immichwallpaper.core

/** One rendered image and how to apply it. */
data class SlideFile(
    val which: Int,
    val method: ApplyMethod,
    val fileName: String,
    /** AOSP orientation key → crop, for [ApplyMethod.CROPS]; null for plain images. */
    val cropHints: Map<Int, PixelRect>?,
)

/** A slide ready to apply at screen-off: one file per set call. Never needs the network. */
data class PreparedSlide(val id: String, val assetIds: List<String>, val files: List<SlideFile>)
