package dev.adambench.habbits.sync

/**
 * Buffers events and writes them to the device's own log.
 *
 * Buffered because a single toggle produces one event and touching the disk per
 * tap on a phone is wasteful; [flush] is called when the app pauses and after
 * batches like an import.
 */
class BufferedSyncJournal(
    private val store: SyncStore,
    private val flushThreshold: Int = 32,
) : SyncJournal {

    private val pending = mutableListOf<String>()

    override fun record(event: SyncEvent) {
        val line = SyncMerger.encode(event)
        val shouldFlush: Boolean
        synchronized(pending) {
            pending += line
            shouldFlush = pending.size >= flushThreshold
        }
        if (shouldFlush) flush()
    }

    fun flush() {
        val batch: List<String>
        synchronized(pending) {
            if (pending.isEmpty()) return
            batch = pending.toList()
            pending.clear()
        }
        store.appendToOwnLog(batch)
    }

    /**
     * Collapses this device's own log to one line per key.
     *
     * Only ever rewrites this device's file, which nothing else writes, so it
     * cannot race another device. Merging is last-writer-wins per key, so
     * dropping superseded lines cannot change the merged result.
     */
    fun compactOwnLog(): Int {
        flush()
        val lines = store.readAllLogs()
        if (lines.isEmpty()) return 0

        val own = HashMap<String, Pair<String, String>>() // key -> (hlc, line)
        var kept = 0
        for (line in lines) {
            val event = runCatching { SyncMerger.json.decodeFromString<SyncEvent>(line) }.getOrNull()
                ?: continue
            if (!event.hlc.endsWith("-${store.deviceId}")) continue
            val existing = own[event.key]
            if (existing == null || event.hlc > existing.first) {
                own[event.key] = event.hlc to line
                kept++
            }
        }
        val collapsed = own.values.map { it.second }.sorted()
        store.rewriteOwnLog(collapsed)
        return collapsed.size
    }
}
