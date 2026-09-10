package dev.adambench.habbits

import android.app.Application
import android.net.Uri
import dev.adambench.habbits.data.FileSettingsStore
import dev.adambench.habbits.data.createHabbitsDatabase
import dev.adambench.habbits.sync.SafSyncStore
import java.io.File

class HabbitsApplication : Application() {

    /** Outlives the activity, so a rotation does not reopen the database. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val deviceId = loadOrCreateDeviceId(this)
        container = AppContainer(
            database = createHabbitsDatabase(this),
            settingsStore = FileSettingsStore(File(filesDir, "settings.json")),
            deviceId = deviceId,
            syncStoreFor = { folder ->
                // The folder is a Storage Access Framework tree the user granted.
                runCatching { SafSyncStore(this, Uri.parse(folder), deviceId) }.getOrNull()
            },
        )
        val settings = container.settingsRepository.settings.value
        if (settings.sync.isConfigured) container.bindSync(settings.sync.folder)
    }
}
