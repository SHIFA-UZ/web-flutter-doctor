package shifa.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stops the ViewModels a test made and waits until their work has finished, so nothing resumes on Main while the next
 * test sets it ("Dispatchers.Main is used concurrently with setting it"). Call before `Dispatchers.resetMain()`.
 */
fun List<ViewModel>.stopAll() =
    runBlocking {
        forEach { vm -> withTimeoutOrNull(STOP_MS) { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
    }

private const val STOP_MS = 2_000L
