package shifa.doctor.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.minus
import shifa.doctor.data.chat.ThreadRow
import shifa.doctor.data.chat.threadRows
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.Message
import shifa.doctor.data.model.MessageStatus
import shifa.doctor.data.model.MessageType
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.chatNewMessage
import shifa.doctor.generated.resources.chatNotSent
import shifa.doctor.generated.resources.chatPhoto
import shifa.doctor.generated.resources.chatSayHello
import shifa.doctor.generated.resources.chatStatusDelivered
import shifa.doctor.generated.resources.chatStatusRead
import shifa.doctor.generated.resources.chatStatusSending
import shifa.doctor.generated.resources.chatStatusSent
import shifa.doctor.generated.resources.doctor
import shifa.doctor.generated.resources.document
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.send
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.typeMessage
import shifa.doctor.generated.resources.voiceMessage
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ReceivedShape
import shifa.doctor.ui.widgets.SentShape
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr

class ThreadActions(
    val back: () -> Unit = {},
    val openPatient: (Long) -> Unit = {},
    val draft: (String) -> Unit = {},
    val send: () -> Unit = {},
    val retry: (Long) -> Unit = {},
    val reload: () -> Unit = {},
)

/**
 * A conversation (CHAT-03, flow 07/02, board `DaChat` thread pane), pushed over the list with the tab bar hidden.
 * [known] is the list's card, so the header shows at once.
 */
@Composable
fun ThreadScreen(
    id: Long,
    known: Conversation?,
    onBack: () -> Unit,
    onOpenPatient: (Long) -> Unit,
    // One per conversation for the session: a draft and a failed text survive going back to the list (flow 07/02 §7).
    viewModel: ThreadViewModel = viewModel(key = "thread-$id") { ThreadViewModel(id) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.foreground(true)
        onPauseOrDispose { viewModel.foreground(false) }
    }
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val zone = me?.profile?.timeZone
    val clock = remember(zone) { PracticeClock(zone) }
    ThreadContent(
        state.copy(conversation = state.conversation ?: known),
        ThreadActions(onBack, onOpenPatient, viewModel::draft, viewModel::send, viewModel::retry, viewModel::refresh),
        clock,
    )
}

@Composable
internal fun ThreadContent(
    state: ThreadUiState,
    actions: ThreadActions,
    clock: PracticeClock,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Surface).statusBarsPadding().imePadding()) {
    Header(state.conversation, actions)
    // The board's message area is white, set off from the header and composer by hairlines.
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(ShifaSize.S1, ShifaColors.Border.Default)
            .background(ShifaColors.Bg.Surface),
    ) {
        val messages = state.messages
        when {
            messages == null && state.error == null -> ShifaListSkeleton(Modifier.padding(ShifaSize.S12), count = 5)
            messages == null ->
                Column(Modifier.padding(ShifaSize.S12), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                    state.error?.let { codeBanner(it) {}?.invoke() }
                    ShifaLink(tr(Res.string.retry), actions.reload)
                }
            messages.isEmpty() && state.pending.isEmpty() -> Empty(state.conversation)
            else -> Messages(state, messages, actions, clock)
        }
    }
    Composer(state, actions)
}

