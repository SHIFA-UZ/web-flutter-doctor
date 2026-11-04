package shifa.doctor.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.calendar
import shifa.doctor.generated.resources.chat
import shifa.doctor.generated.resources.clinicNavClinic
import shifa.doctor.generated.resources.home
import shifa.doctor.generated.resources.ic_bell_22
import shifa.doctor.generated.resources.ic_calendar_22
import shifa.doctor.generated.resources.ic_calendar_bold_22
import shifa.doctor.generated.resources.ic_chat_22
import shifa.doctor.generated.resources.ic_chat_bold_22
import shifa.doctor.generated.resources.ic_clinic_22
import shifa.doctor.generated.resources.ic_globe_22
import shifa.doctor.generated.resources.ic_home_22
import shifa.doctor.generated.resources.ic_home_bold_22
import shifa.doctor.generated.resources.ic_logout_22
import shifa.doctor.generated.resources.ic_more_22
import shifa.doctor.generated.resources.ic_reports_22
import shifa.doctor.generated.resources.ic_sparkles_18
import shifa.doctor.generated.resources.ic_tasks_22
import shifa.doctor.generated.resources.ic_user_22
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.ic_users_bold_22
import shifa.doctor.generated.resources.language
import shifa.doctor.generated.resources.more
import shifa.doctor.generated.resources.moreOptions
import shifa.doctor.generated.resources.navReports
import shifa.doctor.generated.resources.needsProPlan
import shifa.doctor.generated.resources.notifications
import shifa.doctor.generated.resources.patients
import shifa.doctor.generated.resources.planPremium
import shifa.doctor.generated.resources.planPro
import shifa.doctor.generated.resources.profile
import shifa.doctor.generated.resources.shifaIntelligence
import shifa.doctor.generated.resources.signOut
import shifa.doctor.generated.resources.talkWithAi
import shifa.doctor.generated.resources.tasks
import shifa.doctor.ui.applock.AppLockSection
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.auth.SignOutSheet
import shifa.doctor.ui.auth.SignOutViewModel
import shifa.doctor.ui.auth.short
import shifa.doctor.ui.debug.DebugMenuScreen
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.trackActivity
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.tr

/** The tab's label and bar icons (DaApp `phoneTabs` / `moreRows`). */
internal val ShellTab.label: StringResource
    get() =
        when (this) {
            ShellTab.Home -> Res.string.home
            ShellTab.Calendar -> Res.string.calendar
            ShellTab.Patients -> Res.string.patients
            ShellTab.Chat -> Res.string.chat
            ShellTab.Clinic -> Res.string.clinicNavClinic
            ShellTab.Tasks -> Res.string.tasks
            ShellTab.Reports -> Res.string.navReports
            ShellTab.Notifications -> Res.string.notifications
            ShellTab.Profile -> Res.string.profile
        }

internal val ShellTab.icon: DrawableResource
    get() =
        when (this) {
            ShellTab.Home -> Res.drawable.ic_home_22
            ShellTab.Calendar -> Res.drawable.ic_calendar_22
            ShellTab.Patients -> Res.drawable.ic_users_22
            ShellTab.Chat -> Res.drawable.ic_chat_22
            ShellTab.Clinic -> Res.drawable.ic_clinic_22
            ShellTab.Tasks -> Res.drawable.ic_tasks_22
            ShellTab.Reports -> Res.drawable.ic_reports_22
            ShellTab.Notifications -> Res.drawable.ic_bell_22
            ShellTab.Profile -> Res.drawable.ic_user_22
        }

private val ShellTab.selectedIcon: DrawableResource
    get() =
        when (this) {
            ShellTab.Home -> Res.drawable.ic_home_bold_22
            ShellTab.Calendar -> Res.drawable.ic_calendar_bold_22
            ShellTab.Patients -> Res.drawable.ic_users_bold_22
            ShellTab.Chat -> Res.drawable.ic_chat_bold_22
            else -> icon
        }

