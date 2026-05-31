package com.beatdrop.kt

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.beatdrop.kt.youtube.InnertubeSearchProvider
import com.beatdrop.kt.youtube.OnlineSearch
import com.beatdrop.kt.youtube.YoutubeService

class BeatDropApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Wire online search to the real Innertube backend (no API key required)
        OnlineSearch.provider = InnertubeSearchProvider()

        // Give YoutubeService a context for the download directory
        YoutubeService.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
