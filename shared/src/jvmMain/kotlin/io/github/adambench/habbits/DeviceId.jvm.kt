package io.github.adambench.habbits

import java.io.File
import kotlin.random.Random

/** See the Android counterpart: random, not hardware-derived. */
fun loadOrCreateDeviceId(directory: File): String {
    val file = File(directory, "device-id")
    file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val id = Random.nextLong().toULong().toString(16).padStart(16, '0').take(8)
    runCatching {
        directory.mkdirs()
        file.writeText(id)
    }
    return id
}