/** Back, the photo, the name and "Patient"/"Doctor"; a patient's name is a link to their record (flow 07/02 §7). */
@Composable
private fun Header(
    c: Conversation?,
    actions: ThreadActions,
) = Row(
    Modifier.fillMaxWidth().padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S8),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    verticalAlignment = Alignment.CenterVertically,
) {
    ShifaIconButton(
        Res.drawable.ic_chev_l_16,
        tr(Res.string.back),
        actions.back,
        variant = ShifaIconButtonVariant.Secondary,
    )
    val patient = c != null && !c.isDoctor
    Row(
        Modifier
            .weight(1f)
            .clip(ShifaRadius.ControlMd)
            .then(
                if (patient) {
                    Modifier.clickable(
                        role = Role.Button,
                    ) { actions.openPatient(c!!.participantId) }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaAvatar(c?.photoUrl, ShifaSize.S40)
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            val role = tr(if (c?.isDoctor == true) Res.string.doctor else Res.string.patient)
            Text(
                c?.name?.takeIf { it.isNotBlank() } ?: role,
                style =
                    ShifaTextStyles.contextName().let {
                        if (patient) {
                            it.copy(
                                textDecoration = TextDecoration.Underline,
                            )
                        } else {
                            it
                        }
                    },
                color = if (patient) ShifaColors.Text.BrandStrong else ShifaColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(role, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
    }
}

@Composable
private fun Empty(c: Conversation?) =
    Box(Modifier.fillMaxSize().padding(ShifaSize.S24), Alignment.Center) {
        Text(
            tr(Res.string.chatSayHello, c?.name?.takeIf { it.isNotBlank() } ?: tr(Res.string.patient)),
            style = ShifaTextStyles.bodyText(),
            color = ShifaColors.Text.Secondary,
        )
    }

/**
 * The messages, newest at the bottom and opened there (Flutter opened at the oldest); a divider per practice day.
 * A new message from the other side while scrolled up shows "New message" instead of jumping.
 */
@Composable
private fun Messages(
    state: ThreadUiState,
    messages: List<Message>,
    actions: ThreadActions,
    clock: PracticeClock,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val rows =
        threadRows(
            messages,
            state.pending.map { ThreadRow.Pending(it.localId, it.text, it.at, it.failed) },
            clock,
        ).asReversed()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val maxBubble = maxWidth * BUBBLE_SHARE
    // "New message": an incoming one arrived while the newest wasn't in view.
    var seen by remember { mutableStateOf(state.newestIncoming) }
    val atBottom = list.firstVisibleItemIndex == 0
    LaunchedEffect(state.newestIncoming, atBottom) { if (atBottom) seen = state.newestIncoming }
    // Own sends scroll to them.
    LaunchedEffect(state.pending.size) { if (state.pending.isNotEmpty()) list.animateScrollToItem(0) }
    state.error?.let { Box(Modifier.align(Alignment.TopCenter).padding(ShifaSize.S8)) { codeBanner(it) {}?.invoke() } }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        reverseLayout = true,
        contentPadding = PaddingValues(ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8, Alignment.Bottom),
    ) {
        items(
            rows,
            key = {
                when (it) {
                    is ThreadRow.Day -> "d${it.ordinal}"
                    is ThreadRow.Sent -> "m${it.message.id}"
                    is ThreadRow.Pending -> "p${it.localId}"
                }
            },
        ) { row ->
            when (row) {
                is ThreadRow.Day -> DayDivider(row, clock)
                is ThreadRow.Sent -> MessageRow(row.message, maxBubble, clock)
                is ThreadRow.Pending -> PendingRow(row, maxBubble, clock) { actions.retry(row.localId) }
            }
        }
    }
    if (!atBottom && state.newestIncoming != null && state.newestIncoming != seen) {
        Text(
            "↓ " + tr(Res.string.chatNewMessage),
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ShifaSize.S12)
                .clip(ShifaRadius.Full)
                .background(ShifaColors.Interactive.Primary)
                .clickable(role = Role.Button) { scope.launch { list.animateScrollToItem(0) } }
                .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S6)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = ShifaTextStyles.tag(),
            color = ShifaColors.Text.Inverse,
        )
    }
}

// A bubble is at most this share of the width (board `DaChat`: 82 % on phones).
private const val BUBBLE_SHARE = 0.82f

@Composable
private fun DayDivider(
    row: ThreadRow.Day,
    clock: PracticeClock,
) = Box(Modifier.fillMaxWidth(), Alignment.Center) {
    val today = clock.local(clock.nowInstant()).date
    val label =
        when (row.date) {
            today -> tr(Res.string.today)
            today.minus(kotlinx.datetime.DatePeriod(days = 1)) -> tr(Res.string.yesterday)
            else ->
                "${row.date.dayOfMonth} ${tr(
                    shortMonth(row.date.monthNumber),
                )}" + if (row.date.year != today.year) " ${row.date.year}" else ""
        }
    Text(
        label,
        Modifier.clip(
            ShifaRadius.Full,
        ).background(ShifaColors.Bg.Canvas).padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S2),
        style = ShifaTextStyles.tag(),
        color = ShifaColors.Text.Secondary,
    )
}

/**
 * A message (board `DaChat`): mine on the right on a light grey, theirs on the left on the light teal, with "Doctor"
 * or "Patient" above theirs; the time (practice time) and, on mine, the status tick inside the bubble. Images, voice
 * notes and documents get their own bubbles with CHAT-04…06; until then they show what they are.
 */
