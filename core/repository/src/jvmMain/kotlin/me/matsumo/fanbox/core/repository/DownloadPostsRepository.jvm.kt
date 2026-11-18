package me.matsumo.fanbox.core.repository

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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
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

                val items = when (val type = downloadItems.requestType) {
                    FanboxDownloadItems.RequestType.File -> downloadItems.items

                    FanboxDownloadItems.RequestType.Image -> downloadItems.items

                    is FanboxDownloadItems.RequestType.WholePost -> {
                        val postDetail = fanboxRepository.getPostDetail(downloadItems.postId)
                        val images = postDetail.body.imageItems.map { it.toDownloadItem() }
                        val files = postDetail.body.fileItems.map { it.toDownloadItem() }

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

                val saveDirName = resolveSaveDirName(downloadItems.requestType, downloadItems.postId)
                val postDirName = resolvePostDirName(downloadItems.postId, downloadItems.title)

                for ((item, bytes) in results.filterNotNull()) {
                    saveItem(item, saveDirName, postDirName, bytes)
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

    override suspend fun getSaveDirectory(requestType: FanboxDownloadItems.RequestType): String = downloadRoot().toString()
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
    private fun downloadRoot(): Path = picturesDirectory().resolve("FANBOX")

    /** 保存先のサブディレクトリ名。すべてのダウンロードをクリエイターごとのディレクトリにまとめる。 */
    private suspend fun resolveSaveDirName(
        requestType: FanboxDownloadItems.RequestType,
        postId: PostId,
    ): String = when (requestType) {
        is FanboxDownloadItems.RequestType.Image, is FanboxDownloadItems.RequestType.File -> {
            runCatching { fanboxRepository.getPostDetail(postId).user?.name }
                .getOrDefault(null)
                .sanitizeDirName()
        }

        is FanboxDownloadItems.RequestType.WholePost -> requestType.post?.user?.name.sanitizeDirName()
    }

    /** 投稿ごとのサブディレクトリ名。オプションが無効な場合は null を返す。 */
    private suspend fun resolvePostDirName(postId: PostId, title: String): String? {
        if (!userDataStore.setting.first().isUsePostIdDirectory) return null

        return "【${postId.value}.$title】".sanitizeDirName()
    }

    /** ファイルパスに使えない文字を置換する。Windows では末尾のドットと空白も無効。 */
    private fun String?.sanitizeDirName(): String = this
        ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
        ?.trimEnd('.', ' ')
        ?.takeIf { it.isNotBlank() }
        ?.take(100)
        ?: "UnknownUser"

    /** プラットフォーム標準の画像フォルダ。Windows/macOS は ~/Pictures、Linux は XDG 設定を優先する。 */
    private fun picturesDirectory(): Path {
        val home = Path.of(System.getProperty("user.home"))
        val default = home.resolve("Pictures")

        if (!System.getProperty("os.name").contains("Linux", ignoreCase = true)) {
            return default
        }

        val userDirs = home.resolve(".config/user-dirs.dirs")

        return runCatching {
            val dir = userDirs.readText()
                .lineSequence()
                .firstOrNull { it.startsWith("XDG_PICTURES_DIR") }
                ?.substringAfter('=')
                ?.trim()
                ?.trim('"')
                ?.replaceFirst("\$HOME", home.toString())
                ?.takeIf { it.isNotBlank() }

            dir?.let { Path.of(it) }
        }.getOrNull() ?: default
    }
}
