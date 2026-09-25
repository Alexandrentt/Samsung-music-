package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Factory global de Coil: cache de MEMORIA (25 % de la app) + cache de DISCO
 * (2 %, ~200 MB) para que las portadas no se re-descarguen al scrollear o
 * reabrir la app — la razón principal de que las portadas tardaran en cargar.
 * Todas las AsyncImage de la app heredan este loader automáticamente.
 */
class MusicApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .crossfade(120)
            .respectCacheHeaders(false) // YouTubeimg URLs no mandan cache headers útiles
            .build()
    }
}
