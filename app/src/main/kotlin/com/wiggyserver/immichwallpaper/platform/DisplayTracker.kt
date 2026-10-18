package com.wiggyserver.immichwallpaper.platform

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.view.Display
import com.wiggyserver.immichwallpaper.core.ObservedDisplay
import com.wiggyserver.immichwallpaper.core.Screen
import com.wiggyserver.immichwallpaper.core.ScreenClassifier
import com.wiggyserver.immichwallpaper.core.Size
import java.io.File

/**
 * Remembers every display mode seen (on a Fold: cover and inner) so slides can be prepared for
 * both screens, and reports which one is active right now.
 */
class DisplayTracker(private val context: Context, private val file: File) {
    val foldable: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

    @Volatile private var observed: Set<ObservedDisplay> = load()

    fun current(): ObservedDisplay {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        return ObservedDisplay(Size(mode.physicalWidth, mode.physicalHeight), context.resources.displayMetrics.densityDpi)
    }

    /** Records the current display. Returns true when it is a panel not seen before. */
    @Synchronized
    fun observe(): Boolean {
        val now = current()
        if (observed.any { it.samePanel(now) }) return false
        observed = observed + now
        save()
        return true
    }

    fun screens(): List<Screen> = ScreenClassifier.screens(observed, foldable).ifEmpty { listOf(active()) }

    fun active(): Screen = ScreenClassifier.active(ScreenClassifier.screens(observed, foldable), current(), foldable)

    private fun load(): Set<ObservedDisplay> = runCatching {
        file.readLines().mapNotNull { line ->
            val p = line.split(',')
            if (p.size == 3) ObservedDisplay(Size(p[0].toInt(), p[1].toInt()), p[2].toInt()) else null
        }.toSet()
    }.getOrDefault(emptySet())

    private fun save() {
        file.parentFile?.mkdirs()
        file.writeText(observed.joinToString("\n") { "${it.size.width},${it.size.height},${it.densityDpi}" })
    }
}
