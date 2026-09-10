package io.github.adambench.habbits.ui.manage

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * Long-press-and-drag reordering for a [LazyListState].
 *
 * Deliberately small: it tracks the dragged row's key and its accumulated
 * offset, and reports a swap the moment the pointer's centre crosses another
 * draggable row. The list itself is the source of truth, so a swap that the
 * caller rejects simply does not happen.
 */
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val onSwap: (from: Any, to: Any) -> Unit,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    var offset by mutableStateOf(0f)
        private set

    private var startOffset = 0f

    internal fun start(key: Any) {
        draggedKey = key
        startOffset = itemInfo(key)?.offset?.toFloat() ?: 0f
        offset = 0f
    }

    internal fun stop() {
        draggedKey = null
        offset = 0f
    }

    internal fun drag(delta: Float, isDraggable: (Any) -> Boolean) {
        val key = draggedKey ?: return
        offset += delta

        val dragged = itemInfo(key) ?: return
        val centre = startOffset + offset + dragged.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.key != key &&
                isDraggable(candidate.key) &&
                centre.toInt() in candidate.offset..(candidate.offset + candidate.size)
        } ?: return

        onSwap(key, target.key)
        // The list re-emits in its new order, so the anchor has to move with it
        // or the row would appear to snap back under the finger.
        startOffset = target.offset.toFloat()
        offset = 0f
    }

    private fun itemInfo(key: Any): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    fun offsetFor(key: Any): Int = if (key == draggedKey) offset.roundToInt() else 0
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onSwap: (from: Any, to: Any) -> Unit,
): ReorderState = remember(listState) { ReorderState(listState, onSwap) }

/**
 * Attaches the drag gesture to one row. [isDraggable] keeps category headers
 * from being picked up or dropped onto.
 */
fun Modifier.reorderable(
    state: ReorderState,
    key: Any,
    isDraggable: (Any) -> Boolean,
): Modifier = this.pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = { state.start(key) },
        onDragEnd = { state.stop() },
        onDragCancel = { state.stop() },
        onDrag = { change, dragAmount ->
            change.consume()
            state.drag(dragAmount.y, isDraggable)
        },
    )
}
