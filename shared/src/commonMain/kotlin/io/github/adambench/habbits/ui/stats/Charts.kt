package io.github.adambench.habbits.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.adambench.habbits.domain.stats.DayStat
import io.github.adambench.habbits.ui.theme.LocalDarkTheme
import io.github.adambench.habbits.ui.theme.heatColor
import io.github.adambench.habbits.ui.theme.heatLegend
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

private val CELL = 13.dp
private val GAP = 3.dp

/**
 * Completion heatmap: one column per week, one row per weekday.
 *
 * Magnitude, so the colour job is sequential — a single hue, more-is-darker.
 * Tapping a cell reports the day underneath rather than relying on colour alone.
 */
@Composable
fun CompletionHeatmap(
    days: List<DayStat>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val dark = LocalDarkTheme.current
    val byDate = days.associateBy { it.date }

    val first = days.first().date
    val start = first.minus(DatePeriod(days = first.dayOfWeek.isoDayNumber - 1))
    val last = days.last().date
    val weekCount = ((last.toEpochDays() - start.toEpochDays()) / 7).toInt() + 1

    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                listOf("M", "", "W", "", "F", "", "S").forEach { label ->
                    Box(Modifier.height(CELL).width(14.dp), contentAlignment = Alignment.CenterStart) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                repeat(weekCount) { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                        repeat(7) { dayOfWeek ->
                            val date = start.plusDays(week * 7 + dayOfWeek)
                            val stat = byDate[date]
                            HeatCell(
                                stat = stat,
                                isSelected = date == selected,
                                dark = dark,
                                onClick = { if (stat != null) onSelect(date) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            heatLegend(dark).forEach { color ->
                Box(
                    Modifier
                        .padding(end = GAP)
                        .size(CELL)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.width(3.dp))
            Text(
                text = "More",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LocalDate.plusDays(n: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + n)

@Composable
private fun HeatCell(stat: DayStat?, isSelected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val base = when {
        stat == null -> MaterialTheme.colorScheme.surface
        stat.due == 0 -> heatColor(0f, dark)
        else -> heatColor(stat.ratio, dark)
    }
    Box(
        Modifier
            .size(CELL)
            .clip(RoundedCornerShape(3.dp))
            .background(base)
            .then(
                if (isSelected) {
                    // A 2px surface ring, so a selected cell reads without
                    // changing the value colour underneath it.
                    Modifier.padding(2.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = stat != null, onClick = onClick),
    )
}

/**
 * A row of bars for a single measure.
 *
 * One series, so one hue and no legend: the section title names it, and bar
 * length already encodes the value — colouring by value would spend the
 * identity channel re-encoding what length shows.
 */
@Composable
fun RateBars(
    entries: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
) {
    if (entries.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        entries.forEachIndexed { index, (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(track),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((72 * value.coerceIn(0f, 1f)).dp.coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(accent),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (index % labelEvery == 0) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A labelled horizontal meter, for a rate against its own 100%. */
@Composable
fun RateRow(label: String, rate: Float, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = "${(rate * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(rate.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
