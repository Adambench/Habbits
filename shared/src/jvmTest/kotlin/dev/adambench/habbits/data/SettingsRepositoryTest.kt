package dev.adambench.habbits.data

import dev.adambench.habbits.domain.AsrMadhab
import dev.adambench.habbits.domain.PrayerMethod
import dev.adambench.habbits.domain.PrayerSettings
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "habbits-settings-${System.nanoTime()}")
    private val file = File(dir, "settings.json")

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun repo() = SettingsRepository(FileSettingsStore(file))

    @Test
    fun defaults_to_montreal_and_the_north_american_method() {
        val settings = repo().settings.value
        assertEquals("Montreal", settings.locationName)
        assertEquals(PrayerMethod.NorthAmerica, settings.method)
        assertTrue(settings.enabled)
    }

    @Test
    fun changes_survive_a_restart() {
        repo().update(
            PrayerSettings(
                locationName = "Ottawa",
                latitude = 45.4215,
                longitude = -75.6972,
                madhab = AsrMadhab.Hanafi,
            ),
        )
        val reloaded = repo().settings.value
        assertEquals("Ottawa", reloaded.locationName)
        assertEquals(AsrMadhab.Hanafi, reloaded.madhab)
        assertEquals(45.4215, reloaded.latitude)
    }

    @Test
    fun a_corrupt_settings_file_falls_back_to_defaults() {
        dir.mkdirs()
        file.writeText("{ this is not json")
        // Starting the app matters more than honouring a broken file.
        assertEquals(PrayerSettings(), repo().settings.value)
    }

    @Test
    fun an_unknown_future_field_is_ignored_rather_than_fatal() {
        dir.mkdirs()
        file.writeText("""{"locationName":"Laval","somethingNew":42}""")
        assertEquals("Laval", repo().settings.value.locationName)
    }

    @Test
    fun writing_leaves_no_temporary_file_behind() {
        repo().update(PrayerSettings(locationName = "Longueuil"))
        val strays = dir.listFiles()?.map { it.name }?.filter { it.endsWith(".tmp") } ?: emptyList()
        assertTrue(strays.isEmpty(), "left behind: $strays")
    }
}
