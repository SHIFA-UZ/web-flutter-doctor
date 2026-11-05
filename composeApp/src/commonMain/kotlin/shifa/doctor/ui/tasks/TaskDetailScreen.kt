package shifa.doctor.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import shifa.doctor.data.model.CheckInStatus
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCheckIn
import shifa.doctor.data.model.TaskInputType
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.schedule.hhmm
import shifa.doctor.data.tasks.effectiveStatus
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.awaitingSubmission
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.boolean
import shifa.doctor.generated.resources.cancelTask
import shifa.doctor.generated.resources.cancelTaskConfirm
import shifa.doctor.generated.resources.category
import shifa.doctor.generated.resources.checkInCompleted
import shifa.doctor.generated.resources.checkInDetails
import shifa.doctor.generated.resources.checkIns
import shifa.doctor.generated.resources.checkInsLoadFailed
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.description
import shifa.doctor.generated.resources.duration
import shifa.doctor.generated.resources.endDate
import shifa.doctor.generated.resources.ic_tasks_22
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.inputLabel
import shifa.doctor.generated.resources.inputType
import shifa.doctor.generated.resources.missed
import shifa.doctor.generated.resources.no
import shifa.doctor.generated.resources.noCheckInsScheduled
import shifa.doctor.generated.resources.noSubmissionReceived
import shifa.doctor.generated.resources.notes
import shifa.doctor.generated.resources.numeric
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientNoSubmissionsYet
import shifa.doctor.generated.resources.patientTimeSuffix
import shifa.doctor.generated.resources.pending
import shifa.doctor.generated.resources.progress
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.scheduled
import shifa.doctor.generated.resources.startDate
import shifa.doctor.generated.resources.status
import shifa.doctor.generated.resources.submission
import shifa.doctor.generated.resources.submittedAt
import shifa.doctor.generated.resources.taskDetails
import shifa.doctor.generated.resources.taskNotFound
import shifa.doctor.generated.resources.taskZoneUnknown
import shifa.doctor.generated.resources.text
import shifa.doctor.generated.resources.value
import shifa.doctor.generated.resources.yes
import shifa.doctor.generated.resources.yesCancel
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaProgressBar
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.tr

class TaskDetailActions(
    val back: () -> Unit = {},
    val reload: () -> Unit = {},
    val openPatient: (Long) -> Unit = {},
    val view: (TaskCheckIn?) -> Unit = {},
    val askCancel: (Boolean) -> Unit = {},
    val cancel: () -> Unit = {},
)

/**
 * One task (TASK-03, flow 07/12, board `DaInbox` "taskDetails"): the info card with its progress, the check-ins
 * (newest first, missed by the patient's clock), a check-in's sheet, and Cancel Task while active.
 */
@Composable
fun TaskDetailScreen(
    id: Long,
    opened: Long,
    onBack: () -> Unit,
    onOpenPatient: (Long) -> Unit,
    viewModel: TaskDetailViewModel = viewModel(key = "task-$id-$opened") { TaskDetailViewModel(id) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read on open and on each return (flow §6: not a 15 s poll).
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.cancelled) { if (state.cancelled) onBack() }
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val practice = me?.profile?.timeZone
    // The practice zone stands in for a missing patient zone; without it, no zone at all (never the phone's).
    val fallback = remember(practice) { practice?.let { runCatching { TimeZone.of(it) }.getOrNull() } }
    TaskDetailContent(
        state,
        TaskDetailActions(
            back = onBack,
            reload = viewModel::load,
            openPatient = onOpenPatient,
            view = viewModel::view,
            askCancel = viewModel::askCancel,
            cancel = viewModel::cancel,
        ),
        fallback,
        currentInstant(),
    )
}

