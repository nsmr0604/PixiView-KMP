package me.matsumo.fanbox.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import me.matsumo.fanbox.core.common.util.suspendRunCatching
import me.matsumo.fanbox.core.datastore.SettingDataStore
import me.matsumo.fanbox.core.logs.category.PostsLog
import me.matsumo.fanbox.core.logs.logger.send
import me.matsumo.fanbox.core.model.DownloadFileType
import me.matsumo.fanbox.core.model.FanboxDownloadItems
import me.matsumo.fanbox.core.model.fanbox.PostDetail
import me.matsumo.fanbox.core.model.fanbox.PostId
import me.matsumo.fanbox.core.repository.download.BaseDownloadPostsRepository
import me.matsumo.fanbox.core.repository.download.desktop.createDesktopSaveDirectoryProvider
import me.matsumo.fanbox.core.repository.download.toPlainText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * デスクトップ（Windows / macOS / Linux）向けのダウンロード保存実装。
 *
 * ダウンロードの進行管理はコアの層 [BaseDownloadPostsRepository] が担い、
 * このクラスはファイルシステムへの書き込みと保存ディレクトリの解決だけを扱う。
 */
internal class DownloadPostsRepositoryImpl(
    fanboxRepository: FanboxRepository,
    userDataStore: SettingDataStore,
    scope: CoroutineScope,
) : BaseDownloadPostsRepository<ByteArray>(fanboxRepository, userDataStore, scope) {

    /** OS ごとの標準画像フォルダを解決する PC 層の実装。 */
    private val saveDirectoryProvider = createDesktopSaveDirectoryProvider()

    override suspend fun getSaveDirectory(requestType: FanboxDownloadItems.RequestType): String = downloadRoot().toString()

    override fun buildItems(
        downloadItems: FanboxDownloadItems,
        postDetail: PostDetail?,
    ): List<FanboxDownloadItems.Item> = when (val type = downloadItems.requestType) {
        FanboxDownloadItems.RequestType.File, FanboxDownloadItems.RequestType.Image -> downloadItems.items

        is FanboxDownloadItems.RequestType.WholePost -> {
            val detail = checkNotNull(postDetail)
            val images = detail.body.imageItems.map { it.toDownloadItem(-1) }
            val files = detail.body.fileItems.map { it.toDownloadItem() }

            images + if (type.isIgnoreFiles) emptyList() else files
        }
    }

    override suspend fun downloadItem(
        item: FanboxDownloadItems.Item,
        onDownload: (Float) -> Unit,
    ): Pair<FanboxDownloadItems.Item, ByteArray>? = suspendRunCatching {
        val fileType = userDataStore.setting.first().downloadFileType
        val url = if (item.extension.lowercase() != "gif" || fileType == DownloadFileType.ORIGINAL) item.originalUrl else item.thumbnailUrl
        // saveItem がバイト列を受け取る契約のため、チャンクをメモリ上に蓄積する。中断した
        // 場合はここで組み立てた値ごと捨てられ、保存先には何も残らない。
        val buffer = mutableListOf<ByteArray>()

        fanboxRepository.download(
            url = url,
            onDownload = onDownload,
            onChunk = { chunk -> buffer += chunk },
        )

        val bytes = ByteArray(buffer.sumOf { it.size }).also { target ->
            var offset = 0
            for (chunk in buffer) {
                chunk.copyInto(target, offset)
                offset += chunk.size
            }
        }

        onDownload.invoke(1f)

        item to bytes
    }.also {
        PostsLog.download(
            type = "unknown",
            postId = item.postId.value,
            itemId = item.name,
            extension = item.extension,
            isSuccess = it.isSuccess,
        ).send()
    }.getOrNull()

    override suspend fun saveResults(
        downloadItems: FanboxDownloadItems,
        postDetail: PostDetail?,
        results: List<Pair<FanboxDownloadItems.Item, ByteArray>?>,
    ) {
        // Image / File の一括ダウンロードではクリエイター名と本文のために投稿詳細を取得する。
        val detail = postDetail ?: runCatching { fanboxRepository.getPostDetail(downloadItems.postId) }.getOrNull()

        val saveDirName = downloadDirectories.creatorDirName(downloadItems.requestType, detail)
        val postDirName = downloadDirectories.postDirName(downloadItems.postId, downloadItems.title)

        for ((item, bytes) in results.filterNotNull()) {
            saveItem(item, saveDirName, postDirName, bytes)
        }

        if (downloadDirectories.isSavePostBody()) {
            savePostBody(downloadItems.postId, detail, saveDirName, postDirName)
        }
    }

    // toDownloadItem は PC では画像の連番を使わないため index を無視する。
    // Android との共通シグネチャのためにパラメータだけ残している。
    @Suppress("UnusedParameter")
    override fun PostDetail.ImageItem.toDownloadItem(index: Int): FanboxDownloadItems.Item = FanboxDownloadItems.Item(
        postId = postId,
        itemId = id,
        name = "image-${postId.value}-${id.value}",
        extension = extension,
        originalUrl = originalUrl,
        thumbnailUrl = thumbnailUrl,
        type = FanboxDownloadItems.Item.Type.Image,
    )

    override fun PostDetail.FileItem.toDownloadItem(): FanboxDownloadItems.Item = FanboxDownloadItems.Item(
        postId = postId,
        itemId = id,
        name = "file-${postId.value}-${id.value}",
        extension = extension,
        originalUrl = url,
        thumbnailUrl = "",
        type = FanboxDownloadItems.Item.Type.File,
    )

    private fun saveItem(
        item: FanboxDownloadItems.Item,
        saveDirName: String,
        postDirName: String?,
        bytes: ByteArray,
    ) {
        runCatching {
            val parentDir = if (postDirName == null) {
                downloadRoot().resolve(saveDirName)
            } else {
                downloadRoot().resolve(saveDirName).resolve(postDirName)
            }

            Files.createDirectories(parentDir)

            parentDir.resolve("${item.name}.${item.extension}").writeBytes(bytes)
        }
    }

    /** ダウンロードの保存先。プラットフォーム標準の画像フォルダ配下の FANBOX ディレクトリを使う。 */
    private fun downloadRoot(): Path = saveDirectoryProvider.picturesDirectory().resolve("FANBOX")

    /** 記事本文をテキストファイルとして保存する。オプションが無効か本文が空の場合は何もしない。 */
    private fun savePostBody(
        postId: PostId,
        postDetail: PostDetail?,
        saveDirName: String,
        postDirName: String?,
    ) {
        val text = postDetail?.body?.toPlainText() ?: return
        val fileName = if (postDirName != null) "本文.txt" else "${postId.value}.txt"

        runCatching {
            val parentDir = if (postDirName == null) {
                downloadRoot().resolve(saveDirName)
            } else {
                downloadRoot().resolve(saveDirName).resolve(postDirName)
            }

            Files.createDirectories(parentDir)
            parentDir.resolve(fileName).writeText(text)
        }
    }
}
