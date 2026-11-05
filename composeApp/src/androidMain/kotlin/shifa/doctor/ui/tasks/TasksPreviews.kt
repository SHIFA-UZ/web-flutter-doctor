@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskInputType
import shifa.doctor.data.model.TaskProgress
import shifa.doctor.data.model.TaskSchedule
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.tasksLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.theme.PreviewTheme

internal fun previewTask(
    id: Long,
    name: String,
    patient: String,
    category: TaskCategory,
    status: TaskStatus,
    times: Int,
    start: LocalDate,
    end: LocalDate?,
    days: Int?,
    progress: TaskProgress?,
) = RemoteCareTask(
    id, 14, patient, "Asia/Tashkent", name, null, category, status,
    TaskSchedule.of(times, "08:00", null, listOf("08:00", "13:00", "20:00").take(times), emptyList()),
    start, end, days, TaskInputType.Numeric, "Systolic (mmHg)", true, "How do you feel?", null, progress, null, null,
)

internal val previewTasks =
    listOf(
        previewTask(
            501, "Blood pressure diary", "Bekzod Aliyev", TaskCategory.Vital, TaskStatus.Active, 3,
            LocalDate(
                2026,
                11,
                4,
            ),
            LocalDate(2026, 11, 17), null, TaskProgress(6, 3, 2, 1),
        ),
        previewTask(
            502, "Evening metformin after dinner, with a glass of water", "Dilnoza Rahimova", TaskCategory.Medication, TaskStatus.Active, 1,
            LocalDate(
                2026,
                10,
                31,
            ),
            null, 30, TaskProgress(5, 4, 0, 1),
        ),
        previewTask(
            503, "Daily 20-minute walk", "Jasur Karimov", TaskCategory.Exercise, TaskStatus.Completed, 1,
            LocalDate(
                2026,
                10,
                16,
            ),
            LocalDate(2026, 10, 30), null, TaskProgress(14, 14, 0, 0),
        ),
        previewTask(
            504, "Weight", "Malika Tursunova", TaskCategory.Other, TaskStatus.Expired, 1,
            LocalDate(
                2025,
                12,
                20,
            ),
            LocalDate(2026, 1, 10), null, null,
        ),
    )

// TASK-02: the list: counts, filters, an active diary, a long name, a finished walk, an expired one without progress.
@Composable
internal fun TasksPreview() = PreviewTheme { TasksContent(TasksUiState(previewTasks), TasksActions()) }

// TASK-02: the first read.
@Composable
internal fun TasksLoadingPreview() = PreviewTheme { TasksContent(TasksUiState(), TasksActions()) }

// TASK-02: no tasks at all, and a filter that hides everything.
@Composable
internal fun TasksEmptyPreview() = PreviewTheme { TasksContent(TasksUiState(emptyList()), TasksActions()) }

@Composable
internal fun TasksFilterEmptyPreview() =
    PreviewTheme {
        TasksContent(TasksUiState(previewTasks, status = TaskStatus.Cancelled), TasksActions())
    }

// TASK-02: the first read failed: the failure in the list's place, not "No tasks found".
@Composable
internal fun TasksFailedPreview() =
    PreviewTheme {
        TasksContent(
            TasksUiState(
                error =
                    shifa.doctor.ui.emailcode.CodeBanner(
                        shifa.doctor.generated.resources.Res.string.tasksLoadFailed,
                        requestId = "req-7f3a",
                    ),
                failed = true,
            ),
            TasksActions(),
        )
    }

private val previewNow = kotlinx.datetime.Instant.parse("2026-11-05T09:12:00Z")

private val previewChecks =
    listOf(
        shifa.doctor.data.model.TaskCheckIn(
            6,
            LocalDate(
                2026,
                11,
                5,
            ),
            kotlinx.datetime.LocalTime(
                20,
                0,
            ),
            shifa.doctor.data.model.CheckInStatus.Pending, null, null, null, null, null,
        ),
        shifa.doctor.data.model.TaskCheckIn(
            5,
            LocalDate(
                2026,
                11,
                5,
            ),
            kotlinx.datetime.LocalTime(
                13,
                0,
            ),
            shifa.doctor.data.model.CheckInStatus.Pending, null, null, null, null, null,
        ),
        shifa.doctor.data.model.TaskCheckIn(
            4,
            LocalDate(
                2026,
                11,
                5,
            ),
            kotlinx.datetime.LocalTime(
                8,
                0,
            ),
            shifa.doctor.data.model.CheckInStatus.Completed, 124.0, null, null, null,
            kotlinx.datetime.Instant.parse(
                "2026-11-05T03:07:00Z",
            ),
        ),
        shifa.doctor.data.model.TaskCheckIn(
            3,
            LocalDate(
                2026,
                11,
                4,
            ),
            kotlinx.datetime.LocalTime(
                20,
                0,
            ),
            shifa.doctor.data.model.CheckInStatus.Completed, 135.0, null, null, "Felt dizzy after dinner",
            kotlinx.datetime.Instant.parse(
                "2026-11-04T15:10:00Z",
            ),
        ),
        shifa.doctor.data.model.TaskCheckIn(
            2,
            LocalDate(
                2026,
                11,
                4,
            ),
            kotlinx.datetime.LocalTime(
                13,
                0,
            ),
            shifa.doctor.data.model.CheckInStatus.Missed, null, null, null, null, null,
        ),
    )

