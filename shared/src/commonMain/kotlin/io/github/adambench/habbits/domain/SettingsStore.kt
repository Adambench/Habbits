package io.github.adambench.habbits.domain

/** Somewhere small and per-device to keep settings. Implemented per platform. */
interface SettingsStore {
    fun read(): String?
    fun write(text: String)
}
