package shifa.doctor.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.push.Push
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.about
import shifa.doctor.generated.resources.calendar
import shifa.doctor.generated.resources.chat
import shifa.doctor.generated.resources.clinicNavClinic
import shifa.doctor.generated.resources.contactAndClinicAddress
import shifa.doctor.generated.resources.document
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
import shifa.doctor.generated.resources.privacyAndData
import shifa.doctor.generated.resources.profile
import shifa.doctor.generated.resources.schedule
import shifa.doctor.generated.resources.security
import shifa.doctor.generated.resources.selectConversation
import shifa.doctor.generated.resources.servicesPricing
import shifa.doctor.generated.resources.settings
import shifa.doctor.generated.resources.shifaIntelligence
import shifa.doctor.generated.resources.signOut
import shifa.doctor.generated.resources.subscriptionAndBilling
import shifa.doctor.generated.resources.talkWithAi
import shifa.doctor.generated.resources.tasks
import shifa.doctor.generated.resources.unreadBadge
import shifa.doctor.generated.resources.unreadCountCap
import shifa.doctor.ui.analytics.AnalyticsActions
import shifa.doctor.ui.analytics.ReportsScreen
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.auth.SignOutSheet
import shifa.doctor.ui.auth.SignOutViewModel
import shifa.doctor.ui.auth.short
import shifa.doctor.ui.calendar.AppointmentPanelScreen
import shifa.doctor.ui.calendar.BlockPanelScreen
import shifa.doctor.ui.calendar.BookingScreen
import shifa.doctor.ui.calendar.CalendarFocus
import shifa.doctor.ui.calendar.CalendarScreen
import shifa.doctor.ui.care.FindPartnerScreen
import shifa.doctor.ui.care.PartnershipScreen
import shifa.doctor.ui.care.PartnershipsScreen
import shifa.doctor.ui.chat.ChatListScreen
import shifa.doctor.ui.chat.PatientChatScreen
import shifa.doctor.ui.chat.ThreadScreen
import shifa.doctor.ui.clinic.ClinicActions
import shifa.doctor.ui.clinic.ClinicDoctorScheduleScreen
import shifa.doctor.ui.clinic.ClinicSectionContent
import shifa.doctor.ui.clinic.ClinicWorkspaceScreen
import shifa.doctor.ui.clinic.PlanDetailScreen
import shifa.doctor.ui.clinic.PlanWizardScreen
import shifa.doctor.ui.debug.DebugMenuScreen
import shifa.doctor.ui.documents.DocumentViewerScreen
import shifa.doctor.ui.forms.FormEditorScreen
import shifa.doctor.ui.home.HomeActions
import shifa.doctor.ui.home.HomeScreen
import shifa.doctor.ui.home.HomeShell
import shifa.doctor.ui.home.PatientSearchScreen
import shifa.doctor.ui.intelligence.IntelligenceScreen
import shifa.doctor.ui.notifications.NotificationGo
import shifa.doctor.ui.notifications.NotificationsScreen
import shifa.doctor.ui.patients.PatientDetailScreen
import shifa.doctor.ui.patients.PatientsScreen
import shifa.doctor.ui.profile.AboutScreen
import shifa.doctor.ui.profile.BillingScreen
import shifa.doctor.ui.profile.ContactScreen
import shifa.doctor.ui.profile.ExtendedProfileScreen
import shifa.doctor.ui.profile.LanguageScreen
import shifa.doctor.ui.profile.PersonalInfoScreen
import shifa.doctor.ui.profile.PracticeTimezoneSheet
import shifa.doctor.ui.profile.PrivacyScreen
import shifa.doctor.ui.profile.ProfileActions
import shifa.doctor.ui.profile.ProfileScreen
import shifa.doctor.ui.profile.ProfileSection
import shifa.doctor.ui.profile.SecurityScreen
import shifa.doctor.ui.profile.ServicesScreen
import shifa.doctor.ui.profile.SettingsActions
import shifa.doctor.ui.profile.SettingsScreen
import shifa.doctor.ui.register.label
import shifa.doctor.ui.schedule.LocationsScreen
import shifa.doctor.ui.schedule.ScheduleScreen
import shifa.doctor.ui.tasks.CreateTaskScreen
import shifa.doctor.ui.tasks.TaskDetailScreen
import shifa.doctor.ui.tasks.TasksScreen
import shifa.doctor.ui.tasks.TemplatesScreen
import shifa.doctor.ui.theme.ShifaBreakpoint
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.visit.VisitScreen
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.LocalWideLayout
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.trackActivity
import shifa.doctor.utils.AskPushPermission
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.doctorDisplayName
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.showBrowserPath
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

