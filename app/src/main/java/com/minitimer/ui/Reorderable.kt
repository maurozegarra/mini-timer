package com.minitimer.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Reordenamiento por arrastre (long-press) para `LazyColumn`, sin librerías externas.
 *
 * Diseño: sólo son arrastrables los items marcados con [ReorderableContentType] vía el
 * parámetro `contentType` de `items`/`itemsIndexed`. Así los headers/footers conviven
 * en la misma lista pero se ignoran al arrastrar.
 *
 * Los índices que expone son índices ABSOLUTOS del LazyList.
 */
const val ReorderableContentType = "reorderable"

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(state = lazyListState, onMove = onMove, scope = scope)
    }
    // Canal de auto-scroll: al arrastrar cerca de los bordes se desplaza la lista.
    LaunchedEffect(state) {
        while (true) {
            val diff = state.scrollChannel.receive()
            lazyListState.scrollBy(diff)
        }
    }
    return state
}

class DragDropState internal constructor(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal val scrollChannel = Channel<Float>()

    private var draggingItemDraggedDelta by mutableStateOf(0f)
    private var draggingItemInitialOffset by mutableStateOf(0)

    internal val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    /** Sólo se consideran arrastrables los items marcados con [ReorderableContentType]. */
    private fun LazyListItemInfo.isReorderable(): Boolean = contentType == ReorderableContentType

    internal fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                item.isReorderable() && offset.y.toInt() in item.offset..(item.offset + item.size)
            }
            ?.also {
                draggingItemIndex = it.index
                draggingItemInitialOffset = it.offset
            }
    }

    internal fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
        draggingItemInitialOffset = 0
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = state.layoutInfo.visibleItemsInfo.find { item ->
            item.isReorderable() &&
                middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                draggingItem.index != item.index
        }

        if (targetItem != null) {
            val scrollToIndex = when {
                targetItem.index == state.firstVisibleItemIndex -> draggingItem.index
                draggingItem.index == state.firstVisibleItemIndex -> targetItem.index
                else -> null
            }
            if (scrollToIndex != null) {
                scope.launch {
                    state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
                    onMove(draggingItem.index, targetItem.index)
                }
            } else {
                onMove(draggingItem.index, targetItem.index)
            }
            draggingItemIndex = targetItem.index
        } else {
            val overscroll = when {
                draggingItemDraggedDelta > 0 ->
                    (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                draggingItemDraggedDelta < 0 ->
                    (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) scrollChannel.trySend(overscroll)
        }
    }
}

/** Se aplica al `LazyColumn`: detecta el long-press + arrastre sobre los items reordenables. */
fun Modifier.dragContainer(dragDropState: DragDropState): Modifier = pointerInput(dragDropState) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> dragDropState.onDragStart(offset) },
        onDrag = { change, offset ->
            change.consume()
            dragDropState.onDrag(offset)
        },
        onDragEnd = { dragDropState.onDragInterrupted() },
        onDragCancel = { dragDropState.onDragInterrupted() },
    )
}

/**
 * Envuelve el contenido de un item reordenable. [index] debe ser el índice ABSOLUTO del
 * LazyList. Mientras se arrastra, eleva el item y aplica el desplazamiento vertical.
 */
@Composable
fun LazyItemScope.DraggableItem(
    dragDropState: DragDropState,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val dragging = index == dragDropState.draggingItemIndex
    val draggingModifier = if (dragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = dragDropState.draggingItemOffset }
    } else {
        Modifier
    }
    Box(modifier = modifier.then(draggingModifier)) {
        content(dragging)
    }
}
