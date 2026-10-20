package io.github.adambench.habbits.domain

import kotlinx.serialization.Serializable

/**
 * Calculation method. Wrapped rather than using the library's enum directly so
 * stored settings stay readable and stable if the library's names change.
 */
@Serializable
enum class PrayerMethod(val displayName: String, val note: String) {
    NorthAmerica("North America (ISNA)", "15° / 15° — the common choice in Canada and the US"),
    MuslimWorldLeague("Muslim World League", "18° / 17°"),
    Egyptian("Egyptian General Authority", "19.5° / 17.5°"),
    UmmAlQura("Umm al-Qura", "18.5°, Isha 90 minutes after Maghrib"),
    Karachi("Karachi", "18° / 18°"),
    MoonsightingCommittee("Moonsighting Committee", "18° / 18°, with seasonal adjustment"),
}

/** Asr timing. Hanafi puts Asr noticeably later. */
@Serializable
enum class AsrMadhab(val displayName: String) {
    Standard("Standard (Shafi'i, Maliki, Hanbali)"),
    Hanafi("Hanafi"),
}

/**
 * What to do when the sun never dips far enough for a true twilight.
 *
 * This matters in Montreal: at 45.5°N the nights around the summer solstice are
 * short enough that the stricter angles do not resolve, and without a rule the
 * Fajr and Isha times drift or vanish.
 */
@Serializable
enum class HighLatitudeRule(val displayName: String) {
    MiddleOfTheNight("Middle of the night"),
    SeventhOfTheNight("Seventh of the night"),
    TwilightAngle("Twilight angle"),
}

@Serializable
data class PrayerSettings(
    val enabled: Boolean = true,
    val locationName: String = "Montreal",
    val latitude: Double = MONTREAL_LATITUDE,
    val longitude: Double = MONTREAL_LONGITUDE,
    val method: PrayerMethod = PrayerMethod.NorthAmerica,
    val madhab: AsrMadhab = AsrMadhab.Standard,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MiddleOfTheNight,
    /** Jump to the prayer window that is live right now when opening today. */
    val autoScroll: Boolean = true,
    val appearance: Appearance = Appearance(),
    val sync: SyncSettings = SyncSettings(),
    val reminders: ReminderSettings = ReminderSettings(),
) {
    companion object {
        const val MONTREAL_LATITUDE = 45.5019
        const val MONTREAL_LONGITUDE = -73.5674
    }
}
