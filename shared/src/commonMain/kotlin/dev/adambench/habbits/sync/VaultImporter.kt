package dev.adambench.habbits.sync

import dev.adambench.habbits.data.EntryEntity
import dev.adambench.habbits.data.HabbitsDatabase
import dev.adambench.habbits.data.HabitEntity
import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.FrequencyType
import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.domain.HabitStatus
import dev.adambench.habbits.domain.HabitType
import dev.adambench.habbits.domain.Weekdays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * Loads a [HabbitsExport] into the database.
 *
 * Idempotent: every write is an upsert keyed the same way the schema is, so
 * running an import twice converges rather than duplicating.
 */
class VaultImporter(
    private val database: HabbitsDatabase,
    private val clock: HlcGenerator,
    private val now: () -> Long,
) {

    suspend fun import(json: String): ImportReport = import(parse(json))

    suspend fun import(export: HabbitsExport): ImportReport {
        require(export.format == HabbitsExport.FORMAT) {
            "not a Habbits export file: format was '${export.format}'"
        }
        require(export.version <= HabbitsExport.VERSION) {
            "export version ${export.version} is newer than this app understands"
        }

        val skipped = mutableListOf<String>()
        val habitDao = database.habitDao()
        val entryDao = database.entryDao()

        val habitRows = export.habits.map { it.toEntity() }
        // Habits first: entries carry a foreign key onto them.
        habitRows.chunked(CHUNK).forEach { habitDao.upsertAll(it) }

        val knownIds = habitRows.mapTo(mutableSetOf()) { it.id }
        val days = mutableSetOf<Long>()
        val entryRows = ArrayList<EntryEntity>(export.entries.size)

        for (entry in export.entries) {
            if (entry.habitId !in knownIds) {
                skipped += "entry on ${entry.date} names unknown habit '${entry.habitId}'"
                continue
            }
            val epochDay = runCatching { LocalDate.parse(entry.date).toEpochDays() }.getOrNull()
            if (epochDay == null) {
                skipped += "entry for '${entry.habitId}' has an unparseable date '${entry.date}'"
                continue
            }
            days += epochDay
            entryRows += EntryEntity(
                date = epochDay,
                habitId = entry.habitId,
                value = entry.value,
                loggedAt = now(),
                hlc = clock.next().encode(),
            )
        }

        entryRows.chunked(CHUNK).forEach { entryDao.upsertAll(it) }

        return ImportReport(
            habits = habitDao.count(),
            entries = entryDao.count(),
            days = entryDao.distinctDayCount(),
            archived = habitRows.count { it.status == HabitStatus.Archived.ordinal },
            skipped = skipped,
        )
    }

    private fun ExportHabit.toEntity(): HabitEntity = HabitEntity(
        id = id,
        label = label,
        description = description,
        category = Category.fromStorageId(category).ordinal,
        type = HabitType.fromStorageId(type).ordinal,
        unit = unit,
        defaultValue = defaultValue,
        step = step ?: Habit.DEFAULT_STEP,
        frequencyType = FrequencyType.fromStorageId(frequencyType).ordinal,
        // A null day list means the vault never narrowed the schedule, which the
        // legacy tracker treated as every day.
        recurringDays = recurringDays?.let { Weekdays.of(it) }?.mask ?: Weekdays.All.mask,
        intervalDays = intervalDays,
        intervalStart = intervalStart?.let { runCatching { LocalDate.parse(it).toEpochDays() }.getOrNull() },
        status = HabitStatus.fromStorageId(status).ordinal,
        sortOrder = sortOrder,
        createdAt = now(),
        hlc = clock.next().encode(),
    )

    companion object {
        private const val CHUNK = 500

        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(text: String): HabbitsExport = json.decodeFromString(text)
    }
}
