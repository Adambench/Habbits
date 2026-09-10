package io.github.adambench.habbits.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Follows the XDG base directory spec, so the database lands somewhere a Linux
 * user expects rather than in a dotfile at `$HOME`.
 */
fun defaultDataDirectory(): File {
    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
    val base = if (xdg != null) File(xdg) else File(System.getProperty("user.home"), ".local/share")
    return File(base, "habbits").apply { mkdirs() }
}

fun createHabbitsDatabase(directory: File = defaultDataDirectory()): HabbitsDatabase =
    Room.databaseBuilder<HabbitsDatabase>(
        name = File(directory, HabbitsDatabase.FILE_NAME).absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
