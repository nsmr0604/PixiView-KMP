package me.matsumo.fanbox.core.repository.download.ios

import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSPredicate
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionChangeRequest
import platform.Photos.PHAssetCollectionSubtype
import platform.Photos.PHAssetCollectionType
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage

/**
 * iOS のネイティブ API（Photos framework）で写真ライブラリのアルバムに画像を保存する層。
 *
 * クリエイター名のアルバムが無ければ作成してから画像を追加する。
 */
internal class PhotosAlbumSaver {

    /** クリエイター名のアルバムに画像を保存する。アルバムがなければ作成する。 */
    @OptIn(BetaInteropApi::class)
    fun save(image: UIImage, albumName: String) {
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
}
