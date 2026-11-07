package shifa.doctor.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.activeTasks
import shifa.doctor.generated.resources.allCategories
import shifa.doctor.generated.resources.allStatuses
import shifa.doctor.generated.resources.category
import shifa.doctor.generated.resources.clearFilters
import shifa.doctor.generated.resources.completedTasks
import shifa.doctor.generated.resources.createFirstRemoteTask
import shifa.doctor.generated.resources.createTask
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.noTasksFound
import shifa.doctor.generated.resources.noTasksMatchFilters
import shifa.doctor.generated.resources.overdueTasks
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.refresh
import shifa.doctor.generated.resources.remoteCareTasks
import shifa.doctor.generated.resources.remoteCareTasksSubtitle
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchTasksOrPatients
import shifa.doctor.generated.resources.status
import shifa.doctor.generated.resources.taskProgress
import shifa.doctor.generated.resources.taskTimesPerDay
import shifa.doctor.generated.resources.tasksLoadFailed
import shifa.doctor.generated.resources.useTemplate
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.LocalWideLayout
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaFab
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaProgressBar
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

class TasksActions(
    val refresh: () -> Unit = {},
    val query: (String) -> Unit = {},
    val status: (TaskStatus?) -> Unit = {},
    val category: (TaskCategory?) -> Unit = {},
    val toggleStatus: (TaskStatus) -> Unit = {},
    val clear: () -> Unit = {},
    val dismissError: () -> Unit = {},
    val open: (RemoteCareTask) -> Unit = {},
    val create: () -> Unit = {},
    val templates: () -> Unit = {},
)

/**
 * The Tasks tab (TASK-02, flow 07/11, board `DaInbox` "tasks"): PREMIUM doctors only (the shell keeps the tab closed
 * otherwise). Read each time it shows, so coming back from details, create or templates shows the change.
 */
