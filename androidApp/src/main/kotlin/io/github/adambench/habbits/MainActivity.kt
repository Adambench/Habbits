package io.github.adambench.habbits

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import io.github.adambench.habbits.ui.AppRoot
import io.github.adambench.habbits.ui.theme.HabbitsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as HabbitsApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val dayModel = container.dayScreenModel(lifecycleScope)
        val manageModel = container.manageScreenModel(lifecycleScope)

        setContent {
            // The system picker grants read access to exactly one file, so the
            // app still needs no storage permission.
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                lifecycleScope.launch {
                    val result = runCatching {
                        val text = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        } ?: error("could not read the selected file")
                        container.importer().import(text)
                    }
                    val message = result.fold(
                        onSuccess = { r ->
                            "Imported ${r.entries} completions across ${r.days} days" +
                                if (r.hasProblems) " (${r.skipped.size} skipped)" else ""
                        },
                        onFailure = { "Import failed: ${it.message}" },
                    )
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                // Persist the grant, or access is lost as soon as the app restarts.
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val repo = container.settingsRepository
                val current = repo.settings.value
                repo.update(
                    current.copy(
                        sync = current.sync.copy(
                            enabled = true,
                            folder = uri.toString(),
                            folderLabel = uri.lastPathSegment ?: uri.toString(),
                        ),
                    ),
                )
                container.bindSync(uri.toString())
            }

            val settings by container.settingsRepository.settings.collectAsState()
            HabbitsTheme(appearance = settings.appearance) {
                AppRoot(
                    dayModel = dayModel,
                    manageModel = manageModel,
                    settingsRepository = container.settingsRepository,
                    today = container.today(),
                    onImport = { picker.launch(arrayOf("application/json", "*/*")) },
                    onPickSyncFolder = { folderPicker.launch(null) },
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

    override fun onPause() {
        super.onPause()
        // Leaving the app is the natural moment to put pending events on disk.
        container.flushSync()
    }
}
