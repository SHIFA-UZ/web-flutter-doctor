package shifa.doctor.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCheckIn
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.HomeEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadTask
import shifa.doctor.generated.resources.failedToCancel
import shifa.doctor.generated.resources.taskCancelled
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice

data class TaskDetailUiState(
    val task: RemoteCareTask? = null,
    // Newest first (flow 07/12 §5 target); null until read.
    val checkIns: List<TaskCheckIn>? = null,
    val checkInsFailed: Boolean = false,
    // 404: "Task not found" with Back; any other failure "Couldn't load task" with Retry (Flutter showed "not found").
    val notFound: Boolean = false,
    val error: CodeBanner? = null,
    val refreshing: Boolean = false,
    val askCancel: Boolean = false,
    val cancelling: Boolean = false,
    // The cancel went through: the screen goes back to the list.
    val cancelled: Boolean = false,
    // The check-in whose sheet is open, by id: a refresh shows its new state in the open sheet.
    val viewingId: Long? = null,
) {
    val viewing: TaskCheckIn? get() = viewingId?.let { id -> checkIns?.firstOrNull { it.id == id } }

    val canCancel: Boolean get() = task?.status == TaskStatus.Active
}

/**
 * One task (TASK-03, flow 07/12): the task and its check-ins read together, again on each return and pull (Flutter
 * polled every 15 s, flow §12). Cancel asks first, then `PATCH /tasks/{id}/cancel`; done → back to the list with
 * "Task cancelled successfully".
 */
class TaskDetailViewModel(
    private val id: Long,
    private val repo: TasksRepository = TasksRepository(),
    private val profile: ProfileRepository = profileRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(TaskDetailUiState())
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = it.task != null) }
        viewModelScope.launch {
            val checks = async { repo.checkIns(id) }
            val task = repo.task(id)
            val list = checks.await()
            _state.update { s ->
                task.fold(
                    { t ->
                        s.copy(
                            task = t,
                            notFound = false,
                            error = null,
                            checkIns =
                                list.getOrNull()?.sortedWith(
                                    compareByDescending<TaskCheckIn> {
                                        it.date
                                    }.thenByDescending { it.time },
                                ) ?: s.checkIns,
                            checkInsFailed = list.isFailure,
                        )
                    },
                    { e ->
                        // The plan was taken away on the server: the profile again, so the shell closes Tasks.
                        if (e is ApiException.Forbidden) launch { profile.refresh() }
                        s.copy(
                            // Deleted meanwhile: the details (and Cancel) give way to "Task not found".
                            task = if (e is ApiException.NotFound) null else s.task,
                            notFound = e is ApiException.NotFound,
                            error = CodeBanner(e.toUserMessage(Res.string.couldNotLoadTask), requestId = e.requestId),
                        )
                    },
                ).copy(refreshing = false)
            }
        }
    }

    fun view(c: TaskCheckIn?) = _state.update { it.copy(viewingId = c?.id) }

    fun askCancel(ask: Boolean) = _state.update { it.copy(askCancel = ask) }

    /** "Yes, Cancel": one request however often it's tapped (flow §8). */
    fun cancel() {
        if (_state.value.cancelling) return
        _state.update { it.copy(cancelling = true) }
        viewModelScope.launch {
            repo.cancel(id).fold(
                { t ->
                    tracker.track(HomeEvents.TaskCancelled)
                    ShellNotice.pending.value = Notice(Res.string.taskCancelled, success = true)
                    _state.update { it.copy(task = t, cancelling = false, askCancel = false, cancelled = true) }
                },
                { e ->
                    tracker.track(HomeEvents.TaskCancelFailed(e.toFailureReason()))
                    ShellNotice.pending.value =
                        Notice(
                            e.toUserMessage(Res.string.failedToCancel),
                            requestId = e.requestId,
                        )
                    _state.update { it.copy(cancelling = false, askCancel = false) }
                },
            )
        }
    }
}
