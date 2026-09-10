package io.github.adambench.habbits.domain

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.batoulapps.adhan2.HighLatitudeRule as AdhanHighLatitudeRule

/** The computed times for one day, keyed by the category they open. */
data class DayPrayerTimes(
    val date: LocalDate,
    val times: Map<Category, LocalTime>,
) {
    operator fun get(category: Category): LocalTime? = times[category]

    /**
     * Which category is live at [now].
     *
     * Before Fajr covers midnight until Fajr, and Isha runs to the end of the
     * day — the same boundaries the Obsidian tracker used, except that these
     * ones are actually computed.
     */
    fun windowAt(now: LocalTime): Category {
        val ordered = listOf(
            Category.Fajr,
            Category.Shuruq,
            Category.Dhuhr,
            Category.Asr,
            Category.Maghrib,
            Category.Isha,
        )
        var current: Category = Category.BeforeFajr
        for (category in ordered) {
            val start = times[category] ?: continue
            if (now >= start) current = category else break
        }
        return current
    }

    companion object {
        val Empty = DayPrayerTimes(LocalDate(1970, 1, 1), emptyMap())
    }
}

/** Computes prayer times on the device. No network, no stored timetable. */
object PrayerClock {

    fun timesFor(
        date: LocalDate,
        settings: PrayerSettings,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): DayPrayerTimes {
        val parameters = settings.method.toAdhan()
            .copy(
                madhab = when (settings.madhab) {
                    AsrMadhab.Standard -> Madhab.SHAFI
                    AsrMadhab.Hanafi -> Madhab.HANAFI
                },
                highLatitudeRule = when (settings.highLatitudeRule) {
                    HighLatitudeRule.MiddleOfTheNight -> AdhanHighLatitudeRule.MIDDLE_OF_THE_NIGHT
                    HighLatitudeRule.SeventhOfTheNight -> AdhanHighLatitudeRule.SEVENTH_OF_THE_NIGHT
                    HighLatitudeRule.TwilightAngle -> AdhanHighLatitudeRule.TWILIGHT_ANGLE
                },
            )

        val computed = PrayerTimes(
            coordinates = Coordinates(settings.latitude, settings.longitude),
            dateComponents = DateComponents(date.year, date.month.ordinal + 1, date.day),
            calculationParameters = parameters,
        )

        fun at(instant: kotlin.time.Instant): LocalTime =
            instant.toLocalDateTime(timeZone).time

        return DayPrayerTimes(
            date = date,
            times = mapOf(
                Category.Fajr to at(computed.fajr),
                Category.Shuruq to at(computed.sunrise),
                Category.Dhuhr to at(computed.dhuhr),
                Category.Asr to at(computed.asr),
                Category.Maghrib to at(computed.maghrib),
                Category.Isha to at(computed.isha),
            ),
        )
    }

    private fun PrayerMethod.toAdhan() = when (this) {
        PrayerMethod.NorthAmerica -> CalculationMethod.NORTH_AMERICA
        PrayerMethod.MuslimWorldLeague -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        PrayerMethod.Egyptian -> CalculationMethod.EGYPTIAN
        PrayerMethod.UmmAlQura -> CalculationMethod.UMM_AL_QURA
        PrayerMethod.Karachi -> CalculationMethod.KARACHI
        PrayerMethod.MoonsightingCommittee -> CalculationMethod.MOON_SIGHTING_COMMITTEE
    }.parameters
}
