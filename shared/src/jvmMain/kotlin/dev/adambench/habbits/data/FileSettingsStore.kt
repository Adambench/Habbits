package dev.adambench.habbits.data

import dev.adambench.habbits.domain.SettingsStore
import java.io.File

/** Settings as a small JSON file beside the database. */
class FileSettingsStore(private val file: File) : SettingsStore {

    override fun read(): String? = file.takeIf { it.isFile }?.readText()

    override fun write(text: String) {
        file.parentFile?.mkdirs()
        // Write then move, so an interrupted save cannot truncate the settings.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }
}
