package me.matsumo.fanbox.core.repository.download.desktop.linux

import me.matsumo.fanbox.core.repository.download.desktop.DesktopSaveDirectoryProvider
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Linux の標準画像フォルダを解決する。
 *
 * ネイティブの XDG ツール（xdg-user-dir）を優先し、無い環境では ~/.config/user-dirs.dirs
 * の XDG_PICTURES_DIR を読む。いずれも失敗したら ~/Pictures にフォールバックする。
 */
internal class LinuxSaveDirectoryProvider : DesktopSaveDirectoryProvider {

    override fun picturesDirectory(): Path {
        val home = Path.of(System.getProperty("user.home"))
        val default = home.resolve("Pictures")

        return fromNativeTool(home) ?: fromUserDirsConfig(home) ?: default
    }

    /** xdg-user-dir でピクチャフォルダを問い合わせる。未設定時は $HOME が返る。 */
    private fun fromNativeTool(home: Path): Path? = runCatching {
        val process = ProcessBuilder("xdg-user-dir", "PICTURES")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        if (process.exitValue() == 0 && output.isNotBlank()) {
            Path.of(output).takeIf { it != home }
        } else {
            null
        }
    }.getOrNull()

    /** ~/.config/user-dirs.dirs の XDG_PICTURES_DIR を読む。 */
    private fun fromUserDirsConfig(home: Path): Path? = runCatching {
        val dir = home.resolve(".config/user-dirs.dirs")
            .readText()
            .lineSequence()
            .firstOrNull { it.startsWith("XDG_PICTURES_DIR") }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.replaceFirst("\$HOME", home.toString())
            ?.takeIf { it.isNotBlank() }

        dir?.let { Path.of(it) }
    }.getOrNull()
}
