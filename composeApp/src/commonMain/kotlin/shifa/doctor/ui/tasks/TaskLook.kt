package shifa.doctor.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.active
import shifa.doctor.generated.resources.draft
import shifa.doctor.generated.resources.exercise
import shifa.doctor.generated.resources.expired
import shifa.doctor.generated.resources.ic_activity_18
import shifa.doctor.generated.resources.ic_clipboard_18
import shifa.doctor.generated.resources.ic_heart_18
import shifa.doctor.generated.resources.ic_pill_18
import shifa.doctor.generated.resources.medication
import shifa.doctor.generated.resources.taskCompleted
import shifa.doctor.generated.resources.taskDaysCount
import shifa.doctor.generated.resources.taskOther
import shifa.doctor.generated.resources.taskStatusCancelled
import shifa.doctor.generated.resources.vital
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

/** A status's label and pill colours (board `DaInbox` `stOf`). */
data class TaskStatusLook(
    val label: StringResource,
    val background: Color,
    val text: Color,
)

fun TaskStatus.look(): TaskStatusLook =
    when (this) {
        TaskStatus.Active ->
            TaskStatusLook(
                Res.string.active,
                ShifaColors.Bg.SuccessSubtle,
                ShifaColors.Text.BrandStrong,
            )
        TaskStatus.Completed ->
            TaskStatusLook(
                Res.string.taskCompleted,
                ShifaColors.Bg.InfoSubtle,
                ShifaColors.Text.Info,
            )
        TaskStatus.Expired ->
            TaskStatusLook(
                Res.string.expired,
                ShifaColors.Bg.WarningSubtle,
                ShifaColors.Text.WarningStrong,
            )
        TaskStatus.Cancelled ->
            TaskStatusLook(
                Res.string.taskStatusCancelled,
                ShifaColors.Bg.ErrorSubtle,
                ShifaColors.Text.Error,
            )
        TaskStatus.Draft -> TaskStatusLook(Res.string.draft, ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
    }

/** Localised everywhere (Flutter showed raw codes on phones, flow 07/11 §12). */
val TaskCategory.label: StringResource
    get() =
        when (this) {
            TaskCategory.Vital -> Res.string.vital
            TaskCategory.Exercise -> Res.string.exercise
            TaskCategory.Medication -> Res.string.medication
            TaskCategory.Other -> Res.string.taskOther
        }

/** The board's category icons (`catIcon`): heart, activity, pill, clipboard. */
val TaskCategory.icon: DrawableResource
    get() =
        when (this) {
            TaskCategory.Vital -> Res.drawable.ic_heart_18
            TaskCategory.Exercise -> Res.drawable.ic_activity_18
            TaskCategory.Medication -> Res.drawable.ic_pill_18
            TaskCategory.Other -> Res.drawable.ic_clipboard_18
        }

/** "1 Nov 2026" (a task's dates are calendar days, no zone). */
@Composable
internal fun taskDate(d: LocalDate) = "${d.dayOfMonth} ${tr(shortMonth(d.monthNumber))} ${d.year}"

/** "1 Nov – 13 Nov 2026", or the start alone (flow 07/11 §4: locale dates, not ISO). */
@Composable
internal fun taskRange(t: RemoteCareTask): String {
    val end = t.endDate ?: return taskDate(t.startDate)
    val start =
        if (end.year == t.startDate.year) {
            "${t.startDate.dayOfMonth} ${tr(shortMonth(t.startDate.monthNumber))}"
        } else {
            taskDate(t.startDate)
        }
    return "$start – ${taskDate(end)}"
}

/** "13 days": the dates' span, else the duration the task was given; null when neither is known. */
@Composable
internal fun taskDays(t: RemoteCareTask): String? {
    val n = t.endDate?.let { (it.toEpochDays() - t.startDate.toEpochDays() + 1).toInt() } ?: t.durationDays
    return n?.takeIf { it > 0 }?.let { trPlural(Res.plurals.taskDaysCount, it, it.toString()) }
}
