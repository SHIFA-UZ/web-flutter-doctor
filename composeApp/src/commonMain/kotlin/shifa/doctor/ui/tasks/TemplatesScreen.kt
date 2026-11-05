package shifa.doctor.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.TaskTemplate
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.createTask
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_tasks_22
import shifa.doctor.generated.resources.moreCount
import shifa.doctor.generated.resources.noTemplatesBody
import shifa.doctor.generated.resources.noTemplatesTitle
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.taskTimesPerDay
import shifa.doctor.generated.resources.templatesLoadFailed
import shifa.doctor.generated.resources.useTemplate
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

data class TemplatesUiState(
    // Null until the first answer; [failed] when no read has ever worked (never the empty state then).
    val templates: List<TaskTemplate>? = null,
    val refreshing: Boolean = false,
    val error: CodeBanner? = null,
    val failed: Boolean = false,
)

/**
 * "Use Templates" (TASK-05, flow 07/14): `GET /tasks/templates` (the doctor's earlier tasks' settings; there is no
 * template editing anywhere), read on open, return and pull, one read at a time.
 */
class TemplatesViewModel(
    private val repo: TasksRepository = TasksRepository(),
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    private var reading: Job? = null

    fun load() {
        reading?.cancel()
        // A retry after a first failure shows the skeleton again, not the failure with a live Retry.
        _state.update { it.copy(refreshing = it.templates != null, failed = false) }
        reading =
            viewModelScope.launch {
                repo.templates().fold(
                    {
                            list ->
                        _state.update { it.copy(templates = list, refreshing = false, error = null, failed = false) }
                    },
                    { e ->
                        if (e is ApiException.Forbidden) launch { profile.refresh() }
                        val banner =
                            CodeBanner(e.toUserMessage(Res.string.templatesLoadFailed), requestId = e.requestId)
                        _state.update { it.copy(refreshing = false, error = banner, failed = it.templates == null) }
                    },
                )
            }
    }
}

class TemplatesActions(
    val back: () -> Unit = {},
    val reload: () -> Unit = {},
    val open: (TaskTemplate) -> Unit = {},
    val create: () -> Unit = {},
)

/** Opens the create form pre-filled ([onOpen]) or blank from the empty state ([onCreate]). */
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onOpen: (TaskTemplate) -> Unit,
    onCreate: () -> Unit,
    viewModel: TemplatesViewModel = viewModel { TemplatesViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // One form per tap: two quick taps don't push two (flow §8); free again once this screen is back in front.
    var opening by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        opening = false
        viewModel.load()
        onPauseOrDispose {}
    }
    TemplatesContent(
        state,
        TemplatesActions(
            back = onBack,
            reload = viewModel::load,
            open = {
                if (!opening) {
                    opening = true
                    onOpen(it)
                }
            },
            create = {
                if (!opening) {
                    opening = true
                    onCreate()
                }
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatesContent(
    state: TemplatesUiState,
    actions: TemplatesActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(tr(Res.string.useTemplate), Modifier.statusBarsPadding(), onBack = actions.back)
    val templates = state.templates
    val error = state.error
    when {
        templates == null && state.failed ->
            EmptyState(
                tr(error?.text ?: Res.string.templatesLoadFailed),
                Res.drawable.ic_tasks_22,
                Modifier.padding(ShifaSpacing.Inset.Md),
                body = error?.requestId,
            ) { ShifaSecondaryButton(tr(Res.string.retry), actions.reload) }
        templates == null -> ShifaListSkeleton(Modifier.padding(ShifaSpacing.Inset.Md), count = 5)
        else ->
            PullToRefreshBox(state.refreshing, actions.reload, Modifier.weight(1f)) {
                LazyColumn(
                    // A tablet keeps the board's form width, centred.
                    Modifier.fillMaxSize().wrapContentWidth().widthIn(max = ShifaDimens.Layout.FormWide),
                    contentPadding = PaddingValues(ShifaSpacing.Inset.Md),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                ) {
                    if (error != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                                codeBanner(error)?.invoke()
                                ShifaLink(tr(Res.string.retry), actions.reload)
                            }
                        }
                    }
                    if (templates.isEmpty()) {
                        item {
                            EmptyState(
                                tr(Res.string.noTemplatesTitle),
                                Res.drawable.ic_tasks_22,
                                body = tr(Res.string.noTemplatesBody),
                            ) {
                                ShifaPrimaryButton(tr(Res.string.createTask), actions.create)
                            }
                        }
                    }
                    // Names can repeat (flow §8): keyed by position.
                    itemsIndexed(templates, key = { i, _ -> i }) { _, t -> TemplateCard(t) { actions.open(t) } }
                }
            }
    }
}

/** The board's card: the category tile, the name (2 lines), "Vital · 3× per day", the day's times (+n more past 4). */
@Composable
private fun TemplateCard(
    t: TaskTemplate,
    onClick: () -> Unit,
) {
    val category = tr(t.category.label)
    val perDay = trPlural(Res.plurals.taskTimesPerDay, t.schedule.timesPerDay, t.schedule.timesPerDay.toString())
    val times = t.schedule.times
    val shown = times.take(MAX_TIMES).joinToString(" · ", transform = ::displayTime)
    val more = (times.size - MAX_TIMES).takeIf { it > 0 }?.let { tr(Res.string.moreCount, it.toString()) }
    val schedule = listOfNotNull(shown.ifEmpty { null }, more).joinToString(" ")
    val type = tr(t.inputType.label)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, PressVariant.Card)
            .clearAndSetSemantics {
                contentDescription =
                    listOf(
                        t.name,
                        category,
                        perDay,
                        schedule,
                        type,
                    ).filter(String::isNotEmpty).joinToString(", ")
            }
            .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.ControlMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(t.category.icon), null, tint = ShifaColors.Text.BrandStrong)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(
                t.name,
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$category · $perDay · $type", style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            if (schedule.isNotEmpty()) {
                Text(
                    schedule,
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
        Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Secondary)
    }
}

private const val MAX_TIMES = 4
