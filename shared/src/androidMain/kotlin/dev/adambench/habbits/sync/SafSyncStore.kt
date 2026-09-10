package dev.adambench.habbits.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * A sync folder the user picked through the system folder picker.
 *
 * Uses the Storage Access Framework rather than a storage permission: the user
 * grants access to exactly one tree, which is enough to reach a Syncthing or
 * Nextcloud folder without the app being able to read anything else.
 */
class SafSyncStore(
    private val context: Context,
    private val treeUri: Uri,
    override val deviceId: String,
) : SyncStore {

    private val ownName = "device-$deviceId.jsonl"

    private fun tree(): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    private fun ownFile(create: Boolean): DocumentFile? {
        val root = tree() ?: return null
        return root.findFile(ownName)
            ?: if (create) root.createFile("application/json", ownName) else null
    }

    override fun appendToOwnLog(lines: List<String>) {
        if (lines.isEmpty()) return
        val file = ownFile(create = true) ?: return
        // SAF has no append handle everywhere, so "wa" is tried first and a
        // read-modify-write is the fallback.
        val payload = lines.joinToString(separator = "\n", postfix = "\n")
        val appended = runCatching {
            context.contentResolver.openOutputStream(file.uri, "wa")?.use {
                it.write(payload.encodeToByteArray())
                true
            } ?: false
        }.getOrDefault(false)
        if (appended) return

        val existing = runCatching { readFile(file) }.getOrDefault(emptyList())
        rewrite(file, existing + lines)
    }

    override fun readAllLogs(): List<String> {
        val root = tree() ?: return emptyList()
        return root.listFiles()
            .filter { it.isFile && (it.name?.endsWith(".jsonl") == true) }
            .sortedBy { it.name }
            .flatMap { runCatching { readFile(it) }.getOrElse { emptyList() } }
    }

    override fun rewriteOwnLog(lines: List<String>) {
        val file = ownFile(create = true) ?: return
        rewrite(file, lines)
    }

    override fun ownLogLineCount(): Int =
        ownFile(create = false)?.let { runCatching { readFile(it).size }.getOrDefault(0) } ?: 0

    private fun readFile(file: DocumentFile): List<String> =
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { reader ->
            reader.readLines().filter { it.isNotBlank() }
        } ?: emptyList()

    private fun rewrite(file: DocumentFile, lines: List<String>) {
        runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(lines.joinToString(separator = "\n", postfix = "\n").encodeToByteArray())
            }
        }
    }
}
