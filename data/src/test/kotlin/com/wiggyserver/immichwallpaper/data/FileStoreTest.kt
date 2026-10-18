package com.wiggyserver.immichwallpaper.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class FileStoreTest {
    @TempDir
    lateinit var dir: File

    private fun store(maxBytes: Long = Long.MAX_VALUE) = FileStore(dir, maxBytes)

    @Test
    fun `writes a file atomically and reads it back`() {
        val s = store()
        val f = s.write("a.jpg") { it.write(byteArrayOf(1, 2, 3)) }
        assertTrue(s.exists("a.jpg"))
        assertEquals(3, f.length())
        assertEquals(f, s.file("a.jpg"))
    }

    @Test
    fun `a failed write leaves neither a partial file nor a temp file`() {
        val s = store()
        assertThrows<IOException> { s.write("b.jpg") { it.write(1); throw IOException("disk full") } }
        assertFalse(s.exists("b.jpg"))
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `overwrites an existing file`() {
        val s = store()
        s.write("a.jpg") { it.write(1) }
        s.write("a.jpg") { it.write(byteArrayOf(1, 2)) }
        assertEquals(2, s.file("a.jpg").length())
    }

    @Test
    fun `rejects names that escape the directory`() {
        assertThrows<IllegalArgumentException> { store().write("../evil") { } }
        assertThrows<IllegalArgumentException> { store().file("a/b") }
    }

    @Test
    fun `cleanup deletes files not kept, including stray temp files`() {
        val s = store()
        s.write("keep.jpg") { it.write(1) }
        s.write("old.jpg") { it.write(1) }
        File(dir, "x.jpg.tmp").writeText("partial")
        assertEquals(setOf("old.jpg", "x.jpg.tmp"), s.cleanup(keep = setOf("keep.jpg")).toSet())
        assertEquals(listOf("keep.jpg"), s.names())
    }

    @Test
    fun `tracks total size and the size cap`() {
        val s = store(maxBytes = 4)
        s.write("a") { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals(3, s.totalBytes())
        assertFalse(s.isOverCap())
        s.write("b") { it.write(byteArrayOf(1, 2)) }
        assertTrue(s.isOverCap())
    }

    @Test
    fun `delete removes a file and ignores missing ones`() {
        val s = store()
        s.write("a") { it.write(1) }
        s.delete("a")
        s.delete("missing")
        assertFalse(s.exists("a"))
    }
}
