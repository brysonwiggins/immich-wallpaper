package com.wiggyserver.immichwallpaper.ui

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.wiggyserver.immichwallpaper.data.PhotoLibrary
import com.wiggyserver.immichwallpaper.presentation.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the screens draw photos. The app loads them from Immich; screenshot tests draw placeholders. */
interface Photos {
    @Composable fun Asset(id: String, modifier: Modifier)

    @Composable fun Person(id: String, modifier: Modifier)

    /** A prepared wallpaper file, cropped to what the screen in use shows. */
    @Composable fun Rendered(preview: Preview, modifier: Modifier)
}

val LocalPhotos = staticCompositionLocalOf<Photos> { PlaceholderPhotos }

/** Immich thumbnails through Coil (the image loader adds the API key; see ImmichWallApp). */
class ImmichPhotos(private val library: PhotoLibrary) : Photos {
    @Composable
    override fun Asset(id: String, modifier: Modifier) = AsyncImage(
        model = library.thumbnailUrl(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )

    @Composable
    override fun Person(id: String, modifier: Modifier) = AsyncImage(
        model = library.personThumbnailUrl(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )

    @Composable
    override fun Rendered(preview: Preview, modifier: Modifier) {
        val bitmap by produceState<ImageBitmap?>(null, preview) { value = withContext(Dispatchers.IO) { decode(preview) } }
        val b = bitmap
        if (b == null) {
            Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        } else {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        }
    }

    /** Decodes only the cropped region, subsampled to about 800 px on the long side. */
    private fun decode(preview: Preview): ImageBitmap? = runCatching {
        val path = preview.file.path
        val crop = preview.crop?.let { Rect(it.left, it.top, it.right, it.bottom) } ?: run {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            Rect(0, 0, o.outWidth, o.outHeight)
        }
        var sample = 1
        while (maxOf(crop.width(), crop.height()) / (sample * 2) >= 800) sample *= 2
        val decoder = if (Build.VERSION.SDK_INT >= 31) {
            BitmapRegionDecoder.newInstance(path)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(path, false)
        } ?: return@runCatching null
        try {
            decoder.decodeRegion(crop, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } finally {
            decoder.recycle()
        }
    }.getOrNull()
}

/** Deterministic colored tiles (previews and screenshot tests): a hue per id. */
object PlaceholderPhotos : Photos {
    private fun brush(id: String): Brush {
        val hue = (((id.hashCode() * -1640531527) ushr 1) % 360).toFloat() // spread similar ids apart
        return Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.85f), Color.hsv((hue + 40) % 360, 0.6f, 0.55f)))
    }

    @Composable override fun Asset(id: String, modifier: Modifier) = Box(modifier.background(brush(id)))

    @Composable override fun Person(id: String, modifier: Modifier) = Box(modifier.background(brush(id)))

    @Composable override fun Rendered(preview: Preview, modifier: Modifier) = Box(modifier.background(brush(preview.file.name)))
}
