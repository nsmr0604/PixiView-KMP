package me.matsumo.fanbox.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.Path

class PreferenceHelperImpl(private val ioDispatcher: CoroutineDispatcher) : PreferenceHelper {

    override fun create(name: String): DataStore<Preferences> {
        val directory = Path.of(System.getProperty("user.home"), ".pixiview")

        Files.createDirectories(directory)

        return PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = null,
            migrations = emptyList(),
            scope = CoroutineScope(ioDispatcher),
            produceFile = { directory.resolve("$name.preferences_pb").toString().toPath() },
        )
    }

    override fun delete(name: String) {
        // do nothing
    }
}
