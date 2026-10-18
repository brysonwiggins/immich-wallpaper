package com.wiggyserver.immichwallpaper.core

/** AOSP `WallpaperManager.ORIENTATION_*` buckets, the keys of a per-orientation crop map. */
enum class ScreenOrientation(val key: Int) {
    PORTRAIT(0),
    LANDSCAPE(1),
    SQUARE_PORTRAIT(2),
    SQUARE_LANDSCAPE(3),
    ;

    companion object {
        /** Mirrors AOSP `WallpaperManager.getOrientation(Point)`: ratios between 3/4 and 4/3 are "square". */
        fun of(size: Size): ScreenOrientation {
            val ratio = size.ratio
            return when {
                ratio >= 4 / 3.0 -> LANDSCAPE
                ratio > 1.0 -> SQUARE_LANDSCAPE
                ratio > 3 / 4.0 -> SQUARE_PORTRAIT
                else -> PORTRAIT
            }
        }
    }
}

enum class ScreenKind { COVER, INNER, PHONE, TABLET }

/** One way a screen can be held, with the size the wallpaper is drawn at. */
data class Viewport(val screen: Screen, val size: Size) {
    val orientation: ScreenOrientation get() = ScreenOrientation.of(size)
    val isLandscape: Boolean get() = size.isLandscape
}

/** Fractions of the screen height kept free of faces (lock screen clock at the top, controls at the bottom). */
data class SafeZone(val top: Double, val bottom: Double) {
    companion object {
        val NONE = SafeZone(0.0, 0.0)
    }
}

/**
 * A physical screen. [naturalSize] is in the display's natural orientation.
 * [rotates] = the lock screen rotates with the device (true for the Fold inner screen, false for its cover).
 */
data class Screen(
    val kind: ScreenKind,
    val naturalSize: Size,
    val smallestWidthDp: Int,
    val rotates: Boolean,
    val safeZone: SafeZone = SafeZone.NONE,
) {
    private val portraitSize: Size get() = if (naturalSize.isLandscape) naturalSize.rotated() else naturalSize

    /** Sizes the wallpaper may be shown at, portrait first. */
    fun viewports(): List<Viewport> {
        val portrait = Viewport(this, portraitSize)
        return if (rotates && naturalSize.width != naturalSize.height) listOf(portrait, Viewport(this, portraitSize.rotated())) else listOf(portrait)
    }

    /**
     * Image size for the plain (no crop map) path, where the system center-crops: a rotating screen
     * gets a square of its long side, so both orientations are covered.
     */
    fun plainImageSize(): Size {
        if (!rotates) return portraitSize
        val side = maxOf(naturalSize.width, naturalSize.height)
        return Size(side, side)
    }

    /**
     * Side-by-side portraits make sense on a large screen whose landscape halves are portrait-shaped
     * (Fold 8 inner: each half 1224x1848 = 0.66).
     */
    fun supportsSideBySide(): Boolean {
        if (smallestWidthDp < 600) return false
        val landscape = if (naturalSize.isLandscape) naturalSize else naturalSize.rotated()
        val halfRatio = (landscape.width / 2.0) / landscape.height
        return halfRatio in 0.55..0.85
    }
}

/** Measured display profiles. */
object Screens {
    val FOLD8_COVER = Screen(ScreenKind.COVER, Size(1248, 1972), smallestWidthDp = 475, rotates = false, safeZone = SafeZone(0.28, 0.18))
    val FOLD8_INNER = Screen(ScreenKind.INNER, Size(2448, 1848), smallestWidthDp = 704, rotates = true, safeZone = SafeZone(0.33, 0.18))
}
