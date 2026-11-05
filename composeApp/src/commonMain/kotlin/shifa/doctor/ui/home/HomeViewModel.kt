package shifa.doctor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.home.HomeRepository
import shifa.doctor.data.home.HomeSchedule
import shifa.doctor.data.home.RefreshGate
import shifa.doctor.data.home.homeRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.homeLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.PracticeClock

data class HomeUiState(
    // The first read is on its way (skeletons).
    val loading: Boolean = true,
    // A pull-to-refresh is on its way (the content stays).
    val refreshing: Boolean = false,
    val schedule: HomeSchedule? = null,
    val error: CodeBanner? = null,
)

/**
 * The Home tab (HOME-02, 02 §1, flow 02/01): today's schedule and the hero (HOME-01), read on the tab's focus (at most
 * once a minute, never twice at once) and on pull-to-refresh (always). A refresh keeps what is shown until the answer
 * (Flutter dropped back to the skeleton).
 */
class HomeViewModel(
    private val repo: HomeRepository = homeRepository,
    private val clockFor: (String?) -> PracticeClock = { PracticeClock(it) },
    private val gate: RefreshGate = RefreshGate(),
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Nothing is read before the profile brings the practice zone (a read before it would count the UTC day).
    private var zoneKnown = false
    private var zone: String? = null

    /** The profile's practice zone, each time it may have changed: the first one, or a different one, reads at once. */
    fun practiceZone(id: String?) {
        if (zoneKnown && zone == id) return
        zoneKnown = true
        zone = id
        viewModelScope.launch { gate.run(force = true) { read(force = false) } }
    }

    /**
     * The tab came into view, or the app came back: the schedule and the shell's [counts], at most once a minute and
     * never while a read is out. The counts come along even before the zone is known.
     */
    fun focus(counts: () -> Unit = {}) {
        viewModelScope.launch {
            gate.run {
                counts()
                if (zoneKnown) read(force = false)
            }
        }
    }

    /** Pull-to-refresh and Refresh: everything again now, unless a read is already out (that one keeps the indicator). */
    fun refresh(counts: () -> Unit = {}) {
        viewModelScope.launch {
            gate.run(force = true) {
                _state.update { it.copy(refreshing = true) }
                try {
                    counts()
                    if (zoneKnown) read(force = true)
                } finally {
                    _state.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    private suspend fun read(force: Boolean) {
        val asked = zone
        val result = repo.schedule(clockFor(asked), force)
        // The zone changed during the read (the gate skipped that read): read again on the new one.
        if (zone != asked) return read(force)
        _state.update {
            result.fold(
                onSuccess = { s -> it.copy(loading = false, schedule = s, error = null) },
                onFailure = { e ->
                    it.copy(
                        loading = false,
                        error = CodeBanner(e.toUserMessage(Res.string.homeLoadFailed), requestId = e.requestId),
                    )
                },
            )
        }
    }
}
