package io.github.adambench.habbits.domain.stats

import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.isScheduledOn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One completion, as the calculator needs it. */
data class Completion(val date: LocalDate, val habitId: String, val value: Int?)

/**
 * Turns habits and completions into every figure the stats screen shows.
 *
 * Pure: no database, no clock, no I/O — the date is passed in. That is what
 * makes the streak and rate rules testable against known data.
 *
 * The central rule is that **a habit only counts on days it was actually due**.
 * Dividing completions by calendar days would punish a Friday-only habit for
 * the six days a week it was never meant to appear.
 */
object StatsCalculator {

    fun compute(
        habits: List<Habit>,
        completions: List<Completion>,
        range: StatsRange,
        today: LocalDate,
        earliest: LocalDate? = null,
    ): StatsSummary {
        // Only active habits can be "due". A sleeping or archived habit is not
        // expected today, so counting it as missed would be a lie.
        val counted = habits.filter { it.status == HabitStatus.Active }
        val countedIds = counted.mapTo(HashSet()) { it.id }

        val start = when {
            range.days != null -> today.minus(DatePeriod(days = range.days - 1))
            else -> earliest ?: completions.minOfOrNull { it.date } ?: today
        }
        val from = if (range.days == null) start else maxOf(start, earliest ?: start)

        val inRange = completions.filter { it.date in from..today }
        val doneByDate = inRange.filter { it.habitId in countedIds }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.mapTo(HashSet()) { it.habitId } }

        val dates = generateSequence(from) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= today }
            .toList()

        // --- per day ---
        val dueByDate = HashMap<LocalDate, List<Habit>>(dates.size)
        val days = dates.map { date ->
            val due = counted.filter { it.isScheduledOn(date) }
            dueByDate[date] = due
            val doneIds = doneByDate[date].orEmpty()
            DayStat(date, due.size, due.count { it.id in doneIds })
        }

        // --- per habit ---
        val completionsByHabit = inRange.groupBy { it.habitId }
        val habitStats = counted.map { habit ->
            val dueDates = dates.filter { habit.isScheduledOn(it) }
            val doneDates = completionsByHabit[habit.id].orEmpty().mapTo(HashSet()) { it.date }
            val hit = dueDates.filter { it in doneDates }
            val values = completionsByHabit[habit.id].orEmpty().mapNotNull { it.value }
            HabitStat(
                habit = habit,
                due = dueDates.size,
                done = hit.size,
                currentStreak = currentStreak(dueDates, doneDates, today),
                longestStreak = longestStreak(dueDates, doneDates),
                total = if (habit.isMeasured && values.isNotEmpty()) values.sum() else null,
                lastDone = doneDates.maxOrNull(),
            )
        }.sortedByDescending { it.rate }

        // --- per category ---
        val categories = Category.entries.mapNotNull { category ->
            val members = counted.filter { it.category == category }
            if (members.isEmpty()) return@mapNotNull null
            var due = 0
            var done = 0
            dates.forEach { date ->
                val doneIds = doneByDate[date].orEmpty()
                members.forEach { habit ->
                    if (habit.isScheduledOn(date)) {
                        due++
                        if (habit.id in doneIds) done++
                    }
                }
            }
            CategoryStat(category, due, done)
        }

        // --- per weekday ---
        val weekdays = (1..7).map { iso ->
            val matching = days.filter { it.date.dayOfWeek.isoDayNumber == iso }
            WeekdayStat(iso, matching.sumOf { it.due }, matching.sumOf { it.done })
        }

        // --- per week, Monday-anchored ---
        val weeks = days.groupBy { day ->
            day.date.minus(DatePeriod(days = day.date.dayOfWeek.isoDayNumber - 1))
        }.map { (start, group) ->
            WeekStat(start, group.sumOf { it.due }, group.sumOf { it.done })
        }.sortedBy { it.start }

        val excludedIds = habits.filter { it.status != HabitStatus.Active }.mapTo(HashSet()) { it.id }

        return StatsSummary(
            range = range,
            from = from,
            to = today,
            days = days,
            habits = habitStats,
            categories = categories,
            weekdays = weekdays,
            weeks = weeks,
            currentStreak = currentDayStreak(days, today),
            longestStreak = longestDayStreak(days),
            perfectDays = days.count { it.isPerfect },
            totalLogged = inRange.size,
            excludedHabits = excludedIds.size,
            excludedLogged = inRange.count { it.habitId in excludedIds },
            isLoading = false,
        )
    }

    /**
     * Consecutive due days completed, counting back from the most recent.
     *
     * Today is skipped rather than treated as a miss when it has not been done
     * yet — the day is not over, and a streak that collapses every morning is
     * worse than useless.
     */
    private fun currentStreak(
        dueDates: List<LocalDate>,
        doneDates: Set<LocalDate>,
        today: LocalDate,
    ): Int {
        var streak = 0
        for (date in dueDates.sortedDescending()) {
            when {
                date in doneDates -> streak++
                date == today -> continue
                else -> break
            }
        }
        return streak
    }

    private fun longestStreak(dueDates: List<LocalDate>, doneDates: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        for (date in dueDates.sorted()) {
            if (date in doneDates) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    /** Consecutive calendar days ending today (or yesterday) with any completion. */
    private fun currentDayStreak(days: List<DayStat>, today: LocalDate): Int {
        var streak = 0
        for (day in days.sortedByDescending { it.date }) {
            when {
                day.hasActivity -> streak++
                day.date == today -> continue
                else -> break
            }
        }
        return streak
    }

    private fun longestDayStreak(days: List<DayStat>): Int {
        var best = 0
        var run = 0
        for (day in days.sortedBy { it.date }) {
            if (day.hasActivity) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }
}
