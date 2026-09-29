package io.github.adambench.habbits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.adambench.habbits.domain.Category
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.LocalTime

/**
 * Days either side of the anchor the pager can reach, about 27 years each way.
 * A pager needs a finite page count; this one is never approached in practice.
 */
private const val PAGE_COUNT = 20_000
private const val ORIGIN_PAGE = PAGE_COUNT / 2

@Composable
fun DayScreen(
    model: DayScreenModel,
    modifier: Modifier = Modifier,
    /** Supplied where the platform can open a file picker; null hides the action. */
    onImport: (() -> Unit)? = null,
    onManage: (() -> Unit)? = null,
    onStats: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()

    // Which measured habit has its stepper open. One at a time, and it closes
    // when the day changes so a stale row cannot stay expanded.
    var expandedHabitId by remember { mutableStateOf<String?>(null) }
    var describedHabitId by remember { mutableStateOf<String?>(null) }
    val selected = state.selectedDate
    remember(selected) {
        expandedHabitId = null
        describedHabitId = null
    }

    // Pages are days counted from a fixed anchor, so a page number means the
    // same date for as long as the screen lives, midnight included.
    val origin = remember { selected }
    val dateOf = { page: Int -> origin.plus(DatePeriod(days = page - ORIGIN_PAGE)) }
    val pageOf = { date: LocalDate -> ORIGIN_PAGE + (date.toEpochDays() - origin.toEpochDays()).toInt() }
    val pagerState = rememberPagerState(initialPage = pageOf(selected)) { PAGE_COUNT }

    // The model owns the date and the pager follows it: the arrows, the week
    // strip and "Back to today" all move the model. A jump is instant rather
    // than animated, because an animation cancelled halfway by a second tap
    // would settle on a page in between and drag the model back with it.
    LaunchedEffect(selected) {
        val target = pageOf(selected)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    // A swipe is the one move that starts in the pager, and it only counts
    // once it has come to rest.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> model.selectDate(dateOf(page)) }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(model) {
        model.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
                withDismissAction = false,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            DayHeader(
                state = state,
                onToday = { model.goToToday() },
                onManage = onManage,
                onStats = onStats,
            )

            WeekStrip(
                days = state.week,
                onSelect = { model.selectDate(it) },
                onPrevious = { model.shiftDay(-1) },
                onNext = { model.shiftDay(1) },
            )

            Spacer(Modifier.height(4.dp))

            HorizontalPager(
                state = pagerState,
                // Draw the neighbours ahead of time, so a swipe reveals the
                // next day's habits rather than an empty page filling in.
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val date = dateOf(page)
                val pageFlow = remember(date) { model.stateFor(date) }
                val pageState by pageFlow.collectAsState(initial = null)
                val isCurrent = date == selected
                DayPage(
                    state = pageState ?: return@HorizontalPager,
                    expandedHabitId = expandedHabitId.takeIf { isCurrent },
                    describedHabitId = describedHabitId.takeIf { isCurrent },
                    onImport = onImport,
                    onDescribe = { id ->
                        describedHabitId = if (describedHabitId == id) null else id
                    },
                    onExpand = { id ->
                        expandedHabitId = if (expandedHabitId == id) null else id
                    },
                    onToggle = { id ->
                        expandedHabitId = null
                        model.toggle(id)
                    },
                    onStep = { id, amount -> model.step(id, amount) },
                )
            }
        }
    }
}

