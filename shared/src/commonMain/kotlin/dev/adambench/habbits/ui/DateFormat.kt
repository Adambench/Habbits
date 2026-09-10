package dev.adambench.habbits.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * Formatted here rather than through a platform locale API, so Android and
 * desktop render the same string and the shared UI stays testable.
 */
fun LocalDate.weekdayShort(): String = WEEKDAYS[dayOfWeek.isoDayNumber - 1]

fun LocalDate.weekdayInitial(): String = weekdayShort().take(1)

fun LocalDate.monthShort(): String = MONTHS[month.ordinal]

/** e.g. `Tue, Sep 9`. */
fun LocalDate.headerLabel(): String = "${weekdayShort()}, ${monthShort()} $day"
