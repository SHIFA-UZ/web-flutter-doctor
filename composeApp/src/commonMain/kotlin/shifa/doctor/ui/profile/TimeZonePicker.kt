package shifa.doctor.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.changeTimezoneConfirm
import shifa.doctor.generated.resources.changeTimezoneDayBefore
import shifa.doctor.generated.resources.changeTimezoneDevice
import shifa.doctor.generated.resources.changeTimezoneExample
import shifa.doctor.generated.resources.changeTimezoneFromTo
import shifa.doctor.generated.resources.changeTimezoneNextDay
import shifa.doctor.generated.resources.changeTimezoneSameOffset
import shifa.doctor.generated.resources.changeTimezoneTitle
import shifa.doctor.generated.resources.current
import shifa.doctor.generated.resources.detectedOnThisDevice
import shifa.doctor.generated.resources.ic_globe_22
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.noTimezoneFound
import shifa.doctor.generated.resources.practiceTimezone
import shifa.doctor.generated.resources.practiceTimezoneSaved
import shifa.doctor.generated.resources.searchTimezone
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaPickerSheet
import shifa.doctor.utils.TimeZones
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayMinutes
import shifa.doctor.utils.tr

/**
 * The practice-timezone list (flow 08/05 §3), shared by registration and the profile: each row the city, then
 * "Region/City · UTC+5" (offset now), the device's zone and the [selected] one marked. Host it in a sheet.
 */
@Composable
internal fun TimeZonePickerSheet(
    query: String,
    onQuery: (String) -> Unit,
    zones: List<String>,
    selected: String?,
    detected: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val device = tr(Res.string.detectedOnThisDevice)
    val current = tr(Res.string.current)
    ShifaPickerSheet(
        tr(Res.string.practiceTimezone),
        query,
        onQuery,
        tr(Res.string.searchTimezone),
        zones.map { zone ->
            val marker =
                when (zone) {
                    detected -> device
                    selected -> current
                    else -> null
                }
            val details = listOfNotNull(zone, TimeZones.utcLabel(zone)).joinToString(" · ")
            OptionItem(
                TimeZones.city(zone),
                selected = zone == selected,
                sub = listOfNotNull(marker, details).joinToString(" · "),
            )
        },
        onPick = { onPick(zones[it]) },
        onClose = onClose,
        // Shown only when nothing matches.
        loadingText = tr(Res.string.noTimezoneFound),
    )
}

data class TimezoneUiState(
    val current: String? = null,
    val device: String? = null,
    val query: String = "",
    val saving: Boolean = false,
    // The zone waiting for "Change practice timezone?" (flow 08/06).
    val confirm: String? = null,
    val banner: CodeBanner? = null,
    val done: Boolean = false,
    // Set from open until the sheet closes, so a rotation (the composition starting again) doesn't reset it.
    val opened: Boolean = false,
) {
    val zones: List<String> get() = TimeZones.options(query, device, current = current)
}

/**
 * PROF-06 (flows 08/05, 08/06): the practice-timezone sheet over the Profile root or Contact. A first zone saves at
 * once; replacing one asks first, since every slot and appointment is then shown in the new zone. `PATCH
 * /doctors/me/profile {"timeZone"}`; the same zone again just closes.
 */
