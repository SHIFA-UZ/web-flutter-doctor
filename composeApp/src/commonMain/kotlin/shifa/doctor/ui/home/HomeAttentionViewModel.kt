package shifa.doctor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.ui.notifications.NotificationGo
import shifa.doctor.ui.notifications.NotificationOpener
import shifa.doctor.utils.PracticeClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** One of the attention centre's rows (02 §1.5): an unread notification, or an active task. */
sealed interface AttentionItem {
    data class Unread(
        val notification: AppNotification,
    ) : AttentionItem

    data class Task(
        val task: RemoteCareTask,
    ) : AttentionItem
}

data class HomeAttentionUiState(
    // The inbox's first page, newest first; null until read.
    val notifications: List<AppNotification>? = null,
    // Active remote-care tasks (PREMIUM); null when not read (or not allowed).
    val tasks: List<RemoteCareTask>? = null,
    // The notifications couldn't be read and none are known (flow 02/03 §6: "Could not load notifications" + Retry).
    val failed: Boolean = false,
    // The read stopped before the end with fewer than 4 unread: more may wait in the inbox.
    val incomplete: Boolean = false,
    val opening: Long? = null,
    val go: NotificationGo? = null,
) {
    /** Up to 4 unread, then up to 3 active tasks, 6 in all (02 §1.5; flow §8: so 4 + 2 at most). */
    val attention: List<AttentionItem>
        get() =
            (
                notifications.orEmpty().filter { !it.read }.take(MAX_UNREAD).map(AttentionItem::Unread) +
                    tasks.orEmpty().take(MAX_TASKS).map(AttentionItem::Task)
            ).take(MAX_ITEMS)

    /** The 8 newest notifications, read or not (02 §1.6). */
    val activity: List<AppNotification> get() = notifications.orEmpty().take(MAX_ACTIVITY)

    private companion object {
        const val MAX_UNREAD = 4
        const val MAX_TASKS = 3
        const val MAX_ITEMS = 6
        const val MAX_ACTIVITY = 8
    }
}

/**
 * Home's attention centre and activity feed (HOME-04): one read of the inbox's first page feeds both, the active tasks
 * read once with it (02 §1.5 fix: not per widget). Read with each read of Home's schedule (gated there) and on pull.
 */
class HomeAttentionViewModel(
    private val notifications: NotificationsRepository = NotificationsRepository(),
    private val tasks: TasksRepository = TasksRepository(),
    private val opener: NotificationOpener = NotificationOpener(),
) : ViewModel() {
    private val _state = MutableStateFlow(HomeAttentionUiState())
    val state: StateFlow<HomeAttentionUiState> = _state.asStateFlow()

    // One read at a time, at most once a minute unless forced (a pull, Retry, a mark that left fewer than four unread)
    // or the plan changed. A forced or plan-changing ask during a read runs again once it ends, with the latest plan
    // (02 §1.5 fix: a task read never goes out twice for the same trigger).
    private var running = false
    private var again = false
    private var lastRead: TimeMark? = null
    private var wanted = false
    private var lastWithTasks: Boolean? = null

    // Rows read here: a read that answers after the mark (asked before it) shows them read.
    private val readHere = mutableSetOf<Long>()

    // The last read reached the end of the list (no older unread to fetch).
    private var reachedEnd = false

    /** [withTasks]: the doctor's plan has remote-care tasks (others never ask: a 403). */
    fun refresh(
        withTasks: Boolean,
        force: Boolean = false,
    ) {
        wanted = withTasks
        val changed = withTasks != lastWithTasks
        if (running) {
            if (force || changed) again = true
            return
        }
        if (!force && !changed && lastRead?.let { it.elapsedNow() < COOLDOWN } == true) return
        read()
    }

    private fun read() {
        running = true
        again = false
        lastRead = TimeSource.Monotonic.markNow()
        val withTasks = wanted
        lastWithTasks = withTasks
        _state.update { it.copy(failed = false) }
        viewModelScope.launch {
            try {
                val taskRead = if (withTasks) async { tasks.tasks(status = TaskStatus.Active) } else null
                val read = unreadPages()
                val active = taskRead?.await()
                _state.update { s ->
                    s.copy(
                        // A failed read keeps what was shown (flow §6 target: keep tiles).
                        notifications =
                            read.getOrNull()?.map {
                                if (it.id in readHere) {
                                    it.copy(
                                        read = true,
                                    )
                                } else {
                                    it
                                }
                            } ?: s.notifications,
                        failed = read.isFailure && s.notifications == null,
                        tasks = if (withTasks) active?.getOrNull() ?: s.tasks else null,
                    )
                }
            } finally {
                running = false
                // Asked again meanwhile, or the plan moved on: once more, with the latest plan.
                if (again || wanted != withTasks) read()
            }
        }
    }

    /**
     * Pages newest first until 4 unread are found or the list ends; past [MAX_PAGES] the card says more unread wait in
     * the inbox ([HomeAttentionUiState.incomplete]) instead of "All caught up!".
     */
    private suspend fun unreadPages(): Result<List<AppNotification>> {
        val all = mutableListOf<AppNotification>()
        var end = false
        for (page in 0 until MAX_PAGES) {
            val p =
                notifications.page(
                    page,
                ).getOrElse { return if (all.isEmpty()) Result.failure(it) else Result.success(all) }
            all += p.items.filter { n -> all.none { it.id == n.id } }
            end = p.last
            if (p.last || all.count { !it.read && it.id !in readHere } >= UNREAD_WANTED) break
        }
        reachedEnd = end
        val short = all.count { !it.read && it.id !in readHere } < UNREAD_WANTED
        _state.update { it.copy(incomplete = !end && short) }
        return Result.success(all)
    }

    /** The row's mark-as-read button: read without going anywhere; the row leaves at once (back if refused). */
    fun markRead(n: AppNotification) {
        setRead(n.id, true)
        readHere += n.id
        viewModelScope.launch {
            notifications.markRead(n.id).fold(
                {
                    // Fewer than four unread left while older ones may wait: read on to refill the card.
                    val left = _state.value.notifications.orEmpty().count { !it.read }
                    if (left < UNREAD_WANTED && !reachedEnd) refresh(wanted, force = true)
                },
                {
                    readHere -= n.id
                    setRead(n.id, false)
                },
            )
        }
    }

    /** A row: an unread one is read; then its target (one at a time, flow §8). [read] false for the activity feed. */
    fun open(
        n: AppNotification,
        staff: Boolean,
        clock: PracticeClock,
        read: Boolean = true,
    ) {
        if (_state.value.opening != null) return
        if (read && !n.read) markRead(n)
        if (opener.slow(n, staff)) _state.update { it.copy(opening = n.id) }
        viewModelScope.launch {
            // Nothing to open here (no ids, no screen yet): the inbox (flow 02/03 §5).
            val to = opener.go(n, staff, clock) ?: NotificationGo.Inbox.takeIf { !opener.slow(n, staff) }
            _state.update { it.copy(opening = null, go = to ?: it.go) }
        }
    }

    fun openTask(t: RemoteCareTask) = _state.update { it.copy(go = NotificationGo.Task(t.id)) }

    fun opened() = _state.update { it.copy(go = null) }

    private companion object {
        const val MAX_PAGES = 10
        const val UNREAD_WANTED = 4
        val COOLDOWN = 60.seconds
    }

    private fun setRead(
        id: Long,
        read: Boolean,
    ) = _state.update {
            s ->
        s.copy(notifications = s.notifications?.map { if (it.id == id) it.copy(read = read) else it })
    }
}
