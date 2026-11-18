package me.matsumo.fanbox.core.repository

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
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
import me.matsumo.fanbox.core.repository.download.ios.PhotosAlbumSaver
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * iOS / iPadOS 向けのダウンロード保存実装。
 *
 * ダウンロードの進行管理はコアの層 [BaseDownloadPostsRepository] が担い、
 * このクラスはネイティブ API へのダウンロードと保存を扱う。
 * 画像は写真ライブラリ（オプションでクリエイター別アルバム）、ファイルは Documents 配下に保存する。
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
internal class DownloadPostsRepositoryImpl(
    fanboxRepository: FanboxRepository,
    userDataStore: SettingDataStore,
    scope: CoroutineScope,
) : BaseDownloadPostsRepository<ByteArray>(fanboxRepository, userDataStore, scope) {

    private val albumSaver = PhotosAlbumSaver()

    override suspend fun getSaveDirectory(requestType: FanboxDownloadItems.RequestType): String = "Unknown"

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
        // アルバム名の解決にはクリエイター名が必要。base が取得していない場合（Image / File）はここで取得する。
        val detail = postDetail ?: runCatching { fanboxRepository.getPostDetail(downloadItems.postId) }.getOrNull()

        val albumName = if (downloadDirectories.isUseCreatorAlbum()) {
            downloadDirectories.creatorDirName(downloadItems.requestType, detail)
        } else {
            null
        }
        val postDirName = downloadDirectories.postDirName(downloadItems.postId, downloadItems.title)

        for ((item, bytes) in results.filterNotNull()) {
            saveItem(item, downloadItems.requestType, albumName, postDirName, bytes)
        }
    }

    // toDownloadItem は iOS では画像の連番を使わないため index を無視する。
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

    @OptIn(BetaInteropApi::class)
    private fun saveItem(
        item: FanboxDownloadItems.Item,
        requestType: FanboxDownloadItems.RequestType,
        albumName: String?,
        postDirName: String?,
        bytes: ByteArray,
    ) {
        runCatching {
            when (item.type) {
                FanboxDownloadItems.Item.Type.Image -> {
                    val nsData = memScoped { NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong()) }
                    val uiImage = UIImage.imageWithData(nsData)!!

                    if (albumName != null) {
                        albumSaver.save(uiImage, albumName)
                    } else {
                        UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
                    }
                }

                FanboxDownloadItems.Item.Type.File -> {
                    var path = NSHomeDirectory() + "/Documents/FANBOX/${getParentDirName(requestType)}"

                    if (postDirName != null) {
                        path += "/$postDirName"
                    }

                    val nsData = memScoped { NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong()) }
                    val fileManager = NSFileManager.defaultManager

                    if (!fileManager.fileExistsAtPath(path)) {
                        fileManager.createDirectoryAtPath(path, true, null, null)
                    }

                    NSFileHandle.fileHandleForWritingAtPath(path + item.name)!!.apply {
                        writeData(nsData)
                        closeFile()
                    }
                }
            }
        }
    }

    /** ファイル保存先のディレクトリ名。投稿一括ダウンロードはクリエイターごとにまとめる。 */
    private fun getParentDirName(requestType: FanboxDownloadItems.RequestType?): String = when (requestType) {
        is FanboxDownloadItems.RequestType.Image -> "images"

        is FanboxDownloadItems.RequestType.File -> "files"

        is FanboxDownloadItems.RequestType.WholePost -> requestType.post?.user?.name ?: "UnknownCreator"

        else -> "FANBOX"
    }
}