internal val ShellTab.selectedIcon: DrawableResource
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
    // WEB-10: the address bar names what the shell shows; signed out, the bare site.
    val path = shellPath(state)
    LaunchedEffect(path) { showBrowserPath(path) }
    DisposableEffect(Unit) { onDispose { showBrowserPath("/") } }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    // PUSH-01: notifications allowed (asked once the doctor is in; staff get no pushes).
    if (!staff) AskPushPermission()
    val actions =
        ShellActions(
            select = viewModel::select,
            openPatientFromSearch = viewModel::openPatientFromSearch,
            openChatWithPatient = viewModel::openChatWithPatient,
            openCalendar = viewModel::openCalendar,
            openTask = viewModel::openTask,
            refreshHomeCounts = viewModel::refreshHomeCounts,
            push = viewModel::push,
            back = { viewModel.back() },
            openMore = viewModel::openMore,
            openIntelligence = viewModel::openIntelligence,
            leaveIntelligence = viewModel::leaveIntelligence,
            closeMore = viewModel::closeMore,
            signOut = {
                viewModel.closeMore()
                signOut.ask()
            },
        )
    // PUSH-03: a tapped push, whenever it came (a cold start's waits here for the shell), opens over Home.
    val tapped by Push.tapped.collectAsStateWithLifecycle()
    LaunchedEffect(tapped) {
        val data = tapped ?: return@LaunchedEffect
        Push.tapped.value = null
        viewModel.openPush(data)
    }
    val documentTitle = tr(Res.string.document)
    LaunchedEffect(state.pushGo) {
        val go = state.pushGo ?: return@LaunchedEffect
        viewModel.pushOpened()
        actions.openOverHome(go, documentTitle)
    }
    MainScreenContent(state, actions, identity = shellIdentity())
    SignOutSheet(question, signOut::confirm, signOut::cancel)
}

/** The doctor's name and first profession for the sidebar (WEB-06); staff have no doctor profile. */
@Composable
private fun shellIdentity(): ShellIdentity? {
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val p = me?.profile ?: return null
    val profession = p.profession?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
    return ShellIdentity(
        doctorDisplayName(p.firstName, p.lastName, tr(Res.string.drMedPrefix)),
        profession?.let { Profession(it, null).label() },
        p.photoUrl,
    )
}

/** HOME-04, PUSH-03: a notification's target over Home (Back returns to it); chat is its tab, the inbox its own. */
internal fun ShellActions.openOverHome(
    go: NotificationGo,
    documentTitle: String,
) {
    val opened = currentInstant().toEpochMilliseconds()
    when (go) {
        is NotificationGo.Appointment -> push(ShellRoute.Appointment(go.entry, opened))
        is NotificationGo.Patient -> push(ShellRoute.Patient(go.id))
        is NotificationGo.Document -> push(ShellRoute.Document(go.patientId, go.documentId, go.title ?: documentTitle))
        NotificationGo.Chat -> select(ShellTab.Chat)
        is NotificationGo.Conversation -> push(ShellRoute.Conversation(go.id, opened))
        NotificationGo.Partnerships -> push(ShellRoute.Partnerships(null, null, opened))
        is NotificationGo.Partnership -> push(ShellRoute.Partnership(go.id, opened))
        is NotificationGo.Task -> push(ShellRoute.Task(go.id, opened))
        NotificationGo.Inbox -> select(ShellTab.Notifications)
    }
}

