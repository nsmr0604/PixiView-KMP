package me.matsumo.fanbox.core.repository.download.desktop.windows

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import me.matsumo.fanbox.core.repository.download.desktop.DesktopSaveDirectoryProvider
import java.nio.file.Path

/**
 * Windows の標準画像フォルダを解決する。
 *
 * ネイティブ API の SHGetKnownFolderPath（JNA 経由）でユーザーが移動済みの
 * ピクチャフォルダも解決する。失敗時は %USERPROFILE%\Pictures にフォールバックする。
 */
internal class WindowsSaveDirectoryProvider : DesktopSaveDirectoryProvider {

    override fun picturesDirectory(): Path = runCatching {
        Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Pictures)?.let { Path.of(it) }
    }.getOrNull() ?: Path.of(System.getProperty("user.home"), "Pictures")
}
