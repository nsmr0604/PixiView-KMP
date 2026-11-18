package me.matsumo.fanbox.feature.setting.top.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.matsumo.fanbox.core.model.DownloadFileType
import me.matsumo.fanbox.core.model.Setting
import me.matsumo.fanbox.core.resources.Res
import me.matsumo.fanbox.core.resources.setting_top_file
import me.matsumo.fanbox.core.resources.setting_top_file_directory
import me.matsumo.fanbox.core.resources.setting_top_file_directory_description
import me.matsumo.fanbox.core.resources.setting_top_creator_album
import me.matsumo.fanbox.core.resources.setting_top_creator_album_description
import me.matsumo.fanbox.core.resources.setting_top_file_type
import me.matsumo.fanbox.core.resources.setting_top_file_type_description
import me.matsumo.fanbox.core.resources.setting_top_post_directory
import me.matsumo.fanbox.core.resources.setting_top_post_directory_description
import me.matsumo.fanbox.core.resources.setting_top_save_body
import me.matsumo.fanbox.core.resources.setting_top_save_body_description
import me.matsumo.fanbox.core.ui.component.SettingSwitchItem
import me.matsumo.fanbox.core.ui.component.SettingTextItem
import me.matsumo.fanbox.core.ui.extensition.Platform
import me.matsumo.fanbox.core.ui.extensition.currentPlatform

@Composable
internal fun SettingTopFileSection(
    setting: Setting,
    onClickDirectory: () -> Unit,
    onClickDownloadFileType: (DownloadFileType) -> Unit,
    onClickUsePostIdDirectory: (Boolean) -> Unit,
    onClickSavePostBody: (Boolean) -> Unit,
    onClickUseCreatorAlbum: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SettingTopTitleItem(
            modifier = Modifier.fillMaxWidth(),
            text = Res.string.setting_top_file,
        )

        SettingTextItem(
            modifier = Modifier.fillMaxWidth(),
            title = Res.string.setting_top_file_directory,
            description = Res.string.setting_top_file_directory_description,
            onClick = onClickDirectory,
        )

        SettingSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = Res.string.setting_top_file_type,
            description = Res.string.setting_top_file_type_description,
            value = setting.downloadFileType == DownloadFileType.ORIGINAL,
            onValueChange = {
                onClickDownloadFileType.invoke(if (it) DownloadFileType.ORIGINAL else DownloadFileType.THUMBNAIL)
            },
        )

        SettingSwitchItem(
            modifier = Modifier.fillMaxWidth(),
            title = Res.string.setting_top_post_directory,
            description = Res.string.setting_top_post_directory_description,
            value = setting.isUsePostIdDirectory,
            onValueChange = onClickUsePostIdDirectory,
        )

        // 記事本文の保存はデスクトップ専用機能
        if (currentPlatform == Platform.Desktop) {
            SettingSwitchItem(
                modifier = Modifier.fillMaxWidth(),
                title = Res.string.setting_top_save_body,
                description = Res.string.setting_top_save_body_description,
                value = setting.isSavePostBody,
                onValueChange = onClickSavePostBody,
            )
        }

        // クリエイター別アルバム保存は iOS 専用機能
        if (currentPlatform == Platform.IOS) {
            SettingSwitchItem(
                modifier = Modifier.fillMaxWidth(),
                title = Res.string.setting_top_creator_album,
                description = Res.string.setting_top_creator_album_description,
                value = setting.isUseCreatorAlbum,
                onValueChange = onClickUseCreatorAlbum,
            )
        }
    }
}
