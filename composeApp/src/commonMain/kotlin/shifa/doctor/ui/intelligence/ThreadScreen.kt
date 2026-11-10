package shifa.doctor.ui.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.data.intelligence.IntelCard
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_dictate_18
import shifa.doctor.generated.resources.ic_mic_18
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.generated.resources.ic_stop_filled_16
import shifa.doctor.generated.resources.ic_user_16
import shifa.doctor.generated.resources.intelBannerHandsFreeSubtitle
import shifa.doctor.generated.resources.intelBannerManualSubtitle
import shifa.doctor.generated.resources.intelBannerPauseSend
import shifa.doctor.generated.resources.intelBannerRecording
import shifa.doctor.generated.resources.intelBannerSpeakNow
import shifa.doctor.generated.resources.intelComposerHint
import shifa.doctor.generated.resources.intelComposerListening
import shifa.doctor.generated.resources.intelComposerSpeaking
import shifa.doctor.generated.resources.intelContextPatient
import shifa.doctor.generated.resources.intelDictateHint
import shifa.doctor.generated.resources.intelDiscardTake
import shifa.doctor.generated.resources.intelDisclaimer
import shifa.doctor.generated.resources.intelHandsFreeOff
import shifa.doctor.generated.resources.intelHandsFreeOn
import shifa.doctor.generated.resources.intelInterruptSpeak
import shifa.doctor.generated.resources.intelMicTalk
import shifa.doctor.generated.resources.intelPatient
import shifa.doctor.generated.resources.intelRecordingHold
import shifa.doctor.generated.resources.intelReviewing
import shifa.doctor.generated.resources.intelSendingRecording
import shifa.doctor.generated.resources.intelSpeechRequiresPro
import shifa.doctor.generated.resources.intelStopListening
import shifa.doctor.generated.resources.intelTapToSendRecording
import shifa.doctor.generated.resources.intelTranscribingRecording
import shifa.doctor.generated.resources.intelVoiceStop
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.send
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.transcribing
import shifa.doctor.generated.resources.voiceSpeaking
import shifa.doctor.generated.resources.voiceSpeakingSubtitle
import shifa.doctor.generated.resources.voiceThinkingSubtitle
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.speech.Wave
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.AiResponseText
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.VoiceOrbPanel
import shifa.doctor.ui.widgets.VoicePhase
import shifa.doctor.ui.widgets.sendOnEnter
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

class ThreadActions(
    val draft: (String) -> Unit = {},
    val send: () -> Unit = {},
    val retry: () -> Unit = {},
    // INTEL-09: the voice buttons, the orb panel's Stop, dictation's Discard, hands-free's Turn off.
    val mic: () -> Unit = {},
    val record: () -> Unit = {},
    val stopTake: () -> Unit = {},
    val discard: () -> Unit = {},
    val handsFreeOff: () -> Unit = {},
)

/** Where a saved card sits in the chat, and whether its buttons work (see [ToolCard]). */
data class CardAt(
    val index: Int,
    val live: Boolean,
)

/**
 * The conversation (flow 03/03, board `DaIntel` thread): the doctor's bubbles on the right, Shifa's answers with the
 * sparkle, rendered text and the card slot, "Reviewing…" until the first token, the growing reply, an error with
 * Retry. The list follows the newest content while the doctor is at the bottom. [card] draws a tool card (INTEL-05).
 */
