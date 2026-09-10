package dev.adambench.habbits

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.adambench.habbits.sync.VaultImporter
import dev.adambench.habbits.ui.DayScreen
import dev.adambench.habbits.ui.theme.HabbitsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as HabbitsApplication).container
        val model = container.dayScreenModel(lifecycleScope)

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

            HabbitsTheme {
                DayScreen(
                    model = model,
                    onImport = { picker.launch(arrayOf("application/json", "*/*")) },
                )
            }
        }
    }
}
