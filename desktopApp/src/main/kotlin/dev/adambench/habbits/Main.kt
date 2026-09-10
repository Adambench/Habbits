package dev.adambench.habbits

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.adambench.habbits.data.FileSettingsStore
import dev.adambench.habbits.data.createHabbitsDatabase
import dev.adambench.habbits.data.defaultDataDirectory
import dev.adambench.habbits.ui.AppRoot
import dev.adambench.habbits.sync.VaultImporter
import dev.adambench.habbits.ui.theme.HabbitsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dataDir = defaultDataDirectory()
    val container = AppContainer(
        database = createHabbitsDatabase(dataDir),
        settingsStore = FileSettingsStore(File(dataDir, "settings.json")),
        deviceId = loadOrCreateDeviceId(dataDir),
    )
    // `--import <file>` runs the migration and exits, so the desktop app can
    // load a vault export without a running UI.
    val importIndex = args.indexOf("--import")
    if (importIndex >= 0) {
        val path = args.getOrNull(importIndex + 1)
        if (path == null) {
            System.err.println("error: --import needs a path to a habits-export.json")
            exitProcess(1)
        }
        val file = File(path)
        if (!file.isFile) {
            System.err.println("error: no such file: $path")
            exitProcess(1)
        }
        val report = runBlocking {
            VaultImporter(
                container.database,
                dev.adambench.habbits.sync.HlcGenerator(loadOrCreateDeviceId(dataDir)) {
                    System.currentTimeMillis()
                },
                { System.currentTimeMillis() },
            ).import(file.readText())
        }
        println(
            "Imported ${report.habits} habits (${report.archived} archived), " +
                "${report.entries} completions across ${report.days} days into $dataDir",
        )
        report.skipped.take(10).forEach { println("  skipped: $it") }
        container.close()
        exitProcess(if (report.hasProblems) 2 else 0)
    }

    val scope = CoroutineScope(SupervisorJob())
    val dayModel = container.dayScreenModel(scope)
    val manageModel = container.manageScreenModel(scope)

    application {
        Window(
            onCloseRequest = {
                scope.cancel()
                container.close()
                exitApplication()
            },
            title = "Habbits",
            state = rememberWindowState(size = DpSize(460.dp, 900.dp)),
        ) {
            HabbitsTheme {
                AppRoot(
                    dayModel = dayModel,
                    manageModel = manageModel,
                    settingsRepository = container.settingsRepository,
                    today = container.today(),
                    onImport = {
                        val chosen = FileDialog(null as Frame?, "Choose a backup", FileDialog.LOAD)
                            .apply {
                                setFilenameFilter { _, name -> name.endsWith(".json") }
                                isVisible = true
                            }
                            .let { dialog ->
                                dialog.file?.let { File(dialog.directory ?: ".", it) }
                            }
                        if (chosen != null && chosen.isFile) {
                            scope.launch {
                                runCatching { container.importer().import(chosen.readText()) }
                                    .onFailure { System.err.println("Import failed: ${'$'}{it.message}") }
                            }
                        }
                    },
                )
            }
        }
    }
}
