package me.matsumo.fanbox

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import me.matsumo.fanbox.di.applyModules
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import java.nio.file.Files
import java.nio.file.Path

fun initKoin() {
    startKoin {
        applyModules()
    }
}

fun initNapier() {
    Napier.base(DebugAntilog())
}

@OptIn(ExperimentalCoilApi::class)
fun initCoil() {
    val imageCacheDir = Path.of(System.getProperty("user.home"), ".pixiview", "cache", "image_cache")

    Files.createDirectories(imageCacheDir)

    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(it, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir.toString().toPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(KtorNetworkFetcherFactory())
            }
            .crossfade(true)
            .build()
    }
}
