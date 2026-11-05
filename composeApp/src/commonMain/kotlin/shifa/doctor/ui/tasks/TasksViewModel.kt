package shifa.doctor.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.tasksLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner

data class TasksUiState(
    // Server order; null until the first answer (skeletons, not the empty card: flow 07/11 §12).
    val tasks: List<RemoteCareTask>? = null,
    val refreshing: Boolean = false,
    val error: CodeBanner? = null,
    // No read has ever worked: the list is unknown (never the empty card, which means "no tasks").
    val failed: Boolean = false,
    val query: String = "",
    val status: TaskStatus? = null,
    val category: TaskCategory? = null,
) {
    /** Search (task or patient name) and both dropdowns together, on the phone (07 §3.1). */
    val shown: List<RemoteCareTask>
        get() {
            val q = query.trim()
            return tasks.orEmpty().filter { t ->
                (
                    q.isEmpty() ||
                        t.name.contains(
                            q,
                            ignoreCase = true,
                        ) || t.patientName.contains(q, ignoreCase = true)
                ) &&
                    (status == null || t.status == status) &&
                    (category == null || t.category == category)
            }
        }

    val filtered: Boolean get() = query.isNotBlank() || status != null || category != null

    /** The summary cards over every task (flow §4: "Overdue" counts expired tasks, as the backend marks them). */
    fun count(s: TaskStatus) = tasks.orEmpty().count { it.status == s }
}

/**
 * The tasks list (TASK-02, flow 07/11): read on open and on every return (from details, create, templates), pull and
 * Refresh; a failed read keeps the old list under a banner (Flutter swallowed errors). PREMIUM only: the shell never
 * shows this to other plans.
 */
class TasksViewModel(
    private val repo: TasksRepository = TasksRepository(),
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    // One read at a time, the newest wins (a tap on Refresh during the first read replaces it).
    private var reading: Job? = null

    fun refresh() {
        reading?.cancel()
        // A retry after a first failure shows the skeleton again, not the failure with a live Retry.
        _state.update { it.copy(refreshing = it.tasks != null, failed = false) }
        reading =
            viewModelScope.launch {
                repo.tasks().fold(
                    {
                            list ->
                        _state.update { it.copy(tasks = list, refreshing = false, error = null, failed = false) }
                    },
                    { e ->
                        // The plan was taken away on the server: read the profile again, so the shell closes the tab
                        // and says why (flow 07/11 §8, its `tasksNotOnPlan` note).
                        if (e is ApiException.Forbidden) launch { profile.refresh() }
                        val banner = CodeBanner(e.toUserMessage(Res.string.tasksLoadFailed), requestId = e.requestId)
                        _state.update { it.copy(refreshing = false, error = banner, failed = it.tasks == null) }
                    },
                )
            }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun query(text: String) = _state.update { it.copy(query = text) }

    fun status(s: TaskStatus?) = _state.update { it.copy(status = s) }

    fun category(c: TaskCategory?) = _state.update { it.copy(category = c) }

    /** A summary card: its status as the filter, or off when it already is (board `DaInbox`). */
    fun toggleStatus(s: TaskStatus) = _state.update { it.copy(status = if (it.status == s) null else s) }

    fun clearFilters() = _state.update { it.copy(query = "", status = null, category = null) }
}
