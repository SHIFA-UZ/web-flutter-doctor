@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.tasks

import androidx.compose.runtime.Composable
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