/** The doctor's shell (SHELL-01, DaApp phone): the current tab's screen, the floating bar, More, sign-out. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    staff: Boolean = false,
    viewModel: ShellViewModel = viewModel { ShellViewModel(staff) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signOut = viewModel { SignOutViewModel() }
    val question by signOut.state.collectAsStateWithLifecycle()
    // Home's root lets the system close the app; everything else is handled here (flow 01/16 §7).
    BackHandler(enabled = state.moreOpen || state.stack.size > 1 || state.tab != state.home) { viewModel.back() }
    MainScreenContent(
        state,
        ShellActions(
            select = viewModel::select,
            push = viewModel::push,
            back = { viewModel.back() },
            openMore = viewModel::openMore,
            closeMore = viewModel::closeMore,
            signOut = {
                viewModel.closeMore()
                signOut.ask()
            },
        ),
    )
    SignOutSheet(question, signOut::confirm, signOut::cancel)
}

class ShellActions(
    val select: (ShellTab) -> Unit = {},
    val push: (ShellRoute) -> Unit = {},
    val back: () -> Unit = {},
    val openMore: () -> Unit = {},
    val closeMore: () -> Unit = {},
    val signOut: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    state: ShellUiState,
    actions: ShellActions,
) {
    Box(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            ShellNoticeBanner()
            // Each route keeps its own UI state (scroll, fields) while another tab or screen is shown (flow 01/16 §5).
            val saved = rememberSaveableStateHolder()
            Box(Modifier.weight(1f)) {
                saved.SaveableStateProvider("${state.tab.key}/${state.stack.size}/${state.route}") {
                    RouteContent(state.route, actions, state.staff)
                }
            }
        }
        if (state.barVisible) {
            val barTabs = state.barTabs
            val selected = barTabs.indexOf(state.tab).takeIf { it >= 0 } ?: barTabs.size
            // Staff: four tabs, the last one their photo (flow 01/18 §3); doctors: four and More.
            val items =
                barTabs.map {
                    val avatar = state.staff && it == ShellTab.Profile
                    NavBarItem(tr(it.label), it.icon, it.selectedIcon, avatar = avatar, avatarUrl = state.avatarUrl)
                }
            ShifaNavBar(
                if (state.staff) {
                    items
                } else {
                    items +
                        NavBarItem(
                            tr(Res.string.more),
                            Res.drawable.ic_more_22,
                            description = tr(Res.string.moreOptions),
                        )
                },
                selected,
                onSelect = { i -> if (i < barTabs.size) actions.select(barTabs[i]) else actions.openMore() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Xs),
            )
        }
    }
    if (state.moreOpen) {
        ModalBottomSheet(
            actions.closeMore,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            dragHandle = null,
        ) {
            MoreSheetContent(state, actions)
        }
    }
}

/** One route's screen. Each tab's root is a placeholder until its feature ticket builds it. */
@Composable
private fun RouteContent(
    route: ShellRoute,
    actions: ShellActions,
    staff: Boolean,
) = when (route) {
    is ShellRoute.Root ->
        if (route.tab == ShellTab.Profile) {
            ProfilePlaceholder(actions, staff)
        } else {
            TabPlaceholder(route.tab.label, route.tab.icon)
        }
    ShellRoute.Language ->
        Pushed(Res.string.language, actions) {
            TabPlaceholder(Res.string.language, Res.drawable.ic_globe_22)
        }
    ShellRoute.Intelligence ->
        Pushed(Res.string.shifaIntelligence, actions) {
            TabPlaceholder(Res.string.shifaIntelligence, Res.drawable.ic_sparkles_18)
        }
    ShellRoute.Debug -> DebugMenuScreen(onBack = actions.back)
}

@Composable
private fun Pushed(
    title: StringResource,
    actions: ShellActions,
    content: @Composable () -> Unit,
) = Column(Modifier.fillMaxSize()) {
    ShifaSubHeader(tr(title), Modifier.statusBarsPadding(), onBack = actions.back)
    Box(Modifier.weight(1f)) { content() }
}

@Composable
private fun TabPlaceholder(
    title: StringResource,
    icon: DrawableResource,
) = Box(Modifier.fillMaxSize().padding(ShifaSpacing.Inset.Md), contentAlignment = Alignment.Center) {
    EmptyState(tr(title), icon)
}

