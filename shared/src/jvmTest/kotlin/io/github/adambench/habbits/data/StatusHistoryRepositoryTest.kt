package io.github.adambench.habbits.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.StatusChange
import io.github.adambench.habbits.domain.StatusHistory
import io.github.adambench.habbits.sync.FileSyncStore
import io.github.adambench.habbits.sync.BufferedSyncJournal
import io.github.adambench.habbits.sync.HlcGenerator
import io.github.adambench.habbits.sync.SyncMerger
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Status history as the repository records it, and as it survives upgrade and sync. */
class StatusHistoryRepositoryTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-history-${System.nanoTime()}")
    private val opened = mutableListOf<HabbitsDatabase>()

    private var today = LocalDate(2026, 9, 28)
    private fun day(m: Int, d: Int) = LocalDate(2026, m, d)

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun open(sub: String = "db") =
        createHabbitsDatabase(File(dir, sub).apply { mkdirs() }).also { opened += it }

    private fun repositoryFor(db: HabbitsDatabase, journal: BufferedSyncJournal? = null, name: String = "test"): HabitRepository {
        var tick = 1_000L
        return HabitRepository(
            habitDao = db.habitDao(),
            entryDao = db.entryDao(),
            clock = HlcGenerator(name) { tick++ },
            now = { tick },
            journal = journal ?: io.github.adambench.habbits.sync.SyncJournal.None,
            today = { today },
        )
    }

    private suspend fun HabitRepository.history(id: String) =
        getHabits().first { it.id == id }.statusHistory

    @Test
    fun a_new_habit_starts_its_history_on_the_day_it_is_added() = runTest {
        val repo = repositoryFor(open())
        repo.saveHabit(Habit(id = "a", label = "A"))
        assertEquals(listOf(StatusChange(today, HabitStatus.Active)), repo.history("a"))
    }

    @Test
    fun sleeping_and_waking_are_dated_to_the_day_they_happen() = runTest {
        val repo = repositoryFor(open())
        today = day(4, 5)
        repo.saveHabit(Habit(id = "a", label = "A"))
        today = day(6, 1)
        repo.setStatus("a", HabitStatus.Sleeping)
        today = day(9, 1)
        repo.setStatus("a", HabitStatus.Active)

        assertEquals(
            listOf(
                StatusChange(day(4, 5), HabitStatus.Active),
                StatusChange(day(6, 1), HabitStatus.Sleeping),
                StatusChange(day(9, 1), HabitStatus.Active),
            ),
            repo.history("a"),
        )
    }

    @Test
    fun editing_a_habit_does_not_disturb_its_history() = runTest {
        val repo = repositoryFor(open())
        today = day(4, 5)
        repo.saveHabit(Habit(id = "a", label = "A"))
        today = day(6, 1)
        // An editor holding a stale copy with no history at all.
        repo.saveHabit(Habit(id = "a", label = "Renamed"))
        assertEquals(listOf(StatusChange(day(4, 5), HabitStatus.Active)), repo.history("a"))
    }

    @Test
    fun a_habit_from_before_history_is_backfilled_from_its_completions() = runTest {
        val db = open()
        val repo = repositoryFor(db)
        // Written the way version 1 wrote rows: no history column value.
        db.habitDao().upsert(
            Habit(id = "nap", label = "Nap", status = HabitStatus.Sleeping).toEntity(hlc = "x", createdAt = 0L),
        )
        today = day(4, 5); repo.toggle(today, "nap")
        today = day(5, 31); repo.toggle(today, "nap")
        today = day(9, 28)
        assertNull(db.habitDao().getById("nap")?.statusHistory)

        assertEquals(1, repo.backfillStatusHistory())
        assertEquals(
            listOf(StatusChange(day(4, 5), HabitStatus.Active), StatusChange(day(6, 1), HabitStatus.Sleeping)),
            repo.history("nap"),
        )
        assertEquals(0, repo.backfillStatusHistory(), "nothing left to do the second time")
    }

    @Test
    fun waking_a_never_backfilled_habit_keeps_its_inferred_past() = runTest {
        val db = open()
        val repo = repositoryFor(db)
        db.habitDao().upsert(
            Habit(id = "nap", label = "Nap", status = HabitStatus.Sleeping).toEntity(hlc = "x", createdAt = 0L),
        )
        today = day(4, 5); repo.toggle(today, "nap")
        today = day(9, 28)
        repo.setStatus("nap", HabitStatus.Active)
        assertEquals(
            listOf(
                StatusChange(day(4, 5), HabitStatus.Active),
                StatusChange(day(4, 6), HabitStatus.Sleeping),
                StatusChange(day(9, 28), HabitStatus.Active),
            ),
            repo.history("nap"),
        )
    }

    /**
     * The phone's database was created by version 1.2. Build one exactly as that
     * schema did, then open it with this code — what an Obtainium update does.
     */
    @Test
    fun a_version_1_database_migrates_and_keeps_everything() = runTest {
        val sub = File(dir, "v1").apply { mkdirs() }
        val schema = Json.parseToJsonElement(
            File("schemas/io.github.adambench.habbits.data.HabbitsDatabase/1.json").readText(),
        ).jsonObject["database"]!!.jsonObject
        val connection = BundledSQLiteDriver().open(File(sub, HabbitsDatabase.FILE_NAME).absolutePath)
        schema["entities"]!!.jsonArray.forEach { entity ->
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            connection.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity.jsonObject["indices"]?.jsonArray?.forEach {
                connection.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
        connection.execSQL(
            "INSERT INTO habits VALUES ('nap','Nap',NULL,2,0,NULL,NULL,25,0,127,NULL,NULL,1,0,0,'h1')",
        )
        connection.execSQL("INSERT INTO entries VALUES (${day(4, 5).toEpochDays()},'nap',NULL,0,'h2')")
        connection.execSQL("PRAGMA user_version = 1")
        connection.close()

        val db = createHabbitsDatabase(sub).also { opened += it }
        val repo = repositoryFor(db)
        assertEquals(1, db.entryDao().count(), "the completion survived")
        assertEquals(HabitStatus.Sleeping, repo.getHabits().single().status)
        assertNull(db.habitDao().getById("nap")?.statusHistory, "the new column starts empty")

        repo.backfillStatusHistory()
        assertEquals(
            listOf(StatusChange(day(4, 5), HabitStatus.Active), StatusChange(day(4, 6), HabitStatus.Sleeping)),
            repo.history("nap"),
        )
    }

    @Test
    fun history_travels_with_the_habit_through_sync() = runTest {
        val shared = File(dir, "shared").apply { mkdirs() }
        val phoneStore = FileSyncStore(shared, "phone")
        val phoneJournal = BufferedSyncJournal(phoneStore, flushThreshold = 1_000)
        val phone = repositoryFor(open("phone"), phoneJournal, "phone")
        today = day(4, 5)
        phone.saveHabit(Habit(id = "a", label = "A"))
        today = day(6, 1)
        phone.setStatus("a", HabitStatus.Sleeping)
        phoneJournal.flush()

        val laptopDb = open("laptop")
        SyncMerger(laptopDb, FileSyncStore(shared, "laptop")).merge()
        val row = assertNotNull(laptopDb.habitDao().getById("a"))
        assertEquals(
            listOf(StatusChange(day(4, 5), HabitStatus.Active), StatusChange(day(6, 1), HabitStatus.Sleeping)),
            StatusHistory.decode(row.statusHistory!!),
        )
    }

    @Test
    fun an_event_from_an_older_version_leaves_the_history_to_be_backfilled() = runTest {
        val shared = File(dir, "shared").apply { mkdirs() }
        // A 1.2 device's log line: no statusHistory field at all.
        File(shared, "old.jsonl").writeText(
            """{"type":"habit","habit":{"id":"a","label":"A","status":"sleeping","sortOrder":0},"hlc":"0000000000001:0000:old"}""" + "\n",
        )
        val db = open()
        SyncMerger(db, FileSyncStore(shared, "me")).merge()
        assertNull(db.habitDao().getById("a")?.statusHistory)
        repositoryFor(db).backfillStatusHistory()
        assertNotNull(db.habitDao().getById("a")?.statusHistory)
    }
}