@Composable
private fun MessageRow(
    m: Message,
    max: androidx.compose.ui.unit.Dp,
    clock: PracticeClock,
) {
    val time = hm(m.sentAt, clock)
    if (m.type == MessageType.System) {
        return Box(Modifier.fillMaxWidth(), Alignment.Center) {
            SelectionContainer {
                Text(
                    m.text.orEmpty(),
                    style =
                        ShifaTextStyles.metaSmall().copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        ),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
    }
    val role = tr(if (m.fromPatient) Res.string.patient else Res.string.doctor)
    // Read out as words: the type, then its name ("Document, blood_test_results.pdf"), never the icon.
    val spoken =
        when (m.type) {
            MessageType.Text -> m.text.orEmpty()
            MessageType.Image -> listOfNotNull(tr(Res.string.chatPhoto), m.text).joinToString(", ")
            MessageType.Voice -> tr(Res.string.voiceMessage)
            MessageType.Document -> listOfNotNull(tr(Res.string.document), m.fileName).joinToString(", ")
            MessageType.System -> ""
        }
    val body =
        when (m.type) {
            MessageType.Image -> "🖼 " + (m.text ?: tr(Res.string.chatPhoto))
            MessageType.Voice -> "🎤 " + tr(Res.string.voiceMessage)
            MessageType.Document -> "📄 " + (m.fileName ?: tr(Res.string.document))
            else -> spoken
        }
    val status = if (m.isMine) statusLabel(m.status) else null
    // Read out as "Patient, 08:37, …, Read": the bubble says who, when and how it went; a text is its own node after
    // that, so its links stay actionable for screen readers (an attachment says what it is in words, never its icon).
    val context =
        listOfNotNull(if (m.isMine) null else role, time, if (m.type == MessageType.Text) null else spoken, status)
            .joinToString(", ")
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = context },
        horizontalAlignment = if (m.isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
    ) {
        if (!m.isMine) {
            Text(
                role,
                Modifier.clearAndSetSemantics {},
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
        }
        Bubble(m.isMine, max) {
            // Long-press selects and copies (flow 07/02 §7).
            SelectionContainer {
                Text(
                    linkified(body),
                    if (m.type == MessageType.Text) Modifier else Modifier.clearAndSetSemantics {},
                    style = ShifaTextStyles.chatBubble(),
                    color = ShifaColors.Text.Primary,
                )
            }
            Footer(time, if (m.isMine) tick(m.status) else null)
        }
    }
}

/** A text on its way (◷) or failed ("Not sent. Tap to retry", in red under it). */
@Composable
private fun PendingRow(
    p: ThreadRow.Pending,
    max: androidx.compose.ui.unit.Dp,
    clock: PracticeClock,
    onRetry: () -> Unit,
) = Column(
    Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
) {
    val time = hm(p.at, clock)
    val status = tr(if (p.failed) Res.string.chatNotSent else Res.string.chatStatusSending)
    Bubble(true, max, Modifier.clearAndSetSemantics { contentDescription = "$time, ${p.text}, $status" }) {
        SelectionContainer { Text(p.text, style = ShifaTextStyles.chatBubble(), color = ShifaColors.Text.Primary) }
        Footer(time, if (p.failed) "!" else "◷")
    }
    if (p.failed) {
        Text(
            tr(Res.string.chatNotSent),
            Modifier.clip(ShifaRadius.Sm).clickable(role = Role.Button, onClick = onRetry).padding(ShifaSize.S2),
            style = ShifaTextStyles.tag(),
            color = ShifaColors.Text.Error,
        )
    }
}

@Composable
private fun Bubble(
    mine: Boolean,
    max: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) = Column(
    modifier
        .widthIn(max = max)
        .background(
            if (mine) ShifaColors.Alpha.Black5 else ShifaColors.Alpha.Teal25,
            if (mine) SentShape else ReceivedShape,
        )
        .padding(start = ShifaSize.S12, end = ShifaSize.S12, top = ShifaSize.S8, bottom = ShifaSize.S6),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
) { content() }

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Footer(
    time: String,
    tick: String?,
) = Text(
    listOfNotNull(time, tick).joinToString(" "),
    // Said once, in the bubble's own description.
    Modifier.align(Alignment.End).clearAndSetSemantics {},
    style = ShifaTextStyles.caption(),
    color = ShifaColors.Text.Secondary,
)

// The board's ticks: sending ◷, sent ✓, delivered and read ✓✓ (read in brand).
private fun tick(s: MessageStatus) =
    when (s) {
        MessageStatus.Sending -> "◷"
        MessageStatus.Sent -> "✓"
        MessageStatus.Delivered, MessageStatus.Read -> "✓✓"
    }

@Composable
private fun statusLabel(s: MessageStatus) =
    tr(
        when (s) {
            MessageStatus.Sending -> Res.string.chatStatusSending
            MessageStatus.Sent -> Res.string.chatStatusSent
            MessageStatus.Delivered -> Res.string.chatStatusDelivered
            MessageStatus.Read -> Res.string.chatStatusRead
        },
    )

private fun hm(
    at: kotlinx.datetime.Instant,
    clock: PracticeClock,
): String {
    val t = clock.local(at)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

private val URL = Regex("""(https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)

/** Links (`http(s)://…`, `www.…`) underlined and opening in the browser (flow 07/02 §4). */
@Composable
private fun linkified(text: String): AnnotatedString {
    val link =
        TextLinkStyles(SpanStyle(color = ShifaColors.Text.BrandStrong, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        var at = 0
        URL.findAll(text).forEach { m ->
            append(text.substring(at, m.range.first))
            val url = m.value.trimEnd('.', ',', ')', '!', '?')
            val href = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
            withLink(LinkAnnotation.Url(href, link)) { append(url) }
            at = m.range.first + url.length
        }
        append(text.substring(at))
    }
}

/** The composer: the message (up to 4 lines) and Send, off while there is nothing to send (flow 07/02 §9). */
@Composable
private fun Composer(
    state: ThreadUiState,
    actions: ThreadActions,
) = Row(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface)
        .navigationBarsPadding()
        .padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S8),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    verticalAlignment = Alignment.Bottom,
) {
    val style = ShifaTextStyles.chatInput()
    val hint = tr(Res.string.typeMessage)
    BasicTextField(
        state.draft,
        actions.draft,
        Modifier
            .weight(1f)
            .heightIn(min = ShifaSize.Touch)
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Canvas)
            .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12)
            .semantics { contentDescription = hint },
        textStyle = style.copy(color = ShifaColors.Text.Primary),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
