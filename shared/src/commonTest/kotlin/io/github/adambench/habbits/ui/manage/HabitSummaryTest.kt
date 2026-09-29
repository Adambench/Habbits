package io.github.adambench.habbits.ui.manage

import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.Weekdays
import io.github.adambench.habbits.domain.isScheduledOn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitSummaryTest {
    private val today = LocalDate(2026, 9, 29)
    private val habit = Habit(id = "h", label = "Read")

    @Test
    fun weeklyDaysAreNamedAndCommonSetsGetTheirOwnWord() {
        fun weekly(vararg days: Int) =
            habit.copy(frequencyType = FrequencyType.Weekly, recurringDays = Weekdays.of(days.asList()))

        assertEquals("Mon, Wed & Fri", weekly(1, 3, 5).scheduleText())
        assertEquals("Sun", weekly(7).scheduleText())
        assertEquals("Weekdays", weekly(1, 2, 3, 4, 5).scheduleText())
        assertEquals("Weekends", weekly(6, 7).scheduleText())
        assertEquals("Every day", weekly().scheduleText())
    }

    @Test
    fun intervalOfZeroReadsAsEveryDayLikeTheScheduler() {
        val interval = habit.copy(frequencyType = FrequencyType.Interval)
        assertEquals("Every day", interval.copy(intervalDays = 0).scheduleText())
        assertEquals("Every other day", interval.copy(intervalDays = 2).scheduleText())
        assertEquals("Every 5 days", interval.copy(intervalDays = 5).scheduleText())
    }

    @Test
    fun nextDueAgreesWithTheScheduler() {
        val start = LocalDate(2026, 9, 1)
        for (every in 1..9) {
            val h = habit.copy(frequencyType = FrequencyType.Interval, intervalDays = every, intervalStart = start)
            val next = h.nextDue(today)!!
            assertTrue(next >= today)
            assertTrue(h.isScheduledOn(next), "every $every: $next should be due")
            // Nothing between today and the answer is due, or it was not the next.
            var day = today
            while (day < next) {
                assertTrue(!h.isScheduledOn(day), "every $every: $day came first")
                day = day.plus(DatePeriod(days = 1))
            }
        }
    }

    @Test
    fun nextDueIsTheStartWhenThatIsStillAhead() {
        val ahead = today.plus(DatePeriod(days = 4))
        val h = habit.copy(frequencyType = FrequencyType.Interval, intervalDays = 3, intervalStart = ahead)
        assertEquals(ahead, h.nextDue(today))
        assertNull(habit.nextDue(today))
    }

    @Test
    fun trackingSaysWhatIsCounted() {
        assertEquals("Tick off", habit.trackingText())
        assertEquals("Counts pages", habit.copy(unit = " pages ").trackingText())
    }
}
