package io.github.adambench.habbits.domain

import kotlinx.serialization.Serializable

/**
 * Where this device reads and writes its sync log.
 *
 * [folder] is a filesystem path on desktop and a Storage Access Framework tree
 * URI on Android. The app treats it as an opaque handle either way — anything
 * that replicates files will do.
 */
@Serializable
data class SyncSettings(
    val enabled: Boolean = false,
    val folder: String = "",
    val folderLabel: String = "",
    /** Merge on open and flush on leaving, rather than only on demand. */
    val automatic: Boolean = true,
) {
    val isConfigured: Boolean get() = enabled && folder.isNotBlank()
}
