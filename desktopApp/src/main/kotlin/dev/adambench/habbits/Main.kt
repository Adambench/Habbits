package dev.adambench.habbits

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.adambench.habbits.data.createHabbitsDatabase
import dev.adambench.habbits.data.defaultDataDirectory
import dev.adambench.habbits.ui.DayScreen
import dev.adambench.habbits.sync.VaultImporter
import dev.adambench.habbits.ui.theme.HabbitsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dataDir = defaultDataDirectory()
    val container = AppContainer(
        database = createHabbitsDatabase(dataDir),
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
    val model = container.dayScreenModel(scope)

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
                DayScreen(model)
            }
        }
    }
}
