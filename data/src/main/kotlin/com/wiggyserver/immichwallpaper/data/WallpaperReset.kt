package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.WallpaperFlags
import java.io.File

/** `WallpaperManager.clear(which)`: puts the phone's default wallpaper back. */
fun interface WallpaperClearer {
    fun clear(which: Int)
}

/** The `which` flags the app has set a wallpaper with since the last reset (kept in a file). */
class AppliedScreens(private val file: File) {
    private val lock = Any()

    fun add(which: Int) = synchronized(lock) {
        val now = read()
        if (which !in now) write(now + which)
    }

    fun all(): Set<Int> = synchronized(lock) { read() }

    fun forget() = synchronized(lock) { file.delete(); Unit }

    private fun read(): Set<Int> = runCatching { file.readLines().mapNotNull { it.trim().toIntOrNull() }.toSet() }.getOrDefault(emptySet())

    private fun write(flags: Set<Int>) {
        file.parentFile?.mkdirs()
        file.writeText(flags.sorted().joinToString("\n"))
    }
}

/**
 * Turning the app off: Android doesn't let apps read the wallpaper that was there before, so the
 * phone's default goes back on the screens the app changed (and only those).
 */
class WallpaperReset(private val clearer: WallpaperClearer, private val applied: AppliedScreens) {
    /** [current] is used when nothing was recorded (the app was updated from a version that didn't record). */
    fun reset(current: ApplyTarget) {
        val flags = applied.all().ifEmpty { setOf(flagsOf(current)) }
        val done = mutableSetOf<Int>()
        for (which in flags.sorted()) {
            try {
                if (which !in done) clearer.clear(which)
                done += which
            } catch (e: IllegalArgumentException) {
                // No per-screen flags here: clearing the wallpaper itself covers every screen.
                val plain = which and (WallpaperFlags.SYSTEM or WallpaperFlags.LOCK)
                if (plain == which || plain in done) continue
                clearer.clear(plain)
                done += plain
            }
        }
        applied.forget()
    }

    private fun flagsOf(target: ApplyTarget) = when (target) {
        ApplyTarget.LOCK -> WallpaperFlags.LOCK
        ApplyTarget.HOME -> WallpaperFlags.SYSTEM
        ApplyTarget.LOCK_AND_HOME -> WallpaperFlags.SYSTEM or WallpaperFlags.LOCK
    }
}
