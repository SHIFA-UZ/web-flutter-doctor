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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.chat.ThreadRow
import shifa.doctor.data.chat.threadRows
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.Message
import shifa.doctor.data.model.MessageStatus
import shifa.doctor.data.model.MessageType
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.attachFile
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.cameraPermissionDenied
import shifa.doctor.generated.resources.cannotOpenDocumentUrl
import shifa.doctor.generated.resources.chatNewMessage
import shifa.doctor.generated.resources.chatNotSent
import shifa.doctor.generated.resources.chatPhoto
import shifa.doctor.generated.resources.chatRecordingInterrupted
import shifa.doctor.generated.resources.chatSayHello
import shifa.doctor.generated.resources.chatStatusDelivered
import shifa.doctor.generated.resources.chatStatusRead
import shifa.doctor.generated.resources.chatStatusSending
import shifa.doctor.generated.resources.chatStatusSent
import shifa.doctor.generated.resources.chatVoicePlayFailed
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.couldNotLoad
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.doctor
import shifa.doctor.generated.resources.document
import shifa.doctor.generated.resources.errorRecordingVoice
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_clip_22
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.microphonePermissionDenied
import shifa.doctor.generated.resources.openSettings
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.send
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.typeMessage
import shifa.doctor.generated.resources.uploadingFile
import shifa.doctor.generated.resources.voiceMessage
import shifa.doctor.generated.resources.voiceRecordingFinishHint
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.navigation.openPermissionSettings
import shifa.doctor.ui.speech.RecordingPill
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ReceivedShape
import shifa.doctor.ui.widgets.SentShape
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaChatVoiceBubble
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.utils.DocumentKinds
import shifa.doctor.utils.Playback
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.rememberAudioPlayer
import shifa.doctor.utils.rememberCameraLauncher
import shifa.doctor.utils.rememberDocumentPickerLauncher
import shifa.doctor.utils.rememberImagePickerLauncher
import shifa.doctor.utils.rememberInAppBrowser
import shifa.doctor.utils.rememberMicPermission
import shifa.doctor.utils.rememberVoiceRecorder
import shifa.doctor.utils.tr

class ThreadActions(
    val back: () -> Unit = {},
    val openPatient: (Long) -> Unit = {},
    val draft: (String) -> Unit = {},
    val send: () -> Unit = {},
    val retry: (Long) -> Unit = {},
    val reload: () -> Unit = {},
    // CHAT-04: the attach sheet's photo rows, and a photo to view full screen.
    val pickImage: () -> Unit = {},
    val takePhoto: () -> Unit = {},
    // CHAT-05: "Select Document"; a PDF opens in the app over the thread (as a photo does), any other document in the
    // system (browser or the app for its type).
    val pickDocument: () -> Unit = {},
    val openFile: (String) -> Unit = {},
    val openPdf: (String, String?) -> Unit = { _, _ -> },
    val closePdf: () -> Unit = {},
    // The attach sheet, the photo viewer and the camera-refused strip (their state is the host's).
    val attach: (Boolean) -> Unit = {},
    val view: (String?) -> Unit = {},
    val dismissCamera: () -> Unit = {},
    // CHAT-06: "Record Voice Message", the bar's ✕ and ✓, and play/pause on a voice note.
    val recordVoice: () -> Unit = {},
    val cancelVoice: () -> Unit = {},
    val sendVoice: () -> Unit = {},
    val togglePlay: (String) -> Unit = {},
    // A recording the app's going to the background stopped (flow 07/04 §7–8): send it or drop it.
    val sendHeld: () -> Unit = {},
    val discardHeld: () -> Unit = {},
)

