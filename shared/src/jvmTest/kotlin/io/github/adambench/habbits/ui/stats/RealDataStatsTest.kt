package io.github.adambench.habbits.ui.stats

import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.data.createHabbitsDatabase
import io.github.adambench.habbits.domain.stats.Completion
import io.github.adambench.habbits.domain.stats.StatsCalculator
import io.github.adambench.habbits.domain.stats.StatsRange
import io.github.adambench.habbits.sync.HlcGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the stats pipeline over a copy of a real database.
 *
 * A fixture proves the rules; this proves they hold at real scale — hundreds of
 * days, dozens of habits, and the archived orphans an import leaves behind.
 */
class RealDataStatsTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-realstats-${System.nanoTime()}")

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun stats_compute_over_a_real_database() = runTest {
        val path = System.getenv("HABBITS_REAL_DB")?.takeIf { it.isNotBlank() }
        val source = path?.let(::File)
        if (source == null || !source.isFile) {
            println("SKIPPED: set HABBITS_REAL_DB to a copy of a real habbits.db")
            return@runTest
        }
        dir.mkdirs()
        source.copyTo(File(dir, "habbits.db"), overwrite = true)

        val db = createHabbitsDatabase(dir)
        try {
            val repository = HabitRepository(
                habitDao = db.habitDao(),
                entryDao = db.entryDao(),
                clock = HlcGenerator("test") { 1L },
                now = { 1L },
            )
            val earliestDay = db.entryDao().earliestDate()!!
            val earliest = LocalDate.fromEpochDays(earliestDay)
            val rows = db.entryDao().getFrom(earliestDay)
            val latest = LocalDate.fromEpochDays(rows.maxOf { it.date })

            val summary = StatsCalculator.compute(
                habits = repository.getHabits(),
                completions = rows.map {
                    Completion(LocalDate.fromEpochDays(it.date), it.habitId, it.value)
                },
                range = StatsRange.All,
                today = latest,
                earliest = earliest,
            )

            println(
                buildString {
                    appendLine("REAL STATS ($earliest → $latest)")
                    appendLine("  completion rate : ${(summary.rate * 100).toInt()}%  (${summary.done}/${summary.due} due)")
                    appendLine("  days covered    : ${summary.days.size}, active on ${summary.activeDays}")
                    appendLine("  perfect days    : ${summary.perfectDays}")
                    appendLine("  longest streak  : ${summary.longestStreak} days")
                    appendLine("  logged total    : ${summary.totalLogged}")
                    appendLine("  excluded        : ${summary.excludedHabits} habits, ${summary.excludedLogged} completions")
                    summary.bestHabit?.let {
                        appendLine("  strongest       : ${it.habit.label} ${(it.rate * 100).toInt()}% (best run ${it.longestStreak})")
                    }
                    summary.weakestHabit?.let {
                        appendLine("  weakest         : ${it.habit.label} ${(it.rate * 100).toInt()}%")
                    }
                    val topCategory = summary.categories.filter { it.due > 20 }.maxByOrNull { it.rate }
                    topCategory?.let {
                        appendLine("  best window     : ${it.category.displayName} ${(it.rate * 100).toInt()}%")
                    }
                    val bestDay = summary.weekdays.filter { it.due > 0 }.maxByOrNull { it.rate }
                    bestDay?.let {
                        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        appendLine("  best weekday    : ${names[it.isoDayNumber - 1]} ${(it.rate * 100).toInt()}%")
                    }
                },
            )

            assertTrue(summary.days.isNotEmpty(), "the heatmap needs days to draw")
            assertTrue(summary.habits.isNotEmpty(), "per-habit stats should not be empty")
            assertTrue(summary.rate in 0f..1f, "a rate outside 0..1 means the due count is wrong")
            summary.habits.forEach {
                assertTrue(it.done <= it.due, "${it.habit.label} was done more often than it was due")
                assertTrue(it.currentStreak <= it.longestStreak, "current streak cannot beat the best")
            }
        } finally {
            db.close()
        }
    }
}
