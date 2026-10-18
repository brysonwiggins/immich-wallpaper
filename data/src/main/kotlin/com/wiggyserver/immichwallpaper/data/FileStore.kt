package com.wiggyserver.immichwallpaper.data

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Flat directory of app files (sources, rendered slides). Writes go to a temp file and are then
 * renamed, so a reader (the swap path) never sees a half-written file.
 */
class FileStore(private val dir: File, private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    init {
        dir.mkdirs()
    }

    fun file(name: String): File {
        require(name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && name != "." && name != "..") { "bad file name" }
        return File(dir, name)
    }

    fun exists(name: String) = file(name).isFile

    fun write(name: String, block: (OutputStream) -> Unit): File {
        val target = file(name)
        val tmp = File(dir, "$name$TMP")
        try {
            tmp.outputStream().buffered().use(block)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tmp.delete()
        }
        return target
    }

    fun delete(name: String) {
        file(name).delete()
    }

    fun names(): List<String> = dir.list()?.sorted().orEmpty()

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    fun isOverCap() = totalBytes() > maxBytes

    /** Deletes every file not in [keep] (orphans from crashes, old slides, temp files). Returns what was deleted. */
    fun cleanup(keep: Set<String>): List<String> = names().filter { it !in keep }.onEach { File(dir, it).delete() }

    companion object {
        private const val TMP = ".tmp"
        const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024
    }
}
