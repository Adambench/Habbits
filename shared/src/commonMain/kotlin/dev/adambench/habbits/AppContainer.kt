package dev.adambench.habbits

import dev.adambench.habbits.data.HabbitsDatabase
import dev.adambench.habbits.data.HabitRepository
import dev.adambench.habbits.data.SettingsRepository
import dev.adambench.habbits.sync.BufferedSyncJournal
import dev.adambench.habbits.sync.HlcGenerator
import dev.adambench.habbits.sync.SyncJournal
import dev.adambench.habbits.sync.SyncMerger
import dev.adambench.habbits.sync.SyncStore
import dev.adambench.habbits.sync.VaultImporter
import dev.adambench.habbits.ui.DayScreenModel
import dev.adambench.habbits.ui.manage.ManageScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Wiring for the whole app. Manual rather than a DI framework: there are about
 * six objects here, and annotation processing would cost build time for nothing.
 */
class AppContainer(
    val database: HabbitsDatabase,
    settingsStore: dev.adambench.habbits.domain.SettingsStore,
    val deviceId: String,
    /** Built lazily from settings, since the folder can change at runtime. */
    private val syncStoreFor: (folder: String) -> SyncStore? = { null },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val clock = HlcGenerator(deviceId, now)

    private var journal: BufferedSyncJournal? = null

    private val journalProxy = object : SyncJournal {
        override fun record(event: dev.adambench.habbits.sync.SyncEvent) {
            journal?.record(event)
        }
    }

    val repository: HabitRepository = HabitRepository(
        habitDao = database.habitDao(),
        entryDao = database.entryDao(),
        clock = clock,
        now = now,
        journal = journalProxy,
    )

    /** Local calendar date — the unit habits are logged against. */
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val settingsRepository: SettingsRepository = SettingsRepository(settingsStore)

    fun nowTime(): LocalTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

    fun dayScreenModel(scope: CoroutineScope): DayScreenModel =
        DayScreenModel(repository, settingsRepository, scope, ::today, ::nowTime)

    fun importer(): VaultImporter = VaultImporter(database, clock, now)

    private var store: SyncStore? = null

    /** Rebinds the sync folder when the setting changes. Safe to call often. */
    fun bindSync(folder: String) {
        if (folder.isBlank()) {
            journal?.flush()
            store = null
            journal = null
            return
        }
        if (store != null && boundFolder == folder) return
        journal?.flush()
        store = syncStoreFor(folder)
        journal = store?.let { BufferedSyncJournal(it) }
        boundFolder = folder
    }

    private var boundFolder: String? = null

    fun flushSync() = journal?.flush()

    /** Writes anything pending, then folds every device's log back in. */
    suspend fun syncNow(): dev.adambench.habbits.sync.MergeReport? {
        val current = store ?: return null
        journal?.flush()
        return SyncMerger(database, current).merge()
    }

    fun compactSync(): Int? = journal?.compactOwnLog()

    fun manageScreenModel(scope: CoroutineScope): ManageScreenModel =
        ManageScreenModel(repository, scope) { "habit_${now()}" }

    fun close() = database.close()
}