@Composable
fun TasksScreen(
    onOpen: (RemoteCareTask) -> Unit,
    onCreate: () -> Unit,
    onTemplates: () -> Unit,
    viewModel: TasksViewModel = viewModel { TasksViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    TasksContent(
        state,
        TasksActions(
            refresh = viewModel::refresh,
            query = viewModel::query,
            status = viewModel::status,
            category = viewModel::category,
            toggleStatus = viewModel::toggleStatus,
            clear = viewModel::clearFilters,
            dismissError = viewModel::dismissError,
            open = onOpen,
            create = onCreate,
            templates = onTemplates,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksContent(
    state: TasksUiState,
    actions: TasksActions,
) = Box(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).statusBarsPadding()) {
    val wide = LocalWideLayout.current
    PullToRefreshBox(state.refreshing, actions.refresh, Modifier.fillMaxSize()) {
        LazyColumn(
            // A tablet keeps the board's page width, centred.
            Modifier.fillMaxSize().wrapContentWidth().widthIn(max = ShifaDimens.Layout.Page),
            // The floating tab bar and the docked Create button cover the last part.
            contentPadding =
                PaddingValues(
                    start = if (wide) ShifaSpacing.Inset.Lg else ShifaSpacing.Inset.Md,
                    end = if (wide) ShifaSpacing.Inset.Lg else ShifaSpacing.Inset.Md,
                    top = if (wide) ShifaSpacing.Inset.Lg else ShifaSize.S16,
                    // Wide: no tab bar and Create sits in the header.
                    bottom = if (wide) ShifaSpacing.Inset.Lg else ShifaSize.S120 + ShifaSize.S56,
                ),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            item { Header(actions, wide) }
            item { Stats(state, actions) }
            // No tasks at all: nothing to filter (flow 07/11 §6); a filter that hides everything keeps them.
            if (state.tasks == null || state.tasks.isNotEmpty()) item { Filters(state, actions, wide) }
            // Over a list read before (stale but real); without one, the failure takes the list's place below.
            val error = state.error
            if (state.tasks != null && error != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                        codeBanner(error, actions.dismissError)?.invoke()
                        ShifaLink(tr(Res.string.retry), actions.refresh)
                    }
                }
            }
            val shown = state.shown
            when {
                state.tasks == null && state.failed ->
                    item {
                        ShifaLoadError(
                            tr(error?.text ?: Res.string.tasksLoadFailed),
                            actions.refresh,
                            requestId = error?.requestId,
                        )
                    }
                state.tasks == null -> item { ShifaListSkeleton(count = 4) }
                state.tasks.isEmpty() -> item { EmptyAll(actions) }
                shown.isEmpty() && state.filtered -> item { EmptyFilter(actions) }
                // Wide (DaInbox web): two cards a row.
                wide ->
                    items(shown.chunked(2), key = { it.first().id }) { pair ->
                        Row(
                            Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                        ) {
                            pair.forEach {
                                    t ->
                                Box(Modifier.weight(1f).fillMaxHeight()) { TaskCard(t) { actions.open(t) } }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                else -> items(shown, key = { it.id }) { TaskCard(it) { actions.open(it) } }
            }
        }
    }
    // `.sh-fab--docked`: above the floating tab bar, bottom right (wide screens have Create in the header).
    if (!wide) {
        ShifaFab(
            Res.drawable.ic_plus_16,
            actions.create,
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = ShifaSpacing.Inset.Md, bottom = ShifaSpacing.Inset.Md + ShifaDimens.FabDockedClear),
            text = tr(Res.string.createTask),
        )
    }
}

@Composable
private fun Header(
    actions: TasksActions,
    wide: Boolean,
) = if (wide) {
    // The title with the actions beside it (board) where they fit at the reader's text size, else the actions under it.
    BoxWithConstraints {
        val title = @Composable { m: Modifier ->
            Column(m, verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                Text(
                    tr(Res.string.remoteCareTasks),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    tr(Res.string.remoteCareTasksSubtitle),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
        val buttons = @Composable {
            ShifaIconButton(
                Res.drawable.ic_refresh_22,
                tr(Res.string.refresh),
                actions.refresh,
                variant = ShifaIconButtonVariant.Secondary,
            )
            ShifaSecondaryButton(tr(Res.string.useTemplate), actions.templates)
            ShifaPrimaryButton(tr(Res.string.createTask), actions.create, icon = Res.drawable.ic_plus_16)
        }
        if (maxWidth >= ShifaDimens.Layout.Document && LocalDensity.current.fontScale <= LARGE_TEXT) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title(Modifier.weight(1f))
                buttons()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
                title(Modifier)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) { buttons() }
            }
        }
    }
} else {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            Text(
                tr(Res.string.remoteCareTasks),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = ShifaColors.Text.Primary,
            )
            Text(
                tr(Res.string.remoteCareTasksSubtitle),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Secondary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaIconButton(
                Res.drawable.ic_refresh_22,
                tr(Res.string.refresh),
                actions.refresh,
                variant = ShifaIconButtonVariant.Secondary,
            )
            ShifaSecondaryButton(tr(Res.string.useTemplate), actions.templates, Modifier.weight(1f))
        }
    }
}

/** Three cards (board): a tap filters by its status, again turns it off; "Overdue" counts expired tasks. */
@Composable
private fun Stats(
    state: TasksUiState,
    actions: TasksActions,
) = Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
    listOf(
        Triple(TaskStatus.Active, Res.string.activeTasks, ShifaColors.Text.Info),
        Triple(TaskStatus.Completed, Res.string.completedTasks, ShifaColors.Text.BrandStrong),
        Triple(TaskStatus.Expired, Res.string.overdueTasks, ShifaColors.Text.Error),
    ).forEach { (status, label, ink) ->
        val on = state.status == status
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pressable({ actions.toggleStatus(status) }, PressVariant.Card, selected = on)
                .semantics { selected = on }
                .padding(ShifaSize.S12),
            // A label that wraps ("Completed tasks") keeps the three numbers on one line.
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(tr(label), style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Secondary)
            Text(
                if (state.tasks == null) "—" else state.count(status).toString(),
                style = ShifaTextStyles.kpiValuePhone(),
                color = ink,
            )
        }
    }
}

