package shifa.doctor.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.notifications.NotificationReads
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.data.notifications.shows
import shifa.doctor.data.push.Push
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.HomeEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accessApprovedBanner
import shifa.doctor.generated.resources.accessDecisionFailed
import shifa.doctor.generated.resources.accessDeclinedBanner
import shifa.doctor.generated.resources.notificationsLoadFailed
import shifa.doctor.generated.resources.notificationsMarkFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

/** Where a tap goes once its target is known (flow 07/08 §10); the inbox stays under it. */
sealed interface NotificationGo {
    data class Appointment(
        val entry: CalendarEntry.Appointment,
    ) : NotificationGo

    data class Patient(
        val id: Long,
    ) : NotificationGo

    data class Document(
        val patientId: Long,
        val documentId: Long,
        val title: String?,
    ) : NotificationGo

    data object Chat : NotificationGo

    data class Conversation(
        val id: Long,
    ) : NotificationGo

    /** CARE-03: the care partnerships, when the row carries no partnership id (older backends). */
    data object Partnerships : NotificationGo

    data class Partnership(
        val id: Long,
    ) : NotificationGo

    data class Task(
        val id: Long,
    ) : NotificationGo

    /** Home only: nothing else to open, so the inbox (flow 02/03 §5). */
    data object Inbox : NotificationGo
}

data class NotificationsUiState(
    // Newest first; null until the first page (skeletons).
    val items: List<AppNotification>? = null,
    val page: Int = 0,
    val last: Boolean = true,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
    val refreshing: Boolean = false,
    val error: CodeBanner? = null,
    val filter: NotificationFilter = NotificationFilter.All,
    // A tapped row whose target is being looked up (one at a time, flow §8 "tap twice").
    val opening: Long? = null,
    val markingAll: Boolean = false,
    // A document-access request being approved or declined (one at a time).
    val deciding: Long? = null,
    val go: NotificationGo? = null,
) {
    val shown: List<AppNotification> get() = items.orEmpty().filter { filter.shows(it.kind) }
    val anyUnread: Boolean get() = items.orEmpty().any { !it.read }
}

/**
 * The inbox (NOTIF-02, flow 07/08): pages newest first, chips client-side, a tap marks the row read (at once, back if
 * the server refuses) and opens its target. An appointment opens its Calendar panel: its practice day is read from the
 * calendar (cached) to find the entry; gone → "This appointment no longer exists".
 */
