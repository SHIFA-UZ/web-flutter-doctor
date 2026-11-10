package shifa.doctor.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.care.CarePartnershipsRepository
import shifa.doctor.data.care.PartnershipAction
import shifa.doctor.data.care.carePartnershipsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CarePartnerHit
import shifa.doctor.data.model.CarePartnership
import shifa.doctor.data.model.CareProgress
import shifa.doctor.data.model.PartnerInvite
import shifa.doctor.data.model.PartnershipStatus
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.PatientsEvents
import shifa.doctor.data.tracking.trackName
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.careAccepted
import shifa.doctor.generated.resources.careActionFailed
import shifa.doctor.generated.resources.careCancelled
import shifa.doctor.generated.resources.careCompleted
import shifa.doctor.generated.resources.careDeclined
import shifa.doctor.generated.resources.careInviteExists
import shifa.doctor.generated.resources.careInviteFailed
import shifa.doctor.generated.resources.careLoadFailed
import shifa.doctor.generated.resources.carePostFailed
import shifa.doctor.generated.resources.careSearchFailed
import shifa.doctor.ui.emailcode.CodeBanner
import kotlin.time.Duration.Companion.milliseconds

private fun Throwable.banner(fallback: StringResource) = CodeBanner(toUserMessage(fallback), requestId = requestId)

// --- Find therapy partner (CARE-02) ---

data class FindPartnerUiState(
    val query: String = "",
    val specialty: String = "",
    // Null until the first answer.
    val hits: List<CarePartnerHit>? = null,
    val searching: Boolean = true,
    val error: CodeBanner? = null,
    // The doctors already co-treating this patient with me (pending or active), for the "Already a partner" tag.
    val partners: Set<Long> = emptySet(),
    val selected: CarePartnerHit? = null,
    val reason: String = "",
    // Typed by the doctor: a new pick keeps it; untouched, it follows the picked doctor.
    val reasonEdited: Boolean = false,
    val message: String = "",
    val sending: Boolean = false,
    val banner: CodeBanner? = null,
    val sent: CarePartnership? = null,
    val askDiscard: Boolean = false,
)

/**
 * Find therapy partner (CARE-02, 06 §5.4, flow 06/12, board `DaPatients` partnerFind): the directory searched as the
 * doctor types (300 ms after the last key; the board's, Flutter needed a Search tap), an older answer dropped; one
 * doctor picked; the specialty requested (the filter, else the doctor's profession) and a message; Send invite →
 * the new partnership. A selection that leaves the results is cleared (Flutter kept it invisibly).
 */
class FindPartnerViewModel(
    private val patientId: Long,
    private val country: () -> String? = { null },
    private val repo: CarePartnershipsRepository = carePartnershipsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(FindPartnerUiState())
    val state: StateFlow<FindPartnerUiState> = _state.asStateFlow()
    private var pending: Job? = null
    private var generation = 0

    init {
        search()
        viewModelScope.launch {
            repo.mine().onSuccess { list ->
                val mine =
                    list.filter {
                        it.patientId == patientId && it.status in OPEN
                    }
                _state.update {
                        s ->
                    s.copy(partners = mine.flatMap { listOf(it.initiatingDoctorId, it.partnerDoctorId) }.toSet())
                }
            }
        }
    }

    fun query(text: String) {
        _state.update { it.copy(query = text.take(FIELD_CHARS)) }
        later()
    }

    fun specialty(text: String) {
        _state.update { it.copy(specialty = text.take(FIELD_CHARS)) }
        later()
    }

    private fun later() {
        // An answer for the old text is dropped from now on, not only once the next search starts.
        generation++
        pending?.cancel()
        pending =
            viewModelScope.launch {
                delay(DEBOUNCE)
                search()
            }
    }

    fun search() {
        val gen = ++generation
        val s = _state.value
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            val result = repo.search(s.query, s.specialty, country())
            if (gen != generation) return@launch
            // Only a search the doctor typed (not the list on open); the count as a range, never the text.
            val typed = s.query.isNotBlank() || s.specialty.isNotBlank()
            result.onSuccess { hits ->
                if (typed) {
                    tracker.track(
                        PatientsEvents.CarePartnerSearched(countBucket(hits.size), s.specialty.isNotBlank()),
                    )
                }
            }
            _state.update { st ->
                result.fold(
                    { hits ->
                        st.copy(
                            hits = hits,
                            searching = false,
                            selected = st.selected?.takeIf { sel -> hits.any { it.id == sel.id } },
                        )
                    },
                    { e -> st.copy(searching = false, error = e.banner(Res.string.careSearchFailed)) },
                )
            }
        }
    }

    /**
     * One doctor at a time; again on the same one lets go. The specialty requested starts as the doctor's (the
     * board's), else the filter (a partial filter like "endo" made a poor request).
     */
    fun pick(hit: CarePartnerHit) =
        _state.update { s ->
            if (s.sending) return@update s
            if (s.selected?.id == hit.id) {
                s.copy(selected = null)
            } else {
                s.copy(
                    selected = hit,
                    reason =
                        if (s.reasonEdited) {
                            s.reason
                        } else {
                            hit.profession?.takeIf(
                                String::isNotBlank,
                            ) ?: s.specialty.trim()
                        },
                )
            }
        }

    fun reason(text: String) =
        _state.update { if (it.sending) it else it.copy(reason = text.take(FIELD_CHARS), reasonEdited = true) }

    fun message(text: String) = _state.update { if (it.sending) it else it.copy(message = text.take(MESSAGE_CHARS)) }

    fun send() {
        val s = _state.value
        val hit = s.selected ?: return
        if (s.sending) return
        _state.update { it.copy(sending = true, banner = null) }
        viewModelScope.launch {
            repo.invite(
                PartnerInvite(
                    patientId,
                    hit.id,
                    s.reason.trim().ifEmpty { null },
                    s.message.trim().ifEmpty { null },
                ),
            ).fold(
                { created ->
                    tracker.track(PatientsEvents.CareInviteSent(s.message.isNotBlank()))
                    _state.update { it.copy(sending = false, sent = created) }
                },
                { e ->
                    tracker.track(PatientsEvents.CareInviteFailed(e.toFailureReason()))
                    val text =
                        if (e is ApiException.Conflict) Res.string.careInviteExists else Res.string.careInviteFailed
                    _state.update { it.copy(sending = false, banner = e.banner(text)) }
                },
            )
        }
    }

    /** Back: true when the screen may go; a typed message asks first (flow §7). */
    fun back(): Boolean {
        val s = _state.value
        if (s.sending) return false
        if (s.message.isNotBlank() && !s.askDiscard) return false.also { _state.update { it.copy(askDiscard = true) } }
        return true
    }

    fun keep() = _state.update { it.copy(askDiscard = false) }

    fun dismiss() = _state.update { it.copy(banner = null) }

    companion object {
        val DEBOUNCE = 300.milliseconds
        const val FIELD_CHARS = 100
        const val MESSAGE_CHARS = 500
        private val OPEN = setOf(PartnershipStatus.Pending, PartnershipStatus.Active)
    }
}

