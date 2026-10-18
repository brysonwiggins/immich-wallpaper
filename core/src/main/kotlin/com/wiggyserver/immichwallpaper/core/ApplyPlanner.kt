package com.wiggyserver.immichwallpaper.core

/** Which wallpapers the user wants changed (Settings → Apply to). */
enum class ApplyTarget { LOCK, HOME, LOCK_AND_HOME }

/** What the device supports, detected at runtime by the platform layer. */
data class Capabilities(
    /** One UI per-display flag bits (inner = 4, cover = 16) are accepted. */
    val perScreenFlags: Boolean,
    /** `setStreamWithCrops(InputStream, SparseArray<Rect>, …)` is callable. */
    val cropsApi: Boolean,
)

enum class ApplyMethod {
    /** One composite image + a crop per orientation (honored only together with FLAG_SYSTEM). */
    CROPS,

    /** A plain image the system center-crops. */
    PLAIN,
}

/** `WallpaperManager` flag bits, including One UI's per-display bits. */
object WallpaperFlags {
    const val SYSTEM = 1
    const val LOCK = 2
    const val INNER = 4
    const val COVER = 16 // SEM_FLAG_SUB_DISPLAY
}

/** One wallpaper set call: which screen, which flags, how, and the viewports its image must serve. */
data class ScreenJob(val screen: Screen, val which: Int, val method: ApplyMethod, val viewports: List<Viewport>)

/**
 * Turns the Apply-to setting and device capabilities into set calls (owner decision "B with fallback A").
 * Every screen is updated on every swap when per-screen flags work; otherwise only the active screen.
 */
class ApplyPlanner {
    fun plan(target: ApplyTarget, capabilities: Capabilities, screens: List<Screen>, active: Screen): List<ScreenJob> {
        val base = when (target) {
            ApplyTarget.LOCK -> WallpaperFlags.LOCK
            ApplyTarget.HOME -> WallpaperFlags.SYSTEM
            ApplyTarget.LOCK_AND_HOME -> WallpaperFlags.LOCK or WallpaperFlags.SYSTEM
        }
        // One UI ignores per-orientation crops on a lock-only wallpaper, so "Lock" always goes plain.
        val method = if (capabilities.cropsApi && target != ApplyTarget.LOCK) ApplyMethod.CROPS else ApplyMethod.PLAIN
        val targets = if (capabilities.perScreenFlags) screens else listOf(active)
        return targets.map { screen ->
            val bit = if (capabilities.perScreenFlags) screenBit(screen) else 0
            val viewports = when (method) {
                ApplyMethod.CROPS -> screen.viewports()
                ApplyMethod.PLAIN -> listOf(Viewport(screen, screen.plainImageSize()))
            }
            ScreenJob(screen, base or bit, method, viewports)
        }
    }

    private fun screenBit(screen: Screen) = when (screen.kind) {
        ScreenKind.COVER -> WallpaperFlags.COVER
        ScreenKind.INNER -> WallpaperFlags.INNER
        ScreenKind.PHONE, ScreenKind.TABLET -> 0
    }
}
