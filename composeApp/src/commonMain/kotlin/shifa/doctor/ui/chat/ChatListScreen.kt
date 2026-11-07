package shifa.doctor.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.chat.ActivityTime
import shifa.doctor.data.chat.ConversationSort
import shifa.doctor.data.chat.activityTime
import shifa.doctor.data.chat.unreadLabel
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.UserSearchResult
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.chat
import shifa.doctor.generated.resources.chatEmptyHint
import shifa.doctor.generated.resources.chatPhoto
import shifa.doctor.generated.resources.chatUnreadEmptyHint
import shifa.doctor.generated.resources.couldNotLoad
import shifa.doctor.generated.resources.doctor
import shifa.doctor.generated.resources.document
import shifa.doctor.generated.resources.filterConversations
import shifa.doctor.generated.resources.ic_chat_16
import shifa.doctor.generated.resources.ic_sliders_22
import shifa.doctor.generated.resources.newestFirst
import shifa.doctor.generated.resources.noConversations
import shifa.doctor.generated.resources.noMessages
import shifa.doctor.generated.resources.noUnreadConversations
import shifa.doctor.generated.resources.noUsersFound
import shifa.doctor.generated.resources.oldestFirst
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchDoctorsAndPatients
import shifa.doctor.generated.resources.unreadBadge
import shifa.doctor.generated.resources.unreadOnlyNewest
import shifa.doctor.generated.resources.unreadOnlyOldest
import shifa.doctor.generated.resources.voiceMessage
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.LocalWideLayout
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr

class ChatListActions(
    val refresh: () -> Unit = {},
    val type: (String) -> Unit = {},
    val choosingSort: (Boolean) -> Unit = {},
    val sort: (ConversationSort) -> Unit = {},
    val open: (Conversation) -> Unit = {},
    val startWith: (UserSearchResult) -> Unit = {},
    val retrySearch: () -> Unit = {},
)

/**
 * The Chat tab's root (CHAT-02, flow 07/01, board `DaChat` list pane): read on coming back and every 5 s while in
 * front; a card or a search result opens the thread through [onOpen].
 */
@Composable
fun ChatListScreen(
    onOpen: (Conversation) -> Unit,
    // WEB-07e: the conversation open beside the list (wide screens), marked selected.
    selectedId: Long? = null,
    viewModel: ChatListViewModel = viewModel { ChatListViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.foreground(true)
        onPauseOrDispose { viewModel.foreground(false) }
    }
    LaunchedEffect(state.open) {
        state.open?.let {
            onOpen(it)
            viewModel.opened()
        }
    }
    // The practice zone as the profile brings it (Chat can open before it has).
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val zone = me?.profile?.timeZone
    val clock = remember(zone) { PracticeClock(zone) }
    ChatListContent(
        state,
        ChatListActions(
            { viewModel.refresh(pull = true) },
            viewModel::type,
            viewModel::choosingSort,
            viewModel::sort,
            viewModel::open,
            viewModel::startWith,
            viewModel::retrySearch,
        ),
        clock,
        selectedId,
        viewModel.listState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatListContent(
    state: ChatListUiState,
    actions: ChatListActions,
    clock: PracticeClock,
    selectedId: Long? = null,
    listState: LazyListState = rememberLazyListState(),
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).statusBarsPadding()) {
    Column(
        Modifier.padding(horizontal = ShifaSpacing.Inset.Md).padding(top = ShifaSize.S16, bottom = ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        Text(
            tr(Res.string.chat),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = ShifaColors.Text.Primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaSearchField(state.typed, actions.type, tr(Res.string.searchDoctorsAndPatients), Modifier.weight(1f))
            ShifaIconButton(
                Res.drawable.ic_sliders_22,
                tr(Res.string.filterConversations),
                { actions.choosingSort(true) },
                // A filter other than the default shows (Flutter's arrow never said one was on).
                variant =
                    if (state.sort != ConversationSort.Newest) {
                        ShifaIconButtonVariant.Primary
                    } else {
                        ShifaIconButtonVariant.Secondary
                    },
            )
        }
    }
    PullToRefreshBox(state.refreshing, actions.refresh, Modifier.weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // The floating tab bar covers the last 120 (no bar on wide screens).
            contentPadding =
                PaddingValues(
                    start = ShifaSpacing.Inset.Md,
                    end = ShifaSpacing.Inset.Md,
                    bottom = if (LocalWideLayout.current) ShifaSpacing.Inset.Md else ShifaSize.S120,
                ),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            state.error?.let { e ->
                item {
                    // No list read yet: the failure takes the list's place; over a list, a banner above it.
                    if (state.conversations == null && !state.searching) {
                        ShifaLoadError(tr(e.text), actions.refresh, requestId = e.requestId)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                            codeBanner(e)?.invoke()
                            ShifaLink(tr(Res.string.retry), actions.refresh)
                        }
                    }
                }
            }
            when {
                state.searching -> {
                    val results = state.results
                    when {
                        results == null -> item { ShifaListSkeleton(count = 3) }
                        // A failed search says so, with Retry (not "No users found").
                        state.searchFailed ->
                            item { ShifaLoadError(tr(Res.string.couldNotLoad), actions.retrySearch) }
                        results.isEmpty() -> item { Message(tr(Res.string.noUsersFound), null) }
                        else ->
                            items(
                                results,
                                key = { "u${it.isDoctor}${it.id}" },
                            ) { ResultRow(it) { actions.startWith(it) } }
                    }
                }
                state.conversations == null -> if (state.error == null) item { ShifaListSkeleton(count = 6) }
                state.shown.isEmpty() ->
                    item {
                        if (state.sort.unreadOnly) {
                            Message(tr(Res.string.noUnreadConversations), tr(Res.string.chatUnreadEmptyHint))
                        } else {
                            Message(tr(Res.string.noConversations), tr(Res.string.chatEmptyHint))
                        }
                    }
                else ->
                    items(
                        state.shown,
                        key = { it.id },
                    ) { ConversationRow(it, clock, it.id == selectedId) { actions.open(it) } }
            }
        }
    }
    if (state.choosingSort) SortSheet(state.sort, actions)
}