// --- Care partnerships list (CARE-03) ---

enum class PartnershipFilter { All, NeedsAnswer, Active, Sent, Finished }

data class PartnershipsUiState(
    val all: List<CarePartnership>? = null,
    val error: CodeBanner? = null,
    val refreshing: Boolean = false,
    val filter: PartnershipFilter = PartnershipFilter.All,
    // The patient the list opened for; [showAll] lifts it.
    val showAll: Boolean = false,
)

/**
 * The care partnerships (CARE-03, flow 06/13, board `DaPatients` partnerships): filter chips, the patient's only when
 * opened from their record ("Show all" lifts it), those needing my answer first, then the latest change. Read again
 * on every return.
 */
class PartnershipsViewModel(
    private val patientId: Long?,
    private val me: () -> Long?,
    private val repo: CarePartnershipsRepository = carePartnershipsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(PartnershipsUiState())
    val state: StateFlow<PartnershipsUiState> = _state.asStateFlow()
    private var generation = 0

    fun load(refresh: Boolean = false) {
        val gen = ++generation
        _state.update { it.copy(refreshing = refresh, error = null) }
        viewModelScope.launch {
            val result = repo.mine()
            if (gen != generation) return@launch
            _state.update { s ->
                result.fold(
                    { list -> s.copy(all = list, refreshing = false) },
                    { e -> s.copy(refreshing = false, error = e.banner(Res.string.careLoadFailed)) },
                )
            }
        }
    }

    fun filter(f: PartnershipFilter) {
        if (f == _state.value.filter) return
        _state.update { it.copy(filter = f) }
        tracker.track(PatientsEvents.CarePartnershipsFiltered(f.trackName()))
    }

    fun showAll() = _state.update { it.copy(showAll = true) }

    /** The rows shown: the patient's (unless lifted), the filter's, needing my answer first, then the latest change. */
    fun rows(s: PartnershipsUiState): List<CarePartnership>? {
        val me = me()
        return s.all
            ?.filter { patientId == null || s.showAll || it.patientId == patientId }
            ?.filter { p ->
                when (s.filter) {
                    PartnershipFilter.All -> true
                    PartnershipFilter.NeedsAnswer -> p.needsMyAnswer(me)
                    PartnershipFilter.Active -> p.status == PartnershipStatus.Active
                    PartnershipFilter.Sent -> p.status == PartnershipStatus.Pending && p.iInitiated(me) == true
                    PartnershipFilter.Finished -> p.status.ended
                }
            }
            ?.sortedWith(
                compareBy<CarePartnership> {
                    if (it.needsMyAnswer(me)) 0 else 1
                }.thenByDescending { it.updatedAt.orEmpty() },
            )
    }
}

