package com.wiggyserver.immichwallpaper.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.util.SparseArray
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.data.WallpaperClearer
import com.wiggyserver.immichwallpaper.data.WallpaperSetter
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * WallpaperManager calls. `setStreamWithCrops(InputStream, SparseArray<Rect>, boolean, int)` is a
 * hidden API that One UI exposes to apps; it is looked up reflectively.
 */
class PlatformWallpaperSetter(context: Context) : WallpaperSetter, WallpaperClearer {
    private val wm = WallpaperManager.getInstance(context)

    override fun setPlain(stream: InputStream, which: Int) {
        val id = wm.setStream(stream, null, true, which)
        // One UI returns 0 instead of throwing for flags it ignores (e.g. which=34).
        if (id == 0) throw IllegalArgumentException("setStream ignored which=$which")
    }

    override fun clear(which: Int) = wm.clear(which)

    override fun setWithCrops(stream: InputStream, crops: Map<Int, PixelRect>, which: Int) {
        val method = cropsMethod ?: throw UnsupportedOperationException("setStreamWithCrops not available")
        val hints = SparseArray<Rect>().apply { crops.forEach { (k, r) -> put(k, Rect(r.left, r.top, r.right, r.bottom)) } }
        val id = try {
            method.invoke(wm, stream, hints, true, which) as Int
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
        if (id == 0) throw IllegalArgumentException("setStreamWithCrops ignored which=$which")
    }

    companion object {
        private val cropsMethod: Method? by lazy {
            runCatching {
                WallpaperManager::class.java.getMethod(
                    "setStreamWithCrops", InputStream::class.java, SparseArray::class.java,
                    Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                )
            }.getOrNull()
        }

        val cropsApiAvailable: Boolean get() = cropsMethod != null

        /** One UI exposes SEM_FLAG_SUB_DISPLAY (16) on devices with a cover screen. */
        val samsungPerScreenFlags: Boolean
            get() = runCatching { WallpaperManager::class.java.getField("SEM_FLAG_SUB_DISPLAY").getInt(null) == 16 }.getOrDefault(false)
    }
}
