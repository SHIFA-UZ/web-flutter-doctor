package shifa.doctor.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository

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

    /** More → Shifa Intelligence (area 03 builds it). */
    data object Intelligence : ShellRoute

    /** Profile → Debug menu, debug builds only. */
    data object Debug : ShellRoute
}

/** The plan's gates the shell needs before SHELL-04's full feature gating: unknown means "not yet" (D-03). */
enum class Plan { Unknown, Basic, Pro, Premium }

data class ShellUiState(
    val tab: ShellTab = ShellTab.Home,
    val stacks: Map<ShellTab, List<ShellRoute>> = emptyMap(),
    val moreOpen: Boolean = false,
    // Null until `GET /me/clinics` answers; a failure counts as no clinic for this session (flow 01/16 §6).
    val hasClinic: Boolean? = null,
    val plan: Plan = Plan.Unknown,
) {
    val stack: List<ShellRoute> get() = stacks[tab] ?: listOf(ShellRoute.Root(tab))
    val route: ShellRoute get() = stack.last()

    // Pushed screens hide the bar (flow 01/16 §3 target).
    val barVisible: Boolean get() = stack.size == 1

    val canTasks: Boolean get() = plan == Plan.Premium
    val canIntelligence: Boolean get() = plan == Plan.Pro || plan == Plan.Premium
}

/**
 * SHELL-01 (flow 01/16, 01 §6): the tab the doctor is on, each tab's own back stack (kept when switching), the More
 * sheet, and the start tab from the profile, applied once per session after it loads unless the doctor already
 * switched; a start tab the doctor can't use (Tasks below PREMIUM, Clinic without a clinic) falls back to Home.
 */
class ShellViewModel(
    private val profile: ProfileRepository = profileRepository,
    private val clinics: ClinicsRepository = ClinicsRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    private var switched = false

    // A gated tab picked before its gate is known (Tasks: the plan; Clinic: membership); opened once allowed.
    private var pending: ShellTab? = null

    init {
        viewModelScope.launch {
            val clinicResult = clinics.mine()
            _state.update { it.copy(hasClinic = clinicResult.getOrNull()?.isNotEmpty() ?: false) }
            val me = profile.refresh().getOrNull()
            _state.update { it.copy(plan = planOf(me?.subscription?.tier)) }
            val waiting = pending
            pending = null
            when {
                waiting != null -> _state.update { it.copy(tab = clamp(waiting, it)) }
                !switched -> {
                    val start = ShellTab.fromKey(me?.settings?.defaultStartTab) ?: ShellTab.Home
                    _state.update { it.copy(tab = clamp(start, it)) }
                }
            }
        }
    }

    /**
     * A bar or More tap. Another tab: switch, its stack kept. The current tab again: back to its root (flow 01/16 §7
     * target; scrolling to the top is the screen's).
     */
    fun select(tab: ShellTab) {
        switched = true
        val s0 = _state.value
        if ((tab == ShellTab.Tasks && s0.plan == Plan.Unknown) || (tab == ShellTab.Clinic && s0.hasClinic == null)) {
            pending = tab
            return closeMore()
        }
        pending = null
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
        _state.update { s -> s.copy(stacks = s.stacks + (s.tab to s.stack + route), moreOpen = false) }
    }

    fun openMore() = _state.update { it.copy(moreOpen = true) }

    fun closeMore() = _state.update { it.copy(moreOpen = false) }

    /**
     * System Back (flow 01/16 §7 target): close More; pop a pushed screen; from another tab's root go Home. False on
     * Home's root: the app may close.
     */
    fun back(): Boolean {
        val s = _state.value
        when {
            s.moreOpen -> closeMore()
            s.stack.size > 1 -> _state.update { it.copy(stacks = it.stacks + (it.tab to it.stack.dropLast(1))) }
            s.tab != ShellTab.Home -> _state.update { it.copy(tab = ShellTab.Home) }
            else -> return false
        }
        return true
    }

    private fun clamp(
        tab: ShellTab,
        s: ShellUiState,
    ): ShellTab =
        when {
            tab == ShellTab.Tasks && !s.canTasks -> ShellTab.Home
            tab == ShellTab.Clinic && s.hasClinic != true -> ShellTab.Home
            else -> tab
        }

    private fun planOf(tier: String?) =
        when (tier?.uppercase()) {
            "BASIC" -> Plan.Basic
            "PRO" -> Plan.Pro
            "PREMIUM" -> Plan.Premium
            else -> Plan.Unknown
        }
}
