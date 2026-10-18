package com.wiggyserver.immichwallpaper.core

/** A display mode the app has seen (physical size as reported, plus density). */
data class ObservedDisplay(val size: Size, val densityDpi: Int) {
    val smallestWidthDp: Int get() = minOf(size.width, size.height) * 160 / densityDpi
    val area: Int get() = size.width * size.height

    /** Same panel regardless of orientation. */
    fun samePanel(other: ObservedDisplay) =
        (size == other.size || size == other.size.rotated()) && densityDpi == other.densityDpi
}

/**
 * Builds [Screen]s from observed display modes (a foldable has one logical display whose
 * mode switches between cover and inner). On a foldable the largest panel is the inner screen and
 * the smallest the cover; a single panel is classified by its smallest width.
 */
object ScreenClassifier {
    private val PHONE_SAFE = SafeZone(0.28, 0.18)
    private val LARGE_SAFE = SafeZone(0.33, 0.18)

    fun screens(observed: Set<ObservedDisplay>, foldable: Boolean): List<Screen> {
        val panels = observed.fold(mutableListOf<ObservedDisplay>()) { acc, d -> acc.apply { if (none { it.samePanel(d) }) add(d) } }
        if (panels.isEmpty()) return emptyList()
        if (foldable && panels.size >= 2) {
            val smallest = panels.minBy { it.area }
            val largest = panels.maxBy { it.area }
            return listOf(screen(smallest, ScreenKind.COVER), screen(largest, ScreenKind.INNER))
        }
        return listOf(single(panels.maxBy { it.area }, foldable))
    }

    fun active(screens: List<Screen>, current: ObservedDisplay, foldable: Boolean): Screen =
        screens.firstOrNull { current.samePanel(ObservedDisplay(it.naturalSize, current.densityDpi)) }
            ?: single(current, foldable)

    private fun single(d: ObservedDisplay, foldable: Boolean): Screen {
        val large = d.smallestWidthDp >= 600
        val kind = when {
            foldable && large -> ScreenKind.INNER
            foldable -> ScreenKind.COVER
            large -> ScreenKind.TABLET
            else -> ScreenKind.PHONE
        }
        return screen(d, kind)
    }

    private fun screen(d: ObservedDisplay, kind: ScreenKind): Screen {
        val large = kind == ScreenKind.INNER || kind == ScreenKind.TABLET
        return Screen(kind, d.size, d.smallestWidthDp, rotates = large, safeZone = if (large) LARGE_SAFE else PHONE_SAFE)
    }
}
