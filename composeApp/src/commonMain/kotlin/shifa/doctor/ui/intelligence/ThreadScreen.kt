package shifa.doctor.ui.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.generated.resources.ic_user_16
import shifa.doctor.generated.resources.intelComposerHint
import shifa.doctor.generated.resources.intelContextPatient
import shifa.doctor.generated.resources.intelDisclaimer
import shifa.doctor.generated.resources.intelPatient
import shifa.doctor.generated.resources.intelReviewing
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.send
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.AiResponseText
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

class ThreadActions(
    val draft: (String) -> Unit = {},
    val send: () -> Unit = {},
    val retry: () -> Unit = {},
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
    listState: LazyListState = rememberLazyListState(),
    card: @Composable (toolName: String?, payload: JsonObject) -> Unit = { _, _ -> },
) {
    val messages = chat?.messages.orEmpty()
    val extra = if (state.asking != null) 1 else 0
    // Follow the newest content while the doctor is at the very bottom (flow 03/03 §3): a scroll the doctor ends above
    // the bottom stops it, one back to the bottom starts it again; a new question always jumps down.
    var follow by remember(listState) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) follow = !listState.canScrollForward
        }
    }
    LaunchedEffect(messages.size, state.asking, state.streaming.length, state.card, state.error) {
        val total = messages.size + extra
        if (total > 0 && (follow || state.asking != null && state.streaming.isEmpty())) {
            // To the end of the last item, not its start: one long reply is a single item.
            listState.scrollToItem(total - 1, Int.MAX_VALUE)
            follow = true
        }
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        listState,
        contentPadding = PaddingValues(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S16),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
    ) {
        itemsIndexed(messages, key = { i, _ -> i }) { _, m ->
            if (m.isUser) {
                UserBubble(
                    m.content,
                    m.at,
                    now,
                )
            } else {
                AssistantBlock(m.content, m.at, now, streaming = false) {
                    m.cardPayload?.let { card(m.toolName, it) }
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
                                state.card?.let { card(state.toolName, it) }
                            }
                    }
                }
            }
        }
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
    Row(
        Modifier.fillMaxWidth().border(
            ShifaSize.S1,
            ShifaColors.Border.Default,
            ShifaRadius.ControlLg,
        ).padding(ShifaSize.S6),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        // The board's chat-bar textarea: 1–4 lines, no field border of its own (the box is the border).
        val hint = tr(Res.string.intelComposerHint)
        val style = ShifaTextStyles.chatBubble()
        BasicTextField(
            state.draft,
            actions.draft,
            Modifier
                .weight(1f)
                .heightIn(min = ShifaSize.S40)
                .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S10)
                .semantics { contentDescription = hint },
            enabled = !state.busy,
            textStyle = style.copy(color = ShifaColors.Text.Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (state.canSend) actions.send() }),
            maxLines = MAX_LINES,
            cursorBrush = SolidColor(ShifaColors.Border.Focus),
            decorationBox = { field ->
                Box {
                    if (state.draft.isEmpty()) Text(hint, style = style, color = ShifaColors.Text.Tertiary)
                    field()
                }
            },
        )
        ShifaIconButton(
            Res.drawable.ic_send_18,
            tr(Res.string.send),
            actions.send,
            variant = ShifaIconButtonVariant.Primary,
            enabled = state.canSend,
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
