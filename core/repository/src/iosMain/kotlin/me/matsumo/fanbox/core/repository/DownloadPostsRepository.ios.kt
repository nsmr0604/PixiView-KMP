package me.matsumo.fanbox.core.repository

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.matsumo.fanbox.core.common.util.suspendRunCatching
import me.matsumo.fanbox.core.datastore.SettingDataStore
import me.matsumo.fanbox.core.logs.category.PostsLog
import me.matsumo.fanbox.core.logs.logger.send
import me.matsumo.fanbox.core.model.DownloadFileType
import me.matsumo.fanbox.core.model.DownloadState
import me.matsumo.fanbox.core.model.FanboxDownloadItems
import me.matsumo.fanbox.core.model.fanbox.Post
import me.matsumo.fanbox.core.model.fanbox.PostDetail
import me.matsumo.fanbox.core.model.fanbox.PostId
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSPredicate
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionChangeRequest
import platform.Photos.PHAssetCollectionSubtype
import platform.Photos.PHAssetCollectionType
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
class DownloadPostsRepositoryImpl(
    private val fanboxRepository: FanboxRepository,
    private val userDataStore: SettingDataStore,
    private val scope: CoroutineScope,
) : DownloadPostsRepository {

    private val semaphore = Semaphore(2)

    private var _reservingPosts = MutableStateFlow(emptyList<FanboxDownloadItems>())
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.None)

    override val reservingPosts: StateFlow<List<FanboxDownloadItems>> = _reservingPosts.asStateFlow()
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(500.milliseconds)

                val downloadItems = _reservingPosts.getAndUpdate { it.drop(1) }.firstOrNull()

                if (downloadItems == null) {
                    _downloadState.value = DownloadState.None
                    continue
                }

                // クリエイター名の解決とアルバム保存に使う投稿詳細。Image / File の一括ダウンロードでも取得する。
                val postDetail = runCatching { fanboxRepository.getPostDetail(downloadItems.postId) }.getOrNull()

                val items = when (val type = downloadItems.requestType) {
                    FanboxDownloadItems.RequestType.File -> downloadItems.items

                    FanboxDownloadItems.RequestType.Image -> downloadItems.items

                    is FanboxDownloadItems.RequestType.WholePost -> {
                        val detail = checkNotNull(postDetail)
                        val images = detail.body.imageItems.map { it.toDownloadItem() }
                        val files = detail.body.fileItems.map { it.toDownloadItem() }

                        images + if (type.isIgnoreFiles) emptyList() else files
                    }
                }

                val itemProgresses = MutableList(items.size) { MutableStateFlow(0f) }
                val data = items.mapIndexed { index, item ->
                    async {
                        semaphore.withPermit {
                            downloadItem(item) { progress ->
                                itemProgresses[index].value = progress

                                _downloadState.value = DownloadState.Downloading(
                                    items = downloadItems,
                                    progress = itemProgresses.sumOf { it.value.toDouble() }.toFloat() / items.size,
                                )
                            }
                        }
                    }
                }

                val results = data.awaitAll()

                val albumName = if (userDataStore.setting.first().isUseCreatorAlbum) {
                    resolveAlbumName(downloadItems.requestType, postDetail)
                } else {
                    null
                }
                val postDirName = resolvePostDirName(downloadItems.postId, downloadItems.title)

                for ((item, bytes) in results.filterNotNull()) {
                    saveItem(item, downloadItems.requestType, albumName, postDirName, bytes)
                }
            }
        }
    }

    override fun cancelDownload(key: String) {
        _reservingPosts.update { posts ->
            posts.filter { it.key != key }
        }
    }

    override fun requestDownloadPost(post: Post, isIgnoreFiles: Boolean) {
        scope.launch {
            val items = FanboxDownloadItems(
                postId = post.id,
                title = post.title,
                items = emptyList(),
                requestType = FanboxDownloadItems.RequestType.WholePost(post, isIgnoreFiles),
                key = Uuid.random().toHexString(),
            )

            _reservingPosts.update { it + items }
        }
    }

    override fun requestDownloadImages(
        postId: PostId,
        title: String,
        images: List<PostDetail.ImageItem>,
    ) {
        val items = FanboxDownloadItems(
            postId = postId,
            title = title,
            items = images.map { it.toDownloadItem() },
            requestType = FanboxDownloadItems.RequestType.Image,
            key = Uuid.random().toHexString(),
        )

        _reservingPosts.update { it + items }
    }

    override fun requestDownloadFiles(
        postId: PostId,
        title: String,
        files: List<PostDetail.FileItem>,
    ) {
        val items = FanboxDownloadItems(
            postId = postId,
            title = title,
            items = files.map { it.toDownloadItem() },
            requestType = FanboxDownloadItems.RequestType.File,
            key = Uuid.random().toHexString(),
        )

        _reservingPosts.update { it + items }
    }

    override suspend fun getSaveDirectory(requestType: FanboxDownloadItems.RequestType): String = "Unknown"

    private fun PostDetail.ImageItem.toDownloadItem(): FanboxDownloadItems.Item = FanboxDownloadItems.Item(
        postId = postId,
        itemId = id,
        name = "image-${postId.value}-${id.value}",
        extension = extension,
        originalUrl = originalUrl,
        thumbnailUrl = thumbnailUrl,
        type = FanboxDownloadItems.Item.Type.Image,
    )

    private fun PostDetail.FileItem.toDownloadItem(): FanboxDownloadItems.Item = FanboxDownloadItems.Item(
        postId = postId,
        itemId = id,
        name = "file-${postId.value}-${id.value}",
        extension = extension,
        originalUrl = url,
        thumbnailUrl = "",
        type = FanboxDownloadItems.Item.Type.File,
    )

    private suspend fun downloadItem(item: FanboxDownloadItems.Item, onDownload: (Float) -> Unit): Pair<FanboxDownloadItems.Item, ByteArray>? = suspendRunCatching {
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
                        saveImageToAlbum(uiImage, albumName)
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

    /** アルバム名。すべてのダウンロードをクリエイターごとのアルバムにまとめる。 */
    private fun resolveAlbumName(
        requestType: FanboxDownloadItems.RequestType,
        postDetail: PostDetail?,
    ): String = when (requestType) {
        is FanboxDownloadItems.RequestType.Image, is FanboxDownloadItems.RequestType.File -> postDetail?.user?.name

        is FanboxDownloadItems.RequestType.WholePost -> requestType.post?.user?.name ?: postDetail?.user?.name
    }.sanitizeAlbumName()

    /** アルバム名に使えない文字を置換する。 */
    private fun String?.sanitizeAlbumName(): String = this
        ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
        ?.trimEnd('.', ' ')
        ?.takeIf { it.isNotBlank() }
        ?.take(100)
        ?: "UnknownCreator"

    /** クリエイター名のアルバムに画像を保存する。アルバムがなければ作成する。 */
    private fun saveImageToAlbum(image: UIImage, albumName: String) {
        PHPhotoLibrary.requestAuthorization { status ->
            if (status == PHAuthorizationStatus.Denied || status == PHAuthorizationStatus.Restricted) return@requestAuthorization

            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                {
                    val album = fetchAlbum(albumName)
                        ?: PHAssetCollectionChangeRequest.creationRequestForAssetCollectionWithTitle(albumName)
                            .placeholderForCreatedAssetCollection

                    val asset = PHAssetCreationRequest.creationRequestForAssetFromImage(image)?.placeholderForCreatedAsset

                    if (album != null && asset != null) {
                        PHAssetCollectionChangeRequest.forAssetCollection(album).addAssets(listOf(asset))
                    }
                },
                { error ->
                    if (error != null) {
                        Napier.e { "Failed to save image to album: ${error.localizedDescription}" }
                    }
                },
            )
        }
    }

    /** 指定名のアルバムを検索する。performChanges の change block 内で呼ぶこと。 */
    private fun fetchAlbum(albumName: String): PHAssetCollection? {
        val options = PHFetchOptions().apply {
            predicate = NSPredicate(format = "title == %@", albumName)
        }

        return PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionType.Album,
            PHAssetCollectionSubtype.AlbumRegular,
            options,
        ).firstObject as? PHAssetCollection
    }

    /** 投稿ごとのサブディレクトリ名。オプションが無効な場合は null を返す。 */
    private suspend fun resolvePostDirName(postId: PostId, title: String): String? {
        if (!userDataStore.setting.first().isUsePostIdDirectory) return null

        return "${postId.value}.$title"
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trimEnd('.', ' ')
            .take(100)
            .takeIf { it.isNotBlank() }
    }

    private fun getParentDirName(requestType: FanboxDownloadItems.RequestType?): String = when (requestType) {
        is FanboxDownloadItems.RequestType.Image -> "images"
        is FanboxDownloadItems.RequestType.File -> "files"
        is FanboxDownloadItems.RequestType.WholePost -> requestType.post?.user?.name ?: "UnknownUser"
        else -> "FANBOX"
    }
}
