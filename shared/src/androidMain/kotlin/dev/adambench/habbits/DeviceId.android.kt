package dev.adambench.habbits

import android.content.Context
import java.io.File
import kotlin.random.Random

/**
 * A random per-install id used to namespace this device's sync log.
 *
 * Deliberately not derived from any hardware or advertising identifier: it only
 * has to be unique among the user's own devices, and a random value cannot
 * follow them anywhere.
 */
fun loadOrCreateDeviceId(context: Context): String =
    deviceIdIn(File(context.filesDir, "device-id"))

internal fun deviceIdIn(file: File): String {
    file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val id = Random.nextLong().toULong().toString(16).padStart(16, '0').take(8)
    runCatching {
        file.parentFile?.mkdirs()
        file.writeText(id)
    }
    return id
}
