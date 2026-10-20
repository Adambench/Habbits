package io.github.adambench.habbits

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.adambench.habbits.data.FileSettingsStore
import io.github.adambench.habbits.data.createHabbitsDatabase
import io.github.adambench.habbits.sync.FileSyncStore
import io.github.adambench.habbits.data.defaultDataDirectory
import io.github.adambench.habbits.ui.AppRoot
import io.github.adambench.habbits.sync.VaultImporter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.adambench.habbits.ui.theme.HabbitsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dataDir = defaultDataDirectory()
    val deviceId = loadOrCreateDeviceId(dataDir)
    val container = AppContainer(
        database = createHabbitsDatabase(dataDir),
        settingsStore = FileSettingsStore(File(dataDir, "settings.json")),
        deviceId = deviceId,
        syncStoreFor = { folder ->
            File(folder).takeIf { it.isDirectory }?.let { FileSyncStore(it, deviceId) }
        },
    )
    container.settingsRepository.settings.value.sync
        .takeIf { it.isConfigured }
        ?.let { container.bindSync(it.folder) }
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
                io.github.adambench.habbits.sync.HlcGenerator(loadOrCreateDeviceId(dataDir)) {
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
    val statsModel = container.statsScreenModel(scope)

    application {
        Window(
            onCloseRequest = {
                container.flushSync()
                scope.cancel()
                container.close()
                exitApplication()
            },
            title = "Habbits",
            state = rememberWindowState(size = DpSize(460.dp, 900.dp)),
        ) {
            val settings by container.settingsRepository.settings.collectAsState()
            HabbitsTheme(appearance = settings.appearance) {
                AppRoot(
                    dayModel = dayModel,
                    manageModel = manageModel,
                    statsModel = statsModel,
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
                    onPickSyncFolder = {
                        // A directory chooser, which AWT only offers through this flag.
                        System.setProperty("apple.awt.fileDialogForDirectories", "true")
                        val dialog = FileDialog(null as Frame?, "Choose a sync folder", FileDialog.LOAD)
                        dialog.isVisible = true
                        val chosen = dialog.directory?.let { dir ->
                            dialog.file?.let { File(dir, it) } ?: File(dir)
                        }
                        System.setProperty("apple.awt.fileDialogForDirectories", "false")
                        val folder = chosen?.let { if (it.isDirectory) it else it.parentFile }
                        if (folder != null && folder.isDirectory) {
                            val repo = container.settingsRepository
                            val current = repo.settings.value
                            repo.update(
                                current.copy(
                                    sync = current.sync.copy(
                                        enabled = true,
                                        folder = folder.absolutePath,
                                        folderLabel = folder.absolutePath,
                                    ),
                                ),
                            )
                            container.bindSync(folder.absolutePath)
                        }
                    },
                    onSyncNow = {
                        val report = withContext(Dispatchers.IO) { container.syncNow() }
                        if (report == null) {
                            "No sync folder configured"
                        } else {
                            "Merged ${report.eventsApplied} changes — " +
                                "${report.habits} habits, ${report.entries} completions" +
                                if (report.malformed > 0) " (${report.malformed} bad lines)" else ""
                        }
                    },
                )
            }
        }
    }
}
