package it.saranave.ciclo.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Colonna riordinabile: tieni premuto sulla "maniglia" e trascina.
 * itemContent riceve il modifier da mettere sulla parte da tenere premuta.
 * onReordered viene chiamato a fine trascinamento con il nuovo ordine delle chiavi.
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> String,
    spacing: Dp = 12.dp,
    onReordered: (List<String>) -> Unit,
    itemContent: @Composable (item: T, dragging: Boolean, handle: Modifier) -> Unit,
) {
    val incomingKeys = items.map(keyOf)
    val order = remember { mutableStateListOf<String>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val haptic = LocalHapticFeedback.current
    val byKey = items.associateBy(keyOf)

    val onReorderedState by rememberUpdatedState(onReordered)
    val latestKeys by rememberUpdatedState(incomingKeys)
    // Fuori dal trascinamento l'ordine segue sempre i dati in arrivo.
    val shown = if (dragging != null) order.toList() else incomingKeys

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (k in shown) {
            val item = byKey[k] ?: continue
            key(k) {
                val isDrag = dragging == k
                val handle = Modifier.pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            order.clear(); order.addAll(latestKeys)
                            dragging = k; offset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount.y
                            val i = order.indexOf(k)
                            if (offset > 0 && i < order.lastIndex) {
                                val h = (heights[order[i + 1]] ?: 0) + spacingPx
                                if (offset > h / 2) { order.removeAt(i); order.add(i + 1, k); offset -= h }
                            } else if (offset < 0 && i > 0) {
                                val h = (heights[order[i - 1]] ?: 0) + spacingPx
                                if (-offset > h / 2) { order.removeAt(i); order.add(i - 1, k); offset += h }
                            }
                        },
                        onDragEnd = { dragging = null; offset = 0f; onReorderedState(order.toList()) },
                        onDragCancel = { dragging = null; offset = 0f; onReorderedState(order.toList()) },
                    )
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[k] = it.height }
                        .zIndex(if (isDrag) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDrag) offset else 0f
                            if (isDrag) { scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 16f }
                        },
                ) { itemContent(item, isDrag, handle) }
            }
        }
    }
}
