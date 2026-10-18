package com.wiggyserver.immichwallpaper

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.wiggyserver.immichwallpaper.data.ImmichClient
import com.wiggyserver.immichwallpaper.service.SwapService
import com.wiggyserver.immichwallpaper.work.Work
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class ImmichWallApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        graph = AppGraph(this)
        graph.start()
        if (SwapService.isWanted(this)) Work.schedulePeriodicSync(this)
    }

    /** Thumbnails for the UI: the API key is added only to requests for the configured server. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val server = runCatching { ImmichClient.normalizeUrl(graph.config.value.serverUrl) }.getOrNull()
            val key = graph.credentials.apiKey()
            if (server != null && key != null && request.url.toString().startsWith(server)) {
                chain.proceed(request.newBuilder().header("x-api-key", key).build())
            } else {
                chain.proceed(request)
            }
        }.build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbnails").toOkioPath()).maxSizeBytes(64L * 1024 * 1024).build() }
            .build()
    }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as ImmichWallApp).graph
