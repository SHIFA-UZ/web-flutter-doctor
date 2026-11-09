package shifa.doctor.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import shifa.doctor.data.badges.BadgesRepository
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.chat.ChatReads
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.forms.Icd10Hit
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.notifications.NotificationReads
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.push.Push
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.tasksNotOnPlan
import shifa.doctor.ui.notifications.NotificationGo
import shifa.doctor.ui.notifications.NotificationOpener
import shifa.doctor.ui.profile.ProfileSection
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import kotlin.time.Duration.Companion.seconds

/**
 * The doctor's destinations (01 §6, flow 01/16): four in the bar, five more behind "More". [key] is the backend's
 * `settings.defaultStartTab` value.
 */
enum class ShellTab(
    val key: String,
    val inBar: Boolean,
) {
    Home("home", true),
    Calendar("calendar", true),
    Patients("patients", true),
    Chat("chat", true),
    Clinic("clinic", false),
    Tasks("tasks", false),
    Reports("reports", false),
    Notifications("notifications", false),
    Profile("profile", false),
    ;

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** A screen inside a tab's stack. Feature tickets add theirs; the root of every stack is [Root]. */
sealed interface ShellRoute {
    data class Root(
        val tab: ShellTab,
    ) : ShellRoute

    /** More → Language (SET-02 builds the screen). */
    data object Language : ShellRoute

    /** Chat → one conversation (CHAT-03 builds the thread); [opened] as for [Appointment]. */
    data class Conversation(
        val id: Long,
        val opened: Long,
        // The list's card, so the header shows before the thread is read.
        val known: shifa.doctor.data.model.Conversation? = null,
    ) : ShellRoute

    /**
     * Patient → "Send message" (CHAT-07, flow 07/07): the conversation with [patientId], found or started by the
     * backend; [name] and [photoUrl] fill the header while it resolves.
     */
    data class PatientChat(
        val patientId: Long,
        val name: String,
        val photoUrl: String?,
        val opened: Long,
    ) : ShellRoute

    /** Home → patient search (HOME-05); [opened] makes each open a fresh search (an empty query, a staleness check). */
    data class Search(
        val opened: Long,
    ) : ShellRoute

    /** Tasks → one task (TASK-03); [opened] as for [Appointment]. */
    data class Task(
        val id: Long,
        val opened: Long,
    ) : ShellRoute

    /** Tasks → a new task (TASK-04), from a template (TASK-05) when [template] is set. */
    data class TaskCreate(
        val opened: Long,
        val template: shifa.doctor.data.model.TaskTemplate? = null,
        // Opened from "Use Templates": a successful create closes that list too (flow 07/14 §5).
        val fromTemplates: Boolean = false,
    ) : ShellRoute

    /** Tasks → "Use Templates" (TASK-05). */
    data class TaskTemplates(
        val opened: Long,
    ) : ShellRoute

    /** Profile → Debug menu, debug builds only. */
    data object Debug : ShellRoute

    /** Profile → Schedule setup (area 04, SCHED-1 builds it). */
    data object Schedule : ShellRoute

    /** Patients → one patient (PAT-05 builds the detail). */
    data class Patient(
        val id: Long,
        // Opened from a clinic's workspace (CLINIC-06): read in that clinic's scope (staff have no other).
        val clinicId: Long? = null,
    ) : ShellRoute

    /** Profile → Locations (LOC-1); also reached from the schedule setup (SCHED-2). */
    data object Locations : ShellRoute

    /** Profile → Reports, pushed so Back returns to Profile (REP-1 builds it). */
    data object Reports : ShellRoute

    /**
     * Calendar → one appointment's panel (CAL-6); pushed so the bar steps aside for its actions. [opened] makes each
     * open a new route, so its ViewModel starts fresh instead of coming back with an open question.
     */
    data class Appointment(
        val appointment: CalendarEntry.Appointment,
        val opened: Long,
        // A colleague's appointment from the clinic doctor schedule (CAL-10 / CLINIC-09).
        val scope: shifa.doctor.ui.calendar.CalendarScope? = null,
    ) : ShellRoute

    /**
     * A visit's screen (APPT-01): in the clinic or on video, opened from its id only (the screen reads the rest, the
     * patient included, from `GET /appointments/{id}`); [opened] as for [Appointment].
     */
    data class Visit(
        val appointmentId: Long,
        val kind: VisitKind,
        val opened: Long,
    ) : ShellRoute

    /** Calendar → a blocked row's panel (CAL-8); [opened] as for [Appointment]. */
    data class Block(
        val entry: CalendarEntry.Blocked,
        val opened: Long,
        val scope: shifa.doctor.ui.calendar.CalendarScope? = null,
    ) : ShellRoute

    /** A clinic's treatment plan (TP-03), from the plans list, the finance dashboard or a patient. */
    data class PlanDetail(
        val planId: Long,
        val clinicId: Long,
    ) : ShellRoute

    /**
     * The treatment plan wizard (TP-04): from the plans list, or a clinic patient ([patientId] set, step 2 first).
     * [opened] keys its ViewModel, so a new wizard starts empty.
     */
    data class PlanWizard(
        val clinicId: Long,
        val opened: Long,
        val patientId: Long? = null,
        val patientName: String? = null,
    ) : ShellRoute

    /** CARE-02: find a partner doctor for a patient; [opened] keys its ViewModel. */
    data class FindPartner(
        val patientId: Long,
        val patientName: String,
        val opened: Long,
    ) : ShellRoute

    /** CARE-03: the care partnerships, only [patientId]'s when set ("Show all" lifts it). */
    data class Partnerships(
        val patientId: Long?,
        val patientName: String?,
        val opened: Long,
    ) : ShellRoute

    /** CARE-03: one partnership. */
    data class Partnership(
        val id: Long,
        val opened: Long,
    ) : ShellRoute

    /** A colleague's calendar from the clinic workspace (CAL-10 / CLINIC-09, flow 04/14 B); back returns there. */
    data class DoctorSchedule(
        val scope: shifa.doctor.ui.calendar.CalendarScope,
    ) : ShellRoute

    /** Calendar → book a free slot (CAL-7); [opened] as for [Appointment]. From a patient's record, [patient] is set. */
    data class Booking(
        val slot: CalendarEntry.FreeSlot,
        val opened: Long,
        val patient: shifa.doctor.data.model.PatientOption? = null,
        // Home's Video Call chip (HOME-03): the booking starts as a video call.
        val video: Boolean = false,
        val scope: shifa.doctor.ui.calendar.CalendarScope? = null,
    ) : ShellRoute

    /** A patient's 025-2 form, new ([formId] null) or existing (FORM-04); [opened] as for [Appointment]. */
    data class Form(
        val patientId: Long,
        val formId: Long?,
        val opened: Long,
        // A new form's ICD-10 code from the visit (APPT-08: an AI suggestion applied there).
        val icd: Icd10Hit? = null,
        // CLINIC-06: opened from a patient in a clinic's scope (the patient read and the form's PDF use it).
        val clinicId: Long? = null,
    ) : ShellRoute

    /** A patient's document in the viewer (DOCS-05), in a clinic's scope when [clinicId] is set. */
    data class Document(
        val patientId: Long,
        val documentId: Long,
        val title: String,
        val clinicId: Long? = null,
    ) : ShellRoute

    /** Profile → one of its sections (PROF-02); each section's ticket builds its screen. */
    data class Profile(
        val section: ProfileSection,
    ) : ShellRoute
}

data class ShellUiState(
    // Clinic staff (SHELL-02, flow 01/18): four tabs, no More, no plan, start and "home" on Clinic.
    val staff: Boolean = false,
    val tab: ShellTab = if (staff) ShellTab.Clinic else ShellTab.Home,
    val stacks: Map<ShellTab, List<ShellRoute>> = emptyMap(),
    val moreOpen: Boolean = false,
    // SHELL-05: Shifa Intelligence replaces the tab's content while the bar stays (01 §5.4, §6.6).
    val intelligence: Boolean = false,
    // Screens opened from Shifa Intelligence (a visit, a document; INTEL-07/08), over it: Back returns to the thread.
    val overIntelligence: List<ShellRoute> = emptyList(),
    // Null until `GET /me/clinics` answers; a failure counts as no clinic for this session (flow 01/16 §6).
    val hasClinic: Boolean? = null,
    // SHELL-04: what the plan allows; unknown until the profile answers (D-03).
    val gate: FeatureGate = FeatureGate.Unknown,
    // The staff bar's Profile slot shows the person's photo (`GET /me/profile`).
    val avatarUrl: String? = null,
    // SHELL-03 counts; a failed refresh keeps the last value (flow 01/16 §6: badges freeze offline).
    val chatUnread: Int = 0,
    val notificationsUnread: Int = 0,
    val activeTasks: Int = 0,
    // HOME-02: the unread count has been read at least once (until then Home shows a skeleton, not 0).
    val notificationsKnown: Boolean = false,
    // HOME-02: active and draft tasks for Home's Follow-up card; null until read.
    val followUps: Int? = null,
    // HOME-02: the last read of each failed (Home shows "—" for a count never read, an error for the profile).
    val notificationsFailed: Boolean = false,
    val followUpsFailed: Boolean = false,
    val profileFailed: Boolean = false,
    // PUSH-03: where a tapped push leads, once found; the shell opens it over Home and clears it.
    val pushGo: NotificationGo? = null,
) {
    val home: ShellTab get() = if (staff) ShellTab.Clinic else ShellTab.Home

    val barTabs: List<ShellTab> get() =
        if (staff) STAFF_TABS else ShellTab.entries.filter { it.inBar }

    val stack: List<ShellRoute> get() = stacks[tab] ?: listOf(ShellRoute.Root(tab))
    val route: ShellRoute get() = stack.last()

    // Pushed screens hide the bar (flow 01/16 §3 target).
    val barVisible: Boolean get() = stack.size == 1 && (!intelligence || overIntelligence.isEmpty())

    val canTasks: Boolean get() = gate.allows(DoctorFeature.RemoteCareTasks)
    val canIntelligence: Boolean get() = gate.allows(DoctorFeature.AskShifaAi)

    private companion object {
        val STAFF_TABS = listOf(ShellTab.Chat, ShellTab.Clinic, ShellTab.Notifications, ShellTab.Profile)
    }
}

/**
 * SHELL-01 (flow 01/16, 01 §6): the tab the doctor is on, each tab's own back stack (kept when switching), the More
 * sheet, and the start tab from the profile, applied once per session after it loads unless the doctor already
 * switched; a start tab the doctor can't use (Tasks below PREMIUM, Clinic without a clinic) falls back to Home.
 */
class ShellViewModel(
    staff: Boolean = false,
    private val profile: ProfileRepository = profileRepository,
    private val clinics: ClinicsRepository = clinicsRepository,
    private val badges: BadgesRepository = BadgesRepository(),
    // PUSH-02/03: a push's row is marked read and its target found as the inbox does it.
    private val notifications: NotificationsRepository = NotificationsRepository(),
    private val opener: NotificationOpener = NotificationOpener(),
    private val pushes: Flow<Map<String, String>> = Push.received,
    private val profileWaitMs: Long = PROFILE_WAIT_MS,
    // Leaving Shifa Intelligence: bookings, moves and cancellations made in the chat change the schedule, so today's
    // calendar reads again and every screen showing visits hears of it (flow 03 README §4).
    private val scheduleRefresh: suspend () -> Unit = {
        calendarRepository.changed(PracticeClock(profileRepository.me.value?.profile?.timeZone))
    },
) : ViewModel() {
    private var chatPolling: Job? = null
    private var followUpsRefresh: Job? = null
    private var notificationsRefresh: Job? = null
    private var resumeRefresh: Job? = null

    private val _state = MutableStateFlow(ShellUiState(staff = staff))
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    init {
        // However Shifa Intelligence is left (its back, a tab tap, a route), the schedule it may have changed is read
        // again once.
        viewModelScope.launch {
            _state.map { it.intelligence }.distinctUntilChanged().drop(1).filter { !it }.collect { scheduleRefresh() }
        }
    }

    private var switched = false

    // A gated tab picked before its gate is known (Tasks: the plan; Clinic: membership); opened once allowed.
    private var pending: ShellTab? = null

    init {
        // CLINIC-01: the Clinic entry follows every read of the memberships (the workspace's too), not only the shell's.
        viewModelScope.launch {
            clinics.state.collect { s -> if (s.known && !staff) _state.update { it.copy(hasClinic = s.hasWorkspace) } }
        }
        // CHAT-07: a conversation read in the thread or the list drops the Chat badge at once.
        // NOTIF-02: read in the inbox, the bell's count again.
        viewModelScope.launch { NotificationReads.changed.collect { refreshNotifications() } }
        // PUSH-02: a push while the app is open: the bell's count, and what the push is about.
        viewModelScope.launch {
            pushes.collect { data ->
                refreshNotifications()
                val type = data["type"].orEmpty().uppercase()
                if (type.startsWith("APPOINTMENT_")) scheduleRefresh()
                if (type.contains("MESSAGE")) readChatUnread()
            }
        }
        viewModelScope.launch {
            ChatReads.changed.collect {
                readChatUnread()
            }
        }
        viewModelScope.launch {
            // Staff never call /doctors/me (it answers 403) and have no saved start tab (flow 01/18 §2, §4).
            if (staff) {
                val me = profile.meProfile().getOrNull()
                return@launch _state.update { it.copy(avatarUrl = me?.photoUrl, hasClinic = true) }
            }
            val clinicResult = clinics.mine()
            _state.update { it.copy(hasClinic = clinicResult.getOrNull()?.isNotEmpty() ?: false) }
            val me = readProfile()
            _state.update { it.copy(gate = FeatureGate.of(me?.subscription)) }
            val waiting = pending
            pending = null
            when {
                waiting != null -> _state.update { it.copy(tab = clamp(waiting, it)) }
                !switched -> {
                    val start = ShellTab.fromKey(me?.settings?.defaultStartTab) ?: ShellTab.Home
                    _state.update { it.copy(tab = clamp(start, it)) }
                }
            }
            // Loaded once per shell (01 §6.3), for doctors whose plan has Tasks.
            if (_state.value.canTasks) {
                badges.activeTasks().onSuccess { n -> _state.update { it.copy(activeTasks = n) } }
            }
            // Home's first focus may come before the plan is known.
            refreshFollowUps()
            // Any later profile read (a resume refresh, a Profile save) may bring a new plan.
            profile.me.collect { me -> if (me != null) onPlan(FeatureGate.of(me.subscription)) }
        }
    }

    /** `GET /doctors/me`; failed only while no profile is known at all (Home can't pick its day without one). */
    private suspend fun readProfile() =
        profile.refresh().getOrNull().also { me ->
            _state.update { it.copy(profileFailed = me == null && profile.me.value == null) }
        }

    /**
     * A new plan seen after the start: gated entries follow it, and a doctor on Tasks without Tasks any more goes Home
     * with "Tasks are not available on your plan." (flow 01/16 §7 target; Flutter bounced silently).
     */
    private fun onPlan(gate: FeatureGate) {
        if (gate == _state.value.gate) return
        val gainedTasks = !_state.value.canTasks && gate.allows(DoctorFeature.RemoteCareTasks)
        val lostTasks = _state.value.tab == ShellTab.Tasks && !gate.allows(DoctorFeature.RemoteCareTasks)
        val lostAi = !gate.allows(DoctorFeature.AskShifaAi)
        _state.update {
            it.copy(gate = gate, tab = if (lostTasks) it.home else it.tab, intelligence = it.intelligence && !lostAi)
        }
        if (lostTasks) ShellNotice.pending.value = Notice(Res.string.tasksNotOnPlan)
        // Home's Follow-up card appears with the plan: its count is read then.
        if (gainedTasks) refreshFollowUps()
    }

    /**
     * A bar or More tap. Another tab: switch, its stack kept. The current tab again: back to its root (flow 01/16 §7
     * target; scrolling to the top is the screen's).
     */
    fun select(tab: ShellTab) {
        switched = true
        ShellNotice.dismissSuccess()
        val s0 = _state.value
        val unknownGate =
            (tab == ShellTab.Tasks && !s0.gate.known) || (tab == ShellTab.Clinic && s0.hasClinic == null)
        if (!s0.staff && unknownGate) {
            pending = tab
            return closeMore()
        }
        pending = null
        // Any tab tap leaves Shifa Intelligence (01 §5.4); a tap on the tab under it just closes it.
        if (s0.intelligence) {
            return _state.update {
                it.copy(
                    intelligence = false,
                    moreOpen = false,
                    tab = clamp(tab, it),
                )
            }
        }
        _state.update { s ->
            val target = clamp(tab, s)
            if (target == s.tab) {
                s.copy(stacks = s.stacks - target, moreOpen = false)
            } else {
                s.copy(tab = target, moreOpen = false)
            }
        }
    }

    /**
     * A task from a notification (flow 07/12 §2): the Tasks tab with it over the list, so Back and a cancel land on the
     * list (which reads again). Without the plan (BASIC, PRO) the shell's "not on your plan" note instead.
     */
    fun openTask(
        id: Long,
        opened: Long,
    ) {
        if (!_state.value.canTasks) {
            ShellNotice.pending.value = Notice(Res.string.tasksNotOnPlan)
            return
        }
        switched = true
        ShellNotice.dismissSuccess()
        _state.update { s ->
            s.copy(
                stacks =
                    s.stacks + (
                        ShellTab.Tasks to
                            listOf(
                                ShellRoute.Root(ShellTab.Tasks),
                                ShellRoute.Task(id, opened),
                            )
                    ),
                tab = ShellTab.Tasks,
                moreOpen = false,
                intelligence = false,
            )
        }
    }

    /** The Calendar tab at its root, whatever it had open (the inbox's "Open Calendar", flow 07/08 §7). */
    fun openCalendar() {
        switched = true
        ShellNotice.dismissSuccess()
        _state.update {
                s ->
            // From a Shifa Intelligence card too: the calendar replaces Intelligence.
            s.copy(
                stacks = s.stacks - ShellTab.Calendar,
                tab = clamp(ShellTab.Calendar, s),
                moreOpen = false,
                intelligence = false,
            )
        }
    }

    /**
     * CHAT-07 (flow 07/07 §5, §10): the Chat tab with this patient's thread over its list, whatever the Chat stack held;
     * the patient stays in the Patients tab's stack, and Back from the thread leads to the list.
     */
    fun openChatWithPatient(route: ShellRoute.PatientChat) {
        switched = true
        ShellNotice.dismissSuccess()
        _state.update { s ->
            s.copy(
                stacks = s.stacks + (ShellTab.Chat to listOf(ShellRoute.Root(ShellTab.Chat), route)),
                tab = ShellTab.Chat,
                moreOpen = false,
                intelligence = false,
            )
        }
    }

    /**
     * HOME-05 (flow 02/07 §10): the search closes and [patientId] opens in the Patients tab over its directory, so Back
     * leads to the directory, not to the search.
     */
    fun openPatientFromSearch(patientId: Long) {
        switched = true
        ShellNotice.dismissSuccess()
        _state.update { s ->
            val withoutSearch = s.stack.filterNot { it is ShellRoute.Search }
            val patients = listOf(ShellRoute.Root(ShellTab.Patients), ShellRoute.Patient(patientId))
            s.copy(
                stacks = s.stacks + (s.tab to withoutSearch) + (ShellTab.Patients to patients),
                tab = ShellTab.Patients,
                moreOpen = false,
            )
        }
    }

    /** Opens [route] on the current tab; like a tab tap, it means the saved start tab no longer applies. */
    fun push(route: ShellRoute) {
        switched = true
        ShellNotice.dismissSuccess()
        _state.update { s ->
            if (s.intelligence) {
                s.copy(overIntelligence = s.overIntelligence + route, moreOpen = false)
            } else {
                s.copy(stacks = s.stacks + (s.tab to s.stack + route), moreOpen = false)
            }
        }
    }

    /**
     * The app is in front (SHELL-03, 01 §6.3): the chat count is polled every 10 seconds until [onBackground]; nothing
     * is polled behind the app's back.
     */
    fun onForeground() {
        chatPolling?.cancel()
        chatPolling =
            viewModelScope.launch {
                while (isActive) {
                    readChatUnread()
                    delay(CHAT_POLL)
                }
            }
    }

    // The poll and a read's refresh both ask: only the newest question's answer is shown, so a slower answer from
    // before the read can't put the old count back.
    private var chatAsked = 0

    private suspend fun readChatUnread() {
        val asked = ++chatAsked
        badges.chatUnread().onSuccess { n -> if (asked == chatAsked) _state.update { it.copy(chatUnread = n) } }
    }

    fun onBackground() {
        chatPolling?.cancel()
        chatPolling = null
    }

    /**
     * Each return to the app, including a pause that never stopped it (`ON_RESUME`): the notifications count. A newer
     * refresh cancels an older one still out, so a late answer can't put an old count back.
     */
    fun refreshNotifications() {
        notificationsRefresh?.cancel()
        notificationsRefresh =
            viewModelScope.launch {
                badges
                    .notificationsUnread()
                    .onSuccess { n ->
                        _state.update {
                            it.copy(
                                notificationsUnread = n,
                                notificationsKnown = true,
                                notificationsFailed = false,
                            )
                        }
                    }.onFailure { _state.update { it.copy(notificationsFailed = true) } }
            }
    }

    /** Home's pull-to-refresh and focus (HOME-02): its two counts read again with its schedule. */
    fun refreshHomeCounts() {
        refreshNotifications()
        // Home's schedule waits for the practice zone: a profile that failed to load is read again here.
        if (!_state.value.staff && profile.me.value == null) viewModelScope.launch { readProfile() }
        refreshFollowUps()
    }

    private fun refreshFollowUps() {
        if (!_state.value.canTasks) return
        followUpsRefresh?.cancel()
        followUpsRefresh =
            viewModelScope.launch {
                badges.followUps().fold(
                    { n -> _state.update { it.copy(followUps = n, followUpsFailed = false) } },
                    { _state.update { it.copy(followUpsFailed = true) } },
                )
            }
    }

    /**
     * PUSH-03 (07 §2.3): a tapped push, from the foreground, the background or a cold start, opens what its inbox row
     * would open, found the same way (an appointment on its practice day); the row is read at once, best effort.
     * Without a row id: the inbox. An appointment that's gone leaves Home with the shell's notice.
     */
    fun openPush(data: Map<String, String>) {
        val staff = _state.value.staff
        val mine = ++pushTaps // a later tap wins over a slower earlier one
        viewModelScope.launch {
            val n = AppNotification.fromPush(data, currentInstant())
            if (n == null) {
                return@launch _state.update {
                    if (mine == pushTaps) {
                        it.copy(
                            pushGo = NotificationGo.Inbox,
                        )
                    } else {
                        it
                    }
                }
            }
            // On the side: a slow network never holds the target back.
            launch { notifications.markRead(n.id) }
            // Only an appointment needs the doctor's zone (its practice day); on a cold start the profile may still be
            // loading.
            val zone =
                if (staff || !opener.slow(n, staff)) {
                    null
                } else {
                    withTimeoutOrNull(profileWaitMs) { profile.me.filterNotNull().first() }?.profile?.timeZone
                }
            val go = opener.go(n, staff, PracticeClock(zone)) ?: NotificationGo.Inbox.takeIf { !opener.slow(n, staff) }
            if (mine != pushTaps) return@launch
            // A gone appointment: Home, under the notice the opener left.
            if (go == null) pushOpened() else _state.update { it.copy(pushGo = go) }
        }
    }

    private var pushTaps = 0

    /** The shell took [ShellUiState.pushGo]: Home at its root, to open it over. */
    fun pushOpened() {
        switched = true
        _state.update {
            it.copy(
                pushGo = null,
                tab = it.home,
                stacks = it.stacks - it.home,
                moreOpen = false,
                intelligence = false,
                overIntelligence = emptyList(),
            )
        }
    }

    fun openMore() = _state.update { it.copy(moreOpen = true) }

    fun closeMore() = _state.update { it.copy(moreOpen = false) }

    /** More → Shifa Intelligence, when the plan includes it (`ASK_SHIFA_AI`). */
    fun openIntelligence() =
        _state.update {
            if (it.canIntelligence) {
                it.copy(
                    intelligence = true,
                    overIntelligence = emptyList(),
                    moreOpen = false,
                )
            } else {
                it.copy(moreOpen = false)
            }
        }

    /** Back from Shifa Intelligence's welcome: the clinic again (its appointments are read again on any exit). */
    fun leaveIntelligence() = _state.update { it.copy(intelligence = false) }

    /**
     * Back in front (01 §6.6): the profile (a changed plan reaches the gates), clinic membership and the
     * notifications count are read again. Feature screens refresh their own data.
     */
    fun onResume() {
        refreshNotifications()
        if (_state.value.staff) return
        // A newer resume cancels the older refresh still out; the profile also drops reads older than its latest.
        resumeRefresh?.cancel()
        resumeRefresh =
            viewModelScope.launch {
                clinics.mine().onSuccess { list -> _state.update { it.copy(hasClinic = list.isNotEmpty()) } }
                readProfile()
            }
    }

    /**
     * System Back (flow 01/16 §7 target): close More; pop a pushed screen; from another tab's root go Home. False on
     * Home's root: the app may close.
     */
    fun back(): Boolean {
        val s = _state.value
        when {
            s.moreOpen -> closeMore()
            s.intelligence && s.overIntelligence.isNotEmpty() ->
                _state.update { it.copy(overIntelligence = it.overIntelligence.dropLast(1)) }
            s.intelligence -> _state.update { it.copy(intelligence = false) }
            s.stack.size > 1 -> _state.update { it.copy(stacks = it.stacks + (it.tab to it.stack.dropLast(1))) }
            s.tab != s.home -> _state.update { it.copy(tab = it.home) }
            else -> return false
        }
        return true
    }

    private fun clamp(
        tab: ShellTab,
        s: ShellUiState,
    ): ShellTab =
        when {
            s.staff -> if (tab in s.barTabs) tab else s.home
            tab == ShellTab.Tasks && !s.canTasks -> ShellTab.Home
            tab == ShellTab.Clinic && s.hasClinic != true -> ShellTab.Home
            else -> tab
        }

    private companion object {
        val CHAT_POLL = 10.seconds
    }
}

// PUSH-03: how long a cold start's tap waits for the profile (the practice time zone) before using the device's.
private const val PROFILE_WAIT_MS = 5_000L
