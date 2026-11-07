package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.generated.resources.ic_mic_16
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.generated.resources.ic_sparkles_18
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.ic_sparkles_40
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

// InsightStates: a suggestion (teal), a risk (red: its primary action is destructive), and a visit briefing.
enum class InsightKind { Suggestion, Risk, Briefing }

/**
 * AiInsightCard (`.dr-insight`, Insight board): the attention list on Home and the visit briefing. [source] is named
 * on the right so the doctor can check it ("from 3 documents"); a briefing lists [bullets] and always ends with
 * [disclaimer]. [actions] are compact buttons; the dismiss link sits at the end.
 */
@Composable
fun AiInsightCard(
    kind: InsightKind,
    title: String,
    source: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    bullets: List<String> = emptyList(),
    disclaimer: String? = null,
    actions: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
) {
    val risk = kind == InsightKind.Risk
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(if (risk) ShifaColors.Bg.ErrorSubtle else ShifaColors.Alpha.Teal15)
            .border(
                ShifaSize.S1,
                if (risk) ShifaColors.Border.Error else ShifaColors.Border.Brand,
                ShifaRadius.ControlMd,
            )
            .padding(ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (risk) Res.drawable.ic_alert_16 else Res.drawable.ic_sparkles_16),
                null,
                tint = if (risk) ShifaColors.Icon.Error else ShifaColors.Icon.Brand,
            )
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = ShifaColors.Text.Primary,
            )
            Text(source, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
        }
        body?.let { Text(it, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary) }
        if (bullets.isNotEmpty()) Bullets(bullets)
        disclaimer?.let { Text(it, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary) }
        if (actions != null || dismiss != null) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                actions?.invoke()
                if (dismiss != null) {
                    Spacer(Modifier.weight(1f))
                    dismiss()
                }
            }
        }
    }
}

/** The row an insight leaves after Accept or Dismiss ("Insight dismissed." + Undo). */
@Composable
fun InsightOutcome(
    text: String,
    undo: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(ShifaColors.Bg.SuccessSubtle)
            .border(ShifaSize.S1, ShifaColors.Border.Brand, ShifaRadius.ControlMd)
            .padding(ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_check_circle_16), null, tint = ShifaColors.Icon.Brand)
        Text(text, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
        ShifaTertiaryButton(undo, onUndo, size = ShifaButtonSize.Compact)
    }
}

/** InsightStates "loading": the card's shape in skeletons while the documents are analysed. */
@Composable
fun AiInsightSkeleton(
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(ShifaColors.Bg.Surface)
            .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.ControlMd)
            .padding(ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaSkeleton(Modifier.size(ShifaSize.S16), ShifaRadius.Full)
            ShifaSkeleton(Modifier.fillMaxWidth(0.55f).height(ShifaSize.S14))
        }
        ShifaSkeleton(Modifier.fillMaxWidth().height(ShifaSize.S12))
        ShifaSkeleton(Modifier.fillMaxWidth(0.8f).height(ShifaSize.S12))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShifaSkeleton(Modifier.size(ShifaSize.S140, ShifaSize.S36), ShifaRadius.ControlMd)
            Spacer(Modifier.weight(1f))
            Text(note, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
        }
    }
}

/** `.dr-orb`: Shifa AI's mark, a teal disc with a halo; [large] is the 88 voice-session orb with two rings. */
@Composable
fun AiOrb(
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    if (large) {
        Box(
            modifier.background(ShifaColors.Alpha.Teal15, ShifaRadius.Full).padding(ShifaSize.S12),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.background(ShifaColors.Alpha.Teal25, ShifaRadius.Full).padding(ShifaSize.S10),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(ShifaSize.S88).background(ShifaColors.Bg.Brand, ShifaRadius.Full),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_sparkles_40), null, tint = ShifaColors.Icon.Inverse)
                }
            }
        }
    } else {
        Box(
            modifier.background(ShifaColors.Alpha.Teal25, ShifaRadius.Full).padding(ShifaSize.S4),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(ShifaSize.S36).background(ShifaColors.Bg.Brand, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_sparkles_18), null, tint = ShifaColors.Icon.Inverse)
            }
        }
    }
}

/**
 * `.dr-voiceorb` (InsightStates "voice"): the voice session — large orb, the phase (Listening… / Thinking… /
 * Speaking…), the transcript or answer, and the actions.
 */
@Composable
fun VoiceSessionCard(
    phase: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.Xl).padding(ShifaSpacing.Inset.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        AiOrb(large = true)
        Text(phase, style = ShifaTextStyles.callStatus(), color = ShifaColors.Text.Primary)
        Text(
            text,
            Modifier.widthIn(max = ShifaSize.S320),
            style = ShifaTextStyles.bodyText(),
            color = ShifaColors.Text.Secondary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) { actions() }
    }
}

/** A message of [AiCopilot]: the doctor's question, or Shifa AI's answer (heading + bullets). */
sealed interface CopilotMessage {
    data class User(
        val text: String,
    ) : CopilotMessage

    data class Answer(
        val heading: String,
        val bullets: List<String>,
    ) : CopilotMessage
}

/**
 * AiCopilot (Copilot board, phone): the full-height tray of Shifa Intelligence. Header (orb, title, subtitle, the
 * [scope] pill, collapse), the [intro] until the first question, the thread, a typing bubble while [streaming],
 * [suggestions] pills, and the composer ([composer], a `ShifaChatBar`-style field with mic and send).
 */