class TimezoneViewModel(
    private val profile: ProfileRepository = profileRepository,
    private val deviceZone: () -> String? = { TimeZones.device() },
) : ViewModel() {
    private val _state = MutableStateFlow(TimezoneUiState())
    val state: StateFlow<TimezoneUiState> = _state.asStateFlow()

    /** A sheet already open (rotation) or still saving (closed and reopened) keeps its state. */
    fun open() {
        val s = _state.value
        if (s.opened) return
        if (s.saving) return _state.update { it.copy(opened = true) }
        _state.value =
            TimezoneUiState(
                current = profile.me.value?.profile?.timeZone,
                device = deviceZone(),
                opened = true,
            )
    }

    fun closed() = _state.update { it.copy(opened = false, done = false) }

    fun query(q: String) = _state.update { it.copy(query = q) }

    fun pick(zone: String) {
        val s = _state.value
        when {
            s.saving -> Unit
            zone == s.current -> _state.update { it.copy(done = true) }
            s.current.isNullOrBlank() -> save(zone)
            else -> _state.update { it.copy(confirm = zone) }
        }
    }

    fun confirm() {
        val s = _state.value
        if (!s.saving) s.confirm?.let(::save)
    }

    fun cancelConfirm() = _state.update { it.copy(confirm = null) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    private fun save(zone: String) {
        // The warning stays up while it saves; a failure shows in it and Confirm retries (flow 08/06 §5).
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            profile.update(ProfileUpdateRequest(timeZone = zone)).fold(
                onSuccess = {
                    ShellNotice.pending.value = Notice(Res.string.practiceTimezoneSaved, success = true)
                    _state.update { it.copy(saving = false, current = zone, confirm = null, done = true) }
                },
                // The sheet stays with the old zone selected (flow 08/05 §5).
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.networkError), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }
}

/** The sheet itself, opened in place by the screen beneath; [onClose] when it is done or dismissed. */
@Composable
fun PracticeTimezoneSheet(
    onClose: () -> Unit,
    viewModel: TimezoneViewModel = viewModel { TimezoneViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closeSheet by rememberUpdatedState(onClose)
    val close = {
        viewModel.closed()
        closeSheet()
    }
    LaunchedEffect(Unit) { viewModel.open() }
    LaunchedEffect(state.done) { if (state.done) close() }
    ProfileSheet(close) {
        androidx.compose.foundation.layout.Column {
            if (state.confirm == null) codeBanner(state.banner, viewModel::dismissBanner)?.invoke()
            TimeZonePickerSheet(
                state.query,
                viewModel::query,
                state.zones,
                state.current,
                state.device,
                viewModel::pick,
                close,
            )
        }
    }
    state.confirm?.let { zone ->
        ProfileSheet(viewModel::cancelConfirm) {
            ShifaDialogSheet(
                tr(Res.string.changeTimezoneTitle),
                tr(Res.string.changeTimezoneConfirm),
                viewModel::confirm,
                body =
                    listOfNotNull(
                        changeWarning(state.current.orEmpty(), zone, state.device),
                        state.banner?.let { b -> listOfNotNull(tr(b.text), b.requestId).joinToString("\n") },
                    ).joinToString("\n\n"),
                icon = Res.drawable.ic_globe_22,
                confirmLoading = state.saving,
                cancelText = tr(Res.string.cancel),
                onCancel = viewModel::cancelConfirm,
            )
        }
    }
}

/** Flow 08/06 §3.2: from → to, what 09:00 becomes, and the phone's zone when it differs from the new one. */
@Composable
private fun changeWarning(
    from: String,
    to: String,
    device: String?,
): String {
    fun label(zone: String) = TimeZones.utcLabel(zone)?.let { "$zone ($it)" } ?: zone
    val shown = nineOClockIn(from, to)
    val example =
        when {
            // An unreadable zone: no example rather than a wrong one (flow 08/06 §8).
            shown == null -> null
            shown.first == "09:00" && shown.second == 0 -> tr(Res.string.changeTimezoneSameOffset)
            else -> {
                val time =
                    when (shown.second) {
                        -1 -> tr(Res.string.changeTimezoneDayBefore, shown.first)
                        1 -> tr(Res.string.changeTimezoneNextDay, shown.first)
                        else -> shown.first
                    }
                tr(Res.string.changeTimezoneExample, time)
            }
        }
    val lines =
        listOfNotNull(
            tr(Res.string.changeTimezoneFromTo, label(from), label(to)),
            example,
            device?.takeIf { it != to }?.let { tr(Res.string.changeTimezoneDevice, label(it)) },
        )
    return lines.joinToString("\n\n")
}

/**
 * 09:00 shifted by the two zones' offset difference now (the offsets the warning shows), as "HH:mm" and the day shift
 * (−1, 0, +1); null when either id isn't a zone.
 */
internal fun nineOClockIn(
    from: String,
    to: String,
    now: kotlinx.datetime.Instant = currentInstant(),
): Pair<String, Int>? =
    runCatching {
        val diff = TimeZone.of(to).offsetAt(now).totalSeconds - TimeZone.of(from).offsetAt(now).totalSeconds
        val minutes = 9 * 60 + diff / 60
        val day = minutes.floorDiv(24 * 60)
        val m = minutes.mod(24 * 60)
        displayMinutes(m) to day
    }.getOrNull()
