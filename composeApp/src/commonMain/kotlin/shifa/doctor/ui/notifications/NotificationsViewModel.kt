package shifa.doctor.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.notifications.NotificationReads
import shifa.doctor.data.notifications.NotificationTarget
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.data.notifications.shows
import shifa.doctor.data.notifications.target
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentGone
import shifa.doctor.generated.resources.couldNotLoad
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

    data class Task(
        val id: Long,
    ) : NotificationGo
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
    private val appointments: AppointmentsRepository = AppointmentsRepository(),
    private val calendar: CalendarRepository = calendarRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

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

    fun select(filter: NotificationFilter) = _state.update { it.copy(filter = filter) }

    /** A row (or its action): read, then its target; [staff] have only chat to go to (flow 01/18). */
    fun open(
        n: AppNotification,
        staff: Boolean,
        clock: PracticeClock,
    ) {
        if (_state.value.opening != null) return
        if (!n.read) markRead(n)
        when (val target = n.target(staff)) {
            is NotificationTarget.Appointment -> openAppointment(n.id, target, clock)
            is NotificationTarget.Patient ->
                go(
                    if (target.openDocument && target.documentId != null) {
                        NotificationGo.Document(target.id, target.documentId, n.documentTitle)
                    } else {
                        NotificationGo.Patient(target.id)
                    },
                )
            NotificationTarget.Chat -> go(NotificationGo.Chat)
            is NotificationTarget.Task -> go(NotificationGo.Task(target.id))
            // Partnerships (CARE) have no screens yet: the tap only reads it.
            NotificationTarget.Partnerships, NotificationTarget.Inbox -> Unit
        }
    }

    fun opened() = _state.update { it.copy(go = null) }

    /** Read without going anywhere ("Open Calendar"). */
    fun read(n: AppNotification) {
        if (!n.read) markRead(n)
    }

    /** "Mark all as read": every row read once the server agrees. */
    fun markAll() {
        if (_state.value.markingAll) return
        _state.update { it.copy(markingAll = true) }
        viewModelScope.launch {
            repo.markAllRead().fold(
                {
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

    private fun reconcile(n: AppNotification) = if (n.id in readHere) n.copy(read = true) else n

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

    private fun go(to: NotificationGo) = _state.update { it.copy(go = to) }

    private fun openAppointment(
        id: Long,
        target: NotificationTarget.Appointment,
        clock: PracticeClock,
    ) {
        _state.update { it.copy(opening = id) }
        viewModelScope.launch {
            val found =
                runCatching {
                    val start =
                        target.startAt
                            ?: appointments.detail(target.id).getOrThrow().startAt.let(PracticeClock::parseInstant)
                    start?.let { at ->
                        calendar
                            .day(DayKey(clock.local(at).date))
                            .getOrThrow()
                            .filterIsInstance<CalendarEntry.Appointment>()
                            .firstOrNull { it.appointmentId == target.id }
                    }
                }
            _state.update { it.copy(opening = null) }
            found.fold(
                { entry ->
                    if (entry != null) {
                        go(NotificationGo.Appointment(entry))
                    } else {
                        ShellNotice.pending.value = Notice(Res.string.appointmentGone)
                    }
                },
                { e ->
                    // A 404 from the appointment means it's gone; anything else is a failed read.
                    val gone = e is ApiException.NotFound
                    ShellNotice.pending.value =
                        if (gone) {
                            Notice(Res.string.appointmentGone)
                        } else {
                            Notice(e.toUserMessage(Res.string.couldNotLoad), requestId = e.requestId)
                        }
                },
            )
        }
    }
}
