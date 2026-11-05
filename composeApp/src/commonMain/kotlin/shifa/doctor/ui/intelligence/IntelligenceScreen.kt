package shifa.doctor.ui.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.delete
import shifa.doctor.generated.resources.hoursAgo
import shifa.doctor.generated.resources.ic_arrow_l_18
import shifa.doctor.generated.resources.ic_chat_18
import shifa.doctor.generated.resources.ic_download_18
import shifa.doctor.generated.resources.ic_edit_18
import shifa.doctor.generated.resources.ic_history_18
import shifa.doctor.generated.resources.ic_more_v_18
import shifa.doctor.generated.resources.ic_plus_18
import shifa.doctor.generated.resources.ic_trash_18
import shifa.doctor.generated.resources.ic_user_16
import shifa.doctor.generated.resources.intelBackToClinic
import shifa.doctor.generated.resources.intelBackToWelcome
import shifa.doctor.generated.resources.intelDaysAgo
import shifa.doctor.generated.resources.intelDeleteBusyConfirm
import shifa.doctor.generated.resources.intelDeleteConversation
import shifa.doctor.generated.resources.intelDeleteConversationConfirm
import shifa.doctor.generated.resources.intelDownloadChat
import shifa.doctor.generated.resources.intelHistory
import shifa.doctor.generated.resources.intelMoreActions
import shifa.doctor.generated.resources.intelNewConversation
import shifa.doctor.generated.resources.intelNoConversations
import shifa.doctor.generated.resources.intelNoSearchResults
import shifa.doctor.generated.resources.intelPatientConversation
import shifa.doctor.generated.resources.intelRename
import shifa.doctor.generated.resources.intelRenameConversation
import shifa.doctor.generated.resources.intelRenameHint
import shifa.doctor.generated.resources.intelSearchConversations
import shifa.doctor.generated.resources.intelligenceEmptyHint
import shifa.doctor.generated.resources.justNow
import shifa.doctor.generated.resources.minutesAgo
import shifa.doctor.generated.resources.newChat
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.shifaIntelligence
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.rememberFileShare
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

class IntelligenceActions(
    val back: () -> Unit = {},
    val history: (Boolean) -> Unit = {},
    val newConversation: () -> Unit = {},
    val query: (String) -> Unit = {},
    val select: (String) -> Unit = {},
    val menu: (Boolean) -> Unit = {},
    val askRename: (ChatSession?) -> Unit = {},
    val renameText: (String) -> Unit = {},
    val rename: () -> Unit = {},
    val askDelete: (ChatSession?) -> Unit = {},
    val delete: () -> Unit = {},
    val export: () -> Unit = {},
    val dismissBanner: () -> Unit = {},
)