// --- Partnership detail (CARE-03) ---

data class PartnershipUiState(
    val partnership: CarePartnership? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: CodeBanner? = null,
    val progress: List<CareProgress>? = null,
    val progressFailed: Boolean = false,
    // The action asking for confirmation; [acting] while it's sent.
    val confirm: PartnershipAction? = null,
    val acting: Boolean = false,
    val draft: String = "",
    val posting: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
)

/**
 * One partnership (CARE-03, flow 06/14, board `DaPatients` partnerDetail): who, for which patient, the status, the
 * actions by status and side (Accept / Decline for the partner, Cancel invite for the initiator, Mark completed when
 * active; each asks first), the progress updates and, when active, a composer. A failed progress read keeps the rest
 * working (Retry); after an action the partnership is the server's answer.
 */
class PartnershipViewModel(
    private val id: Long,
    private val repo: CarePartnershipsRepository = carePartnershipsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(PartnershipUiState())
    val state: StateFlow<PartnershipUiState> = _state.asStateFlow()
    private var generation = 0
    private var progressGeneration = 0

    fun load() {
        val gen = ++generation
        _state.update { it.copy(loading = it.partnership == null, error = null) }
        viewModelScope.launch {
            val result = repo.get(id)
            if (gen != generation) return@launch
            _state.update { s ->
                result.fold(
                    { p -> s.copy(partnership = p, loading = false, notFound = false) },
                    { e ->
                        if (e is ApiException.NotFound) {
                            s.copy(loading = false, notFound = true)
                        } else {
                            s.copy(loading = false, error = e.banner(Res.string.careLoadFailed))
                        }
                    },
                )
            }
        }
        loadProgress()
    }

    fun loadProgress() {
        val gen = ++progressGeneration
        _state.update { it.copy(progressFailed = false) }
        viewModelScope.launch {
            val result = repo.progress(id)
            // A read that started before an update was posted would hide it.
            if (gen != progressGeneration) return@launch
            _state.update { s ->
                result.fold({
                    s.copy(
                        progress =
                            it.sortedBy {
                                    p ->
                                p.createdAt.orEmpty()
                            },
                    )
                }, { s.copy(progressFailed = true) })
            }
        }
    }

    fun ask(action: PartnershipAction?) = _state.update { if (it.acting) it else it.copy(confirm = action) }

    fun act() {
        val action = _state.value.confirm ?: return
        if (_state.value.acting) return
        _state.update { it.copy(acting = true, banner = null) }
        viewModelScope.launch {
            repo.act(id, action).fold(
                { p ->
                    tracker.track(PatientsEvents.CarePartnershipActed(action.trackName()))
                    val done =
                        when (action) {
                            PartnershipAction.Accept -> Res.string.careAccepted
                            PartnershipAction.Decline -> Res.string.careDeclined
                            PartnershipAction.Cancel -> Res.string.careCancelled
                            PartnershipAction.Complete -> Res.string.careCompleted
                        }
                    _state.update {
                        it.copy(
                            partnership = p,
                            acting = false,
                            confirm = null,
                            banner = CodeBanner(done, error = false),
                        )
                    }
                    if (action == PartnershipAction.Accept) loadProgress()
                },
                { e ->
                    tracker.track(PatientsEvents.CarePartnershipActFailed(action.trackName(), e.toFailureReason()))
                    _state.update {
                        it.copy(
                            acting = false,
                            confirm = null,
                            banner = e.banner(Res.string.careActionFailed),
                        )
                    }
                    // The other side may have changed it meanwhile: read it again.
                    load()
                },
            )
        }
    }

    fun draft(text: String) = _state.update { if (it.posting) it else it.copy(draft = text.take(UPDATE_CHARS)) }

    fun post() {
        val s = _state.value
        val body = s.draft.trim()
        if (body.isEmpty() || s.posting || s.partnership?.status != PartnershipStatus.Active) return
        _state.update { it.copy(posting = true, banner = null) }
        viewModelScope.launch {
            repo.addProgress(id, body).fold(
                { p ->
                    tracker.track(PatientsEvents.CareProgressPosted)
                    progressGeneration++
                    _state.update { it.copy(posting = false, draft = "", progress = it.progress.orEmpty() + p) }
                },
                // The text stays for another try.
                { e ->
                    tracker.track(PatientsEvents.CareProgressPostFailed(e.toFailureReason()))
                    _state.update { it.copy(posting = false, banner = e.banner(Res.string.carePostFailed)) }
                },
            )
        }
    }

    /** Back: an unsent update asks first. */
    fun back(): Boolean {
        val s = _state.value
        if (s.draft.isNotBlank() && !s.askDiscard) return false.also { _state.update { it.copy(askDiscard = true) } }
        return true
    }

    fun keep() = _state.update { it.copy(askDiscard = false) }

    fun dismiss() = _state.update { it.copy(banner = null) }

    companion object {
        const val UPDATE_CHARS = 1000
    }
}