/** One day's list. Several exist at once while the pager is moving. */
@Composable
private fun DayPage(
    state: DayUiState,
    expandedHabitId: String?,
    describedHabitId: String?,
    onImport: (() -> Unit)?,
    onDescribe: (String) -> Unit,
    onExpand: (String) -> Unit,
    onToggle: (String) -> Unit,
    onStep: (String, Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Jump to the window that is live now. Keyed on the window, so it fires
    // once per window rather than fighting the user's own scrolling.
    LaunchedEffect(state.liveCategory, state.autoScroll) {
        val live = state.liveCategory ?: return@LaunchedEffect
        if (!state.autoScroll) return@LaunchedEffect
        val index = state.headerIndexOf(live) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
    }

    if (state.sections.isEmpty()) {
        EmptyDay(
            isLoading = state.isLoading,
            hasAnyHabits = state.hasAnyHabits,
            onImport = onImport,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp,
        ),
    ) {
        state.sections.forEach { section ->
            item(key = "header-${section.category.name}") {
                CategoryHeader(section)
            }
            items(
                count = section.rows.size,
                key = { i -> section.rows[i].habit.id },
            ) { i ->
                val row = section.rows[i]
                Box(Modifier.padding(start = 20.dp, bottom = 8.dp)) {
                    HabitCard(
                        row = row,
                        isExpanded = expandedHabitId == row.habit.id,
                        showDescription = describedHabitId == row.habit.id,
                        hapticsEnabled = state.hapticsEnabled,
                        onLongPress = { onDescribe(row.habit.id) },
                        onToggle = { onToggle(row.habit.id) },
                        onExpandToggle = { onExpand(row.habit.id) },
                        onStep = { onStep(row.habit.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    state: DayUiState,
    onToday: () -> Unit,
    onManage: (() -> Unit)?,
    onStats: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(completed = state.completed, total = state.total, size = 48.dp)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = state.selectedDate.headerLabel(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = if (state.isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (state.isToday) {
                Text(
                    text = "${state.completed} of ${state.total} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Back to today",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToday)
                        .padding(vertical = 2.dp),
                )
            }
        }

        if (onStats != null) {
            HeaderButton("Stats", onStats) { StatsIcon(size = 20.dp) }
            Spacer(Modifier.width(6.dp))
        }
        if (onManage != null) {
            HeaderButton("Habits", onManage) { HabitsIcon(size = 20.dp) }
        }
    }
}

/**
 * An icon with its name under it. The name is the point: a bare glyph left the
 * way to the stats a guess.
 */
@Composable
private fun HeaderButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            icon()
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavArrow(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekStrip(
    days: List<DayChip>,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavArrow("‹", onPrevious)
        days.forEach { chip ->
            DayChipView(chip, Modifier.weight(1f)) { onSelect(chip.date) }
        }
        NavArrow("›", onNext)
    }
}

@Composable
private fun DayChipView(chip: DayChip, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val selectedBg = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (chip.isSelected) selectedBg else MaterialTheme.colorScheme.surface)
            .then(
                if (chip.isToday && !chip.isSelected) {
                    Modifier.border(1.dp, selectedBg, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = chip.date.weekdayInitial(),
            style = MaterialTheme.typography.labelSmall,
            color = if (chip.isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = "${chip.date.day}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chip.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (chip.isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // Completion dot: filled once every habit due that day is done.
        val complete = chip.total > 0 && chip.completed == chip.total
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        chip.isSelected -> MaterialTheme.colorScheme.onPrimary.copy(
                            alpha = if (chip.ratio > 0f) 1f else 0.25f,
                        )
                        complete -> MaterialTheme.colorScheme.primary
                        chip.ratio > 0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        )
    }
}

@Composable
private fun CategoryHeader(section: CategorySection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The timeline rail dot, carried over from the Obsidian tracker. It
        // grows and fills for the window that is live right now.
        Box(
            Modifier
                .size(if (section.isLive) 11.dp else 9.dp)
                .clip(CircleShape)
                .background(
                    if (section.isLive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    },
                ),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = section.category.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        section.startsAt?.let { time ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = time.hhmm(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (section.isLive) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "${section.completed}/${section.rows.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LocalTime.hhmm(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * Index of a category's header in the flat lazy list: every earlier section
 * contributes its own header plus one item per habit.
 */
private fun DayUiState.headerIndexOf(category: Category): Int? {
    var index = 0
    for (section in sections) {
        if (section.category == category) return index
        index += 1 + section.rows.size
    }
    return null
}

@Composable
private fun EmptyDay(isLoading: Boolean, hasAnyHabits: Boolean, onImport: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = when {
                    isLoading -> "Loading…"
                    !hasAnyHabits -> "No habits yet."
                    else -> "Nothing scheduled for this day."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isLoading && !hasAnyHabits && onImport != null) {
                Text(
                    text = "Import a backup",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onImport)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
