package me.matsumo.fanbox.core.repository.download

import kotlinx.coroutines.flow.first
import me.matsumo.fanbox.core.datastore.SettingDataStore
import me.matsumo.fanbox.core.model.FanboxDownloadItems
import me.matsumo.fanbox.core.model.fanbox.PostDetail
import me.matsumo.fanbox.core.model.fanbox.PostId

/**
 * ダウンロードの保存構造（クリエイター・投稿のディレクトリ名）を組み立てる共通ロジック。
 *
 * ファイルシステムや写真ライブラリへの実際の保存はプラットフォーム実装が行い、
 * このクラスはディレクトリ・アルバム名の算出とオプション判定だけを担当する。
 */
internal class DownloadDirectories(private val userDataStore: SettingDataStore) {

    /** 保存先のサブディレクトリ名。すべてのダウンロードをクリエイターごとのディレクトリ・アルバムにまとめる。 */
    suspend fun creatorDirName(
        requestType: FanboxDownloadItems.RequestType,
        postDetail: PostDetail?,
    ): String = when (requestType) {
        is FanboxDownloadItems.RequestType.Image, is FanboxDownloadItems.RequestType.File -> postDetail?.user?.name

        is FanboxDownloadItems.RequestType.WholePost -> requestType.post?.user?.name ?: postDetail?.user?.name
    }.sanitizeDirName()

    /** 投稿ごとのサブディレクトリ名。オプションが無効な場合は null を返す。 */
    suspend fun postDirName(postId: PostId, title: String): String? {
        if (!userDataStore.setting.first().isUsePostIdDirectory) return null

        return "${postId.value}.$title".sanitizeDirName()
    }

    /** 記事本文を保存するかどうか。 */
    suspend fun isSavePostBody(): Boolean = userDataStore.setting.first().isSavePostBody

    /** クリエイター別アルバムに保存するかどうか。 */
    suspend fun isUseCreatorAlbum(): Boolean = userDataStore.setting.first().isUseCreatorAlbum

    /** ファイルパス・アルバム名に使えない文字を置換する。Windows では末尾のドットと空白も無効。 */
    private fun String?.sanitizeDirName(): String = this
        ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
        ?.trimEnd('.', ' ')
        ?.takeIf { it.isNotBlank() }
        ?.take(100)
        ?: "UnknownCreator"
}
