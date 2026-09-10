package dev.adambench.habbits

import android.app.Application
import dev.adambench.habbits.data.createHabbitsDatabase

class HabbitsApplication : Application() {

    /** Outlives the activity, so a rotation does not reopen the database. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            database = createHabbitsDatabase(this),
            deviceId = loadOrCreateDeviceId(this),
        )
    }
}
