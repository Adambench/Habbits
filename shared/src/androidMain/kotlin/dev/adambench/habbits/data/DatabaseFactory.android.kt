package dev.adambench.habbits.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createHabbitsDatabase(context: Context): HabbitsDatabase =
    Room.databaseBuilder<HabbitsDatabase>(
        context = context.applicationContext,
        name = context.getDatabasePath(HabbitsDatabase.FILE_NAME).absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
