package shifa.doctor.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import shifa.doctor.data.badges.BadgesRepository
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.tasksNotOnPlan
import shifa.doctor.ui.profile.ProfileSection
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

    /** Profile → Debug menu, debug builds only. */
    data object Debug : ShellRoute

    /** Profile → Schedule setup (area 04, SCHED-1 builds it). */
    data object Schedule : ShellRoute

    /** Patients → one patient (PAT-05 builds the detail). */
    data class Patient(
        val id: Long,
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
    ) : ShellRoute

    /** Calendar → a blocked row's panel (CAL-8); [opened] as for [Appointment]. */
    data class Block(
        val entry: CalendarEntry.Blocked,
        val opened: Long,
    ) : ShellRoute

    /** Calendar → book a free slot (CAL-7); [opened] as for [Appointment]. From a patient's record, [patient] is set. */
    data class Booking(
        val slot: CalendarEntry.FreeSlot,
        val opened: Long,
        val patient: shifa.doctor.data.model.PatientOption? = null,
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
) {
    val home: ShellTab get() = if (staff) ShellTab.Clinic else ShellTab.Home

    val barTabs: List<ShellTab> get() =
        if (staff) STAFF_TABS else ShellTab.entries.filter { it.inBar }

    val stack: List<ShellRoute> get() = stacks[tab] ?: listOf(ShellRoute.Root(tab))
    val route: ShellRoute get() = stack.last()

    // Pushed screens hide the bar (flow 01/16 §3 target).
    val barVisible: Boolean get() = stack.size == 1

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
    private val clinics: ClinicsRepository = ClinicsRepository(),
    private val badges: BadgesRepository = BadgesRepository(),
) : ViewModel() {
    private var chatPolling: Job? = null
    private var notificationsRefresh: Job? = null
    private var resumeRefresh: Job? = null

    private val _state = MutableStateFlow(ShellUiState(staff = staff))
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    private var switched = false

    // A gated tab picked before its gate is known (Tasks: the plan; Clinic: membership); opened once allowed.
    private var pending: ShellTab? = null

    init {
        viewModelScope.launch {
            // Staff never call /doctors/me (it answers 403) and have no saved start tab (flow 01/18 §2, §4).
            if (staff) {
                val me = profile.meProfile().getOrNull()
                return@launch _state.update { it.copy(avatarUrl = me?.photoUrl, hasClinic = true) }
            }
            val clinicResult = clinics.mine()
            _state.update { it.copy(hasClinic = clinicResult.getOrNull()?.isNotEmpty() ?: false) }
            val me = profile.refresh().getOrNull()
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
            // Any later profile read (a resume refresh, a Profile save) may bring a new plan.
            profile.me.collect { me -> if (me != null) onPlan(FeatureGate.of(me.subscription)) }
        }
    }

    /**
     * A new plan seen after the start: gated entries follow it, and a doctor on Tasks without Tasks any more goes Home
     * with "Tasks are not available on your plan." (flow 01/16 §7 target; Flutter bounced silently).
     */
    private fun onPlan(gate: FeatureGate) {
        if (gate == _state.value.gate) return
        val lostTasks = _state.value.tab == ShellTab.Tasks && !gate.allows(DoctorFeature.RemoteCareTasks)
        val lostAi = !gate.allows(DoctorFeature.AskShifaAi)
        _state.update {
            it.copy(gate = gate, tab = if (lostTasks) it.home else it.tab, intelligence = it.intelligence && !lostAi)
        }
        if (lostTasks) ShellNotice.pending.value = Notice(Res.string.tasksNotOnPlan)
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

    /** Opens [route] on the current tab; like a tab tap, it means the saved start tab no longer applies. */
    fun push(route: ShellRoute) {
        switched = true
        ShellNotice.dismissSuccess()
        _state.update { s -> s.copy(stacks = s.stacks + (s.tab to s.stack + route), moreOpen = false) }
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
                    badges.chatUnread().onSuccess { n -> _state.update { it.copy(chatUnread = n) } }
                    delay(CHAT_POLL)
                }
            }
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
                badges.notificationsUnread().onSuccess { n -> _state.update { it.copy(notificationsUnread = n) } }
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
                    moreOpen = false,
                )
            } else {
                it.copy(moreOpen = false)
            }
        }

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
                profile.refresh()
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
