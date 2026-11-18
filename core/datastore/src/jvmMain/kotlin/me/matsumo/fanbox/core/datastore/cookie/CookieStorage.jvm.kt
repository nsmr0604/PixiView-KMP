package me.matsumo.fanbox.core.datastore.cookie

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CoroutineDispatcher
import me.matsumo.fanbox.core.datastore.OldCookieDataStore
import me.matsumo.fankt.fanbox.persistence.room.createRoomFanboxCookieStorage

/**
 * Desktop 向けの Cookie 保存先を作る。
 *
 * 資格情報は KSafe で保存する。JVM 実装は OS の資格情報ストアを経由しないため、
 * Android/iOS より保護が弱い点に注意すること。
 * 既存の `fankt.db` からの移行を挟む。
 * 返した実体はアプリの生存期間中 1 つだけ使い、閉じないこと。
 */
internal fun createCookieStorage(ioDispatcher: CoroutineDispatcher, oldCookieDataStore: OldCookieDataStore): MigratingFanboxCookieStorage {
    return createMigratingCookieStorage(
        kSafe = KSafe(),
        legacyStorageFactory = {
            RoomLegacyCookieStorage(
                createRoomFanboxCookieStorage(ioDispatcher = ioDispatcher),
            )
        },
        oldCookieDataStore = oldCookieDataStore,
    )
}