/**
 * Shifa Intelligence in the shell (INTEL-02, flow 03/01–03/02, board `DaIntel`): the app bar, the welcome or the
 * open conversation in [body], and the history, options, rename and delete sheets. [onLeave]: back from the welcome
 * returns to the clinic.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IntelligenceScreen(
    onLeave: () -> Unit,
    // Room for the shell's floating tab bar under the composer.
    bottomInset: Dp? = null,
    viewModel: IntelligenceViewModel = viewModel { IntelligenceViewModel() },
    thread: ThreadViewModel = viewModel { ThreadViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val turn by thread.state.collectAsStateWithLifecycle()
    // A running reply stops when the open chat changes (stopped first, below, so a finishing turn can't reopen the old
    // chat) and when Intelligence leaves the screen (Flutter kept the stream open).
    LaunchedEffect(state.active?.id) { thread.stop() }
    DisposableEffect(thread) { onDispose { thread.stop() } }
    LaunchedEffect(turn.busy) { viewModel.busy(turn.busy) }
    val threadActions = ThreadActions(thread::draft, { thread.send() }, thread::retry)
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    val back = {
        if (state.conversationOpen || turn.asking != null) thread.stop()
        if (viewModel.back()) onLeave()
    }
    BackHandler(onBack = back)
    val share = rememberFileShare()
    IntelligenceContent(
        state,
        IntelligenceActions(
            back = back,
            history = viewModel::history,
            newConversation = {
                thread.stop()
                viewModel.newConversation()
            },
            query = viewModel::query,
            select = {
                if (it != state.active?.id) thread.stop()
                viewModel.select(it)
            },
            menu = viewModel::menu,
            askRename = viewModel::askRename,
            renameText = viewModel::renameText,
            rename = viewModel::rename,
            askDelete = viewModel::askDelete,
            delete = {
                if (state.deleting?.id == state.active?.id) thread.stop()
                viewModel.delete()
            },
            export = {
                viewModel.export()?.let {
                    if (!share(
                            it.fileName,
                            "text/plain",
                            it.bytes,
                        )
                    ) {
                        viewModel.exportFailed()
                    }
                }
            },
            dismissBanner = viewModel::dismissBanner,
        ),
    ) { s ->
        val chat = s.active
        Box(Modifier.weight(1f)) {
            if (s.conversationOpen || turn.asking != null) {
                ThreadList(
                    chat,
                    turn,
                    threadActions,
                    Modifier.fillMaxSize(),
                    currentInstant(),
                ) { tool, card -> ToolCard(tool, card) }
            }
        }
        Composer(chat, turn, threadActions, bottomInset)
    }
}

@Composable
internal fun IntelligenceContent(
    state: IntelligenceUiState,
    actions: IntelligenceActions,
    now: Instant = currentInstant(),
    body: @Composable ColumnScope.(IntelligenceUiState) -> Unit = {},
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Surface)) {
    AppBar(state, actions)
    codeBanner(state.banner, actions.dismissBanner)?.let {
        Box(Modifier.padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S8)) { it() }
    }
    body(state)
    if (state.history) HistorySheet(state, actions, now)
    if (state.menu) state.active?.let { OptionsSheet(it, download = true, actions) { actions.menu(false) } }
    state.renaming?.let { RenameSheet(state, actions) }
    state.deleting?.let { s ->
        val busy = state.busy && s.id == state.active?.id
        ProfileSheet({ actions.askDelete(null) }) {
            ShifaDialogSheet(
                tr(Res.string.intelDeleteConversation),
                tr(Res.string.delete),
                actions.delete,
                body = tr(if (busy) Res.string.intelDeleteBusyConfirm else Res.string.intelDeleteConversationConfirm),
                tone = DialogTone.Destructive,
                cancelText = tr(Res.string.cancel),
                onCancel = { actions.askDelete(null) },
            )
        }
    }
}

/** Back, "Shifa Intelligence" with its subtitle, History, New conversation; the options only in a conversation. */
@Composable
private fun AppBar(
    state: IntelligenceUiState,
    actions: IntelligenceActions,
) = Row(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface)
        .statusBarsPadding()
        .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSize.S8),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    val inThread = state.conversationOpen
    ShifaIconButton(
        Res.drawable.ic_arrow_l_18,
        tr(if (inThread) Res.string.intelBackToWelcome else Res.string.intelBackToClinic),
        actions.back,
        variant = ShifaIconButtonVariant.Secondary,
    )
    Column(Modifier.weight(1f)) {
        Text(
            tr(Res.string.shifaIntelligence),
            Modifier.semantics { heading() },
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            tr(Res.string.intelligenceEmptyHint),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ShifaIconButton(
        Res.drawable.ic_history_18,
        tr(Res.string.intelHistory),
        { actions.history(true) },
        variant = ShifaIconButtonVariant.Secondary,
    )
    ShifaIconButton(
        Res.drawable.ic_plus_18,
        tr(Res.string.intelNewConversation),
        actions.newConversation,
        variant = ShifaIconButtonVariant.Secondary,
    )
    if (inThread) {
        ShifaIconButton(
            Res.drawable.ic_more_v_18,
            tr(Res.string.intelMoreActions),
            { actions.menu(true) },
            variant = ShifaIconButtonVariant.Secondary,
        )
    }
}

/** Flow 03/02: New conversation, search over every message, the chats newest first (the open one tinted). */
@Composable
private fun HistorySheet(
    state: IntelligenceUiState,
    actions: IntelligenceActions,
    now: Instant,
) = ProfileSheet({ actions.history(false) }) {
    HistoryTray(state, actions, now)
}

@Composable
internal fun HistoryTray(
    state: IntelligenceUiState,
    actions: IntelligenceActions,
    now: Instant,
) = ShifaTray(tr(Res.string.intelHistory), { actions.history(false) }, Modifier.fillMaxHeight(HISTORY_HEIGHT)) {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
        ShifaSecondaryButton(tr(Res.string.intelNewConversation), actions.newConversation, Modifier.fillMaxWidth())
        ShifaSearchField(state.query, actions.query, tr(Res.string.intelSearchConversations), Modifier.fillMaxWidth())
        val listed = state.listed
        if (listed.isEmpty()) {
            Text(
                tr(if (state.query.isBlank()) Res.string.intelNoConversations else Res.string.intelNoSearchResults),
                Modifier.fillMaxWidth().padding(ShifaSize.S12),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        }
        var options by remember { mutableStateOf<ChatSession?>(null) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            items(listed, key = { it.id }) { s ->
                SessionTile(s, s.id == state.active?.id, now, { actions.select(s.id) }, { options = s })
            }
        }
        options?.let { s ->
            OptionsSheet(s, download = false, actions) { options = null }
        }
    }
}

/**
 * One chat (flow 03/02 §3): the chat icon, a person icon when it is about a patient, the title (bold when open), how
 * long ago, the preview when it differs from the title, and its options.
 */
@Composable
internal fun SessionTile(
    session: ChatSession,
    active: Boolean,
    now: Instant,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
) {
    val title = sessionTitle(session)
    val preview = session.preview.takeIf { it.isNotEmpty() && it != title }
    val patient = session.patientLinked
    val patientLabel = tr(Res.string.intelPatientConversation)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
            .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.ControlMd)
            .padding(end = ShifaSize.S6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        Row(
            Modifier
                .weight(1f)
                .pressable(onOpen, PressVariant.Row)
                .clearAndSetSemantics { contentDescription = if (patient) "$title. $patientLabel" else title }
                .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) {
            Icon(
                painterResource(Res.drawable.ic_chat_18),
                null,
                Modifier.padding(top = ShifaSize.S2),
                tint = ShifaColors.Icon.Secondary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                // At large text sizes the time goes under the title, which would otherwise be squeezed to nothing
                // (flow 03/02 §8).
                val stacked = LocalDensity.current.fontScale >= LARGE_TEXT
                val time = @Composable {
                    Text(
                        ago(session.updatedAt, now),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Tertiary,
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (patient) Icon(painterResource(Res.drawable.ic_user_16), null, tint = ShifaColors.Icon.Secondary)
                    Text(
                        title,
                        Modifier.weight(1f),
                        style =
                            ShifaTextStyles.chipSlot().copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                        color = ShifaColors.Text.Primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!stacked) time()
                }
                if (stacked) time()
                preview?.let {
                    Text(
                        it,
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ShifaIconButton(
            Res.drawable.ic_more_v_18,
            tr(Res.string.intelMoreActions),
            onOptions,
            variant = ShifaIconButtonVariant.Ghost,
        )
    }
}

/** A chat's options: Download (the open conversation, from the app bar), Rename, Delete. */
@Composable
private fun OptionsSheet(
    session: ChatSession,
    download: Boolean,
    actions: IntelligenceActions,
    close: () -> Unit,
) = ProfileSheet(close) {
    ShifaTray(sessionTitle(session), close) {
        if (download) {
            ShifaSettingsItem(tr(Res.string.intelDownloadChat), actions.export, icon = Res.drawable.ic_download_18)
        }
        ShifaSettingsItem(tr(Res.string.intelRename), {
            close()
            actions.askRename(session)
        }, icon = Res.drawable.ic_edit_18)
        ShifaSettingsItem(
            tr(Res.string.delete),
            {
                close()
                actions.askDelete(session)
            },
            icon = Res.drawable.ic_trash_18,
            destructive = true,
            divider = false,
        )
    }
}

@Composable
private fun RenameSheet(
    state: IntelligenceUiState,
    actions: IntelligenceActions,
) = ProfileSheet({ actions.askRename(null) }) {
    ShifaDialogSheet(
        tr(Res.string.intelRenameConversation),
        tr(Res.string.save),
        actions.rename,
        cancelText = tr(Res.string.cancel),
        onCancel = { actions.askRename(null) },
        confirmEnabled = state.canRename,
    ) {
        ShifaInputField(
            state.renameText,
            actions.renameText,
            tr(Res.string.intelRenameHint),
            Modifier.fillMaxWidth(),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { actions.rename() }),
        )
    }
}

/** The title the doctor sees: "New chat" in the app's language. */
@Composable
internal fun sessionTitle(session: ChatSession): String =
    session.displayTitle.takeUnless { it == ChatSession.UNTITLED } ?: tr(Res.string.newChat)

/**
 * Flow 03/01 §4: "Just now" under a minute, minutes, hours under a day, "Yesterday" under two, "{n}d ago" under a
 * week, else the date; on the phone's clock (the doctor's own history).
 */
@Composable
internal fun ago(
    at: Instant,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val minutes = (now - at).inWholeMinutes
    return when {
        minutes < 1 -> tr(Res.string.justNow)
        minutes < 60 -> trPlural(Res.plurals.minutesAgo, minutes.toInt(), minutes.toString())
        minutes < 24 * 60 -> (minutes / 60).toInt().let { trPlural(Res.plurals.hoursAgo, it, it.toString()) }
        minutes < 2 * 24 * 60 -> tr(Res.string.yesterday)
        minutes < 7 * 24 * 60 -> tr(Res.string.intelDaysAgo, (minutes / (24 * 60)).toString())
        else -> displayDate(at.toLocalDateTime(zone).date)
    }
}

private const val HISTORY_HEIGHT = 0.72f
private const val LARGE_TEXT = 1.5f
