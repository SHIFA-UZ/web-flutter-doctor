package shifa.doctor.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.SignatureState
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentEndedDocumentationSaved
import shifa.doctor.generated.resources.appointmentEndedShort
import shifa.doctor.generated.resources.backToNotes
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.continueWithoutWaiting
import shifa.doctor.generated.resources.dentalLineItems
import shifa.doctor.generated.resources.done
import shifa.doctor.generated.resources.endAnyway
import shifa.doctor.generated.resources.endChecklistChars
import shifa.doctor.generated.resources.endChecklistNone
import shifa.doctor.generated.resources.endChecklistNotRequested
import shifa.doctor.generated.resources.endChecklistProcessing
import shifa.doctor.generated.resources.endChecklistReady
import shifa.doctor.generated.resources.endChecklistRequested
import shifa.doctor.generated.resources.endChecklistSigned
import shifa.doctor.generated.resources.endVisitChecklistAi
import shifa.doctor.generated.resources.endVisitChecklistDental
import shifa.doctor.generated.resources.endVisitChecklistNotes
import shifa.doctor.generated.resources.endVisitChecklistSignature
import shifa.doctor.generated.resources.endVisitNothingToSave
import shifa.doctor.generated.resources.endVisitSub
import shifa.doctor.generated.resources.endVisitTitle
import shifa.doctor.generated.resources.endingAppointment
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.openSummary
import shifa.doctor.generated.resources.retrySummary
import shifa.doctor.generated.resources.signatureNotYet
import shifa.doctor.generated.resources.stepCompleting
import shifa.doctor.generated.resources.stepSavingNotes
import shifa.doctor.generated.resources.stepSavingSummary
import shifa.doctor.generated.resources.stepWaitingAi
import shifa.doctor.generated.resources.waitForSignature
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.seconds

class EndActions(
    val end: () -> Unit = {},
    val close: () -> Unit = {},
    val skipScribe: () -> Unit = {},
    val retrySummary: () -> Unit = {},
    // Done: back to the caller with the result as a notice.
    val finish: () -> Unit = {},
    val openSummary: () -> Unit = {},
)

/**
 * End appointment (APPT-07, flow 05/12, board `DaVisit` "end visit" and "end progress"): first what will be saved
 * (notes, AI notes, dental services, signature; plan, payment and photos join with their tickets), then the steps with a spinner
 * or a check, "Continue without waiting" on the AI step, the result. It can't be closed while it runs.
 */
@Composable
internal fun EndSheet(
    state: VisitUiState,
    notes: VisitNotesState,
    // A dental doctor's record (DENTAL-02); null: no dental row.
    dental: DentalNotesState?,
    schedule: String,
    minutes: Int,
    actions: EndActions,
) {
    val end = state.end
    ProfileSheet(actions.close, confirmHide = { !end.running && !end.completed }) {
        if (end.confirm && !end.running && !end.completed) {
            EndConfirm(state, notes, dental, schedule, minutes, actions)
        } else {
            EndProgress(end, actions)
        }
    }
}