/** [fallback]: the zone for a task without the patient's (the practice zone; null when even that is unknown). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailContent(
    state: TaskDetailUiState,
    actions: TaskDetailActions,
    fallback: TimeZone?,
    now: Instant,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(tr(Res.string.taskDetails), Modifier.statusBarsPadding(), onBack = actions.back)
    val task = state.task
    val error = state.error
    when {
        task == null && state.notFound ->
            EmptyState(tr(Res.string.taskNotFound), Res.drawable.ic_tasks_22, Modifier.padding(ShifaSpacing.Inset.Md)) {
                ShifaSecondaryButton(tr(Res.string.back), actions.back)
            }
        task == null && error != null ->
            EmptyState(
                tr(error.text),
                Res.drawable.ic_tasks_22,
                Modifier.padding(ShifaSpacing.Inset.Md),
                body = error.requestId,
            ) {
                ShifaSecondaryButton(tr(Res.string.retry), actions.reload)
            }
        task == null -> ShifaListSkeleton(Modifier.padding(ShifaSpacing.Inset.Md), count = 4)
        else -> {
            val patientZone = task.patientTimeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() }
            val zone = patientZone ?: fallback
            PullToRefreshBox(state.refreshing, actions.reload, Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ShifaSpacing.Inset.Md),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    if (error != null) item { codeBanner(error) {}?.invoke() }
                    item { Info(task, state.checkIns, zone, now, actions) }
                    item {
                        Text(
                            tr(Res.string.checkIns),
                            Modifier.padding(top = ShifaSize.S8).semantics { heading() },
                            style = ShifaTextStyles.sectionHeading(),
                            color = ShifaColors.Text.Primary,
                        )
                    }
                    // Flow §8: without the patient's zone, times follow the practice's, and the screen says so.
                    if (patientZone == null && fallback != null) {
                        item {
                            Text(
                                tr(Res.string.taskZoneUnknown),
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Secondary,
                            )
                        }
                    }
                    val checks = state.checkIns
                    // A refresh of them failed over an older list: say so, keep the list.
                    if (state.checkInsFailed && checks != null) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    tr(Res.string.checkInsLoadFailed),
                                    Modifier.weight(1f),
                                    style = ShifaTextStyles.metaSmall(),
                                    color = ShifaColors.Text.Error,
                                )
                                ShifaLink(tr(Res.string.retry), actions.reload)
                            }
                        }
                    }
                    when {
                        state.checkInsFailed && checks == null ->
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                                    Text(
                                        tr(Res.string.checkInsLoadFailed),
                                        style = ShifaTextStyles.dialogBody(),
                                        color = ShifaColors.Text.Secondary,
                                    )
                                    ShifaLink(tr(Res.string.retry), actions.reload)
                                }
                            }
                        checks == null -> item { ShifaListSkeleton(count = 3) }
                        checks.isEmpty() ->
                            item {
                                Text(
                                    tr(Res.string.noCheckInsScheduled),
                                    Modifier.fillMaxWidth().background(
                                        ShifaColors.Bg.Surface,
                                        ShifaRadius.ControlLg,
                                    ).padding(ShifaSize.S16),
                                    style = ShifaTextStyles.dialogBody(),
                                    color = ShifaColors.Text.Secondary,
                                )
                            }
                        else ->
                            items(
                                checks,
                                key = { it.id },
                            ) { c -> CheckInRow(task, c, zone, now) { actions.view(c) } }
                    }
                    if (state.canCancel) {
                        item {
                            ShifaSecondaryButton(
                                tr(Res.string.cancelTask),
                                { actions.askCancel(true) },
                                Modifier.fillMaxWidth().padding(top = ShifaSize.S8),
                                destructive = true,
                            )
                        }
                    }
                }
            }
            state.viewing?.let { c -> CheckInSheet(task, c, zone, patientZone != null, now) { actions.view(null) } }
            if (state.askCancel) {
                ProfileSheet(
                    { actions.askCancel(false) },
                ) { CancelTaskDialog(state.cancelling, actions) }
            }
        }
    }
}

/** The board's info card: name and status, the rows (the patient opens their record), the progress block. */
@Composable
private fun Info(
    t: RemoteCareTask,
    checks: List<TaskCheckIn>?,
    zone: TimeZone?,
    now: Instant,
    actions: TaskDetailActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S16),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    val status = t.status.look()
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        Text(
            t.name,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = ShifaColors.Text.Primary,
        )
        ProfileChip(tr(status.label), status.background, status.text)
    }
    InfoRow(
        tr(Res.string.patient),
        t.patientName.ifBlank { tr(Res.string.patient) },
    ) { actions.openPatient(t.patientId) }
    InfoRow(tr(Res.string.category), tr(t.category.label))
    InfoRow(tr(Res.string.inputType), tr(t.inputType.label))
    t.inputLabel?.let { InfoRow(tr(Res.string.inputLabel), it) }
    t.description?.let { InfoRow(tr(Res.string.description), it) }
    InfoRow(tr(Res.string.startDate), taskDate(t.startDate))
    t.endDate?.let { InfoRow(tr(Res.string.endDate), taskDate(it)) }
    taskDays(t)?.let { InfoRow(tr(Res.string.duration), it) }
    val p = t.progress ?: return@Column
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        Row {
            Text(
                tr(Res.string.progress),
                Modifier.weight(1f),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            Text("${p.completed}/${p.total}", style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
        }
        ShifaProgressBar(p.fraction, Modifier.fillMaxWidth())
        Row {
            listOf(
                Triple(p.completed, Res.string.checkInCompleted, ShifaColors.Text.BrandStrong),
                Triple(p.pending, Res.string.pending, ShifaColors.Text.WarningStrong),
                Triple(p.missed, Res.string.missed, ShifaColors.Text.Error),
            ).forEach { (n, label, ink) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), style = ShifaTextStyles.heroNamePhone(), color = ink)
                    Text(tr(label), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                }
            }
        }
        // Nothing submitted while a slot has passed (flow §8 NEW): likely no app or no habit yet.
        val passed =
            p.missed > 0 ||
                checks.orEmpty().any {
                    it.effectiveStatus(now, t.patientTimeZone, zone) == CheckInStatus.Missed
                }
        if (p.completed == 0 && passed) {
            Text(
                tr(Res.string.patientNoSubmissionsYet),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12), verticalAlignment = Alignment.CenterVertically) {
    Text(
        label,
        Modifier.width(ShifaSize.S100),
        style = ShifaTextStyles.dialogBody(),
        color = ShifaColors.Text.Secondary,
    )
    if (onClick != null) {
        ShifaLink(value, onClick)
    } else {
        Text(value, Modifier.weight(1f), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
    }
}

/** "12 Nov · 08:00", the value and notes when done, the status by the patient's clock (board check-in row). */
@Composable
private fun CheckInRow(
    t: RemoteCareTask,
    c: TaskCheckIn,
    zone: TimeZone?,
    now: Instant,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().pressable(
        onClick,
        PressVariant.Card,
    ).padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(scheduledLabel(c.date, c.time), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
        checkInValue(c)?.let { v ->
            Text(
                listOfNotNull(
                    "${valueLabel(t)}: $v",
                    c.notes?.let { "${tr(Res.string.notes)}: $it" },
                ).joinToString(" · "),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                maxLines = 2,
            )
        }
    }
    CheckInPill(c.effectiveStatus(now, t.patientTimeZone, zone))
}

@Composable
private fun CheckInPill(s: CheckInStatus) {
    val (label, bg, ink) =
        when (s) {
            CheckInStatus.Completed ->
                Triple(
                    Res.string.checkInCompleted,
                    ShifaColors.Bg.SuccessSubtle,
                    ShifaColors.Text.BrandStrong,
                )
            CheckInStatus.Pending ->
                Triple(
                    Res.string.pending,
                    ShifaColors.Bg.WarningSubtle,
                    ShifaColors.Text.WarningStrong,
                )
            CheckInStatus.Missed -> Triple(Res.string.missed, ShifaColors.Bg.ErrorSubtle, ShifaColors.Text.Error)
        }
    ProfileChip(tr(label), bg, ink)
}

/** The check-in's sheet (flow §3.5): scheduled, status, then what came in (in the patient's time) or why nothing did. */
@Composable
private fun CheckInSheet(
    t: RemoteCareTask,
    c: TaskCheckIn,
    zone: TimeZone?,
    patientTime: Boolean,
    now: Instant,
    onClose: () -> Unit,
) = ProfileSheet(onClose) { CheckInTray(t, c, zone, patientTime, now, onClose) }

/** The sheet's content (previews draw it without the sheet). */
@Composable
internal fun CheckInTray(
    t: RemoteCareTask,
    c: TaskCheckIn,
    zone: TimeZone?,
    patientTime: Boolean,
    now: Instant,
    onClose: () -> Unit,
) {
    ShifaTray(tr(Res.string.checkInDetails), onClose) {
        // Long notes or large text scroll (flow §8), Close included.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            val status = c.effectiveStatus(now, t.patientTimeZone, zone)
            InfoRow(tr(Res.string.scheduled), scheduledLabel(c.date, c.time))
            InfoRow(
                tr(Res.string.status),
                tr(
                    when (status) {
                        CheckInStatus.Completed -> Res.string.checkInCompleted
                        CheckInStatus.Pending -> Res.string.pending
                        CheckInStatus.Missed -> Res.string.missed
                    },
                ),
            )
            when (status) {
                CheckInStatus.Completed -> {
                    c.completedAt?.let { at ->
                        val local = at.toLocalDateTime(zone ?: TimeZone.UTC)
                        val at = "${taskDate(local.date)} ${minutes(local.time)}"
                        // "(patient time)" only when it is (the practice zone stands in otherwise, said above).
                        InfoRow(
                            tr(Res.string.submittedAt),
                            when {
                                patientTime -> tr(Res.string.patientTimeSuffix, at)
                                zone == null -> "$at UTC"
                                else -> at
                            },
                        )
                    }
                    InfoRow(valueLabel(t), checkInValue(c) ?: "—")
                    c.notes?.let { InfoRow(tr(Res.string.notes), it) }
                }
                CheckInStatus.Missed -> InfoRow(tr(Res.string.submission), tr(Res.string.noSubmissionReceived))
                CheckInStatus.Pending -> InfoRow(tr(Res.string.submission), tr(Res.string.awaitingSubmission))
            }
            ShifaPrimaryButton(tr(Res.string.close), onClose, Modifier.fillMaxWidth())
        }
    }
}