/** What the thread's host keeps besides the ViewModel's state: the attach sheet, the photo viewed, a refused camera. */
data class ThreadOverlays(
    val attaching: Boolean = false,
    val viewing: String? = null,
    // A refused permission (the camera, the microphone): its strip with Open settings.
    val denied: StringResource? = null,
    // CHAT-06: the recorder bar in place of the composer, its level and seconds; what the player is playing.
    val recording: Boolean = false,
    val level: Float = 0f,
    val seconds: Int = 0,
    val playback: Playback? = null,
    val held: Boolean = false,
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
    var attaching by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by remember { mutableStateOf<StringResource?>(null) }
    // CHAT-06: one recording at a time, dropped when the thread closes; one voice note playing at a time.
    val recorder = rememberVoiceRecorder()
    val recorderScope = rememberCoroutineScope()
    val player = rememberAudioPlayer { ShellNotice.pending.value = Notice(Res.string.chatVoicePlayFailed) }
    var recording by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    var seconds by remember { mutableStateOf(0) }
    // Going to the background (Home, the lock, a call) stops the microphone and keeps the clip to send or drop.
    var held by remember { mutableStateOf<Pair<ByteArray, Int>?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!recording) return@LifecycleEventEffect
        recording = false
        recorderScope.launch {
            val audio = recorder.stop()
            val length = recorder.lastDurationSeconds
            when {
                length < 1 -> Unit
                audio == null -> ShellNotice.pending.value = Notice(Res.string.errorRecordingVoice)
                else -> held = audio to length
            }
        }
    }
    LaunchedEffect(recording) {
        val started = currentInstant()
        while (recording) {
            level = recorder.level()
            seconds = ((currentInstant() - started).inWholeSeconds).toInt()
            delay(RECORDER_TICK)
        }
    }
    val mic =
        rememberMicPermission { granted ->
            if (!granted) {
                denied = Res.string.microphonePermissionDenied
            } else if (recorder.start()) {
                denied = null
                player.playback?.takeIf { it.playing }?.let { player.toggle(it.url) }
                seconds = 0
                recording = true
            } else {
                ShellNotice.pending.value = Notice(Res.string.errorRecordingVoice)
            }
        }
    // CHAT-04: the library and the camera (asking for the camera permission first; refused → a strip with Open settings).
    val pickImage = rememberImagePickerLauncher { it?.let(viewModel::sendImage) }
    val takePhoto =
        rememberCameraLauncher({
            denied = null
            it?.let(viewModel::sendImage)
        }, { denied = Res.string.cameraPermissionDenied })
    // CHAT-05: pdf, doc, docx, jpg, png; a document message opens in the browser (the system shows or hands it on).
    val pickDocument = rememberDocumentPickerLauncher(DocumentKinds.ChatAttachment) { it?.let(viewModel::sendDocument) }
    val browser = rememberInAppBrowser()
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val zone = me?.profile?.timeZone
    val clock = remember(zone) { PracticeClock(zone) }
    ThreadContent(
        state.copy(conversation = state.conversation ?: known),
        ThreadActions(
            back = onBack,
            openPatient = onOpenPatient,
            draft = viewModel::draft,
            send = viewModel::send,
            retry = viewModel::retry,
            reload = viewModel::refresh,
            pickImage = pickImage,
            takePhoto = takePhoto,
            pickDocument = pickDocument,
            // Nothing could open it (no browser, a URL it can't take): say so (flow 07/06 §9).
            openFile = { if (!browser(it)) ShellNotice.pending.value = Notice(Res.string.cannotOpenDocumentUrl) },
            openPdf = viewModel::openPdf,
            closePdf = viewModel::closePdf,
            attach = { attaching = it },
            view = { viewing = it },
            dismissCamera = { denied = null },
            recordVoice = mic,
            cancelVoice = {
                recording = false
                recorder.cancel()
            },
            sendVoice = {
                recording = false
                recorderScope.launch {
                    val audio = recorder.stop()
                    viewModel.sendVoice(audio, recorder.lastDurationSeconds)
                }
            },
            // While recording the microphone has the audio session (iOS: playing would switch it to playback only).
            togglePlay = { if (!recording) player.toggle(it) },
            sendHeld = {
                held?.let { (audio, length) -> viewModel.sendVoice(audio, length) }
                held = null
            },
            discardHeld = { held = null },
        ),
        clock,
        ThreadOverlays(attaching, viewing, denied, recording, level, seconds, player.playback, held != null),
    )
}