@Composable
private fun Filters(
    state: TasksUiState,
    actions: TasksActions,
    wide: Boolean,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S12),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    // Wide: the search and both filters in one row (board).
    if (!wide) {
        ShifaSearchField(
            state.query,
            actions.query,
            tr(Res.string.searchTasksOrPatients),
            Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8), verticalAlignment = Alignment.CenterVertically) {
        if (wide) {
            ShifaSearchField(
                state.query,
                actions.query,
                tr(Res.string.searchTasksOrPatients),
                Modifier.weight(SEARCH_WEIGHT),
            )
        }
        var statusOpen by remember { mutableStateOf(false) }
        val statuses = listOf<TaskStatus?>(null) + TaskStatus.entries
        ShifaDropdown(
            tr(Res.string.status),
            statuses.map { s -> s?.let { tr(it.look().label) } ?: tr(Res.string.allStatuses) },
            state.status?.let { tr(it.look().label) } ?: tr(Res.string.allStatuses),
            { i ->
                statusOpen = false
                actions.status(statuses[i])
            },
            statusOpen,
            { statusOpen = it },
            Modifier.weight(1f),
        )
        var categoryOpen by remember { mutableStateOf(false) }
        val categories = listOf<TaskCategory?>(null) + TaskCategory.entries
        ShifaDropdown(
            tr(Res.string.category),
            categories.map { c -> c?.let { tr(it.label) } ?: tr(Res.string.allCategories) },
            state.category?.let { tr(it.label) } ?: tr(Res.string.allCategories),
            { i ->
                categoryOpen = false
                actions.category(categories[i])
            },
            categoryOpen,
            { categoryOpen = it },
            Modifier.weight(1f),
        )
    }
}

/**
 * A task (board): the category tile, name (2 lines) and patient, the status pill, progress "3/6" with its bar, then
 * "3× per day", the dates and the days. Read out as one line (flow §8).
 */
@Composable
private fun TaskCard(
    t: RemoteCareTask,
    onClick: () -> Unit,
) {
    val status = t.status.look()
    val patient = t.patientName.ifBlank { tr(Res.string.patient) }
    val progress = t.progress
    val done = "${progress?.completed ?: 0}/${progress?.total ?: 0}"
    val perDay = trPlural(Res.plurals.taskTimesPerDay, t.schedule.timesPerDay, t.schedule.timesPerDay.toString())
    val range = taskRange(t)
    val days = taskDays(t)
    val statusText = tr(status.label)
    val categoryText = tr(t.category.label)
    val progressLabel = tr(Res.string.taskProgress)
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, PressVariant.Card)
            .clearAndSetSemantics {
                contentDescription =
                    listOfNotNull(
                        t.name,
                        patient,
                        statusText,
                        categoryText,
                        perDay,
                        range,
                        days,
                        "$progressLabel $done",
                    )
                        .joinToString(", ")
            }.padding(ShifaSize.S14),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            Box(
                Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.ControlMd),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(t.category.icon), null, tint = ShifaColors.Text.BrandStrong) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                Text(
                    t.name,
                    style = ShifaTextStyles.contextName(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // No photo in a task: the brand picture (CLAUDE.md "UI layer"; not the board's initials).
                    ShifaAvatar(null, ShifaSize.S20)
                    Text(
                        patient,
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ProfileChip(statusText, status.background, status.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            Row {
                Text(
                    progressLabel,
                    Modifier.weight(1f),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
                Text(done, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary)
            }
            ShifaProgressBar(progress?.fraction ?: 0f, Modifier.fillMaxWidth())
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            listOfNotNull(categoryText, perDay, range, days).forEach {
                Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            }
        }
    }
}

@Composable
private fun EmptyAll(actions: TasksActions) =
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S28),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        Text(tr(Res.string.noTasksFound), style = ShifaTextStyles.contextName(), color = ShifaColors.Text.Primary)
        Text(
            tr(Res.string.createFirstRemoteTask),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Secondary,
        )
        ShifaPrimaryButton(tr(Res.string.createTask), actions.create)
    }

/** A filter that hides everything: not the create card (flow §8), but a way back. */
@Composable
private fun EmptyFilter(actions: TasksActions) =
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        Text(
            tr(Res.string.noTasksMatchFilters),
            style = ShifaTextStyles.contextName(),
            color = ShifaColors.Text.Primary,
        )
        ShifaLink(tr(Res.string.clearFilters), actions.clear)
    }

private const val SEARCH_WEIGHT = 1.6f
private const val LARGE_TEXT = 1.3f