// TASK-03: an active diary: rows, progress, check-ins newest first (13:00 today already missed for the patient), Cancel.
@Composable
internal fun TaskDetailPreview() =
    PreviewTheme {
        TaskDetailContent(
            TaskDetailUiState(previewTasks[0], previewChecks),
            TaskDetailActions(),
            kotlinx.datetime.TimeZone.of("Asia/Tashkent"),
            previewNow,
        )
    }

// TASK-03: a check-in's sheet (done, with notes, submitted in the patient's time).
@Composable
internal fun TaskCheckInSheetPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            CheckInTray(
                previewTasks[0],
                previewChecks[3],
                kotlinx.datetime.TimeZone.of("Asia/Tashkent"),
                true,
                previewNow,
            ) {}
        }
    }

// TASK-03: Cancel Task asked.
@Composable
internal fun TaskCancelPreview() =
    PreviewTheme {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) { CancelTaskDialog(false, TaskDetailActions()) }
    }

// TASK-03: a task that's gone.
@Composable
internal fun TaskNotFoundPreview() =
    PreviewTheme {
        TaskDetailContent(
            TaskDetailUiState(notFound = true, error = CodeBanner(Res.string.tasksLoadFailed)),
            TaskDetailActions(),
            kotlinx.datetime.TimeZone.of("Asia/Tashkent"),
            previewNow,
        )
    }

private val createToday = LocalDate(2026, 11, 5)

private fun createState(
    form: shifa.doctor.data.tasks.TaskForm,
    tried: Boolean = false,
    template: String? = null,
    adjusted: Int? = null,
) = CreateTaskUiState(form, form, templateName = template, intervalAdjustedTo = adjusted, tried = tried)

// TASK-04: a blank form tried: patient and name required.
@Composable
internal fun CreateTaskErrorsPreview() =
    PreviewTheme {
        CreateTaskContent(
            createState(shifa.doctor.data.tasks.TaskForm.blank(createToday), tried = true),
            createToday,
            CreateTaskActions(),
        )
    }

// TASK-04: from a template, even spacing 15 × every 2 h: 7 slots won't fit before midnight.
@Composable
internal fun CreateTaskEvenPreview() =
    PreviewTheme {
        CreateTaskContent(
            createState(
                shifa.doctor.data.tasks.TaskForm.blank(
                    createToday,
                    shifa.doctor.data.model.PatientOption(14, "Bekzod Aliyev", null),
                ).copy(
                    name = "Blood pressure diary",
                    inputLabel = "Systolic (mmHg)",
                    timesPerDay = 15,
                    intervalHours = 2,
                    endDate = LocalDate(2026, 11, 30),
                ),
                template = "Blood pressure diary",
                adjusted = 2,
            ),
            createToday,
            CreateTaskActions(),
        )
    }

// TASK-04: custom times, three slots, duration instead of an end date.
@Composable
internal fun CreateTaskCustomPreview() =
    PreviewTheme {
        CreateTaskContent(
            createState(
                shifa.doctor.data.tasks.TaskForm.blank(
                    createToday,
                    shifa.doctor.data.model.PatientOption(11, "Dilnoza Rahimova", null),
                ).copy(
                    name = "Medication",
                    category = shifa.doctor.data.model.TaskCategory.Medication,
                    inputType = shifa.doctor.data.model.TaskInputType.Boolean,
                    mode = shifa.doctor.data.tasks.ScheduleMode.Custom,
                    customTimes =
                        listOf(
                            kotlinx.datetime.LocalTime(8, 0),
                            kotlinx.datetime.LocalTime(13, 30),
                            kotlinx.datetime.LocalTime(21, 0),
                        ),
                    useEndDate = false,
                    durationText = "14",
                ),
            ),
            createToday,
            CreateTaskActions(),
        )
    }