@Composable
internal fun EndConfirm(
    state: VisitUiState,
    notes: VisitNotesState,
    dental: DentalNotesState?,
    schedule: String,
    minutes: Int,
    actions: EndActions,
) {
    val chars = notes.text.body(SoapLabels()).length
    val aiReady = notes.notes.orEmpty().any { it.fromAi } || notes.drafts.orEmpty().isNotEmpty()
    val sig = state.signature
    val nothing = chars == 0 && !aiReady && !state.awaitingScribe && dental?.hasContent != true
    val waiting = sig == SignatureState.Waiting
    ShifaDialogSheet(
        tr(Res.string.endVisitTitle),
        tr(if (waiting) Res.string.endAnyway else Res.string.endVisitTitle),
        actions.end,
        body = tr(Res.string.endVisitSub, state.visit?.patientName.orEmpty(), schedule, minutes),
        tone = DialogTone.Destructive,
        cancelText = tr(if (waiting) Res.string.waitForSignature else Res.string.backToNotes),
        onCancel = actions.close,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            Row(
                Res.string.endVisitChecklistNotes,
                if (chars > 0) tr(Res.string.endChecklistChars, chars) else tr(Res.string.endChecklistNone),
                chars > 0,
            )
            Row(
                Res.string.endVisitChecklistAi,
                tr(
                    when {
                        aiReady -> Res.string.endChecklistReady
                        state.awaitingScribe -> Res.string.endChecklistProcessing
                        else -> Res.string.endChecklistNone
                    },
                ),
                aiReady || state.awaitingScribe,
            )
            dental?.record?.let { r ->
                Row(
                    Res.string.endVisitChecklistDental,
                    if (r.lineCount > 0) {
                        "${r.lineCount} ${tr(Res.string.dentalLineItems)} · ${sums(r.totals)}"
                    } else {
                        tr(Res.string.endChecklistNone)
                    },
                    r.lineCount > 0,
                )
            }
            Row(
                Res.string.endVisitChecklistSignature,
                tr(
                    when (sig) {
                        SignatureState.Signed -> Res.string.endChecklistSigned
                        SignatureState.Waiting -> Res.string.endChecklistRequested
                        SignatureState.None -> Res.string.endChecklistNotRequested
                    },
                ),
                sig == SignatureState.Signed,
            )
            if (nothing) {
                Text(
                    tr(Res.string.endVisitNothingToSave),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Warning,
                )
            }
            if (waiting) {
                Text(
                    tr(Res.string.signatureNotYet),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            codeBanner(state.end.error) {}?.invoke()
        }
    }
}

@Composable
private fun Row(
    label: org.jetbrains.compose.resources.StringResource,
    value: String,
    on: Boolean,
) = androidx.compose.foundation.layout.Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Text(tr(label), Modifier.weight(1f), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary)
    Text(
        value,
        style = ShifaTextStyles.buttonCompact(),
        color = if (on) ShifaColors.Text.Brand else ShifaColors.Text.Secondary,
    )
}

@Composable
internal fun EndProgress(
    end: EndUiState,
    actions: EndActions,
) {
    val finished = end.done
    val stuck = !end.running && !end.done
    ShifaDialogSheet(
        tr(if (finished) Res.string.appointmentEndedShort else Res.string.endingAppointment),
        tr(
            when {
                finished -> Res.string.done
                end.summaryFailed -> Res.string.retrySummary
                else -> Res.string.close
            },
        ),
        when {
            finished -> actions.finish
            end.summaryFailed -> actions.retrySummary
            else -> actions.close
        },
        confirmEnabled = !end.running,
        confirmLoading = end.running,
        // After a failed summary the visit is completed: Close stays on it, its footer offers Retry summary.
        cancelText =
            if (finished && end.summary != null) {
                tr(Res.string.openSummary)
            } else if (end.summaryFailed) {
                tr(Res.string.close)
            } else {
                null
            },
        onCancel = if (finished) actions.openSummary else actions.close,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            val at = end.steps.indexOf(end.at)
            end.steps.forEachIndexed { i, step ->
                val running = i == at && end.running
                val done = finished || (at >= 0 && i < at) || (stuck && end.completed && step != EndStep.Summary)
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
                ) {
                    Box(Modifier.size(ShifaSize.S20), contentAlignment = Alignment.Center) {
                        when {
                            running ->
                                CircularProgressIndicator(
                                    Modifier.size(ShifaSize.S16),
                                    strokeWidth = ShifaSize.S2,
                                )
                            done -> Icon(painterResource(Res.drawable.ic_check_16), null, tint = ShifaColors.Icon.Brand)
                        }
                    }
                    val label =
                        tr(
                            when (step) {
                                EndStep.Notes -> Res.string.stepSavingNotes
                                EndStep.Complete -> Res.string.stepCompleting
                                EndStep.Scribe -> Res.string.stepWaitingAi
                                EndStep.Summary -> Res.string.stepSavingSummary
                            },
                        )
                    Text(
                        if (step == EndStep.Scribe && running) {
                            "$label (${clockText(
                                end.scribeSeconds.seconds,
                            )})"
                        } else {
                            label
                        },
                        Modifier.weight(1f),
                        style = if (running) ShifaTextStyles.buttonCompact() else ShifaTextStyles.metaSmall(),
                        color = if (running || done) ShifaColors.Text.Primary else ShifaColors.Text.Secondary,
                    )
                    if (step == EndStep.Scribe && running) {
                        ShifaLink(
                            tr(Res.string.continueWithoutWaiting),
                            actions.skipScribe,
                        )
                    }
                }
            }
            if (finished) {
                Text(
                    tr(end.noSummary ?: Res.string.appointmentEndedDocumentationSaved),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Brand,
                )
            }
            codeBanner(end.error) {}?.invoke()
        }
    }
}
