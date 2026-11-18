package me.matsumo.fanbox.feature.setting.directory.items

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.matsumo.fanbox.core.resources.Res
import me.matsumo.fanbox.core.resources.setting_top_file_directory_file
import me.matsumo.fanbox.core.resources.setting_top_file_directory_image
import me.matsumo.fanbox.core.resources.setting_top_file_directory_post
import me.matsumo.fanbox.core.ui.component.SettingTextItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

// デスクトップでは保存先をプラットフォーム標準の画像フォルダ配下の FANBOX に固定し、
// 設定画面ではパスの確認とフォルダを開く操作だけを提供する。
@Composable
internal actual fun SettingDirectoryContent(
    imageDirectory: String,
    fileDirectory: String,
    postDirectory: String,
    onSaveImageDirectory: (String) -> Unit,
    onSaveFileDirectory: (String) -> Unit,
    onSavePostDirectory: (String) -> Unit,
    onShowSnackbar: (StringResource) -> Unit,
    onTerminate: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier) {
        item {
            SettingTextItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.setting_top_file_directory_image),
                description = imageDirectory,
                onClick = { openDirectory(imageDirectory) },
            )
        }

        item {
            SettingTextItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.setting_top_file_directory_file),
                description = fileDirectory,
                onClick = { openDirectory(fileDirectory) },
            )
        }

        item {
            SettingTextItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.setting_top_file_directory_post),
                description = postDirectory,
                onClick = { openDirectory(postDirectory) },
            )
        }
    }
}

/** 保存先のディレクトリを OS 標準のファイラーで開く。 */
private fun openDirectory(path: String) {
    runCatching {
        val dir = Path.of(path)

        Files.createDirectories(dir)
        Desktop.getDesktop().open(dir.toFile())
    }
}
