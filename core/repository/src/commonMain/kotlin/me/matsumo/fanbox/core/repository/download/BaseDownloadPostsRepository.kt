package me.matsumo.fanbox.core.repository.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.matsumo.fanbox.core.common.util.suspendRunCatching
import me.matsumo.fanbox.core.datastore.SettingDataStore
import me.matsumo.fanbox.core.model.DownloadState
import me.matsumo.fanbox.core.model.FanboxDownloadItems
import me.matsumo.fanbox.core.model.fanbox.Post
import me.matsumo.fanbox.core.model.fanbox.PostDetail
import me.matsumo.fanbox.core.model.fanbox.PostId
import me.matsumo.fanbox.core.repository.DownloadPostsRepository
import me.matsumo.fanbox.core.repository.FanboxRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [DownloadPostsRepository] のプラットフォーム共通実装。
 *
 * ダウンロードキューの消化・進捗通知・ダウンロード完了後の流れはコアの層で行い、
 * アイテムの取得（[downloadItem]）と保存（[saveResults]）だけをプラットフォーム層に委ねる。
 * 型引数 [F] はダウンロード済みデータの型（Android は一時ファイル、PC はバイト列など）。
 */
@OptIn(ExperimentalUuidApi::class)
internal abstract class BaseDownloadPostsRepository<F>(
    protected val fanboxRepository: FanboxRepository,
    protected val userDataStore: SettingDataStore,
    private val scope: CoroutineScope,
) : DownloadPostsRepository {

    /** 同時にダウンロードできるアイテム数。プラットフォーム層が上書きできる。 */
    protected open val maxConcurrentDownloads: Int = 2

    // 初期化順序の制約（スーパークラスの init 中にサブクラスの override は読めない）があるため lazy にする
    private val semaphore by lazy { Semaphore(maxConcurrentDownloads) }

    private val _reservingPosts = MutableStateFlow(emptyList<FanboxDownloadItems>())
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.None)

    /** ディレクトリ・アルバム名の算出とオプション判定を担うコアの層。 */
    protected val downloadDirectories = DownloadDirectories(userDataStore)

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

                // 投稿一括ダウンロードでは本文やアイテムのために投稿詳細を取得する。
                // Image / File の一括ダウンロードは、必要なプラットフォーム層が saveResults 内で取得する。
                val postDetail = if (downloadItems.requestType is FanboxDownloadItems.RequestType.WholePost) {
                    runCatching { fanboxRepository.getPostDetail(downloadItems.postId) }.getOrNull()
                } else {
                    null
                }

                // 1 件の失敗でダウンロードループ自体が死ぬと以降の予約が消化されなくなるため、
                // バッチ単位で失敗を握りつぶす。
                suspendRunCatching {
                    val items = buildItems(downloadItems, postDetail)

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

                    saveResults(downloadItems, postDetail, data.awaitAll())
                }
            }
        }
    }

    final override fun cancelDownload(key: String) {
        _reservingPosts.update { posts ->
            posts.filter { it.key != key }
        }
    }

    final override fun requestDownloadPost(post: Post, isIgnoreFiles: Boolean) {
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

    final override fun requestDownloadImages(
        postId: PostId,
        title: String,
        images: List<PostDetail.ImageItem>,
    ) {
        val items = FanboxDownloadItems(
            postId = postId,
            title = title,
            items = images.mapIndexed { index, image -> image.toDownloadItem(index) },
            requestType = FanboxDownloadItems.RequestType.Image,
            key = Uuid.random().toHexString(),
        )

        _reservingPosts.update { it + items }
    }

    final override fun requestDownloadFiles(
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

    /** バッチで保存するアイテムの一覧を組み立てる。 */
    protected abstract fun buildItems(
        downloadItems: FanboxDownloadItems,
        postDetail: PostDetail?,
    ): List<FanboxDownloadItems.Item>

    /** 1 アイテムをダウンロードし、プラットフォーム固有の形式（[F]）で返す。 */
    protected abstract suspend fun downloadItem(
        item: FanboxDownloadItems.Item,
        onDownload: (Float) -> Unit,
    ): Pair<FanboxDownloadItems.Item, F>?

    /** ダウンロード済みのアイテムをプラットフォーム固有の保存先に書き込む。 */
    protected abstract suspend fun saveResults(
        downloadItems: FanboxDownloadItems,
        postDetail: PostDetail?,
        results: List<Pair<FanboxDownloadItems.Item, F>?>,
    )

    /** 画像アイテムをダウンロード対象に変換する。Android は同時取得画像に index 付きの命名を使う。 */
    protected abstract fun PostDetail.ImageItem.toDownloadItem(index: Int): FanboxDownloadItems.Item

    /** [toDownloadItem] のファイル版。 */
    protected abstract fun PostDetail.FileItem.toDownloadItem(): FanboxDownloadItems.Item
}