@Composable
internal fun ThreadContent(
    state: ThreadUiState,
    actions: ThreadActions,
    clock: PracticeClock,
    overlays: ThreadOverlays = ThreadOverlays(),
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
                ShifaLoadError(
                    tr(state.error?.text ?: Res.string.couldNotLoad),
                    actions.reload,
                    Modifier.padding(ShifaSize.S12),
                    requestId = state.error?.requestId,
                )
            messages.isEmpty() && state.pending.isEmpty() -> Empty(state.conversation)
            else -> Messages(state, messages, actions, clock, overlays.playback) { actions.view(it) }
        }
    }
    // An attachment on its way (flow 07/03 §3: a strip above the composer, Flutter's SnackBars).
    if (state.uploading) {
        Text(
            tr(Res.string.uploadingFile),
            Modifier.fillMaxWidth().background(
                ShifaColors.Bg.Canvas,
            ).padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S6),
            style = ShifaTextStyles.tag(),
            color = ShifaColors.Text.Secondary,
        )
    }
    // The camera was refused (now or for good): say so, with the way to the OS settings (flow 07/05 §6).
    overlays.denied?.let { refused ->
        WarningStrip(tr(refused)) {
            ShifaLink(tr(Res.string.openSettings), ::openPermissionSettings)
            ShifaLink(tr(Res.string.close), actions.dismissCamera)
        }
    }
    if (overlays.held) {
        WarningStrip(tr(Res.string.chatRecordingInterrupted)) {
            ShifaLink(tr(Res.string.send), actions.sendHeld)
            ShifaLink(tr(Res.string.discard), actions.discardHeld)
        }
    }
    if (overlays.recording) {
        // The recorder bar in place of the composer (flow 07/04 §3): the dark pill, then the hint.
        Column(
            Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface).navigationBarsPadding().padding(ShifaSize.S10),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
        ) {
            RecordingPill(
                overlays.level,
                overlays.seconds,
                false,
                actions.cancelVoice,
                actions.sendVoice,
                tr(Res.string.send),
            )
            Text(
                tr(Res.string.voiceRecordingFinishHint),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
        }
    } else {
        Composer(state, actions) { actions.attach(true) }
    }
    if (overlays.attaching) AttachSheet(actions, !overlays.held) { actions.attach(false) }
    overlays.viewing?.let { url -> ImageViewer(url) { actions.view(null) } }
    state.pdf?.let { pdf ->
        PdfViewer(pdf, onClose = actions.closePdf, onRetry = { actions.openPdf(pdf.url, pdf.name) }) {
            actions.closePdf()
            actions.openFile(pdf.url)
        }
    }
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
    playback: Playback?,
    onView: (String) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val rows =
        threadRows(
            messages,
            state.pending.map {
                ThreadRow.Pending(
                    localId = it.localId,
                    text = it.text,
                    at = it.at,
                    failed = it.failed,
                    image = it.image,
                    documentName = if (it.document != null) it.fileName else null,
                    documentSize = it.document?.size?.toLong(),
                    voiceSeconds = if (it.voice != null) it.durationSeconds else null,
                )
            },
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
    state.error?.let { Box(Modifier.align(Alignment.TopCenter).padding(ShifaSize.S8)) { codeBanner(it)?.invoke() } }
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
                is ThreadRow.Sent ->
                    MessageRow(
                        row.message,
                        maxBubble,
                        clock,
                        onView,
                        actions.openFile,
                        actions.openPdf,
                        playback,
                        actions.togglePlay,
                    )
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
    onView: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenPdf: (String, String?) -> Unit,
    playback: Playback?,
    onTogglePlay: (String) -> Unit,
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
    // No emoji in front: the web has no emoji font and drew them as nothing; the words say what it is.
    val body =
        when (m.type) {
            MessageType.Image -> m.text ?: tr(Res.string.chatPhoto)
            MessageType.Voice -> tr(Res.string.voiceMessage)
            MessageType.Document -> m.fileName ?: tr(Res.string.document)
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
        if (m.type == MessageType.Image) {
            return@Column ImageTile(m.fileUrl, null, m.isMine, time, if (m.isMine) tick(m.status) else null) {
                m.fileUrl?.let(onView)
            }
        }
        if (m.type == MessageType.Voice) {
            // One voice note plays at a time (the shared player); the length from the message until the player knows it.
            val mine = playback?.takeIf { it.url == m.fileUrl }
            return@Column ShifaChatVoiceBubble(
                playing = mine?.playing == true,
                position = mine?.position ?: 0,
                duration = mine?.duration?.takeIf { it > 0 } ?: (m.durationSeconds ?: 0),
                onToggle = { m.fileUrl?.let(onTogglePlay) },
                received = !m.isMine,
                time = listOfNotNull(time, if (m.isMine) tick(m.status) else null).joinToString(" "),
            )
        }
        if (m.type == MessageType.Document) {
            return@Column DocumentTile(
                m.fileName,
                m.fileSize,
                m.isMine,
                time,
                if (m.isMine) tick(m.status) else null,
                max,
                // Without a URL the card is there but off, saying "File unavailable" (flow 07/06 §6).
                m.fileUrl?.let { url ->
                    { if (isPdf(m.fileName, url)) onOpenPdf(url, m.fileName) else onOpenFile(url) }
                },
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
    val label =
        when {
            p.image != null -> tr(Res.string.chatPhoto)
            p.voiceSeconds != null -> tr(Res.string.voiceMessage)
            else -> p.text
        }
    if (p.image != null) {
        Box(Modifier.clearAndSetSemantics { contentDescription = "$time, $label, $status" }) {
            ImageTile(null, p.image, true, time, if (p.failed) "!" else "◷") {}
        }
    } else if (p.voiceSeconds != null) {
        Box(Modifier.clearAndSetSemantics { contentDescription = "$time, $label, $status" }) {
            ShifaChatVoiceBubble(false, 0, p.voiceSeconds, {}, time = "$time ${if (p.failed) "!" else "◷"}")
        }
    } else if (p.documentName != null || p.documentSize != null) {
        val doc = tr(Res.string.document)
        Box(
            Modifier.clearAndSetSemantics { contentDescription = "$time, $doc, ${p.documentName.orEmpty()}, $status" },
        ) {
            DocumentTile(p.documentName, p.documentSize, true, time, if (p.failed) "!" else "◷", max, onOpen = {})
        }
    } else {
        Bubble(true, max, Modifier.clearAndSetSemantics { contentDescription = "$time, $label, $status" }) {
            SelectionContainer { Text(p.text, style = ShifaTextStyles.chatBubble(), color = ShifaColors.Text.Primary) }
            Footer(time, if (p.failed) "!" else "◷")
        }
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
    onAttach: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface)
        .navigationBarsPadding()
        .padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S8),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    verticalAlignment = Alignment.Bottom,
) {
    // The paperclip: the attach sheet (CHAT-04 photos; documents and voice notes join with CHAT-05 and CHAT-06).
    ShifaIconButton(
        Res.drawable.ic_clip_22,
        tr(Res.string.attachFile),
        onAttach,
        variant = ShifaIconButtonVariant.Ghost,
        enabled = state.messages != null,
    )
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

// The recorder bar's level and timer refresh (the shared wave reads 60–100 ms steps).

/** A strip above the composer saying what needs the doctor (a refused permission, an interrupted recording). */
@Composable
private fun WarningStrip(
    text: String,
    links: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(
            ShifaColors.Bg.WarningSubtle,
        ).padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S6),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = ShifaTextStyles.tag(), color = ShifaColors.Text.Primary)
        links()
    }
}

private val RECORDER_TICK = kotlin.time.Duration.parse("100ms")
