package me.matsumo.fanbox.core.repository.download.desktop

import me.matsumo.fanbox.core.repository.download.desktop.linux.LinuxSaveDirectoryProvider
import me.matsumo.fanbox.core.repository.download.desktop.macos.MacOsSaveDirectoryProvider
import me.matsumo.fanbox.core.repository.download.desktop.windows.WindowsSaveDirectoryProvider
import java.nio.file.Path

/**
 * デスクトップの標準画像フォルダを解決する。
 *
 * OS ごとの実装は Windows / MacOS / Linux の各パッケージに分ける。既定のディレクトリ以外の
 * 差分（フォルダーを開く方法やサムネイルなど）を追加する場合もこの interface に実装を集める。
 */
internal interface DesktopSaveDirectoryProvider {

    /** プラットフォーム標準の画像フォルダ。 */
    fun picturesDirectory(): Path
}

/** 現在の OS に対応する [DesktopSaveDirectoryProvider] を生成する。 */
internal fun createDesktopSaveDirectoryProvider(): DesktopSaveDirectoryProvider = when (currentDesktopPlatform) {
    DesktopPlatform.Windows -> WindowsSaveDirectoryProvider()

    DesktopPlatform.MacOS -> MacOsSaveDirectoryProvider()

    DesktopPlatform.Linux -> LinuxSaveDirectoryProvider()
}
