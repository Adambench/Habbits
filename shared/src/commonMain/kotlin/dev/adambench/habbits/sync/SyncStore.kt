package dev.adambench.habbits.sync

/**
 * The synced folder, as the app sees it.
 *
 * Only [appendToOwnLog] writes, and only ever to this device's own file. That
 * single restriction is what makes the design transport-agnostic: no two devices
 * write the same file, so a file-sync tool never has a conflict to resolve.
 */
interface SyncStore {

    val deviceId: String

    fun appendToOwnLog(lines: List<String>)

    /** Every line from every device's log, this device's own included. */
    fun readAllLogs(): List<String>

    /** Replaces this device's own log. Used only by compaction. */
    fun rewriteOwnLog(lines: List<String>)

    fun ownLogLineCount(): Int
}
