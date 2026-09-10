package io.github.adambench.habbits.sync

import io.github.adambench.habbits.data.createHabbitsDatabase
import io.github.adambench.habbits.domain.HabitStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Imports a real `habits-export.json` into a real database and checks the
 * totals — the reconciliation gate from the plan.
 *
 * The export names a machine-specific vault, so the file is supplied through
 * `HABBITS_IMPORT_FILE`. Without it the test reports that it was not run rather
 * than passing silently on nothing.
 */
class VaultImportEndToEndTest {

    private fun exportFile(): File? =
        System.getenv("HABBITS_IMPORT_FILE")?.takeIf { it.isNotBlank() }
            ?.let(::File)?.takeIf { it.isFile }

    @Test
    fun imports_the_vault_export_and_reconciles() = runTest {
        val file = exportFile() ?: run {
            println("SKIPPED: set HABBITS_IMPORT_FILE to a habits-export.json to run this")
            return@runTest
        }

        val dir = File(System.getProperty("java.io.tmpdir"), "habbits-test-${System.nanoTime()}")
        dir.mkdirs()
        val db = createHabbitsDatabase(dir)
        try {
            val text = file.readText()
            val export = VaultImporter.parse(text)
            val importer = VaultImporter(db, HlcGenerator("test") { 1_700_000_000_000 }, { 1_700_000_000_000 })

            val report = importer.import(export)

            assertTrue(report.skipped.isEmpty(), "unexpected skips: ${report.skipped.take(5)}")
            assertTrue(
                report.matches(export.stats),
                "database ${report.habits}/${report.entries}/${report.days} " +
                    "does not match file ${export.stats}",
            )

            // The gate from the plan, verified against the real vault.
            assertEquals(3178, report.entries, "completions")
            assertEquals(239, report.days, "days with at least one completion")
            assertEquals(60, report.habits, "habits after import")
            assertEquals(24, report.archived, "orphans archived")

            val archived = db.habitDao().getAll().count { it.status == HabitStatus.Archived.ordinal }
            assertEquals(24, archived, "orphans persisted as archived")

            println(
                "IMPORTED ${report.habits} habits (${report.archived} archived), " +
                    "${report.entries} completions across ${report.days} days from ${file.name}",
            )

            // Re-importing must converge, not duplicate.
            val second = importer.import(export)
            assertEquals(report.entries, second.entries, "import is idempotent")
            assertEquals(report.habits, second.habits, "import is idempotent")
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}
