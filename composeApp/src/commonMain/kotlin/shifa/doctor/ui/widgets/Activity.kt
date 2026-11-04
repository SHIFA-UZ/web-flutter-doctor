package shifa.doctor.ui.widgets

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/** What a press or release anywhere means to the app; `App.kt` provides the session's activity check (LOCK-02). */
val LocalActivity = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Reports presses and releases (not moves, scrolls or hovers: flow 01/13 appendix) on the way in, never consuming
 * them. The app's root has it, and so does every separate window (dialogs, sheets, the lock), which the root can't see.
 */
fun Modifier.trackActivity(): Modifier =
    composed {
        val activity = LocalActivity.current
        pointerInput(activity) {
            awaitPointerEventScope {
                while (true) {
                    val type = awaitPointerEvent(PointerEventPass.Initial).type
                    if (type == PointerEventType.Press || type == PointerEventType.Release) activity()
                }
            }
        }
    }
