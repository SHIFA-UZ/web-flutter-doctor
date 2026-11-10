package shifa.doctor.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.MAX_TIMES_PER_DAY
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.TaskTemplate
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tasks.ScheduleMode
import shifa.doctor.data.tasks.TaskForm
import shifa.doctor.data.tasks.TaskFormProblem
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.data.tasks.withSlot
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.HomeEvents
import shifa.doctor.data.tracking.trackName
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.customTimesAddAtLeastOne
import shifa.doctor.generated.resources.failedToCreateTask
import shifa.doctor.generated.resources.failedToLoadPatients
import shifa.doctor.generated.resources.pleaseSelectPatient
import shifa.doctor.generated.resources.taskCreated
import shifa.doctor.generated.resources.timeAlreadyInList
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice

/** Which date the inline month is open for. */
enum class TaskDateField { Start, End }

data class CreateTaskUiState(
    val form: TaskForm,
    // What the form opened with: leaving with anything else asks first (flow 07/13 §7 NEW).
    val initial: TaskForm,
    // "From template: …" and, when its interval wasn't a choice, the one it was snapped to (flow §6 NEW).
    val templateName: String? = null,
    val intervalAdjustedTo: Int? = null,
    // Create was tried: the problems show from then on.
    val tried: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val askDiscard: Boolean = false,
    val picking: Boolean = false,
    val query: String = "",
    val patients: List<PatientOption>? = null,
    val patientsError: CodeBanner? = null,
    val dateOpen: TaskDateField? = null,
    // The month the open date picker shows.
    val month: LocalDate = form.startDate,
) {
    val dirty: Boolean get() = form != initial

    fun shows(p: TaskFormProblem) = tried && p in form.problems
}

/**
 * The create form (TASK-04, flow 07/13): blank, from a template (TASK-05), or with a patient set. Create checks the
 * form (flow §9), then `POST /tasks` once; done → back with "Task created successfully".
 */
