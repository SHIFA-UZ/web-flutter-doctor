package shifa.doctor.ui.widgets

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

/** Pinch zoom and pan of a [zoomable] view; 1× is the content's own fit. */
@Stable
class ZoomState(
    val max: Float = 4f,
) {
    var scale by mutableStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    internal fun change(
        zoom: Float,
        pan: Offset,
        size: IntSize,
    ) {
        scale = (scale * zoom).coerceIn(1f, max)
        // Panned only as far as the zoomed content reaches the edges.
        val limitX = size.width * (scale - 1) / 2
        val limitY = size.height * (scale - 1) / 2
        offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
    }

    /** Double tap: 2× from fit, back to fit otherwise (flow 06/08 §7). */
    fun toggle() {
        scale = if (scale > 1f) 1f else 2f
        offset = Offset.Zero
    }
}

/**
 * Two fingers zoom (1×–[ZoomState.max]) and pan; once zoomed, one finger pans too. At 1× a single finger is left to the
 * content, so a scrolling page list still scrolls. Double tap toggles 2×; it is read here, ignoring what the content
 * consumed, because a page list that handles its own taps would otherwise swallow it.
 */
fun Modifier.zoomable(state: ZoomState): Modifier =
    clipToBounds()
        .pointerInput(state) {
            var lastTap = 0L
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var moved = false
                var multi = false
                var up = down.uptimeMillis
                do {
                    val event = awaitPointerEvent()
                    val fingers = event.changes.count { it.pressed }
                    if (fingers >= 2) multi = true
                    event.changes.firstOrNull { it.id == down.id }?.let { c ->
                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        up = c.uptimeMillis
                    }
                    if (fingers >= 2 || state.scale > 1f) {
                        state.change(event.calculateZoom(), event.calculatePan(), size)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                // A tap: one finger, no travel, short. Two of them close together toggle the zoom.
                val tap = !multi && !moved && up - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                lastTap =
                    when {
                        !tap -> 0L
                        lastTap != 0L && down.uptimeMillis - lastTap < viewConfiguration.doubleTapTimeoutMillis -> {
                            state.toggle()
                            0L
                        }
                        else -> up
                    }
            }
        }.graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
            translationX = state.offset.x
            translationY = state.offset.y
        }
