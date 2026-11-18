package me.matsumo.fanbox.core.repository.download.android

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import me.matsumo.fanbox.core.datastore.SettingDataStore
import me.matsumo.fanbox.core.model.FanboxDownloadItems
import me.matsumo.fanbox.core.repository.download.DownloadDirectories
import java.io.File
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android のネイティブ API（SAF / MediaStore）でダウンロード結果を保存する層。
 *
 * 保存先はユーザー選択のディレクトリ（SAF）を優先し、未選択なら MediaStore の
 * Pictures / Downloads 配下の FANBOX ディレクトリに保存する。
 */
internal class AndroidDownloadFileSaver(
    private val context: Context,
    private val userDataStore: SettingDataStore,
) {

    private val directories = DownloadDirectories(userDataStore)

    /** 保存先のパス表現。設定画面の表示に使う。 */
    suspend fun saveLocation(requestType: FanboxDownloadItems.RequestType): String =
        (getParentFile(requestType) ?: getOldParentFile(requestType, true))?.filePath ?: "Unknown"

    /** ダウンロード済みの一時ファイルを保存先へ書き込む。 */
    suspend fun save(
        downloadItems: FanboxDownloadItems,
        results: List<Pair<FanboxDownloadItems.Item, File>?>,
    ) {
        val postDirName = directories.postDirName(downloadItems.postId, downloadItems.title)

        for ((item, tmpFile) in results.filterNotNull()) {
            runCatching {
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extension)
                val selectedLocation = (getParentFile(downloadItems.requestType) ?: getOldParentFile(downloadItems.requestType))
                    ?.let { if (postDirName != null) it.createDirectory(postDirName) else it }
                val name = "${item.name}.${item.extension.ifBlank { "jpeg" }}"

                when {
                    selectedLocation != null -> {
                        saveFileToSelectedLocation(name, tmpFile, selectedLocation, mimeType)
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        saveFileAfterSdkQ(name, tmpFile, mimeType, downloadItems.requestType, postDirName)
                    }
                }

                tmpFile.delete()
                delay(500.milliseconds)
            }.onFailure {
                Napier.e(it) { "Failed to download item: ${item.name}" }
            }
        }
    }

    private fun getOldParentFile(
        requestType: FanboxDownloadItems.RequestType,
        isDryRun: Boolean = false,
    ): UniFile? {
        val environmentDir = when (requestType) {
            is FanboxDownloadItems.RequestType.Image -> Environment.DIRECTORY_PICTURES
            is FanboxDownloadItems.RequestType.File -> Environment.DIRECTORY_DOWNLOADS
            is FanboxDownloadItems.RequestType.WholePost -> Environment.DIRECTORY_DOWNLOADS
        }

        val child = when (requestType) {
            is FanboxDownloadItems.RequestType.Image -> "FANBOX"
            is FanboxDownloadItems.RequestType.File -> "FANBOX"
            is FanboxDownloadItems.RequestType.WholePost -> requestType.post?.user?.name ?: "UnknownCreator"
        }

        val dir = File("${Environment.getExternalStorageDirectory().path}/$environmentDir", "FANBOX")
        val childDir = File(dir, child)

        if (!isDryRun) {
            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    return null
                }
            }

            if (child.isNotBlank() && !childDir.exists()) {
                if (!childDir.mkdir()) {
                    return null
                }
            }
        }

        return UniFile.fromFile(childDir)!!
    }

    private suspend fun getParentFile(requestType: FanboxDownloadItems.RequestType): UniFile? {
        val userData = userDataStore.setting.first()

        return when (requestType) {
            is FanboxDownloadItems.RequestType.Image -> {
                if (userData.imageSaveDirectory.isBlank()) return null
                UniFile.fromUri(context, userData.imageSaveDirectory.toUri())
            }

            is FanboxDownloadItems.RequestType.File -> {
                if (userData.fileSaveDirectory.isBlank()) return null
                UniFile.fromUri(context, userData.fileSaveDirectory.toUri())
            }

            is FanboxDownloadItems.RequestType.WholePost -> {
                if (userData.postSaveDirectory.isBlank()) return null
                val parentFile = UniFile.fromUri(context, userData.postSaveDirectory.toUri())
                parentFile.createDirectory(requestType.post?.user?.name ?: "UnknownCreator")
            }
        }
    }

    private suspend fun saveFileToSelectedLocation(
        name: String,
        tmpFile: File,
        parentFile: UniFile,
        mimeType: String?,
    ) = suspendCancellableCoroutine<Uri> { continuation ->
        val file = parentFile.createFile(name)

        context.contentResolver.openOutputStream(file.uri)!!.use {
            tmpFile.inputStream().use { input ->
                input.copyTo(it)
            }
        }

        MediaScannerConnection.scanFile(context, arrayOf(parentFile.filePath), arrayOf(mimeType)) { _, uri ->
            continuation.resume(uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveFileAfterSdkQ(
        fileName: String,
        tmpFile: File,
        mimeType: String?,
        requestType: FanboxDownloadItems.RequestType,
        postDirName: String?,
    ) = suspendCancellableCoroutine { continuation ->
        val basePath = getOldParentFile(requestType, true)?.filePath
        val path = if (postDirName != null) "$basePath/$postDirName" else basePath
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, path)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)

        if (itemUri != null) {
            resolver.openOutputStream(itemUri)?.use {
                tmpFile.inputStream().use { input ->
                    input.copyTo(it)
                }
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)

            continuation.resume(itemUri)
        } else {
            error("Failed to save file.")
        }
    }
}