@Composable
internal fun ThreadList(
    chat: ChatSession?,
    state: ThreadUiState,
    actions: ThreadActions,
    modifier: Modifier = Modifier,
    now: Instant,
    // INTEL-09: the voice, whose orb panel ends the list while it's active.
    voice: VoiceUiState = VoiceUiState(),
    listState: LazyListState = rememberLazyListState(),
    card: @Composable (toolName: String?, payload: JsonObject, at: CardAt?) -> Unit = { _, _, _ -> },
) {
    val messages = chat?.messages.orEmpty()
    // Live cards (INTEL-06), none while a turn runs: the newest proposal, and a cancellation the last answer waits on.
    val lastProposal =
        messages.indexOfLast {
                m ->
            m.cardPayload?.let { IntelCard.typeOf(m.toolName, it) in IntelCard.PROPOSALS } == true
        }

    fun live(
        i: Int,
        m: ChatMessage,
    ) = state.asking == null &&
        (
            i == lastProposal || i == messages.lastIndex && IntelCard.typeOf(
                m.toolName,
                m.cardPayload!!,
            ) == "cancel_preview"
        )
    val cardText = rememberCardText()
    val extra = (if (state.asking != null) 1 else 0) + (if (voice.panel) 1 else 0)
    // Follow the newest content while the doctor is at the very bottom (flow 03/03 §3): a scroll the doctor ends above
    // the bottom stops it, one back to the bottom starts it again; a new question always jumps down.
    // Saveable: back from a screen opened over Intelligence, the thread is where the doctor left it.
    var follow by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) follow = !listState.canScrollForward
        }
    }
    LaunchedEffect(messages.size, state.asking, state.streaming.length, state.card, state.error, voice.panel) {
        // Before any suspension: a quick answer changes these keys again and cancels this run, and the next run must
        // still follow (it didn't when the demo answered at once).
        if (state.asking != null && state.streaming.isEmpty()) follow = true
        val total = messages.size + extra
        if (total > 0 && follow) {
            // To the end of the last item, not its start: one long reply is a single item.
            listState.scrollToItem(total - 1, Int.MAX_VALUE)
        }
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        listState,
        contentPadding = PaddingValues(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S16),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
    ) {
        itemsIndexed(messages, key = { i, _ -> i }) { i, m ->
            if (m.isUser) {
                UserBubble(
                    m.content,
                    m.at,
                    now,
                )
            } else {
                // A card's reply line may be the backend's English status or hint (INTEL-10 localises those).
                AssistantBlock(
                    if (m.cardPayload != null) cardText.localizeHint(m.content) else m.content,
                    m.at,
                    now,
                    streaming = false,
                ) {
                    m.cardPayload?.let { card(m.toolName, it, CardAt(i, live(i, m))) }
                }
            }
        }
        if (state.asking != null) {
            item(key = "asking") {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S16)) {
                    UserBubble(state.asking, state.askedAt ?: now, now)
                    when {
                        state.error != null ->
                            Row(
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
                            ) {
                                Text(
                                    tr(state.error.text),
                                    Modifier.weight(1f, fill = false),
                                    style = ShifaTextStyles.buttonCompact(),
                                    color = ShifaColors.Text.Error,
                                )
                                ShifaLink(tr(Res.string.retry), actions.retry)
                            }
                        state.streaming.isEmpty() && state.card == null ->
                            Text(
                                tr(Res.string.intelReviewing),
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = ShifaTextStyles.bodyText().copy(fontStyle = FontStyle.Italic),
                                color = ShifaColors.Text.Secondary,
                            )
                        else ->
                            AssistantBlock(state.streaming, null, now, streaming = true) {
                                state.card?.let { card(state.toolName, it, null) }
                            }
                    }
                }
            }
        }
        if (voice.panel) item(key = "voice") { VoicePanel(voice, actions.stopTake) }
    }
}

@Composable
private fun UserBubble(
    text: String,
    at: Instant,
    now: Instant,
) = Column(
    Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
) {
    Text(stamp(at, now), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Tertiary)
    Text(
        text,
        Modifier
            .fillMaxWidth(USER_BUBBLE_WIDTH)
            .wrapContentWidth(Alignment.End)
            .background(ShifaColors.Alpha.Teal15, ShifaRadius.ControlLg)
            .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S10),
        style = ShifaTextStyles.aiText(),
        color = ShifaColors.Text.Primary,
    )
}

@Composable
private fun AssistantBlock(
    text: String,
    at: Instant?,
    now: Instant,
    streaming: Boolean,
    card: @Composable () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    at?.let { Text(stamp(it, now), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Tertiary) }
    if (text.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            Icon(
                painterResource(Res.drawable.ic_sparkles_16),
                null,
                Modifier.padding(top = ShifaSize.S4),
                tint = ShifaColors.Text.BrandStrong,
            )
            // While streaming the text is plain (half a "**" would flash); the finished reply is rendered.
            if (streaming) {
                Text(text, Modifier.weight(1f), style = ShifaTextStyles.aiText(), color = ShifaColors.Text.Primary)
            } else {
                AiResponseText(text, Modifier.weight(1f))
            }
        }
    }
    Box(Modifier.padding(start = ShifaSize.S26)) { card() }
}

