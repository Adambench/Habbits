package dev.adambench.habbits.sync

import java.io.File

/**
 * The synced folder on disk: one append-only `.jsonl` per device.
 *
 * Point [directory] at anything a sync tool replicates — a Syncthing folder, a
 * Nextcloud folder, a git checkout, a mounted USB stick. The app never needs to
 * know which.
 */
class FileSyncStore(
    private val directory: File,
    override val deviceId: String,
) : SyncStore {

    private val ownLog: File get() = File(directory, "device-$deviceId.jsonl")

    override fun appendToOwnLog(lines: List<String>) {
        if (lines.isEmpty()) return
        directory.mkdirs()
        // Appending, never rewriting, so a sync tool only ever sees the file grow.
        ownLog.appendText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    override fun readAllLogs(): List<String> {
        val files = directory.listFiles { f: File -> f.isFile && f.name.endsWith(".jsonl") }
            ?: return emptyList()
        // Sorted so a run is reproducible; the merge does not depend on it.
        return files.sortedBy { it.name }.flatMap { file ->
            runCatching { file.readLines() }.getOrElse { emptyList() }
        }
    }

    override fun rewriteOwnLog(lines: List<String>) {
        directory.mkdirs()
        val temp = File(directory, ownLog.name + ".tmp")
        temp.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        if (!temp.renameTo(ownLog)) {
            ownLog.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
            temp.delete()
        }
    }

    override fun ownLogLineCount(): Int =
        runCatching { ownLog.takeIf { it.isFile }?.readLines()?.size ?: 0 }.getOrDefault(0)
}
