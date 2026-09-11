package io.github.adambench.habbits

import io.github.adambench.habbits.data.HabbitsDatabase
import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.data.SettingsRepository
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.isVisibleOn
import io.github.adambench.habbits.sync.BufferedSyncJournal
import io.github.adambench.habbits.sync.HlcGenerator
import io.github.adambench.habbits.sync.SyncJournal
import io.github.adambench.habbits.sync.SyncMerger
import io.github.adambench.habbits.sync.SyncStore
import io.github.adambench.habbits.sync.VaultImporter
import io.github.adambench.habbits.ui.DayScreenModel
import io.github.adambench.habbits.ui.manage.ManageScreenModel
import io.github.adambench.habbits.ui.stats.StatsScreenModel
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
    settingsStore: io.github.adambench.habbits.domain.SettingsStore,
    val deviceId: String,
    /** Built lazily from settings, since the folder can change at runtime. */
    private val syncStoreFor: (folder: String) -> SyncStore? = { null },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val clock = HlcGenerator(deviceId, now)

    private var journal: BufferedSyncJournal? = null

    private val journalProxy = object : SyncJournal {
        override fun record(event: io.github.adambench.habbits.sync.SyncEvent) {
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

    /**
     * Habits due in [category] on [date] that have not been logged yet.
     *
     * Used by reminders so a notification can stay quiet when there is nothing
     * left to do, and name what is outstanding when there is.
     */
    suspend fun outstanding(date: LocalDate, category: Category): List<Habit> {
        val logged = database.entryDao().getDay(date.toEpochDays()).mapTo(HashSet()) { it.habitId }
        return repository.getHabits().filter {
            it.category == category && it.isVisibleOn(date) && it.id !in logged
        }
    }

    fun statsScreenModel(scope: CoroutineScope): StatsScreenModel =
        StatsScreenModel(repository, database.entryDao(), scope, ::today)

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
    suspend fun syncNow(): io.github.adambench.habbits.sync.MergeReport? {
        val current = store ?: return null
        journal?.flush()
        return SyncMerger(database, current).merge()
    }

    fun compactSync(): Int? = journal?.compactOwnLog()

    fun manageScreenModel(scope: CoroutineScope): ManageScreenModel =
        ManageScreenModel(repository, scope) { "habit_${now()}" }

    fun close() = database.close()
}
