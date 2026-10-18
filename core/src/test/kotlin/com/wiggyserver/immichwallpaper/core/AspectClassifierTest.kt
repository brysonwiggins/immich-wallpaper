package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AspectClassifierTest {
    private val classifier = AspectClassifier()

    @Test
    fun `4 by 3 is landscape`() = assertEquals(AspectClass.LANDSCAPE, classifier.classify(Size(4000, 3000)))

    @Test
    fun `3 by 4 is portrait`() = assertEquals(AspectClass.PORTRAIT, classifier.classify(Size(3000, 4000)))

    @Test
    fun `square is square`() = assertEquals(AspectClass.SQUARE, classifier.classify(Size(2000, 2000)))

    @Test
    fun `threshold is inclusive`() {
        assertEquals(AspectClass.LANDSCAPE, classifier.classify(Size(1200, 1000)))
        assertEquals(AspectClass.PORTRAIT, classifier.classify(Size(1000, 1200)))
        assertEquals(AspectClass.SQUARE, classifier.classify(Size(1190, 1000)))
    }

    @Test
    fun `threshold is configurable`() {
        val strict = AspectClassifier(threshold = 1.5)
        assertEquals(AspectClass.SQUARE, strict.classify(Size(4000, 3000)))
        assertEquals(AspectClass.LANDSCAPE, strict.classify(Size(1920, 1080)))
    }

    @Test
    fun `degenerate sizes are square rather than crashing`() =
        assertEquals(AspectClass.SQUARE, classifier.classify(Size(0, 0)))
}