@Composable
fun AiCopilot(
    title: String,
    subtitle: String,
    scope: String,
    intro: String,
    disclaimer: String,
    collapseDescription: String,
    onCollapse: () -> Unit,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    messages: List<CopilotMessage> = emptyList(),
    streaming: String? = null,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
) {
    val shape = RoundedCornerShape(topStart = ShifaSize.S24, topEnd = ShifaSize.S24)
    Column(modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, shape)) {
        Box(
            Modifier
                .padding(top = ShifaSpacing.Inset.Xs)
                .align(Alignment.CenterHorizontally)
                .size(ShifaSize.S36, ShifaSize.S4)
                .background(ShifaColors.Border.Default, ShifaRadius.Full),
        )
        Row(
            Modifier.fillMaxWidth().padding(ShifaSpacing.Inset.Md),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiOrb()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                Text(title, style = ShifaTextStyles.trayTitle(), color = ShifaColors.Text.Primary)
                Text(subtitle, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            }
            StatusBadge(scope)
            ShifaIconButton(
                Res.drawable.ic_chev_d_16,
                collapseDescription,
                onCollapse,
                variant = ShifaIconButtonVariant.Secondary,
            )
        }
        // The thread scrolls inside the sheet and follows the newest message or the streaming answer.
        val scroll = rememberScrollState()
        LaunchedEffect(messages.size, streaming) { scroll.animateScrollTo(scroll.maxValue) }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(scroll)
                .padding(start = ShifaSpacing.Inset.Md, end = ShifaSpacing.Inset.Md, bottom = ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) {
            if (messages.isEmpty() && streaming == null) {
                Text(
                    intro,
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            messages.forEach { m ->
                when (m) {
                    is CopilotMessage.User ->
                        // A user bubble hugs its text at the end of the row, at most 85 % wide.
                        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                m.text,
                                Modifier
                                    .widthIn(max = maxWidth * USER_BUBBLE_SHARE)
                                    .background(ShifaColors.Alpha.Black5, ShifaRadius.ControlLg)
                                    .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S10),
                                style = ShifaTextStyles.dialogBody(),
                                color = ShifaColors.Text.Primary,
                            )
                        }
                    is CopilotMessage.Answer ->
                        Column(Modifier.fillMaxWidth(0.92f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                            Column(
                                Modifier
                                    .background(ShifaColors.Alpha.Teal25, ShifaRadius.ControlLg)
                                    .padding(horizontal = ShifaSize.S14, vertical = ShifaSpacing.Inset.Sm),
                                verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                            ) {
                                Text(
                                    m.heading,
                                    style = ShifaTextStyles.bodyText().copy(fontWeight = FontWeight.Bold),
                                    color = ShifaColors.Text.Primary,
                                )
                                Bullets(m.bullets)
                            }
                            Text(disclaimer, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
                        }
                }
            }
            streaming?.let {
                Row(
                    Modifier
                        .background(ShifaColors.Alpha.Teal25, ShifaRadius.ControlLg)
                        .padding(horizontal = ShifaSize.S14, vertical = ShifaSpacing.Inset.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(it, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S3)) {
                        repeat(
                            3,
                        ) { Box(Modifier.size(ShifaSize.S6).background(ShifaColors.Text.Primary, ShifaRadius.Full)) }
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                ) {
                    suggestions.forEach { s ->
                        Text(
                            s,
                            Modifier
                                .clip(ShifaRadius.Full)
                                .background(ShifaColors.Bg.Surface)
                                .border(ShifaSize.S1, ShifaColors.Border.Brand, ShifaRadius.Full)
                                .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button) { onSuggestion(s) }
                                .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
                            style = ShifaTextStyles.legend(),
                            color = ShifaColors.Text.BrandStrong,
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
        Box(Modifier.padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Sm)) { composer() }
    }
}

/** The copilot's composer: the pill field ([field]), the mic inside it, and the send disc. */
@Composable
fun CopilotComposer(
    field: @Composable () -> Unit,
    micDescription: String,
    sendDescription: String,
    onMic: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sendEnabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(ShifaSize.Touch)
                .clip(ShifaRadius.Full)
                .border(ShifaSize.S2, ShifaColors.Border.Default, ShifaRadius.Full)
                .padding(start = ShifaSpacing.Inset.Md, end = ShifaSize.S6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { field() }
            ShifaIconButton(Res.drawable.ic_mic_16, micDescription, onMic, variant = ShifaIconButtonVariant.Ghost)
        }
        ShifaIconButton(Res.drawable.ic_send_18, sendDescription, onSend, enabled = sendEnabled)
    }
}

/** Copilot "minimised": the 56 orb button at the bottom right; tap to reopen. */
@Composable
fun AiOrbButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier
        .background(ShifaColors.Alpha.Teal25, ShifaRadius.Full)
        .padding(ShifaSize.S6)
        .size(ShifaSize.S56)
        .clip(ShifaRadius.Full)
        .background(ShifaColors.Bg.Brand)
        .pointerHoverIcon(
            PointerIcon.Hand,
        ).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(painterResource(Res.drawable.ic_sparkles_22), description, tint = ShifaColors.Icon.Inverse)
}

@Composable
internal fun Bullets(
    items: List<String>,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    items.forEach { b ->
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            if (icon != null) {
                Icon(painterResource(icon), null, tint = ShifaColors.Icon.Brand)
            } else {
                Text("•", style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
            }
            Text(b, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
        }
    }
}

private const val USER_BUBBLE_SHARE = 0.85f