/** "Patient · Karimov Anvar" above the composer while the chat is about a patient (flow 03/03 §3). */
@Composable
internal fun PatientContextLine(chat: ChatSession?) {
    val mark = chat?.patientMark ?: return
    if (!mark.linked) return
    Row(
        Modifier.background(
            ShifaColors.Bg.Canvas,
            ShifaRadius.Full,
        ).padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        Icon(painterResource(Res.drawable.ic_user_16), null, tint = ShifaColors.Icon.Secondary)
        Text(
            mark.name?.let { tr(Res.string.intelContextPatient, it) } ?: tr(Res.string.intelPatient),
            style = ShifaTextStyles.legend(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The composer (text and Send; the mic and dictation come with INTEL-09) and the disclaimer under it. */
@Composable
internal fun Composer(
    chat: ChatSession?,
    state: ThreadUiState,
    actions: ThreadActions,
    bottomInset: Dp? = null,
    voice: VoiceUiState = VoiceUiState(),
    canSpeech: Boolean = false,
) = Column(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface)
        .imePadding()
        .navigationBarsPadding()
        .then(bottomInset?.let { Modifier.padding(bottom = it) } ?: Modifier)
        .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S8),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
) {
    PatientContextLine(chat)
    val dictating = voice.recording && voice.dictating
    val dictationSent = voice.transcribing && voice.dictating
    // Board `DaIntel` dictation: the helper line above the box ("Recording… tap the check…" / "Transcribing…"),
    // and the flow's target Discard (03/05 §7: nothing else drops a take).
    if (dictating || dictationSent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Text(
                tr(if (dictating) Res.string.intelRecordingHold else Res.string.intelTranscribingRecording),
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = ShifaTextStyles.metaSmall().copy(fontWeight = FontWeight.Bold),
                color = ShifaColors.Text.BrandStrong,
            )
            if (dictating) ShifaLink(tr(Res.string.intelDiscardTake), actions.discard)
        }
    }
    Row(
        Modifier.fillMaxWidth().border(
            if (dictating) ShifaSize.S2 else ShifaSize.S1,
            if (dictating) ShifaColors.Border.Focus else ShifaColors.Border.Default,
            ShifaRadius.ControlLg,
        ).padding(ShifaSize.S6),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        val listening = voice.recording && !voice.dictating
        val speaking = voice.phase == VoicePhase.Speaking
        if (dictating) {
            // The take's level as 22 bars across the box (board `DaIntel`).
            Wave(
                voice.level,
                Modifier
                    .weight(1f)
                    .heightIn(min = ShifaSize.S40)
                    .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S6)
                    .height(ShifaSize.S28),
                bars = WAVE_BARS,
                barWidth = null,
            )
        } else {
            // The board's chat-bar textarea: 1–4 lines, no field border of its own (the box is the border).
            val hint =
                tr(
                    when {
                        listening -> Res.string.intelComposerListening
                        speaking -> Res.string.intelComposerSpeaking
                        else -> Res.string.intelComposerHint
                    },
                )
            val style = ShifaTextStyles.chatBubble()
            BasicTextField(
                state.draft,
                actions.draft,
                Modifier
                    .weight(1f)
                    .heightIn(min = ShifaSize.S40)
                    .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S10)
                    .sendOnEnter { if (state.canSend && voice.phase == VoicePhase.Idle) actions.send() }
                    .semantics { contentDescription = hint },
                enabled = !state.busy && !listening && !speaking,
                textStyle = style.copy(color = ShifaColors.Text.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = { if (state.canSend && voice.phase == VoicePhase.Idle) actions.send() },
                    ),
                maxLines = MAX_LINES,
                cursorBrush = SolidColor(ShifaColors.Border.Focus),
                decorationBox = { field ->
                    Box {
                        if (state.draft.isEmpty()) Text(hint, style = style, color = ShifaColors.Text.Tertiary)
                        field()
                    }
                },
            )
        }
        // INTEL-09 (board `DaIntel`): dictation (record → check, a spinner while transcribing), the mic (a red stop
        // while listening, "interrupt" while Shifa speaks), send. Locked below PRO: a tap says so.
        if (dictationSent) {
            Box(Modifier.size(ShifaSize.Touch), contentAlignment = Alignment.Center) {
                ShifaSpinner(ShifaColors.Text.BrandStrong)
            }
        } else {
            ShifaIconButton(
                if (dictating) Res.drawable.ic_check_16 else Res.drawable.ic_dictate_18,
                tr(if (dictating) Res.string.intelTapToSendRecording else Res.string.intelDictateHint),
                actions.record,
                variant = if (dictating) ShifaIconButtonVariant.Primary else ShifaIconButtonVariant.Secondary,
            )
        }
        ShifaIconButton(
            if (listening) Res.drawable.ic_stop_filled_16 else Res.drawable.ic_mic_18,
            tr(
                when {
                    listening -> Res.string.intelStopListening
                    speaking -> Res.string.intelInterruptSpeak
                    else -> Res.string.intelMicTalk
                },
            ),
            actions.mic,
            variant =
                when {
                    listening -> ShifaIconButtonVariant.Danger
                    speaking -> ShifaIconButtonVariant.Primary
                    else -> ShifaIconButtonVariant.Secondary
                },
        )
        ShifaIconButton(
            Res.drawable.ic_send_18,
            tr(Res.string.send),
            actions.send,
            variant = ShifaIconButtonVariant.Primary,
            enabled = state.canSend && voice.phase == VoicePhase.Idle,
        )
    }
    if (voice.handsFree && voice.phase == VoicePhase.Idle) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Text(
                tr(Res.string.intelHandsFreeOn),
                Modifier.weight(1f, fill = false),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            ShifaLink(tr(Res.string.intelHandsFreeOff), actions.handsFreeOff)
        }
    }
    if (!canSpeech) {
        Text(
            tr(Res.string.intelSpeechRequiresPro),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    Text(
        tr(Res.string.intelDisclaimer),
        Modifier.fillMaxWidth(),
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Tertiary,
        textAlign = TextAlign.Center,
    )
}

/**
 * The orb panel at the end of the thread (flow 03/04 §3, board `DaIntel`): while listening, dictating, speaking, or
 * thinking with hands-free on (so the orb doesn't flash away between turns); Stop sends what was captured.
 */
@Composable
internal fun VoicePanel(
    voice: VoiceUiState,
    stop: () -> Unit,
) {
    val listening = voice.recording && !voice.dictating
    val status =
        when {
            voice.dictating && voice.recording -> Res.string.intelBannerRecording
            listening -> if (voice.heard) Res.string.intelBannerPauseSend else Res.string.intelBannerSpeakNow
            voice.phase == VoicePhase.Speaking -> Res.string.voiceSpeaking
            voice.transcribing -> Res.string.transcribing
            else -> Res.string.intelReviewing
        }
    val subtitle =
        when {
            voice.dictating && voice.recording -> Res.string.intelBannerManualSubtitle
            listening -> Res.string.intelBannerHandsFreeSubtitle
            voice.phase == VoicePhase.Speaking -> Res.string.voiceSpeakingSubtitle
            voice.transcribing -> Res.string.intelSendingRecording
            else -> Res.string.voiceThinkingSubtitle
        }
    VoiceOrbPanel(
        voice.phase,
        voice.level,
        tr(status),
        tr(subtitle),
        tr(Res.string.intelVoiceStop),
        onStop = stop.takeIf { voice.recording },
    )
}

/** Whether the orb panel shows (see [VoicePanel]). */
internal val VoiceUiState.panel: Boolean
    get() =
        recording || phase == VoicePhase.Speaking || phase == VoicePhase.Thinking && (handsFree || transcribing)

private const val WAVE_BARS = 22

/** "Today · 09:41", "Yesterday · 18:02", "24 October 2026 · 08:15": the doctor's own clock (flow 03/03 §4). */
@Composable
internal fun stamp(
    at: Instant,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val day =
        when (local.date) {
            today -> tr(Res.string.today)
            kotlinx.datetime.LocalDate.fromEpochDays(today.toEpochDays() - 1) -> tr(Res.string.yesterday)
            else -> longDate(local.date)
        }
    return "$day · ${displayTime(local.time)}"
}

private const val USER_BUBBLE_WIDTH = 0.78f
private const val MAX_LINES = 4
