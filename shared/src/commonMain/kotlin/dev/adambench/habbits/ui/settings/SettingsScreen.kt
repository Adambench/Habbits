package dev.adambench.habbits.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.adambench.habbits.data.SettingsRepository
import kotlinx.coroutines.launch
import dev.adambench.habbits.domain.AsrMadhab
import dev.adambench.habbits.domain.ThemeMode
import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.HighLatitudeRule
import dev.adambench.habbits.domain.PrayerClock
import dev.adambench.habbits.domain.PrayerMethod
import kotlinx.datetime.LocalDate

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    today: LocalDate,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onPickSyncFolder: (() -> Unit)? = null,
    onSyncNow: (suspend () -> String)? = null,
) {
    val settings by repository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var syncStatus by remember { mutableStateOf<String?>(null) }
    // Recomputed as the settings change, so the effect of a choice is visible
    // before leaving the screen.
    val preview = if (settings.enabled) PrayerClock.timesFor(today, settings) else null

    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDone)
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Done",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            SwitchRow("Show prayer times", settings.enabled) {
                repository.update(settings.copy(enabled = it))
            }
            SwitchRow("Jump to the current window", settings.autoScroll) {
                repository.update(settings.copy(autoScroll = it))
            }

            if (preview != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Today in ${settings.locationName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        Category.Fajr, Category.Shuruq, Category.Dhuhr,
                        Category.Asr, Category.Maghrib, Category.Isha,
                    ).forEach { category ->
                        val time = preview[category] ?: return@forEach
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${time.hour.toString().padStart(2, '0')}:" +
                                    time.minute.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Label("Location")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = settings.locationName,
                    onValueChange = { repository.update(settings.copy(locationName = it)) },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                DecimalField("Latitude", settings.latitude, Modifier.weight(1f)) {
                    repository.update(settings.copy(latitude = it))
                }
                DecimalField("Longitude", settings.longitude, Modifier.weight(1f)) {
                    repository.update(settings.copy(longitude = it))
                }
            }

            Label("Calculation method")
            Text(
                text = "Mosques differ. Check these against your own mosque's timetable " +
                    "and change the method if they disagree.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Chips(
                options = PrayerMethod.entries,
                selected = settings.method,
                label = { it.displayName },
            ) { repository.update(settings.copy(method = it)) }
            Text(
                text = settings.method.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Label("Asr")
            Chips(
                options = AsrMadhab.entries,
                selected = settings.madhab,
                label = { it.displayName },
            ) { repository.update(settings.copy(madhab = it)) }

            Label("Short summer nights")
            Chips(
                options = HighLatitudeRule.entries,
                selected = settings.highLatitudeRule,
                label = { it.displayName },
            ) { repository.update(settings.copy(highLatitudeRule = it)) }
            Text(
                text = "At Montreal's latitude the sun barely dips in June, so a rule is " +
                    "needed for Fajr and Isha to resolve at all.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Label("Sync")
            Text(
                text = "Point this at any folder that something replicates — Syncthing, " +
                    "Nextcloud, a git checkout, a USB stick. Each device only ever writes " +
                    "its own file, so there is nothing to conflict.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("Sync between devices", settings.sync.enabled) {
                repository.update(settings.copy(sync = settings.sync.copy(enabled = it)))
            }
            if (settings.sync.enabled) {
                Text(
                    text = settings.sync.folderLabel.ifBlank {
                        settings.sync.folder.ifBlank { "No folder chosen yet" }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onPickSyncFolder != null) {
                        SmallButton("Choose folder") { onPickSyncFolder() }
                    }
                    if (onSyncNow != null && settings.sync.isConfigured) {
                        SmallButton("Sync now") {
                            scope.launch {
                                syncStatus = "Syncing…"
                                syncStatus = runCatching { onSyncNow() }
                                    .getOrElse { "Sync failed: ${it.message}" }
                            }
                        }
                    }
                }
                syncStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Label("Appearance")
            Chips(
                options = ThemeMode.entries,
                selected = settings.appearance.themeMode,
                label = { it.displayName },
            ) { repository.update(settings.copy(appearance = settings.appearance.copy(themeMode = it))) }
            Text(
                text = "Black switches the background to true black, which costs no power " +
                    "on an OLED screen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow("Use system colours", settings.appearance.dynamicColor) {
                repository.update(settings.copy(appearance = settings.appearance.copy(dynamicColor = it)))
            }
            SwitchRow("Vibrate on tap", settings.appearance.haptics) {
                repository.update(settings.copy(appearance = settings.appearance.copy(haptics = it)))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toDoubleOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun <T> Chips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val accent = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) accent else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(option) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
