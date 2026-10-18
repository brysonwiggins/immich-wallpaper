package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyMethod
import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.SlideFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.InputStream

class WallpaperResetTest {
    @TempDir
    lateinit var dir: File

    private val cleared = mutableListOf<Int>()
    private var rejectScreenBits = false
    private val clearer = WallpaperClearer { which ->
        if (rejectScreenBits && which and (4 or 16) != 0) throw IllegalArgumentException("bad which")
        cleared += which
    }
    private val setter = object : WallpaperSetter {
        override fun setPlain(stream: InputStream, which: Int) = Unit
        override fun setWithCrops(stream: InputStream, crops: Map<Int, PixelRect>, which: Int) = Unit
    }

    private fun record() = AppliedScreens(File(dir, "applied.txt"))

    private fun applyFoldSlide(applied: AppliedScreens) {
        val files = FileStore(File(dir, "q")).apply {
            write("c.jpg") { it.write(1) }
            write("i.jpg") { it.write(1) }
        }
        val slide = PreparedSlide(
            "s", listOf("a"),
            listOf(SlideFile(2 or 16, ApplyMethod.PLAIN, "c.jpg", null), SlideFile(2 or 4, ApplyMethod.PLAIN, "i.jpg", null)),
        )
        SlideApplier(setter, files, applied).apply(slide, activeScreenBit = 16)
    }

    @Test
    fun `resets exactly the screens the app changed, and remembers them across restarts`() {
        applyFoldSlide(record())
        WallpaperReset(clearer, record()).reset(ApplyTarget.LOCK_AND_HOME)
        assertEquals(listOf(2 or 4, 2 or 16), cleared)
    }

    @Test
    fun `once reset, nothing is reset again`() {
        applyFoldSlide(record())
        val reset = WallpaperReset(clearer, record())
        reset.reset(ApplyTarget.LOCK)
        cleared.clear()
        reset.reset(ApplyTarget.LOCK)
        assertEquals(listOf(2), cleared) // nothing recorded any more: only the current target
    }

    @Test
    fun `without per-screen flags it resets that wallpaper on every screen`() {
        rejectScreenBits = true
        applyFoldSlide(record())
        WallpaperReset(clearer, record()).reset(ApplyTarget.LOCK)
        assertEquals(listOf(2), cleared)
    }

    @Test
    fun `with nothing recorded (an update from an older version) the current target is reset`() {
        WallpaperReset(clearer, record()).reset(ApplyTarget.HOME)
        WallpaperReset(clearer, record()).reset(ApplyTarget.LOCK_AND_HOME)
        assertEquals(listOf(1, 1 or 2), cleared)
    }
}