class ShellActions(
    val select: (ShellTab) -> Unit = {},
    // HOME-02: Home's notification and follow-up counts read again.
    val refreshHomeCounts: () -> Unit = {},
    // HOME-05: a search result opens in the Patients tab.
    val openPatientFromSearch: (Long) -> Unit = {},
    // CHAT-07: "Send message" on a patient opens the Chat tab on their thread.
    val openChatWithPatient: (ShellRoute.PatientChat) -> Unit = {},
    // NOTIF-02: the Calendar tab at its root (the day comes through `CalendarFocus`).
    val openCalendar: () -> Unit = {},
    // TASK-03: a task from a notification, in the Tasks tab (or the plan note).
    val openTask: (Long, Long) -> Unit = { _, _ -> },
    val push: (ShellRoute) -> Unit = {},
    val back: () -> Unit = {},
    val openMore: () -> Unit = {},
    val openIntelligence: () -> Unit = {},
    val leaveIntelligence: () -> Unit = {},
    val closeMore: () -> Unit = {},
    val signOut: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    state: ShellUiState,
    actions: ShellActions,
    // Shifa Intelligence over the tab (INTEL-02); previews pass its stateless content (its chats are in secure storage).
    intelligence: @Composable () -> Unit = {
        IntelligenceScreen(
            onLeave = actions.leaveIntelligence,
            // Clear of the phone's floating bar; wide screens have none.
            bottomInset =
                if (LocalWideLayout.current) {
                    ShifaSpacing.Inset.Md
                } else {
                    ShifaSpacing.Inset.Md + ShifaDimens.FabDockedClear
                },
            onOpenCalendar = { day ->
                day?.let { CalendarFocus.day.value = it }
                actions.openCalendar()
            },
            onOpen = actions.push,
        )
    },
    // A route's screen; shell previews pass a stand-in (the tabs' screens read repositories and the clock).
    route: @Composable (ShellRoute) -> Unit = { RouteContent(it, actions, state.staff, state) },
    // The sidebar's footer (WEB-06); null shows the photo only.
    identity: ShellIdentity? = null,
) {
    // WEB-06 (DaApp): below 768 the phone's bottom bar and More; from 768 the icon rail, from 1100 the sidebar.
    BoxWithConstraints(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        val wide = maxWidth >= ShifaBreakpoint.Mobile
        val rail = maxWidth < ShifaBreakpoint.Sidebar
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                ShellSidebar(
                    state,
                    actions,
                    identity,
                    rail = rail,
                    search = {
                        actions.select(ShellTab.Home)
                        actions.push(ShellRoute.Search(currentInstant().toEpochMilliseconds()))
                    },
                )
                VerticalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Wide (DaApp web): page titles at the desktop size (32/40) instead of the phone's 24/28.
                val typography = MaterialTheme.typography
                MaterialTheme(
                    MaterialTheme.colorScheme,
                    MaterialTheme.shapes,
                    if (wide) typography.copy(headlineSmall = typography.headlineLarge) else typography,
                ) {
                    CompositionLocalProvider(LocalWideLayout provides wide) {
                        Column(Modifier.fillMaxSize()) {
                            ShellNoticeBanner()
                            // Each route keeps its own UI state (scroll, fields) while another tab or screen is shown (flow 01/16 §5).
                            val saved = rememberSaveableStateHolder()
                            Box(Modifier.weight(1f)) {
                                val overIntelligence = state.overIntelligence.lastOrNull()
                                if (state.intelligence && overIntelligence != null) {
                                    // A visit or document opened from Intelligence (INTEL-07/08); Back returns to the thread.
                                    saved.SaveableStateProvider(
                                        "intelligence/${state.overIntelligence.size}/$overIntelligence",
                                    ) {
                                        route(overIntelligence)
                                    }
                                } else if (state.intelligence) {
                                    // INTEL-02: Shifa Intelligence over the tab, which keeps its stack and state; back from the
                                    // welcome returns to it. Its own state (the thread's scroll, an opened document) is kept while a
                                    // screen opened from it is on top.
                                    saved.SaveableStateProvider("intelligence") { intelligence() }
                                } else {
                                    saved.SaveableStateProvider("${state.tab.key}/${state.stack.size}/${state.route}") {
                                        route(state.route)
                                    }
                                }
                            }
                        }
                    }
                }
                if (!wide && state.barVisible) {
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
                val documentTitle = tr(Res.string.document)
                val me by profileRepository.me.collectAsStateWithLifecycle()
                val p = me?.profile
                var timezone by rememberSaveable { mutableStateOf(false) }
                if (timezone) PracticeTimezoneSheet(onClose = { timezone = false })
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
                        advancedAnalytics =
                            shell.gate.allows(
                                DoctorFeature.AdvancedAnalytics,
                            ).takeIf { shell.gate.known },
                    ),
                    HomeActions(
                        openProfile = { actions.select(ShellTab.Profile) },
                        refreshCounts = actions.refreshHomeCounts,
                        search = { actions.push(ShellRoute.Search(currentInstant().toEpochMilliseconds())) },
                        notifications = { actions.select(ShellTab.Notifications) },
                        calendar = { actions.select(ShellTab.Calendar) },
                        tasks = { actions.select(ShellTab.Tasks) },
                        // HOME-03: the visit, the patient and the panels open inside Home's stack, as from Calendar.
                        start = {
                            actions.push(
                                ShellRoute.Visit(it.appointmentId, it.kind, currentInstant().toEpochMilliseconds()),
                            )
                        },
                        openChart = { actions.push(ShellRoute.Patient(it)) },
                        openEntry = {
                            val opened = currentInstant().toEpochMilliseconds()
                            when (it) {
                                is CalendarEntry.Appointment -> actions.push(ShellRoute.Appointment(it, opened))
                                is CalendarEntry.Blocked -> actions.push(ShellRoute.Block(it, opened))
                                is CalendarEntry.FreeSlot -> actions.push(ShellRoute.Booking(it, opened))
                            }
                        },
                        bookVideo = {
                            actions.push(
                                ShellRoute.Booking(it, currentInstant().toEpochMilliseconds(), video = true),
                            )
                        },
                        setTimezone = { timezone = true },
                        patients = { actions.select(ShellTab.Patients) },
                        seePlans = { actions.push(ShellRoute.Profile(ProfileSection.Subscription)) },
                        // HOME-04: a target opens over Home (Back returns to it); chat is its tab.
                        openNotification = { go -> actions.openOverHome(go, documentTitle) },
                    ),
                )
            } else if (route.tab == ShellTab.Tasks && !staff) {
                // TASK-02: PREMIUM doctors (the shell keeps the tab closed for other plans and for staff).
                TasksScreen(
                    onOpen = { actions.push(ShellRoute.Task(it.id, currentInstant().toEpochMilliseconds())) },
                    onCreate = { actions.push(ShellRoute.TaskCreate(currentInstant().toEpochMilliseconds())) },
                    onTemplates = { actions.push(ShellRoute.TaskTemplates(currentInstant().toEpochMilliseconds())) },
                )
            } else if (route.tab == ShellTab.Notifications) {
                // NOTIF-02: doctors (More, the bell) and clinic staff (their third tab); a target opens over the inbox.
                val documentTitle = tr(Res.string.document)
                NotificationsScreen(
                    // A count that failed its last read is unknown (Mark all stays on).
                    unread =
                        shell.notificationsUnread.takeIf { shell.notificationsKnown && !shell.notificationsFailed },
                    staff = staff,
                    onOpen = { go ->
                        val opened = currentInstant().toEpochMilliseconds()
                        when (go) {
                            is NotificationGo.Appointment -> actions.push(ShellRoute.Appointment(go.entry, opened))
                            is NotificationGo.Patient -> actions.push(ShellRoute.Patient(go.id))
                            is NotificationGo.Document ->
                                actions.push(
                                    ShellRoute.Document(go.patientId, go.documentId, go.title ?: documentTitle),
                                )
                            NotificationGo.Chat -> actions.select(ShellTab.Chat)
                            is NotificationGo.Conversation -> actions.push(ShellRoute.Conversation(go.id, opened))
                            NotificationGo.Partnerships -> actions.push(ShellRoute.Partnerships(null, null, opened))
                            is NotificationGo.Partnership -> actions.push(ShellRoute.Partnership(go.id, opened))
                            is NotificationGo.Task -> actions.openTask(go.id, opened)
                            // Never from the inbox itself.
                            NotificationGo.Inbox -> Unit
                        }
                    },
                    onSettings = { actions.push(ShellRoute.Profile(ProfileSection.Settings)) },
                    onCalendar = { day ->
                        CalendarFocus.day.value = day
                        actions.openCalendar()
                    },
                )
            } else if (route.tab == ShellTab.Chat) {
                // CHAT-02: doctors and clinic staff alike (chat has no tier gate).
                ChatPanes(null, actions) {
                    ChatListScreen(
                        onOpen = {
                            actions.push(
                                ShellRoute.Conversation(it.id, currentInstant().toEpochMilliseconds(), it),
                            )
                        },
                    )
                }
            } else if (route.tab == ShellTab.Patients && !staff) {
                PatientsScreen(onOpen = { actions.push(ShellRoute.Patient(it.id)) })
            } else if (route.tab == ShellTab.Calendar && !staff) {
                CalendarScreen(
                    onOpen = {
                        val opened = currentInstant().toEpochMilliseconds()
                        when (it) {
                            is CalendarEntry.Appointment -> actions.push(ShellRoute.Appointment(it, opened))
                            is CalendarEntry.FreeSlot -> actions.push(ShellRoute.Booking(it, opened))
                            is CalendarEntry.Blocked ->
                                actions.push(
                                    ShellRoute.Block(it, opened),
                                )
                        }
                    },
                    onOpenColleague = { entry, scope ->
                        val opened = currentInstant().toEpochMilliseconds()
                        actions.push(
                            when (entry) {
                                is CalendarEntry.Appointment -> ShellRoute.Appointment(entry, opened, scope)
                                is CalendarEntry.FreeSlot -> ShellRoute.Booking(entry, opened, scope = scope)
                                is CalendarEntry.Blocked -> ShellRoute.Block(entry, opened, scope)
                            },
                        )
                    },
                )
            } else if (route.tab == ShellTab.Clinic) {
                // M11: the clinic workspace (doctors with a clinic, and clinic staff).
                ClinicWorkspaceScreen(
                    staff,
                    ClinicActions(
                        openMyCalendar = actions.openCalendar,
                        newPlan = { actions.push(ShellRoute.PlanWizard(it, currentInstant().toEpochMilliseconds())) },
                    ),
                ) { section, clinic ->
                    ClinicSectionContent(section, clinic, staff, actions)
                }
            } else if (route.tab == ShellTab.Reports && !staff) {
                // REPORT-01: More → Reports, the tab's root.
                Reports(shell, actions, onBack = null)
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
                            privacy = { actions.push(ShellRoute.Profile(ProfileSection.Privacy)) }.takeIf { !staff },
                        ),
                    )
                // LEGAL-01: the app, its version and the privacy policy.
                ProfileSection.About -> AboutScreen(actions.back, staff = staff)
                // BE-02: a copy of the doctor's data and deleting the account (flow 08/12).
                ProfileSection.Privacy -> PrivacyScreen(actions.back)
                else -> Pushed(route.section.title, actions) { ProfileSectionPlaceholder(route.section) }
            }
        ShellRoute.Schedule ->
            ScheduleScreen(onClose = actions.back, onManageLocations = {
                actions.push(ShellRoute.Locations)
            })
        is ShellRoute.PatientChat -> {
            val chat: @Composable () -> Unit = {
                PatientChatScreen(
                    route.patientId,
                    route.name,
                    route.photoUrl,
                    route.opened,
                    onBack = actions.back,
                    onOpenPatient = { actions.push(ShellRoute.Patient(it)) },
                )
            }
            // CHAT-07 lands on the Chat tab: beside the list there too.
            if (shell.tab == ShellTab.Chat) ChatPanes(null, actions, threadOpen = true, single = chat) else chat()
        }
        // CHAT-03 builds the thread.
        is ShellRoute.Conversation -> {
            val thread: @Composable () -> Unit = {
                ThreadScreen(
                    route.id,
                    route.known,
                    onBack = actions.back,
                    onOpenPatient = { actions.push(ShellRoute.Patient(it)) },
                )
            }
            // Beside the list on the Chat tab only; opened from elsewhere it is the whole screen.
            if (shell.tab == ShellTab.Chat) ChatPanes(route, actions, single = thread) else thread()
        }
        // REPORT-01: Profile → Reports, Back returns there.
        ShellRoute.Reports -> Reports(shell, actions, onBack = actions.back)
        is ShellRoute.Task ->
            TaskDetailScreen(route.id, route.opened, onBack = actions.back, onOpenPatient = {
                actions.push(ShellRoute.Patient(it))
            })
        is ShellRoute.TaskCreate ->
            CreateTaskScreen(
                route.opened,
                route.template,
                onClose = actions.back,
                onCreated = {
                    actions.back()
                    if (route.fromTemplates) actions.back()
                },
            )
        is ShellRoute.TaskTemplates ->
            TemplatesScreen(
                onBack = actions.back,
                onOpen = {
                    actions.push(
                        ShellRoute.TaskCreate(currentInstant().toEpochMilliseconds(), it, fromTemplates = true),
                    )
                },
                onCreate = {
                    actions.push(
                        ShellRoute.TaskCreate(currentInstant().toEpochMilliseconds(), fromTemplates = true),
                    )
                },
            )
        is ShellRoute.Block ->
            BlockPanelScreen(
                route.entry,
                route.opened,
                onClose = actions.back,
                scope = route.scope,
            )
        is ShellRoute.DoctorSchedule -> ClinicDoctorScheduleScreen(route.scope, actions)
        is ShellRoute.PlanDetail -> PlanDetailScreen(route.planId, route.clinicId, actions)
        is ShellRoute.FindPartner ->
            FindPartnerScreen(
                route.patientId,
                route.patientName,
                route.opened,
                actions,
            )
        is ShellRoute.Partnerships -> PartnershipsScreen(route.patientId, route.patientName, route.opened, actions)
        is ShellRoute.Partnership -> PartnershipScreen(route.id, route.opened, actions)
        is ShellRoute.PlanWizard ->
            PlanWizardScreen(
                route.clinicId,
                route.opened,
                route.patientId,
                route.patientName,
                actions,
            )

        is ShellRoute.Booking ->
            BookingScreen(
                route.slot,
                route.opened,
                onClose = actions.back,
                patient = route.patient,
                video = route.video,
                scope = route.scope,
            )
        is ShellRoute.Appointment ->
            AppointmentPanelScreen(
                route.appointment,
                route.opened,
                onClose = actions.back,
                onStart = {
                    actions.push(ShellRoute.Visit(it.appointmentId, it.kind, currentInstant().toEpochMilliseconds()))
                },
                onOpenDocument = {
                        patientId,
                        d,
                    ->
                    actions.push(ShellRoute.Document(patientId, d.id, d.title))
                },
                scope = route.scope,
                // A colleague's patient opens in the clinic's scope (CLINIC-09); Back returns to the panel.
                onOpenPatient = { actions.push(ShellRoute.Patient(it, route.scope?.clinicId)) },
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
        is ShellRoute.Search ->
            PatientSearchScreen(
                route.opened,
                onBack = actions.back,
                onOpen = actions.openPatientFromSearch,
                onAddPatient = { actions.select(ShellTab.Patients) },
            )
        is ShellRoute.Patient ->
            PatientDetailScreen(
                route.id,
                onBack = actions.back,
                clinicId = route.clinicId,
                onBook = {
                        slot,
                        patient,
                    ->
                    actions.push(ShellRoute.Booking(slot, currentInstant().toEpochMilliseconds(), patient))
                },
                onOpenDocument = { d, title ->
                    actions.push(ShellRoute.Document(route.id, d.id, title, route.clinicId))
                },
                onOpenForm = { formId ->
                    actions.push(
                        ShellRoute.Form(
                            route.id,
                            formId,
                            currentInstant().toEpochMilliseconds(),
                            clinicId = route.clinicId,
                        ),
                    )
                },
                onMessage = { p ->
                    actions.openChatWithPatient(
                        ShellRoute.PatientChat(p.id, p.name, p.photoUrl, currentInstant().toEpochMilliseconds()),
                    )
                },
                onFindPartner = { p ->
                    actions.push(ShellRoute.FindPartner(p.id, p.name, currentInstant().toEpochMilliseconds()))
                },
                onPartnerships = { p ->
                    actions.push(ShellRoute.Partnerships(p.id, p.name, currentInstant().toEpochMilliseconds()))
                },
                onNewPlan =
                    route.clinicId?.let { clinicId ->
                        { p ->
                            actions.push(
                                ShellRoute.PlanWizard(clinicId, currentInstant().toEpochMilliseconds(), p.id, p.name),
                            )
                        }
                    },
            )
        is ShellRoute.Form ->
            FormEditorScreen(
                route.patientId,
                route.formId,
                route.opened,
                onClose = actions.back,
                presetIcd = route.icd,
                clinicId = route.clinicId,
            )
        is ShellRoute.Document ->
            DocumentViewerScreen(route.patientId, route.documentId, route.title, route.clinicId, onBack = actions.back)
        ShellRoute.Language -> LanguageScreen(onBack = actions.back)
        ShellRoute.Debug -> DebugMenuScreen(onBack = actions.back)
    }
}

