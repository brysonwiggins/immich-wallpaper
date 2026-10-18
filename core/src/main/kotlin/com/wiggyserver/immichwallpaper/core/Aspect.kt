package com.wiggyserver.immichwallpaper.core

enum class AspectClass { LANDSCAPE, PORTRAIT, SQUARE }

/** Landscape if width/height >= [threshold], portrait if height/width >= [threshold], otherwise square. */
class AspectClassifier(private val threshold: Double = 1.2) {
    fun classify(size: Size): AspectClass {
        if (size.width <= 0 || size.height <= 0) return AspectClass.SQUARE
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        return when {
            w >= h * threshold -> AspectClass.LANDSCAPE
            h >= w * threshold -> AspectClass.PORTRAIT
            else -> AspectClass.SQUARE
        }
    }
}
