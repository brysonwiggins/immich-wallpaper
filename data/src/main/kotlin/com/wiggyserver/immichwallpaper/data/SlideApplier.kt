package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyMethod
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.WallpaperFlags
import java.io.InputStream

/** The two WallpaperManager calls the app makes (implemented over the platform in the app module). */
interface WallpaperSetter {
    fun setPlain(stream: InputStream, which: Int)

    /** Throws [UnsupportedOperationException] when `setStreamWithCrops` is not available. */
    fun setWithCrops(stream: InputStream, crops: Map<Int, PixelRect>, which: Int)
}

sealed class ApplyException(message: String, cause: Throwable) : Exception(message, cause) {
    /** One UI rejected the per-display bits (4/16): fall back to the active screen only. */
    class PerScreenUnsupported(cause: Throwable) : ApplyException("per-screen wallpaper flags rejected", cause)

    /** `setStreamWithCrops` is gone: re-render with plain images. */
    class CropsUnsupported(cause: Throwable) : ApplyException("setStreamWithCrops unavailable", cause)
}

/** Applies a [PreparedSlide] on the swap path: the active screen first, then the others. */
class SlideApplier(private val setter: WallpaperSetter, private val files: FileStore, private val applied: AppliedScreens? = null) {
    fun apply(slide: PreparedSlide, activeScreenBit: Int) {
        val ordered = slide.files.sortedByDescending { activeScreenBit != 0 && it.which and activeScreenBit != 0 }
        for (f in ordered) {
            files.file(f.fileName).inputStream().buffered().use { stream ->
                try {
                    val hints = f.cropHints
                    if (f.method == ApplyMethod.CROPS && hints != null) setter.setWithCrops(stream, hints, f.which) else setter.setPlain(stream, f.which)
                    applied?.add(f.which)
                } catch (e: UnsupportedOperationException) {
                    throw ApplyException.CropsUnsupported(e)
                } catch (e: IllegalArgumentException) {
                    if (f.which and SCREEN_BITS != 0) throw ApplyException.PerScreenUnsupported(e) else throw e
                }
            }
        }
    }

    private companion object {
        const val SCREEN_BITS = WallpaperFlags.INNER or WallpaperFlags.COVER
    }
}
