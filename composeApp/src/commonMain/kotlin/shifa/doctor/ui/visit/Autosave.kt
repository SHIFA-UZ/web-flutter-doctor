package shifa.doctor.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * A draft saved while the doctor types (the visit notes, the dental record): [save] runs [debounce] after the last
 * [changed], and on [flush]. Saves run one at a time, so a newer one never overtakes one on its way to the server;
 * [save] compares [changes] before and after to know whether a newer change is still unsaved.
 */
class Autosave(
    private val scope: CoroutineScope,
    private val debounce: Duration,
    private val save: suspend () -> Unit,
) {
    private var pending: Job? = null
    private val lock = Mutex()

    var changes = 0
        private set

    fun changed() {
        changes++
        // Only the wait is cancelled by a newer change: a save already started runs to its end.
        pending?.cancel()
        pending =
            scope.launch {
                delay(debounce)
                scope.launch { lock.withLock { save() } }
            }
    }

    /** Saves now, after a save already running. */
    suspend fun flush() {
        pending?.cancel()
        lock.withLock { save() }
    }
}
