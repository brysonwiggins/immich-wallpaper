package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyMethod
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.SlideFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class SlideApplierTest {
    @TempDir
    lateinit var dir: File

    private lateinit var files: FileStore
    private val calls = mutableListOf<String>()
    private var rejectWhich: Int? = null
    private var cropsMissing = false

    private val setter = object : WallpaperSetter {
        override fun setPlain(stream: InputStream, which: Int) {
            if (which == rejectWhich) throw IllegalArgumentException("bad which")
            calls += "plain $which ${stream.readBytes().decodeToString()}"
        }

        override fun setWithCrops(stream: InputStream, crops: Map<Int, PixelRect>, which: Int) {
            if (cropsMissing) throw UnsupportedOperationException("no setStreamWithCrops")
            if (which == rejectWhich) throw IllegalArgumentException("bad which")
            calls += "crops $which ${crops.keys.sorted()} ${stream.readBytes().decodeToString()}"
        }
    }

    private val cropsSlide = PreparedSlide(
        "s", listOf("a"),
        listOf(
            SlideFile(1 or 2 or 16, ApplyMethod.CROPS, "cover.jpg", mapOf(0 to PixelRect(0, 0, 1, 1))),
            SlideFile(1 or 2 or 4, ApplyMethod.CROPS, "inner.jpg", mapOf(2 to PixelRect(0, 0, 1, 1), 3 to PixelRect(1, 0, 2, 1))),
        ),
    )

    @BeforeEach
    fun setUp() {
        files = FileStore(dir)
        files.write("cover.jpg") { it.write("C".toByteArray()) }
        files.write("inner.jpg") { it.write("I".toByteArray()) }
    }

    private fun applier() = SlideApplier(setter, files)

    @Test
    fun `applies every file with its flags and crop hints`() {
        applier().apply(cropsSlide, activeScreenBit = 16)
        assertEquals(listOf("crops 19 [0] C", "crops 7 [2, 3] I"), calls)
    }

    @Test
    fun `applies the active screen first`() {
        applier().apply(cropsSlide, activeScreenBit = 4)
        assertEquals(listOf("crops 7 [2, 3] I", "crops 19 [0] C"), calls)
    }

    @Test
    fun `plain files use the plain call`() {
        val slide = PreparedSlide("p", listOf("a"), listOf(SlideFile(2, ApplyMethod.PLAIN, "cover.jpg", null)))
        applier().apply(slide, activeScreenBit = 0)
        assertEquals(listOf("plain 2 C"), calls)
    }

    @Test
    fun `a rejected per-screen flag is reported as per-screen unsupported`() {
        rejectWhich = 1 or 2 or 4
        val e = assertThrows<ApplyException.PerScreenUnsupported> { applier().apply(cropsSlide, activeScreenBit = 16) }
        assertTrue(e.cause is IllegalArgumentException)
    }

    @Test
    fun `a rejection without screen bits is a plain failure`() {
        rejectWhich = 2
        val slide = PreparedSlide("p", listOf("a"), listOf(SlideFile(2, ApplyMethod.PLAIN, "cover.jpg", null)))
        assertThrows<IllegalArgumentException> { applier().apply(slide, activeScreenBit = 0) }
    }

    @Test
    fun `a missing crops API is reported as crops unsupported`() {
        cropsMissing = true
        assertThrows<ApplyException.CropsUnsupported> { applier().apply(cropsSlide, activeScreenBit = 16) }
    }

    @Test
    fun `a missing file fails`() {
        files.delete("inner.jpg")
        assertThrows<FileNotFoundException> { applier().apply(cropsSlide, activeScreenBit = 16) }
    }
}