/**
 * WEB-07e (DaChat web): from 720 wide in the wide shell, the Chat tab shows the list (360) beside the open
 * conversation, or "Select a conversation"; another conversation replaces the open one instead of stacking.
 * [open] is the open conversation (null on the tab's root); [single] is the phone's one-pane screen.
 */
@Composable
private fun ChatPanes(
    open: ShellRoute.Conversation?,
    actions: ShellActions,
    // A thread is open even when [open] is null: "Send message" (CHAT-07) resolves the conversation itself.
    threadOpen: Boolean = open != null,
    single: @Composable () -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    if (!LocalWideLayout.current || maxWidth < ShifaBreakpoint.SplitPane) return@BoxWithConstraints single()
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(ShifaDimens.Layout.DialogSm).fillMaxHeight()) {
            ChatListScreen(
                onOpen = {
                    if (threadOpen) actions.back()
                    actions.push(ShellRoute.Conversation(it.id, currentInstant().toEpochMilliseconds(), it))
                },
                selectedId = open?.id,
            )
        }
        VerticalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
        Box(
            Modifier.weight(1f).fillMaxHeight().background(ShifaColors.Bg.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (!threadOpen) {
                Text(
                    tr(Res.string.selectConversation),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            } else {
                key(open?.id) { single() }
            }
        }
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

/** Reports (REPORT-01) in the practice's day, the plan's advanced analytics once known. */
@Composable
private fun Reports(
    shell: ShellUiState,
    actions: ShellActions,
    onBack: (() -> Unit)?,
) {
    val me by profileRepository.me.collectAsStateWithLifecycle()
    ReportsScreen(
        PracticeClock(me?.profile?.timeZone).today(),
        shell.gate.allows(DoctorFeature.AdvancedAnalytics).takeIf { shell.gate.known },
        AnalyticsActions(
            calendar = actions.openCalendar,
            patients = { actions.select(ShellTab.Patients) },
            seePlans = { actions.push(ShellRoute.Profile(ProfileSection.Subscription)) },
        ),
        onBack,
    )
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
            ProfileSection.Privacy -> Res.string.privacyAndData
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