/** "12 Nov · 08:00" (the patient's wall time, as scheduled; no conversion). */
@Composable
private fun scheduledLabel(
    date: LocalDate,
    time: LocalTime?,
) = listOfNotNull("${date.dayOfMonth} ${tr(shortMonth(date.monthNumber))}", time?.let(::minutes)).joinToString(" · ")

@Composable
private fun valueLabel(t: RemoteCareTask) = t.inputLabel ?: tr(Res.string.value)

/** "120" (not Flutter's "120.0"), "36.6", "Yes"/"No", the text; null when nothing came in. */
@Composable
internal fun checkInValue(c: TaskCheckIn): String? =
    when {
        c.numericValue != null -> numberLabel(c.numericValue)
        c.booleanValue != null -> tr(if (c.booleanValue) Res.string.yes else Res.string.no)
        else -> c.textValue
    }

internal fun numberLabel(v: Double): String =
    if (v % 1.0 == 0.0 && kotlin.math.abs(
            v,
        ) < 1e15
    ) {
        v.toLong().toString()
    } else {
        v.toString()
    }

val TaskInputType.label
    get() =
        when (this) {
            TaskInputType.Numeric -> Res.string.numeric
            TaskInputType.Text -> Res.string.text
            TaskInputType.Boolean -> Res.string.boolean
        }

/** "Cancel Task?" (flow 07/12 §7): No, or the destructive "Yes, Cancel" (a spinner while it goes). */
@Composable
internal fun CancelTaskDialog(
    cancelling: Boolean,
    actions: TaskDetailActions,
) = ShifaDialogSheet(
    tr(Res.string.cancelTask),
    tr(Res.string.yesCancel),
    actions.cancel,
    body = tr(Res.string.cancelTaskConfirm),
    tone = DialogTone.Destructive,
    icon = Res.drawable.ic_trash_22,
    cancelText = tr(Res.string.no),
    onCancel = { actions.askCancel(false) },
    confirmLoading = cancelling,
)

/** "02:39" for display: `hhmm` keeps seconds on purpose (schedule round trips); a person reads minutes. */
private fun minutes(t: LocalTime) = hhmm(LocalTime(t.hour, t.minute))