class NotificationsViewModel(
    private val repo: NotificationsRepository = NotificationsRepository(),
    private val opener: NotificationOpener = NotificationOpener(),
    pushes: Flow<Map<String, String>> = Push.received,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        // PUSH-02: a push while the inbox is open puts its row on top.
        viewModelScope.launch { pushes.collect { if (_state.value.items != null) refresh() } }
    }

    /** The first page once; [refresh] reads it again. */
    fun load() {
        if (_state.value.items == null && !_state.value.refreshing) refresh()
    }

    // Each refresh starts a new list: a page asked for before it is dropped, not appended to the new one.
    private var generation = 0

    /**
     * Page 0 again (pull, Refresh, coming back): the list is replaced; on failure the old one stays under a banner. The
     * shell's count is read again with it (07 §2.4: refreshed when the inbox opens or refreshes, never polled).
     */
    fun refresh() {
        if (_state.value.refreshing) return
        val mine = ++generation
        _state.update { it.copy(refreshing = it.items != null, loadingMore = false) }
        NotificationReads.changed.tryEmit(Unit)
        viewModelScope.launch {
            val result = repo.page(0)
            if (mine != generation) return@launch
            result.fold(
                { p ->
                    _state.update {
                        it.copy(
                            items = p.items.map(::reconcile),
                            page = 0,
                            last = p.last,
                            refreshing = false,
                            error = null,
                            moreFailed = false,
                        )
                    }
                },
                { e ->
                    val banner =
                        CodeBanner(e.toUserMessage(Res.string.notificationsLoadFailed), requestId = e.requestId)
                    _state.update { it.copy(refreshing = false, error = banner, items = it.items ?: emptyList()) }
                },
            )
        }
    }

    /** The next page, at the end of the list; rows already shown aren't repeated (new ones may have shifted the pages). */
    fun loadMore() {
        val s = _state.value
        if (s.last || s.loadingMore || s.refreshing || s.items == null) return
        _state.update { it.copy(loadingMore = true, moreFailed = false) }
        val mine = generation
        viewModelScope.launch {
            val result = repo.page(s.page + 1)
            if (mine != generation) return@launch
            result.fold(
                { p ->
                    _state.update { cur ->
                        val known = cur.items.orEmpty().map { it.id }.toSet()
                        cur.copy(
                            items = cur.items.orEmpty() + p.items.filter { it.id !in known }.map(::reconcile),
                            page = p.page,
                            last = p.last,
                            loadingMore = false,
                        )
                    }
                },
                { _state.update { it.copy(loadingMore = false, moreFailed = true) } },
            )
        }
    }

    fun select(filter: NotificationFilter) {
        if (filter == _state.value.filter) return
        tracker.track(HomeEvents.NotificationsFilterSelected(filter))
        _state.update { it.copy(filter = filter) }
    }

    /** A row (or its action): read, then its target; [staff] have only chat to go to (flow 01/18). */
    fun open(
        n: AppNotification,
        staff: Boolean,
        clock: PracticeClock,
    ) {
        if (_state.value.opening != null) return
        if (!n.read) markRead(n)
        // An appointment is looked up first (its row shows a spinner, taps wait); the rest go at once.
        if (opener.slow(n, staff)) _state.update { it.copy(opening = n.id) }
        viewModelScope.launch {
            val to = opener.go(n, staff, clock)
            tracker.track(HomeEvents.NotificationOpened(n.type, to?.trackTarget ?: "none", "inbox"))
            _state.update { it.copy(opening = null, go = to ?: it.go) }
        }
    }

    fun opened() = _state.update { it.copy(go = null) }

    /** Read without going anywhere ("Open Calendar"). */
    fun read(n: AppNotification) {
        if (!n.read) markRead(n)
    }

    /**
     * NOTIF-04 (board `DaInbox` docAccess): Approve or Decline on a request the doctor owns. Once the server agrees the
     * row is read, its label says what was decided and its buttons go; a refusal leaves it as it was under a banner.
     */
    fun decide(
        n: AppNotification,
        approve: Boolean,
    ) {
        val requestId = n.documentAccessRequestId ?: return
        if (_state.value.deciding != null) return
        _state.update { it.copy(deciding = n.id) }
        viewModelScope.launch {
            repo.decideAccess(requestId, approve).fold(
                {
                    val status = if (approve) ACCESS_APPROVED else ACCESS_REJECTED
                    decidedHere[n.id] = status
                    _state.update { s ->
                        s.copy(
                            deciding = null,
                            items = s.items?.map { if (it.id == n.id) it.copy(accessStatus = status) else it },
                        )
                    }
                    if (!n.read) markRead(n)
                    tracker.track(HomeEvents.AccessRequestDecided(approve))
                    ShellNotice.pending.value =
                        Notice(
                            if (approve) Res.string.accessApprovedBanner else Res.string.accessDeclinedBanner,
                            success = true,
                        )
                },
                { e ->
                    tracker.track(HomeEvents.AccessRequestDecideFailed(approve, e.toFailureReason()))
                    _state.update { it.copy(deciding = null) }
                    ShellNotice.pending.value =
                        Notice(e.toUserMessage(Res.string.accessDecisionFailed), requestId = e.requestId)
                },
            )
        }
    }

    /** "Mark all as read": every row read once the server agrees. */
    fun markAll() {
        if (_state.value.markingAll) return
        val unread = _state.value.items.orEmpty().count { !it.read }
        _state.update { it.copy(markingAll = true) }
        viewModelScope.launch {
            repo.markAllRead().fold(
                {
                    tracker.track(HomeEvents.NotificationsMarkedAllRead(countBucket(unread)))
                    // Pages asked for before it would bring rows back unread: dropped (a new generation).
                    generation++
                    _state.update { s ->
                        s.copy(
                            markingAll = false,
                            refreshing = false,
                            loadingMore = false,
                            items = s.items?.map { it.copy(read = true) },
                        )
                    }
                },
                { e ->
                    tracker.track(HomeEvents.NotificationsMarkAllFailed(e.toFailureReason()))
                    _state.update { it.copy(markingAll = false) }
                    ShellNotice.pending.value =
                        Notice(
                            e.toUserMessage(Res.string.notificationsMarkFailed),
                            requestId = e.requestId,
                        )
                },
            )
        }
    }

    // Rows read here: a page answering after the read (asked before it) shows them read too.
    private val readHere = mutableSetOf<Long>()

    // Requests answered here (NOTIF-04): a page asked before the answer doesn't bring the buttons back.
    private val decidedHere = mutableMapOf<Long, String>()

    private fun reconcile(n: AppNotification): AppNotification {
        val read = if (n.id in readHere) n.copy(read = true) else n
        return decidedHere[n.id]?.let { read.copy(accessStatus = it) } ?: read
    }

    private fun markRead(n: AppNotification) {
        setRead(n.id, true)
        readHere += n.id
        viewModelScope.launch {
            repo.markRead(n.id).onFailure { e ->
                readHere -= n.id
                setRead(n.id, false)
                ShellNotice.pending.value =
                    Notice(
                        e.toUserMessage(Res.string.notificationsMarkFailed),
                        requestId = e.requestId,
                    )
            }
        }
    }

    private fun setRead(
        id: Long,
        read: Boolean,
    ) = _state.update { s -> s.copy(items = s.items?.map { if (it.id == id) it.copy(read = read) else it }) }

    private companion object {
        const val ACCESS_APPROVED = "approved"
        const val ACCESS_REJECTED = "rejected"
    }
}