class CreateTaskViewModel(
    today: LocalDate,
    template: TaskTemplate? = null,
    patient: PatientOption? = null,
    private val repo: TasksRepository = TasksRepository(),
    private val patientsRepo: PatientsRepository = patientsRepository,
    private val profile: ProfileRepository = profileRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state: MutableStateFlow<CreateTaskUiState>

    init {
        val form = template?.let { TaskForm.from(it, today, patient) } ?: TaskForm.blank(today, patient)
        _state =
            MutableStateFlow(
                CreateTaskUiState(
                    form = form,
                    initial = form,
                    templateName = template?.name,
                    intervalAdjustedTo =
                        template?.schedule?.intervalHours?.takeIf {
                            TaskForm.intervalAdjusted(it) && form.mode == ScheduleMode.Even && form.timesPerDay >= 2
                        }?.let { TaskForm.snapInterval(it) },
                ),
            )
    }

    val state: StateFlow<CreateTaskUiState> = _state.asStateFlow()

    fun edit(change: (TaskForm) -> TaskForm) = _state.update { it.copy(form = change(it.form)) }

    fun ui(change: (CreateTaskUiState) -> CreateTaskUiState) = _state.update(change)

    /** Custom mode starts with the current start time when it has no slots yet (flow §7). */
    fun mode(m: ScheduleMode) =
        edit { f ->
            f.copy(
                mode = m,
                customTimes =
                    if (m == ScheduleMode.Custom && f.customTimes.isEmpty()) {
                        listOf(
                            f.startTime,
                        )
                    } else {
                        f.customTimes
                    },
            )
        }

    /** A new slot an hour after the last (the first free quarter hour if that's taken or past midnight). */
    fun addSlot() =
        edit { f ->
            // 15 at most: the button is off then (flow §8 target).
            if (f.customTimes.size >= MAX_TIMES_PER_DAY) return@edit f
            val last = f.customTimes.lastOrNull() ?: f.startTime
            val candidates = (0 until QUARTERS).map { (last.hour * 60 + last.minute + 60 + it * 15) % DAY }
            val free = candidates.map { LocalTime(it / 60, it % 60) }.first { it !in f.customTimes }
            f.copy(customTimes = f.customTimes.withSlot(free) ?: f.customTimes)
        }

    /** Slot [index] set to [time]; a time already in the list says so and changes nothing (flow §8). */
    fun setSlot(
        index: Int,
        time: LocalTime,
    ) {
        val f = _state.value.form
        if (f.customTimes.getOrNull(index) == time) return
        val rest = f.customTimes.filterIndexed { i, _ -> i != index }
        val next = rest.withSlot(time)
        if (next == null) {
            ShellNotice.pending.value = Notice(Res.string.timeAlreadyInList)
        } else {
            edit { it.copy(customTimes = next) }
        }
    }

    /** Never below one slot (flow §7). */
    fun removeSlot(index: Int) =
        edit {
                f ->
            if (f.customTimes.size > 1) {
                f.copy(
                    customTimes =
                        f.customTimes.filterIndexed {
                                i,
                                _,
                            ->
                            i != index
                        },
                )
            } else {
                f
            }
        }

    /** A new start date moves an end date that would now be before it out of the way (flow §8 target). */
    fun startDate(d: LocalDate) =
        _state.update { s ->
            val end = s.form.endDate?.takeIf { it >= d }
            s.copy(form = s.form.copy(startDate = d, endDate = end), dateOpen = null)
        }

    fun endDate(d: LocalDate) = _state.update { it.copy(form = it.form.copy(endDate = d), dateOpen = null) }

    fun openDate(field: TaskDateField?) =
        _state.update { s ->
            val f = s.form
            val month = if (field == TaskDateField.End) f.endDate ?: TaskForm.defaultEnd(f.startDate) else f.startDate
            s.copy(dateOpen = field, month = month)
        }

    private var patientsLoad: Job? = null

    /** The picker over every patient the doctor can assign to, read once (one read at a time). */
    fun openPicker() {
        _state.update { it.copy(picking = true, query = "") }
        if (_state.value.patients == null) loadPatients()
    }

    fun loadPatients() {
        if (patientsLoad?.isActive == true) return
        _state.update { it.copy(patientsError = null) }
        patientsLoad =
            viewModelScope.launch {
                // The doctor's own patients (flow 07/13 §3), not booking's directory of everyone.
                patientsRepo.refreshIfStale().fold(
                    { list ->
                        val options =
                            list.map {
                                PatientOption(
                                    it.id,
                                    it.name,
                                    it.photoUrl,
                                )
                            }.sortedBy { it.name.orEmpty().lowercase() }
                        _state.update { it.copy(patients = options) }
                    },
                    { e ->
                        val banner =
                            CodeBanner(e.toUserMessage(Res.string.failedToLoadPatients), requestId = e.requestId)
                        _state.update { it.copy(patientsError = banner) }
                    },
                )
            }
    }

    fun pickPatient(p: PatientOption) =
        _state.update {
            it.copy(form = it.form.copy(patient = p), picking = false, query = "")
        }

    /** Back: with changes, ask first; true when the screen can close now. */
    fun back(): Boolean {
        val s = _state.value
        if (s.dirty && !s.done && !s.saving) {
            _state.update { it.copy(askDiscard = true) }
            return false
        }
        return true
    }

    fun create() {
        val s = _state.value
        if (s.saving || s.done) return
        _state.update { it.copy(tried = true) }
        val problems = s.form.problems
        // Inline messages for the fields; the two the doctor may not see get a banner too (flow §9).
        when {
            TaskFormProblem.NoPatient in problems -> ShellNotice.pending.value = Notice(Res.string.pleaseSelectPatient)
            TaskFormProblem.NoSlots in problems ->
                ShellNotice.pending.value =
                    Notice(
                        Res.string.customTimesAddAtLeastOne,
                    )
        }
        val request =
            s.form.request() ?: return tracker.track(
                HomeEvents.TaskCreateFailed("invalid", problems.map { it.trackName() }),
            )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.create(request).fold(
                {
                    val f = s.form
                    tracker.track(
                        HomeEvents.TaskCreated(
                            f.category,
                            f.mode,
                            f.slots.size,
                            fromTemplate = s.templateName != null,
                        ),
                    )
                    ShellNotice.pending.value = Notice(Res.string.taskCreated, success = true)
                    _state.update { it.copy(saving = false, done = true) }
                },
                { e ->
                    tracker.track(HomeEvents.TaskCreateFailed(e.toFailureReason()))
                    if (e is ApiException.Forbidden) launch { profile.refresh() }
                    ShellNotice.pending.value =
                        Notice(
                            e.toUserMessage(Res.string.failedToCreateTask),
                            requestId = e.requestId,
                        )
                    _state.update { it.copy(saving = false) }
                },
            )
        }
    }

    private companion object {
        const val QUARTERS = 96
        const val DAY = 24 * 60
    }
}
