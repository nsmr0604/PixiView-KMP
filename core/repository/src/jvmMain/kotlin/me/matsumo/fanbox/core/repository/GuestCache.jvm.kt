package me.matsumo.fanbox.core.repository

import me.matsumo.fankt.fanbox.FanboxGuestCache
import java.nio.file.Files
import java.nio.file.Path

/**
 * guest bundle のキャッシュをユーザーホームのキャッシュディレクトリ配下の [directoryName] に置く。
 */
internal fun createGuestCache(directoryName: String): FanboxGuestCache {
    val directory = Path.of(System.getProperty("user.home"), ".pixiview", "cache", "fanbox-guest")

    Files.createDirectories(directory)

    return FanboxGuestCache(directory.resolve(directoryName).toAbsolutePath().toString())
}

