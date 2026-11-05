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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.about
import shifa.doctor.generated.resources.calendar
import shifa.doctor.generated.resources.chat
import shifa.doctor.generated.resources.clinicNavClinic
import shifa.doctor.generated.resources.contactAndClinicAddress
import shifa.doctor.generated.resources.drMedPrefix
import shifa.doctor.generated.resources.extendedProfile
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
import shifa.doctor.generated.resources.personalInformation
import shifa.doctor.generated.resources.planPremium
import shifa.doctor.generated.resources.planPro
import shifa.doctor.generated.resources.practiceTimezone
import shifa.doctor.generated.resources.profile
import shifa.doctor.generated.resources.schedule
import shifa.doctor.generated.resources.security
import shifa.doctor.generated.resources.servicesPricing
import shifa.doctor.generated.resources.settings
import shifa.doctor.generated.resources.shifaIntelligence
import shifa.doctor.generated.resources.signOut
import shifa.doctor.generated.resources.subscriptionAndBilling
import shifa.doctor.generated.resources.talkWithAi
import shifa.doctor.generated.resources.tasks
import shifa.doctor.generated.resources.unreadBadge
import shifa.doctor.generated.resources.unreadCountCap
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.auth.SignOutSheet
import shifa.doctor.ui.auth.SignOutViewModel
import shifa.doctor.ui.auth.short
import shifa.doctor.ui.calendar.AppointmentPanelScreen
import shifa.doctor.ui.calendar.BlockPanelScreen
import shifa.doctor.ui.calendar.BookingScreen
import shifa.doctor.ui.calendar.CalendarScreen
import shifa.doctor.ui.debug.DebugMenuScreen
import shifa.doctor.ui.documents.DocumentViewerScreen
import shifa.doctor.ui.forms.FormEditorScreen
import shifa.doctor.ui.home.HomeActions
import shifa.doctor.ui.home.HomeScreen
import shifa.doctor.ui.home.HomeShell
import shifa.doctor.ui.patients.PatientDetailScreen
import shifa.doctor.ui.patients.PatientsScreen
import shifa.doctor.ui.profile.BillingScreen
import shifa.doctor.ui.profile.ContactScreen
import shifa.doctor.ui.profile.ExtendedProfileScreen
import shifa.doctor.ui.profile.LanguageScreen
import shifa.doctor.ui.profile.PersonalInfoScreen
import shifa.doctor.ui.profile.PracticeTimezoneSheet
import shifa.doctor.ui.profile.ProfileActions
import shifa.doctor.ui.profile.ProfileScreen
import shifa.doctor.ui.profile.ProfileSection
import shifa.doctor.ui.profile.SecurityScreen
import shifa.doctor.ui.profile.ServicesScreen
import shifa.doctor.ui.profile.SettingsActions
import shifa.doctor.ui.profile.SettingsScreen
import shifa.doctor.ui.schedule.LocationsScreen
import shifa.doctor.ui.schedule.ScheduleScreen
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.visit.VisitScreen
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.trackActivity
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.doctorDisplayName
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
    BackHandler(enabled = state.moreOpen || state.intelligence || state.stack.size > 1 || state.tab != state.home) {
        viewModel.back()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    MainScreenContent(
        state,
        ShellActions(
            select = viewModel::select,
            refreshHomeCounts = viewModel::refreshHomeCounts,
            push = viewModel::push,
            back = { viewModel.back() },
            openMore = viewModel::openMore,
            openIntelligence = viewModel::openIntelligence,
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
    // HOME-02: Home's notification and follow-up counts read again.
    val refreshHomeCounts: () -> Unit = {},
    val push: (ShellRoute) -> Unit = {},
    val back: () -> Unit = {},
    val openMore: () -> Unit = {},
    val openIntelligence: () -> Unit = {},
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
                if (state.intelligence) {
                    // Area 03 builds the surface; the tab underneath keeps its stack and state.
                    TabPlaceholder(Res.string.shifaIntelligence, Res.drawable.ic_sparkles_18)
                } else {
                    saved.SaveableStateProvider("${state.tab.key}/${state.stack.size}/${state.route}") {
                        RouteContent(state.route, actions, state.staff, state)
                    }
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
                    val count = state.count(it)
                    val label = tr(it.label)
                    NavBarItem(
                        label,
                        it.icon,
                        it.selectedIcon,
                        badge = count,
                        // "Chat, 3 unread" (flow 01/16 §8); the badge itself is hidden from screen readers.
                        description = if (count > 0) "$label, ${tr(Res.string.unreadBadge, count)}" else label,
                        avatar = avatar,
                        avatarUrl = state.avatarUrl,
                    )
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
    shell: ShellUiState = ShellUiState(),
) {
    when (route) {
        is ShellRoute.Root ->
            if (route.tab == ShellTab.Profile) {
                var timezone by rememberSaveable { mutableStateOf(false) }
                ProfileScreen(
                    staff,
                    ProfileActions(
                        // The zone is a sheet over the root (flow 08/05 §2), not a pushed screen.
                        open = {
                            if (it == ProfileSection.Timezone) {
                                timezone = true
                            } else {
                                actions.push(
                                    ShellRoute.Profile(it),
                                )
                            }
                        },
                        schedule = { actions.push(ShellRoute.Schedule) },
                        locations = { actions.push(ShellRoute.Locations) },
                        reports = { actions.push(ShellRoute.Reports) },
                        signOut = actions.signOut,
                    ),
                )
                if (timezone) PracticeTimezoneSheet(onClose = { timezone = false })
            } else if (route.tab == ShellTab.Home && !staff) {
                // HOME-02: the doctor and the counts the shell keeps.
                val me by profileRepository.me.collectAsStateWithLifecycle()
                val p = me?.profile
                HomeScreen(
                    HomeShell(
                        name = doctorDisplayName(p?.firstName, p?.lastName, tr(Res.string.drMedPrefix)),
                        photoUrl = p?.photoUrl,
                        unread = shell.notificationsUnread.takeIf { shell.notificationsKnown },
                        unreadFailed = shell.notificationsFailed,
                        followUps = shell.followUps,
                        followUpsFailed = shell.followUpsFailed,
                        profileFailed = shell.profileFailed && me == null,
                        showFollowUps = shell.canTasks,
                        profileLoaded = me != null,
                        zone = p?.timeZone,
                    ),
                    HomeActions(
                        openProfile = { actions.select(ShellTab.Profile) },
                        refreshCounts = actions.refreshHomeCounts,
                        // The search overlay joins with HOME-05; until then the Patients tab has the search.
                        search = { actions.select(ShellTab.Patients) },
                        notifications = { actions.select(ShellTab.Notifications) },
                        calendar = { actions.select(ShellTab.Calendar) },
                        tasks = { actions.select(ShellTab.Tasks) },
                    ),
                )
            } else if (route.tab == ShellTab.Patients && !staff) {
                PatientsScreen(onOpen = { actions.push(ShellRoute.Patient(it.id)) })
            } else if (route.tab == ShellTab.Calendar && !staff) {
                CalendarScreen(
                    onOpen = {
                        val opened = currentInstant().toEpochMilliseconds()
                        when (it) {
                            is CalendarEntry.Appointment -> actions.push(ShellRoute.Appointment(it, opened))
                            is CalendarEntry.FreeSlot -> actions.push(ShellRoute.Booking(it, opened))
                            is CalendarEntry.Blocked -> actions.push(ShellRoute.Block(it, opened))
                        }
                    },
                )
            } else {
                TabPlaceholder(route.tab.label, route.tab.icon)
            }
        is ShellRoute.Profile ->
            when (route.section) {
                ProfileSection.PersonalInfo -> PersonalInfoScreen(onClose = actions.back)
                ProfileSection.Contact -> {
                    var timezone by rememberSaveable { mutableStateOf(false) }
                    ContactScreen(onClose = actions.back, onTimezone = { timezone = true })
                    if (timezone) PracticeTimezoneSheet(onClose = { timezone = false })
                }
                ProfileSection.Subscription -> BillingScreen(onClose = actions.back)
                ProfileSection.Extended -> ExtendedProfileScreen(onClose = actions.back)
                ProfileSection.Services -> ServicesScreen(onClose = actions.back)
                ProfileSection.Security -> SecurityScreen(onClose = actions.back)
                ProfileSection.Settings ->
                    SettingsScreen(
                        staff,
                        SettingsActions(
                            back = actions.back,
                            language = { actions.push(ShellRoute.Language) },
                            security = { actions.push(ShellRoute.Profile(ProfileSection.Security)) },
                            about = { actions.push(ShellRoute.Profile(ProfileSection.About)) },
                            debug = { actions.push(ShellRoute.Debug) }.takeIf { isDebugBuild },
                        ),
                    )
                else -> Pushed(route.section.title, actions) { ProfileSectionPlaceholder(route.section) }
            }
        ShellRoute.Schedule ->
            ScheduleScreen(onClose = actions.back, onManageLocations = {
                actions.push(ShellRoute.Locations)
            })
        ShellRoute.Reports ->
            Pushed(Res.string.navReports, actions) {
                TabPlaceholder(Res.string.navReports, Res.drawable.ic_reports_22)
            }
        is ShellRoute.Block -> BlockPanelScreen(route.entry, route.opened, onClose = actions.back)
        is ShellRoute.Booking ->
            BookingScreen(
                route.slot,
                route.opened,
                onClose = actions.back,
                patient = route.patient,
            )
        is ShellRoute.Appointment ->
            AppointmentPanelScreen(
                route.appointment,
                route.opened,
                onClose = actions.back,
                onStart = {
                    actions.push(ShellRoute.Visit(it.appointmentId, it.kind, currentInstant().toEpochMilliseconds()))
                },
                onOpenDocument = { patientId, d -> actions.push(ShellRoute.Document(patientId, d.id, d.title)) },
            )
        // The visit workspace (APPT-04); a video visit starts on its pre-join page with its call (VIDEO-02).
        is ShellRoute.Visit ->
            VisitScreen(
                route.appointmentId,
                route.opened,
                staff,
                onBack = actions.back,
                onOpenPatient = { actions.push(ShellRoute.Patient(it)) },
                onOpenDocument = { patientId, d, title -> actions.push(ShellRoute.Document(patientId, d.id, title)) },
                onOpenForm = { patientId, icd ->
                    actions.push(ShellRoute.Form(patientId, null, currentInstant().toEpochMilliseconds(), icd))
                },
                onBook = { slot, patient ->
                    actions.push(ShellRoute.Booking(slot, currentInstant().toEpochMilliseconds(), patient))
                },
                video = route.kind == shifa.doctor.data.model.VisitKind.Video,
            )
        ShellRoute.Locations -> LocationsScreen(onClose = actions.back)
        is ShellRoute.Patient ->
            PatientDetailScreen(
                route.id,
                onBack = actions.back,
                onBook = {
                        slot,
                        patient,
                    ->
                    actions.push(ShellRoute.Booking(slot, currentInstant().toEpochMilliseconds(), patient))
                },
                onOpenDocument = { d, title -> actions.push(ShellRoute.Document(route.id, d.id, title)) },
                onOpenForm = { formId ->
                    actions.push(ShellRoute.Form(route.id, formId, currentInstant().toEpochMilliseconds()))
                },
            )
        is ShellRoute.Form ->
            FormEditorScreen(route.patientId, route.formId, route.opened, onClose = actions.back, presetIcd = route.icd)
        is ShellRoute.Document ->
            DocumentViewerScreen(route.patientId, route.documentId, route.title, route.clinicId, onBack = actions.back)
        ShellRoute.Language -> LanguageScreen(onBack = actions.back)
        ShellRoute.Debug -> DebugMenuScreen(onBack = actions.back)
    }
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

/** A Profile section until its ticket builds it (About for now). */
@Composable
private fun ProfileSectionPlaceholder(section: ProfileSection) = TabPlaceholder(section.title, Res.drawable.ic_user_22)

private val ProfileSection.title: StringResource
    get() =
        when (this) {
            ProfileSection.PersonalInfo -> Res.string.personalInformation
            ProfileSection.Contact -> Res.string.contactAndClinicAddress
            ProfileSection.Timezone -> Res.string.practiceTimezone
            ProfileSection.Extended -> Res.string.extendedProfile
            ProfileSection.Services -> Res.string.servicesPricing
            ProfileSection.Subscription -> Res.string.subscriptionAndBilling
            ProfileSection.Settings -> Res.string.settings
            ProfileSection.Security -> Res.string.security
            ProfileSection.About -> Res.string.about
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
        // Gated: hidden until the plan is known (D-03), then shown, with "Pro" when the plan lacks it.
        if (state.gate.known) {
            MoreRow(
                Res.string.shifaIntelligence,
                Res.drawable.ic_sparkles_18,
                sub = tr(if (state.canIntelligence) Res.string.talkWithAi else Res.string.needsProPlan),
                badge = if (state.canIntelligence) null else tr(Res.string.planPro),
                ai = true,
                enabled = state.canIntelligence,
            ) { actions.openIntelligence() }
            HorizontalDivider(Modifier.padding(vertical = ShifaSize.S6), ShifaSize.S1, ShifaColors.Border.Default)
        }
        ShellTab.entries.filterNot { it.inBar }.forEach { tab ->
            when {
                tab == ShellTab.Clinic && state.hasClinic != true -> Unit
                tab == ShellTab.Tasks && !state.canTasks ->
                    // Shown with its plan, not tappable, once the plan is known (D-03: nothing before that).
                    if (state.gate.known) {
                        MoreRow(tab.label, tab.icon, badge = tr(Res.string.planPremium), enabled = false) {}
                    }
                else ->
                    MoreRow(
                        tab.label,
                        tab.icon,
                        selected = state.tab == tab,
                        count = state.count(tab),
                    ) { actions.select(tab) }
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
    count: Int = 0,
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
    if (count > 0) {
        // DaApp's count pill: teal, white, at least 22 round.
        Box(
            Modifier
                .defaultMinSize(minWidth = ShifaSize.S22, minHeight = ShifaSize.S22)
                .background(ShifaColors.Interactive.Primary, ShifaRadius.Full)
                .padding(horizontal = ShifaSize.S6),
            contentAlignment = Alignment.Center,
        ) { Text(countLabel(count), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Interactive.OnPrimary) }
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

/** The count a tab shows: chat in the bar, notifications (bar for staff, More for doctors), active tasks in More. */
internal fun ShellUiState.count(tab: ShellTab): Int =
    when (tab) {
        ShellTab.Chat -> chatUnread
        ShellTab.Notifications -> notificationsUnread
        ShellTab.Tasks -> if (canTasks) activeTasks else 0
        else -> 0
    }

/** "99+" past 99 (flow 01/16 §8). */
@Composable
internal fun countLabel(count: Int): String = if (count > MAX_COUNT) tr(Res.string.unreadCountCap) else count.toString()

private const val MAX_COUNT = 99
