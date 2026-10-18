package com.wiggyserver.immichwallpaper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Diagnostics log (file + StateFlow). Never log API keys, server URLs or photo URLs. */
object AppLog {
    private const val MAX_LINES = 500
    private lateinit var file: File
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    private val format = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    fun init(context: Context) {
        file = File(context.filesDir, "app-log.txt")
        _lines.value = runCatching { file.readLines().takeLast(MAX_LINES) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "${LocalDateTime.now().format(format)}  $tag  $message"
        Log.i("ImmichWall", "$tag $message")
        val next = (_lines.value + line).takeLast(MAX_LINES)
        _lines.value = next
        if (::file.isInitialized) runCatching { file.writeText(next.joinToString("\n", postfix = "\n")) }
    }
}
