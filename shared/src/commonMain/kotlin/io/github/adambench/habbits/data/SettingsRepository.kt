package io.github.adambench.habbits.data

import io.github.adambench.habbits.domain.PrayerSettings
import io.github.adambench.habbits.domain.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Prayer settings, held per device rather than in the synced database.
 *
 * Location is a property of where a device is, not of the habit history, so it
 * deliberately does not travel with the sync log.
 */
class SettingsRepository(private val store: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<PrayerSettings> = _settings.asStateFlow()

    fun update(settings: PrayerSettings) {
        _settings.value = settings
        runCatching { store.write(json.encodeToString(settings)) }
    }

    private fun load(): PrayerSettings {
        val text = runCatching { store.read() }.getOrNull() ?: return PrayerSettings()
        // A corrupt or half-written file falls back to defaults rather than
        // preventing the app from starting.
        return runCatching { json.decodeFromString<PrayerSettings>(text) }
            .getOrElse { PrayerSettings() }
    }
}
