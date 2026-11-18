package me.matsumo.fanbox.core.repository.download.desktop.macos

import me.matsumo.fanbox.core.repository.download.desktop.DesktopSaveDirectoryProvider
import java.nio.file.Path

/**
 * macOS の標準画像フォルダを解決する。
 *
 * ネイティブの AppleScript ランタイム（osascript）に Finder の「ピクチャ」フォルダを
 * 問い合わせる。失敗時は ~/Pictures にフォールバックする。
 */
internal class MacOsSaveDirectoryProvider : DesktopSaveDirectoryProvider {

    override fun picturesDirectory(): Path = runCatching {
        val process = ProcessBuilder("/usr/bin/osascript", "-e", "POSIX path of (path to pictures folder)")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (process.exitValue() == 0 && output.isNotBlank()) Path.of(output) else null
    }.getOrNull() ?: Path.of(System.getProperty("user.home"), "Pictures")
}
