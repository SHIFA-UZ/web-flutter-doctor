package shifa.doctor.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Enter on its own, going down: with Shift (a new line) or any other modifier it is not a "send". */
internal val KeyEvent.isPlainEnter: Boolean
    get() =
        (key == Key.Enter || key == Key.NumPadEnter) &&
            !isShiftPressed && !isAltPressed && !isCtrlPressed && !isMetaPressed

/**
 * A message field at desktop widths (web, tablets with a keyboard): Enter does [action] (send) and Shift+Enter stays a
 * new line, as every chat on a computer works. Phones keep Enter as a new line: their keyboards have no Shift+Enter.
 */
@Composable
fun Modifier.sendOnEnter(action: () -> Unit): Modifier =
    if (!LocalWideLayout.current) {
        this
    } else {
        onPreviewKeyEvent { e ->
            if (!e.isPlainEnter) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) action()
            true
        }
    }
