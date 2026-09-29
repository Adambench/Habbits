package io.github.adambench.habbits.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** From [date] onwards, the habit had [status]. */
data class StatusChange(val date: LocalDate, val status: HabitStatus)

/**
 * When a habit was active, sleeping or archived.
 *
 * Stats need this because the current status says nothing about the past: a
 * habit asleep today may have been done daily for two months, and one woken
 * yesterday was not being missed while it slept. Only the days a habit was
 * actually active are counted for or against it.
 *
 * Before the first change the habit did not exist yet, so a habit added in
 * April is not marked missed for every day since January.
 */
object StatusHistory {

    /**
     * Adds [status] from [date], keeping the list minimal: a second change on
     * the same day replaces the first, and a change back to the previous status
     * disappears rather than leaving a no-op entry behind.
     */
    fun withChange(history: List<StatusChange>, date: LocalDate, status: HabitStatus): List<StatusChange> {
        val kept = history.filter { it.date < date }
        return if (kept.lastOrNull()?.status == status) kept else kept + StatusChange(date, status)
    }

    /**
     * A best guess for a habit whose history was never recorded — everything
     * that existed before history was.
     *
     * - An active habit has existed since it was first done or created.
     * - A sleeping habit was active until the day after it was last done, and
     *   has slept since. Months of real completions then count, and the time
     *   it has been asleep does not.
     * - An archived habit keeps its status throughout. The vault's orphans land
     *   here, and their schedules were lost with the config, so there is no
     *   honest way to say which days they were due.
     */
    fun infer(
        status: HabitStatus,
        firstDone: LocalDate?,
        lastDone: LocalDate?,
        created: LocalDate?,
        today: LocalDate,
    ): List<StatusChange> {
        val start = listOfNotNull(firstDone, created).minOrNull()?.coerceAtMost(today) ?: today
        return when (status) {
            HabitStatus.Active -> listOf(StatusChange(start, HabitStatus.Active))
            HabitStatus.Sleeping -> {
                if (lastDone == null) {
                    listOf(StatusChange(start, HabitStatus.Sleeping))
                } else {
                    // Asleep from the day after the last completion, but never
                    // in the future, or today would still read as active.
                    val slept = lastDone.plus(DatePeriod(days = 1)).coerceAtMost(today)
                    withChange(listOf(StatusChange(start, HabitStatus.Active)), slept, HabitStatus.Sleeping)
                }
            }
            HabitStatus.Archived -> listOf(StatusChange(start, HabitStatus.Archived))
        }
    }

    /** `2026-04-05:active,2026-06-01:sleeping` — compact, sortable, readable in a database browser. */
    fun encode(history: List<StatusChange>): String =
        history.joinToString(",") { "${it.date}:${it.status.storageId}" }

    /** Malformed parts are dropped rather than failing the whole habit. */
    fun decode(text: String): List<StatusChange> =
        text.split(',')
            .mapNotNull { part ->
                val date = part.substringBefore(':', "")
                val status = part.substringAfter(':', "")
                val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
                if (status.isBlank()) return@mapNotNull null
                StatusChange(parsed, HabitStatus.fromStorageId(status))
            }
            .sortedBy { it.date }
}
