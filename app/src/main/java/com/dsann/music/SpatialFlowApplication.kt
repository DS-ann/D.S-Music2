package com.dsann.music

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.dsann.music.data.innertube.InnerTubeClient
import com.dsann.music.data.innertube.NewPipeStreamExtractor
import com.dsann.music.util.CacheManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpatialFlowApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: SpatialFlowApplication
            private set

        /** Shared low-memory profile used by Compose screens to avoid expensive decoration. */
        @Volatile
        var isLowRamDevice: Boolean = false
            private set
    }

    @Volatile
    private var activeImageLoader: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        val lowRam = isLowRamDevice
        val builder = ImageLoader.Builder(this)
            .okHttpClient { InnerTubeClient.httpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Keep artwork from crowding playback buffers and Compose state on 2 GB phones.
                    .maxSizePercent(if (lowRam) 0.06 else 0.11)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (lowRam) 96L * 1024L * 1024L else 160L * 1024L * 1024L)
                    .build()
            }
            .allowHardware(true)
            .allowRgb565(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)

        // Crossfades keep two artwork bitmaps alive during the transition. Avoid them
        // on low-RAM devices; the image still uses the same decoded-size and disk caches.
        if (lowRam) builder.crossfade(false) else builder.crossfade(80)

        return builder.build().also { activeImageLoader = it }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            activeImageLoader?.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        activeImageLoader?.memoryCache?.clear()
    }

    @SuppressLint("VisibleForTests")
    override fun onCreate() {
        super.onCreate()
        instance = this
        isLowRamDevice = (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true

        // Defer stream extractor and cache maintenance initialization until after first draw.
        val appContext = applicationContext
        Thread({
            try {
                InnerTubeClient.initialize(appContext)
                NewPipeStreamExtractor.init(appContext)
                CacheManager.clearOldCache(appContext)
            } catch (error: Exception) {
                Log.e("SpatialFlowApplication", "Background startup initialization failed", error)
            }
        }, "D.S-Music-Startup").start()

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val isDarkMode = prefs.getString("theme_mode", "system")?.let { mode ->
            when (mode) {
                "dark" -> true
                "light" -> false
                else -> prefs.getBoolean("dark_mode", true)
            }
        } ?: true
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Load saved YouTube Music cookies for account-backed features.
        prefs.getString("yt_cookies", null)?.let { InnerTubeClient.cookie = it }

        // Glide remains available for legacy image call sites; Compose artwork uses Coil above.
        Glide.init(this, GlideBuilder().setLogLevel(Log.ERROR))
    }
}
