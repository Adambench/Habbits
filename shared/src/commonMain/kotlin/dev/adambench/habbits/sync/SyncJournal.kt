package dev.adambench.habbits.sync

/**
 * Where writes are recorded for sync.
 *
 * The repository holds one of these and reports every change it makes. The
 * default records nothing, so the database behaves exactly as before when sync
 * is switched off.
 */
interface SyncJournal {

    fun record(event: SyncEvent)

    object None : SyncJournal {
        override fun record(event: SyncEvent) = Unit
    }
}
