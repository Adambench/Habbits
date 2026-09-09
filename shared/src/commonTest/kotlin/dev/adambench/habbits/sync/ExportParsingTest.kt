package dev.adambench.habbits.sync

import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.FrequencyType
import dev.adambench.habbits.domain.HabitStatus
import dev.adambench.habbits.domain.HabitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportParsingTest {

    private val sample = """
        {
          "format": "habbits-export",
          "version": 1,
          "source": { "kind": "obsidian-vault", "vault": "/somewhere", "unknownKey": 1 },
          "stats": { "habits": 2, "entries": 3, "days": 2 },
          "habits": [
            { "id": "tahajjud", "label": "Tahajjud", "category": "Before Fajr",
              "type": "prayer", "unit": "Raka'ats", "defaultValue": 2, "step": 2,
              "frequencyType": "daily", "status": "active", "sortOrder": 0 },
            { "id": "duaYunus_Asr", "label": "Dua Yunus Asr", "status": "archived",
              "sortOrder": 1 }
          ],
          "entries": [
            { "date": "2026-08-15", "habitId": "tahajjud", "value": 2 },
            { "date": "2026-08-15", "habitId": "duaYunus_Asr", "value": null },
            { "date": "2026-08-16", "habitId": "tahajjud" }
          ]
        }
    """.trimIndent()

    @Test
    fun parses_the_export_and_ignores_unknown_keys() {
        val export = VaultImporter.parse(sample)
        assertEquals(HabbitsExport.FORMAT, export.format)
        assertEquals(2, export.habits.size)
        assertEquals(3, export.entries.size)
        assertEquals(2, export.stats?.days)
    }

    @Test
    fun a_missing_or_null_value_means_a_plain_check() {
        val entries = VaultImporter.parse(sample).entries
        assertEquals(2, entries[0].value)
        assertNull(entries[1].value, "explicit null is a plain check")
        assertNull(entries[2].value, "an absent value is also a plain check")
    }

    @Test
    fun storage_ids_map_onto_the_domain_enums() {
        val habit = VaultImporter.parse(sample).habits.first()
        assertEquals(Category.BeforeFajr, Category.fromStorageId(habit.category))
        assertEquals(HabitType.Prayer, HabitType.fromStorageId(habit.type))
        assertEquals(HabitStatus.Active, HabitStatus.fromStorageId(habit.status))
        assertEquals(FrequencyType.Daily, FrequencyType.fromStorageId(habit.frequencyType))
    }

    @Test
    fun orphans_arrive_archived() {
        val orphan = VaultImporter.parse(sample).habits[1]
        assertEquals(HabitStatus.Archived, HabitStatus.fromStorageId(orphan.status))
    }

    @Test
    fun defaults_fill_in_for_a_sparsely_described_habit() {
        val orphan = VaultImporter.parse(sample).habits[1]
        assertNull(orphan.unit)
        assertNull(orphan.step, "step is absent in the file and defaulted at mapping time")
        assertNull(orphan.recurringDays, "a null day list means every day")
        assertTrue(orphan.label.isNotBlank())
    }
}
