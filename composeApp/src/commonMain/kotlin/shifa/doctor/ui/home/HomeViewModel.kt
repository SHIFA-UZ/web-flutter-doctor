package shifa.doctor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.home.HomeRepository
import shifa.doctor.data.home.HomeSchedule
import shifa.doctor.data.home.RefreshGate
import shifa.doctor.data.home.homeRepository
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.HomeEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoad
import shifa.doctor.generated.resources.homeLoadFailed
import shifa.doctor.generated.resources.noSlotsAvailable
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

data class HomeUiState(
    // The first read is on its way (skeletons).
    val loading: Boolean = true,
    // A pull-to-refresh is on its way (the content stays).
    val refreshing: Boolean = false,
    val schedule: HomeSchedule? = null,
    val error: CodeBanner? = null,
    // HOME-03: the hero patient's birth date, once their record is read (no age until then, or if it fails); the age is
    // worked out on the practice date at each draw, so a birthday turns it over.
    val heroBirth: LocalDate? = null,
    // HOME-03: the Video Call chip is looking for a free slot; [videoSlot] is the one found, until the screen opens it.
    val findingSlot: Boolean = false,
    val videoSlot: CalendarEntry.FreeSlot? = null,
    // ANALYTICS-02: counts the schedule's successful reads, so the card's KPIs follow each one (even an unchanged one).
    val reads: Int = 0,
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
    private val patients: PatientsRepository = patientsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Nothing is read before the profile brings the practice zone (a read before it would count the UTC day).
    private var agedFor: Long? = null
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
                onSuccess = { s ->
                    val samePatient = s.hero?.patientId == it.schedule?.hero?.patientId
                    it.copy(
                        loading = false,
                        schedule = s,
                        error = null,
                        heroBirth = it.heroBirth.takeIf { samePatient },
                        reads = it.reads + 1,
                    )
                },
                onFailure = { e ->
                    it.copy(
                        loading = false,
                        error = CodeBanner(e.toUserMessage(Res.string.homeLoadFailed), requestId = e.requestId),
                    )
                },
            )
        }
        // Outside the gate: a slow patient read never holds up the next schedule read.
        result.getOrNull()?.hero?.patientId?.let { id -> viewModelScope.launch { readBirth(id) } }
    }

    // The hero patient's birth date, for the age (flow 02/02 §4: birthday-aware on the practice date).
    private suspend fun readBirth(patientId: Long) {
        // Once per hero patient: a record without a birth date (or a failed read) isn't asked again each minute.
        if (agedFor == patientId) return
        agedFor = patientId
        val birth = patients.patient(patientId).getOrNull()?.birth ?: return
        _state.update { if (it.schedule?.hero?.patientId == patientId) it.copy(heroBirth = birth) else it }
    }

    /**
     * The Video Call chip (flow 02/02 §7): the first free slot from now through the next 29 days, opened by the screen
     * as a video booking; none (or today unreadable) → "No slots available".
     */
    fun findVideoSlot() {
        // Without the practice zone "today" isn't known (a UTC day could skip the earliest slot): the chip is off then.
        if (_state.value.findingSlot || !zoneKnown || zone == null) return
        _state.update { it.copy(findingSlot = true) }
        viewModelScope.launch {
            val found = repo.firstFreeSlot(clockFor(zone))
            val slot = found.getOrNull()
            // A failed read is not "no slots": the doctor would stop looking for a day that is in fact free.
            found.fold(
                {
                    if (it == null) {
                        tracker.track(HomeEvents.HomeVideoSlotSearchFailed("no_slots"))
                        ShellNotice.pending.value = Notice(Res.string.noSlotsAvailable)
                    } else {
                        tracker.track(HomeEvents.HomeVideoSlotFound)
                    }
                },
                { e ->
                    tracker.track(HomeEvents.HomeVideoSlotSearchFailed(e.toFailureReason()))
                    ShellNotice.pending.value =
                        Notice(e.toUserMessage(Res.string.couldNotLoad), requestId = e.requestId)
                },
            )
            _state.update { it.copy(findingSlot = false, videoSlot = slot) }
        }
    }

    /** The found slot was opened. */
    fun videoSlotOpened() = _state.update { it.copy(videoSlot = null) }
}
