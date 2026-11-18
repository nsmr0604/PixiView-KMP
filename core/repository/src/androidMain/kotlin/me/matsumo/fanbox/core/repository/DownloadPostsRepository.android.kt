package me.matsumo.fanbox.core.repository

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import me.matsumo.fanbox.core.common.util.suspendRunCatching
import me.matsumo.fanbox.core.datastore.SettingDataStore
import me.matsumo.fanbox.core.logs.category.PostsLog
import me.matsumo.fanbox.core.logs.logger.send
import me.matsumo.fanbox.core.model.DownloadFileType
import me.matsumo.fanbox.core.model.FanboxDownloadItems
import me.matsumo.fanbox.core.model.fanbox.PostDetail
import me.matsumo.fanbox.core.repository.download.BaseDownloadPostsRepository
import me.matsumo.fanbox.core.repository.download.android.AndroidDownloadFileSaver
import java.io.File
import java.util.Locale
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android 向けのダウンロード保存実装。
 *
 * ダウンロードの進行管理はコアの層 [BaseDownloadPostsRepository] が担い、
 * このクラスはネイティブ API へのダウンロードと保存先の選択を扱う。
 * 保存は SAF / MediaStore を使う [AndroidDownloadFileSaver]（Android 層）が行う。
 */
@OptIn(ExperimentalUuidApi::class)
internal class DownloadPostsRepositoryImpl(
    private val context: Context,
    userDataStore: SettingDataStore,
    fanboxRepository: FanboxRepository,
    scope: CoroutineScope,
) : BaseDownloadPostsRepository<File>(fanboxRepository, userDataStore, scope) {

    private val fileSaver = AndroidDownloadFileSaver(context, userDataStore)

    // これまでは同時ダウンロード数を制限していなかったため、制限なしを維持する
    override val maxConcurrentDownloads = Int.MAX_VALUE

    override fun buildItems(
        downloadItems: FanboxDownloadItems,
        postDetail: PostDetail?,
    ): List<FanboxDownloadItems.Item> = when (val type = downloadItems.requestType) {
        FanboxDownloadItems.RequestType.File, FanboxDownloadItems.RequestType.Image -> downloadItems.items

        is FanboxDownloadItems.RequestType.WholePost -> {
            val detail = checkNotNull(postDetail)
            val images = detail.body.imageItems.mapIndexed { index, image -> image.toDownloadItem(index) }
            val files = detail.body.fileItems.map { it.toDownloadItem() }

            images + if (type.isIgnoreFiles) emptyList() else files
        }
    }

    override suspend fun getSaveDirectory(requestType: FanboxDownloadItems.RequestType): String = fileSaver.saveLocation(requestType)

    override suspend fun downloadItem(
        item: FanboxDownloadItems.Item,
        onDownload: (Float) -> Unit,
    ): Pair<FanboxDownloadItems.Item, File>? = suspendRunCatching {
        val fileType = userDataStore.setting.first().downloadFileType
        val url = if (item.extension.lowercase() != "gif" || fileType == DownloadFileType.ORIGINAL) item.originalUrl else item.thumbnailUrl

        val tmpFile = File(context.cacheDir, "tmp-${item.name}.${item.extension}")

        // チャンクを追記していくため、中断した前回のダウンロードが残っていると先頭に
        // 混入する。書き始める前に必ず消す。
        tmpFile.delete()

        try {
            fanboxRepository.download(
                url = url,
                onDownload = onDownload,
                onChunk = { chunk -> tmpFile.appendBytes(chunk) },
            )
        } catch (e: Throwable) {
            // 失敗とキャンセルのどちらでも書きかけを残さない。CancellationException も
            // ここを通るため、削除してからそのまま再送出する。
            tmpFile.delete()
            throw e
        }

        onDownload.invoke(1f)

        item to tmpFile
    }.onFailure {
        Napier.e(it) { "Failed to download item: ${item.name}" }
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
        results: List<Pair<FanboxDownloadItems.Item, File>?>,
    ) = fileSaver.save(downloadItems, results)

    override fun PostDetail.ImageItem.toDownloadItem(index: Int): FanboxDownloadItems.Item {
        val namePrefix = if (index >= 0) "image-%03d".format(Locale.ROOT, index) else "image"

        return FanboxDownloadItems.Item(
            postId = postId,
            itemId = id,
            name = "$namePrefix-${postId.value}-${id.value}",
            extension = extension,
            originalUrl = originalUrl,
            thumbnailUrl = thumbnailUrl,
            type = FanboxDownloadItems.Item.Type.Image,
        )
    }

    override fun PostDetail.FileItem.toDownloadItem(): FanboxDownloadItems.Item = FanboxDownloadItems.Item(
        postId = postId,
        itemId = id,
        name = "file-${postId.value}-${id.value}",
        extension = extension,
        originalUrl = url,
        thumbnailUrl = "",
        type = FanboxDownloadItems.Item.Type.File,
    )
}
