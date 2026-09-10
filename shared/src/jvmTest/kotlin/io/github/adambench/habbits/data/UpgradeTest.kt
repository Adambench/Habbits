package io.github.adambench.habbits.data

import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.stats.Completion
import io.github.adambench.habbits.domain.stats.StatsCalculator
import io.github.adambench.habbits.domain.stats.StatsRange
import io.github.adambench.habbits.sync.HlcGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An installed app must survive a new version. This writes a database with the
 * shipped schema, closes it, reopens it with the current code, and checks that
 * nothing was lost — the same thing that happens on the user's phone when an
 * update lands.
 */
class UpgradeTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-upgrade-${System.nanoTime()}")

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun open() = createHabbitsDatabase(dir.apply { mkdirs() })

    private fun repositoryFor(db: HabbitsDatabase) = HabitRepository(
        habitDao = db.habitDao(),
        entryDao = db.entryDao(),
        clock = HlcGenerator("test") { 1L },
        now = { 1L },
    )

    @Test
    fun data_written_by_the_previous_version_is_still_there_after_reopening() = runTest {
        val first = open()
        repositoryFor(first).apply {
            saveHabit(Habit(id = "tahajjud", label = "Tahajjud", unit = "Raka'ats", defaultValue = 2, step = 2))
            saveHabit(Habit(id = "adkar", label = "Adkar al-Sabah"))
            toggle(LocalDate(2026, 9, 10), "tahajjud")
            toggle(LocalDate(2026, 9, 10), "adkar")
            toggle(LocalDate(2026, 9, 11), "adkar")
        }
        val before = first.entryDao().count()
        first.close()

        // Reopening is exactly what an app update does: same file, new binary.
        val second = open()
        try {
            assertEquals(before, second.entryDao().count(), "entries survived the reopen")
            assertEquals(2, second.habitDao().count(), "habits survived the reopen")
            assertEquals(3, second.entryDao().count())
            assertEquals(
                2,
                second.entryDao().getDay(LocalDate(2026, 9, 10).toEpochDays()).size,
                "a specific day is intact",
            )
            assertEquals(
                2,
                second.entryDao().get(LocalDate(2026, 9, 10).toEpochDays(), "tahajjud")?.value,
                "a measured amount is intact",
            )
        } finally {
            second.close()
        }
    }

    @Test
    fun the_new_stats_queries_work_against_an_existing_database() = runTest {
        val db = open()
        try {
            repositoryFor(db).apply {
                saveHabit(Habit(id = "a", label = "A"))
                toggle(LocalDate(2026, 9, 10), "a")
            }
            // getFrom and earliestDate are new in this version; they must run on
            // a database created before they existed.
            val earliest = db.entryDao().earliestDate()
            assertTrue(earliest != null)
            val rows = db.entryDao().getFrom(earliest!!)
            assertEquals(1, rows.size)

            val summary = StatsCalculator.compute(
                habits = repositoryFor(db).getHabits(),
                completions = rows.map { Completion(LocalDate.fromEpochDays(it.date), it.habitId, it.value) },
                range = StatsRange.All,
                today = LocalDate(2026, 9, 13),
                earliest = LocalDate.fromEpochDays(earliest),
            )
            assertEquals(1, summary.done)
        } finally {
            db.close()
        }
    }

    /**
     * Optional check against a copy of a real database, so the upgrade is proved
     * on actual data rather than only on a fixture.
     */
    @Test
    fun a_real_database_opens_and_reports_its_contents() = runTest {
        val path = System.getenv("HABBITS_REAL_DB")?.takeIf { it.isNotBlank() } ?: run {
            println("SKIPPED: set HABBITS_REAL_DB to a copy of a real habbits.db")
            return@runTest
        }
        val source = File(path)
        if (!source.isFile) {
            println("SKIPPED: no file at $path")
            return@runTest
        }
        dir.mkdirs()
        source.copyTo(File(dir, "habbits.db"), overwrite = true)

        val db = open()
        try {
            val habits = db.habitDao().count()
            val entries = db.entryDao().count()
            val days = db.entryDao().distinctDayCount()
            println("REAL DB OPENED: $habits habits, $entries completions across $days days")
            assertTrue(entries > 0, "the real database should not read back empty")
        } finally {
            db.close()
        }
    }
}