/** A card (board `DaChat`): photo, name, last message (bold while unread) or "No messages", time and the unread pill. */
@Composable
private fun ConversationRow(
    c: Conversation,
    clock: PracticeClock,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val time = c.lastActivity?.let { timeText(activityTime(it, clock)) }
    val name = c.name.ifBlank { tr(if (c.isDoctor) Res.string.doctor else Res.string.patient) }
    // An attachment's preview is the backend's English word (`lastMessagePreview`): say it in the doctor's language.
    val preview =
        when (c.lastMessage) {
            null -> tr(Res.string.noMessages)
            "Photo" -> tr(Res.string.chatPhoto)
            "Voice message" -> tr(Res.string.voiceMessage)
            "Document" -> tr(Res.string.document)
            else -> c.lastMessage
        }
    val unread = if (c.unread > 0) tr(Res.string.unreadBadge, c.unread.toString()) else null
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, PressVariant.Card, selected = selected)
            .clearAndSetSemantics { contentDescription = listOfNotNull(name, preview, time, unread).joinToString(", ") }
            .padding(ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaAvatar(c.photoUrl, ShifaSize.Touch)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            Text(
                name,
                style = ShifaTextStyles.contextName(),
                color = ShifaColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview,
                style =
                    ShifaTextStyles.metaSmall().let {
                        if (c.unread > 0) {
                            it.copy(
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            it
                        }
                    },
                color = if (c.unread > 0) ShifaColors.Text.Primary else ShifaColors.Text.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            time?.let { Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary) }
            if (c.unread > 0) {
                Text(
                    unreadLabel(c.unread),
                    Modifier
                        .background(ShifaColors.Interactive.Primary, ShifaRadius.Full)
                        .widthIn(min = ShifaSize.S20)
                        .padding(horizontal = ShifaSize.S6),
                    style = ShifaTextStyles.tag().copy(lineHeight = ShifaTextStyles.metaSmall().lineHeight),
                    color = ShifaColors.Text.Inverse,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun timeText(t: ActivityTime) =
    when (t) {
        is ActivityTime.Today -> t.hm
        ActivityTime.Yesterday -> tr(Res.string.yesterday)
        is ActivityTime.Day -> "${t.day} ${tr(shortMonth(t.month))}"
    }

/** A person found: photo, name, "Doctor" or "Patient", a chat icon. */
@Composable
private fun ResultRow(
    u: UserSearchResult,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().pressable(onClick, PressVariant.Card, role = Role.Button).padding(ShifaSize.S10),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    ShifaAvatar(u.photoUrl, ShifaSize.S40)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            u.name.ifBlank { tr(if (u.isDoctor) Res.string.doctor else Res.string.patient) },
            style = ShifaTextStyles.contextName(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            tr(if (u.isDoctor) Res.string.doctor else Res.string.patient),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    Icon(painterResource(Res.drawable.ic_chat_16), null, tint = ShifaColors.Icon.Brand)
}

/** The empty card (board `DaChat`): a title and what to do. */
@Composable
private fun Message(
    title: String,
    hint: String?,
) = Column(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .padding(horizontal = ShifaSize.S20, vertical = ShifaSize.S28),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
) {
    Text(title, style = ShifaTextStyles.contextName(), color = ShifaColors.Text.Primary, textAlign = TextAlign.Center)
    hint?.let {
        Text(it, style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary, textAlign = TextAlign.Center)
    }
}

/** "Filter conversations" (flow 07/01 §3): four choices, the current one checked; the choice lasts the session. */
@Composable
private fun SortSheet(
    current: ConversationSort,
    actions: ChatListActions,
) = ProfileSheet({ actions.choosingSort(false) }) {
    ShifaTray(tr(Res.string.filterConversations), { actions.choosingSort(false) }) {
        val options =
            listOf(
                ConversationSort.Newest to Res.string.newestFirst,
                ConversationSort.Oldest to Res.string.oldestFirst,
                ConversationSort.UnreadNewest to Res.string.unreadOnlyNewest,
                ConversationSort.UnreadOldest to Res.string.unreadOnlyOldest,
            )
        ShifaOptionList(
            options.map { (sort, label) -> OptionItem(tr(label), selected = sort == current) },
            { i -> actions.sort(options[i].first) },
            role = Role.RadioButton,
        )
    }
}