// PROF-02 replaces this; until then Profile holds the app lock (SET-03's) and, in debug builds, the debug menu.
@Composable
private fun ProfilePlaceholder(
    actions: ShellActions,
    staff: Boolean,
) = Column(
    Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
) {
    AppLockSection()
    // Staff have no More sheet: Language and Sign Out live here (flow 01/18 §3).
    if (staff) {
        ShifaSettingsItem(
            tr(Res.string.language),
            { actions.push(ShellRoute.Language) },
            icon = Res.drawable.ic_globe_22,
        )
        ShifaSettingsItem(
            tr(Res.string.signOut),
            actions.signOut,
            icon = Res.drawable.ic_logout_22,
            destructive = true,
        )
    }
    if (isDebugBuild) ShifaSettingsItem("Debug menu", { actions.push(ShellRoute.Debug) }, divider = false)
}

/**
 * The More sheet (flow 01/17, DaApp `moreOpen`): Shifa Intelligence, the five other destinations (Clinic only with a
 * clinic; Tasks marked Premium below that plan), Language, Sign Out. The location row waits for the calendar (M3).
 */
@Composable
internal fun MoreSheetContent(
    state: ShellUiState,
    actions: ShellActions,
) {
    val shape = RoundedCornerShape(topStart = ShifaSize.S24, topEnd = ShifaSize.S24)
    Column(
        Modifier
            .fillMaxWidth()
            .trackActivity()
            .background(ShifaColors.Bg.Surface, shape)
            .navigationBarsPadding()
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Xs)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = ShifaSpacing.Inset.Sm)
                .size(ShifaSize.S36, ShifaSize.S4)
                .background(ShifaColors.Bg.Disabled, ShifaRadius.Full),
        )
        MoreRow(
            Res.string.shifaIntelligence,
            Res.drawable.ic_sparkles_18,
            sub = tr(if (state.canIntelligence) Res.string.talkWithAi else Res.string.needsProPlan),
            badge = if (state.canIntelligence || state.plan == Plan.Unknown) null else tr(Res.string.planPro),
            ai = true,
            enabled = state.canIntelligence,
        ) { actions.push(ShellRoute.Intelligence) }
        HorizontalDivider(Modifier.padding(vertical = ShifaSize.S6), ShifaSize.S1, ShifaColors.Border.Default)
        ShellTab.entries.filterNot { it.inBar }.forEach { tab ->
            when {
                tab == ShellTab.Clinic && state.hasClinic != true -> Unit
                tab == ShellTab.Tasks && !state.canTasks ->
                    // Shown with its plan, not tappable, once the plan is known (D-03: nothing before that).
                    if (state.plan != Plan.Unknown) {
                        MoreRow(tab.label, tab.icon, badge = tr(Res.string.planPremium), enabled = false) {}
                    }
                else -> MoreRow(tab.label, tab.icon, selected = state.tab == tab) { actions.select(tab) }
            }
        }
        MoreRow(Res.string.language, Res.drawable.ic_globe_22, sub = LocalLanguageSwitch.current.current.short) {
            actions.push(ShellRoute.Language)
        }
        MoreRow(Res.string.signOut, Res.drawable.ic_logout_22, danger = true, onClick = actions.signOut)
    }
}

@Composable
private fun MoreRow(
    title: StringResource,
    icon: DrawableResource,
    sub: String? = null,
    badge: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    ai: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = if (ai) ShifaSize.S60 else ShifaSize.S52)
        .clip(ShifaRadius.ControlMd)
        .background(if (selected) ShifaColors.Bg.SuccessSubtle else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(ShifaSize.S8),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        Modifier
            .size(ShifaSize.S36)
            .then(if (ai) Modifier.background(ShifaColors.Interactive.Primary, ShifaRadius.Full) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            null,
            tint =
                when {
                    ai -> ShifaColors.Icon.Inverse
                    danger -> ShifaColors.Icon.Error
                    else -> ShifaColors.Icon.Primary
                },
        )
    }
    Column(Modifier.weight(1f)) {
        Text(
            tr(title),
            style = ShifaTextStyles.panelTitle(),
            color = if (danger) ShifaColors.Text.Error else ShifaColors.Text.Primary,
        )
        sub?.let { Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary) }
    }
    badge?.let {
        Text(
            it,
            Modifier
                .background(ShifaColors.Bg.Canvas, ShifaRadius.Full)
                .padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S4),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
    }
}
