package io.github.adambench.habbits.ui

import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.data.SettingsRepository
import io.github.adambench.habbits.data.createHabbitsDatabase
import io.github.adambench.habbits.domain.PrayerSettings
import io.github.adambench.habbits.domain.SettingsStore
import kotlinx.datetime.LocalTime
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.HabitType
import io.github.adambench.habbits.domain.Weekdays
import io.github.adambench.habbits.sync.HlcGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives the day view against a real database rather than a stub. */
class DayScreenModelTest {

    private val thursday = LocalDate(2026, 9, 10)
    private val friday = LocalDate(2026, 9, 11)
    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-ui-${System.nanoTime()}")
    private val db = createHabbitsDatabase(dir.apply { mkdirs() })

    private var clock = 1_700_000_000_000L
    private val repository = HabitRepository(
        habitDao = db.habitDao(),
        entryDao = db.entryDao(),
        clock = HlcGenerator("test") { clock },
        now = { clock },
    )

    @AfterTest
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun seed() {
        repository.saveHabit(
            Habit(
                id = "tahajjud", label = "Tahajjud", category = Category.BeforeFajr,
                type = HabitType.Prayer, unit = "Raka'ats", defaultValue = 2, step = 2,
            ),
        )
        repository.saveHabit(
            Habit(id = "quranMorning", label = "Read Quran", category = Category.Anytime, type = HabitType.Reading),
        )
        repository.saveHabit(
            Habit(
                id = "readKahf", label = "Read Kahf", category = Category.Anytime,
                type = HabitType.Reading, frequencyType = FrequencyType.Weekly,
                recurringDays = Weekdays.of(listOf(5)),
            ),
        )
        repository.saveHabit(
            Habit(id = "retired", label = "Retired", status = HabitStatus.Archived),
        )
    }

    /** Prayer times are off here: this suite is about habits, not the clock. */
    private val settingsRepository = SettingsRepository(
        object : SettingsStore {
            private var text: String? = null
            override fun read(): String? = text
            override fun write(text: String) {
                this.text = text
            }
        },
    ).also { it.update(PrayerSettings(enabled = false)) }

    private fun model(scope: kotlinx.coroutines.CoroutineScope, today: LocalDate = thursday) =
        DayScreenModel(
            repository = repository,
            settingsRepository = settingsRepository,
            scope = scope,
            today = { today },
            nowTime = { LocalTime(12, 0) },
        )

    @Test
    fun shows_only_habits_due_on_the_selected_day() = runTest {
        seed()
        val m = model(backgroundScope)
        val state = m.state.first { !it.isLoading }

        val ids = state.sections.flatMap { it.rows }.map { it.habit.id }
        assertTrue("tahajjud" in ids)
        assertTrue("quranMorning" in ids)
        assertFalse("readKahf" in ids, "Friday-only habit must not show on Thursday")
        assertFalse("retired" in ids, "archived habits never reach the day view")
    }

    @Test
    fun sections_follow_the_run_of_the_day() = runTest {
        seed()
        val state = model(backgroundScope).state.first { !it.isLoading }
        assertEquals(
            listOf(Category.Anytime, Category.BeforeFajr),
            state.sections.map { it.category },
            "categories render in day order, and empty ones are dropped",
        )
    }

    @Test
    fun toggling_a_measured_habit_stores_its_default_value() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }

        repository.toggle(thursday, "tahajjud")
        val after = m.state.first { s -> s.sections.any { it.rows.any { r -> r.isCompleted } } }
        val row = after.sections.flatMap { it.rows }.first { it.habit.id == "tahajjud" }
        assertTrue(row.isCompleted)
        assertEquals(2, row.value, "completing uses the habit's default value")
    }

    @Test
    fun stepping_down_to_zero_clears_the_completion() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }

        repository.toggle(thursday, "tahajjud")
        repository.incrementValue(thursday, "tahajjud", -2)

        val after = m.state.first { s ->
            s.sections.flatMap { it.rows }.none { it.habit.id == "tahajjud" && it.isCompleted }
        }
        val row = after.sections.flatMap { it.rows }.first { it.habit.id == "tahajjud" }
        assertFalse(row.isCompleted, "zero is not done, rather than done-with-zero")
        assertEquals(0, row.value)
    }

    @Test
    fun week_strip_runs_monday_to_sunday_around_the_selection() = runTest {
        seed()
        val state = model(backgroundScope).state.first { !it.isLoading }
        assertEquals(7, state.week.size)
        assertEquals(LocalDate(2026, 9, 7), state.week.first().date)
        assertEquals(LocalDate(2026, 9, 13), state.week.last().date)
        assertEquals(1, state.week.count { it.isSelected })
        assertEquals(1, state.week.count { it.isToday })
    }

    @Test
    fun week_strip_totals_count_only_habits_due_that_day() = runTest {
        seed()
        val state = model(backgroundScope).state.first { !it.isLoading }
        val thu = state.week.first { it.date == thursday }
        val fri = state.week.first { it.date == friday }
        assertEquals(2, thu.total, "Thursday: tahajjud + quranMorning")
        assertEquals(3, fri.total, "Friday additionally has Read Kahf")
    }

    @Test
    fun selecting_another_day_moves_the_view() = runTest {
        seed()
        val m = model(backgroundScope)
        m.state.first { !it.isLoading }
        m.selectDate(friday)

        val state = m.state.first { it.selectedDate == friday }
        assertFalse(state.isToday)
        val ids = state.sections.flatMap { it.rows }.map { it.habit.id }
        assertTrue("readKahf" in ids, "Friday-only habit appears on Friday")
    }
}
