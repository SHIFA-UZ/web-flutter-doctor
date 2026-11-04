package shifa.doctor.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.locations.LocationsRepository
import shifa.doctor.data.locations.locationsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DoctorLocation
import shifa.doctor.data.model.LocationRequest
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.addLocation
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.city
import shifa.doctor.generated.resources.clinic
import shifa.doctor.generated.resources.delete
import shifa.doctor.generated.resources.deleteLocation
import shifa.doctor.generated.resources.deleteLocationConfirm
import shifa.doctor.generated.resources.editLocation
import shifa.doctor.generated.resources.exampleMainClinic
import shifa.doctor.generated.resources.ic_pin_22
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.label
import shifa.doctor.generated.resources.labelRequired
import shifa.doctor.generated.resources.locationDeleteBlocked
import shifa.doctor.generated.resources.locationDeleteFailed
import shifa.doctor.generated.resources.locationDeleted
import shifa.doctor.generated.resources.locationSaveFailed
import shifa.doctor.generated.resources.locationSaved
import shifa.doctor.generated.resources.locationsLoadFailed
import shifa.doctor.generated.resources.manageLocations
import shifa.doctor.generated.resources.noLocationsYet
import shifa.doctor.generated.resources.primary
import shifa.doctor.generated.resources.primaryLocationHint
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.setAsPrimary
import shifa.doctor.generated.resources.streetAddress
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.BackStep
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileForm
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.profile.backStep
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.utils.tr

/** The editor's fields as typed; [wasPrimary] because the primary can only move by making another one primary. */
data class LocationDraft(
    val id: Long? = null,
    val label: String = "",
    val clinic: String = "",
    val street: String = "",
    val city: String = "",
    val isPrimary: Boolean = false,
    val wasPrimary: Boolean = false,
) {
    /** Blanks go as "" so the backend clears them (a missing key keeps the old value). */
    fun request() =
        LocationRequest(
            label = label.trim(),
            clinic = clinic.trim(),
            address = street.trim(),
            locationStreetAddress = street.trim(),
            locationCity = city.trim(),
            isPrimary = isPrimary,
        )

    companion object {
        fun of(l: DoctorLocation) =
            LocationDraft(
                l.id,
                l.label.orEmpty(),
                l.clinic.orEmpty(),
                (l.locationStreetAddress ?: l.address).orEmpty(),
                l.locationCity.orEmpty(),
                l.isPrimary,
                l.isPrimary,
            )
    }
}

data class LocationsUiState(
    val locations: List<DoctorLocation>? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: CodeBanner? = null,
    val banner: CodeBanner? = null,
    val draft: LocationDraft? = null,
    val original: LocationDraft? = null,
    val labelMissing: Boolean = false,
    val saving: Boolean = false,
    val askDiscard: Boolean = false,
    val askDelete: Boolean = false,
) {
    val dirty: Boolean get() = draft != original
}

/**
 * LOC-1 (flow 04/13, spec 03 §3): the practice locations from the shared [LocationsRepository]: list, add, edit (label
 * required, clinic, street, city, primary) and delete with a confirmation. The form stays open with a spinner while it
 * saves (Flutter closed it first, so errors floated over the list). Map and coordinates come with LOC-2.
 */
class LocationsViewModel(
    private val repo: LocationsRepository = locationsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LocationsUiState())
    val state: StateFlow<LocationsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.locations.collect { list -> _state.update { it.copy(locations = list) } } }
    }

    /** On open, on Retry and on pull-to-refresh; [refreshing] shows the pull indicator over a list already shown. */
    fun load() {
        _state.update { it.copy(loading = it.locations == null, refreshing = it.locations != null, error = null) }
        viewModelScope.launch {
            val result = repo.refresh()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error =
                        result.exceptionOrNull()?.let { e ->
                            CodeBanner(e.toUserMessage(Res.string.locationsLoadFailed), requestId = e.requestId)
                        },
                )
            }
        }
    }

    fun ui(change: (LocationsUiState) -> LocationsUiState) = _state.update(change)

    fun open(location: DoctorLocation?) {
        // The first location becomes the primary anyway (backend), so the switch says so.
        val draft =
            location?.let(
                LocationDraft::of,
            ) ?: LocationDraft(isPrimary = _state.value.locations.isNullOrEmpty())
        _state.update { it.copy(draft = draft, original = draft, labelMissing = false, banner = null) }
    }

    fun edit(change: (LocationDraft) -> LocationDraft) {
        if (_state.value.saving) return
        _state.update { s -> s.copy(draft = s.draft?.let(change), labelMissing = false) }
    }

    fun save() {
        val s = _state.value
        val draft = s.draft ?: return
        if (s.saving) return
        if (draft.label.isBlank()) return _state.update { it.copy(labelMissing = true) }
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            repo.save(draft.id, draft.request()).fold(
                onSuccess = { _ ->
                    _state.update {
                        it.copy(
                            saving = false,
                            draft = null,
                            original = null,
                            banner = CodeBanner(Res.string.locationSaved, error = false),
                        )
                    }
                    // Read again (a primary may have moved); a failed read shows above the list with Retry.
                    load()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            banner =
                                CodeBanner(
                                    e.toUserMessage(Res.string.locationSaveFailed),
                                    requestId = e.requestId,
                                ),
                        )
                    }
                },
            )
        }
    }

    fun delete() {
        val id = _state.value.draft?.id ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            repo.delete(id).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            saving = false,
                            askDelete = false,
                            draft = null,
                            original = null,
                            banner = CodeBanner(Res.string.locationDeleted, error = false),
                        )
                    }
                    // Read again (a primary may have moved); a failed read shows above the list with Retry.
                    load()
                },
                onFailure = { e ->
                    // One 409 for "only one", "has rules" and "has appointments" (BE-34): one message names all three.
                    val text =
                        if (e is ApiException && e.status == 409) {
                            Res.string.locationDeleteBlocked
                        } else {
                            e.toUserMessage(
                                Res.string.locationDeleteFailed,
                            )
                        }
                    _state.update {
                        it.copy(
                            saving = false,
                            askDelete = false,
                            banner = CodeBanner(text, requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    /** Back: in the editor, changes ask first; true when the screen itself may close. */
    fun back(): Boolean {
        val s = _state.value
        if (s.draft == null) return true
        when (backStep(s.saving, s.askDiscard, s.dirty)) {
            BackStep.Close ->
                _state.update {
                    it.copy(
                        draft = null,
                        original = null,
                        askDiscard = false,
                        labelMissing = false,
                    )
                }
            BackStep.Ask -> _state.update { it.copy(askDiscard = true) }
            BackStep.CloseQuestion -> _state.update { it.copy(askDiscard = false) }
            BackStep.Stay -> Unit
        }
        return false
    }
}

class LocationsActions(
    val back: () -> Unit = {},
    val retry: () -> Unit = {},
    val open: (DoctorLocation?) -> Unit = {},
    val ui: ((LocationsUiState) -> LocationsUiState) -> Unit = {},
    val edit: ((LocationDraft) -> LocationDraft) -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LocationsScreen(
    onClose: () -> Unit,
    viewModel: LocationsViewModel = viewModel { LocationsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    BackHandler { if (viewModel.back()) onClose() }
    LocationsContent(
        state,
        LocationsActions(
            back = { if (viewModel.back()) onClose() },
            retry = viewModel::load,
            open = viewModel::open,
            ui = viewModel::ui,
            edit = viewModel::edit,
            save = viewModel::save,
            delete = viewModel::delete,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsContent(
    state: LocationsUiState,
    actions: LocationsActions,
) {
    state.draft?.let { return LocationEditor(state, it, actions) }
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.manageLocations), Modifier.statusBarsPadding(), onBack = actions.back)
        // Lists pull to refresh (project rule); a failed refresh over a shown list says so above it, with Retry.
        PullToRefreshBox(state.refreshing, actions.retry, Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
                verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
            ) {
                codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
                val error = state.error
                val list = state.locations
                if (list != null && error != null) {
                    codeBanner(error) { actions.ui { it.copy(error = null) } }?.invoke()
                    ShifaLink(tr(Res.string.retry), actions.retry)
                }
                when {
                    list == null && state.loading -> ShifaListSkeleton(count = 2)
                    list == null && error != null ->
                        EmptyState(tr(error.text), Res.drawable.ic_pin_22, body = error.requestId) {
                            ShifaSecondaryButton(tr(Res.string.retry), actions.retry)
                        }
                    list.isNullOrEmpty() -> EmptyState(tr(Res.string.noLocationsYet), Res.drawable.ic_pin_22)
                    else -> list.forEach { LocationCard(it) { actions.open(it) } }
                }
            }
        }
        if (state.locations != null) {
            ShifaPrimaryButton(
                tr(Res.string.addLocation),
                { actions.open(null) },
                Modifier.fillMaxWidth().navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
            )
        }
    }
}

/** Label with the Primary badge, then clinic, street and "City, Region, Country" when known (flow 04/13 §3). */
@Composable
private fun LocationCard(
    l: DoctorLocation,
    onClick: () -> Unit,
) = ShifaSection(null) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                l.label?.takeIf { it.isNotBlank() } ?: l.clinic.orEmpty(),
                Modifier.weight(1f, fill = false),
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (l.isPrimary) ProfileChip(tr(Res.string.primary), ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning)
        }
        listOfNotNull(
            l.clinic?.takeIf { it.isNotBlank() },
            (l.locationStreetAddress ?: l.address)?.takeIf { it.isNotBlank() },
            listOfNotNull(l.locationCity, l.locationRegion, l.locationCountry).filter {
                it.isNotBlank()
            }.joinToString(", ").takeIf { it.isNotEmpty() },
        ).forEach { Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary) }
    }
}

/** The target's pushed form (flow 04/13 §3), not Flutter's cramped dialog. */
@Composable
private fun LocationEditor(
    state: LocationsUiState,
    d: LocationDraft,
    actions: LocationsActions,
) {
    val enabled = !state.saving
    ProfileForm(
        tr(if (d.id == null) Res.string.addLocation else Res.string.editLocation),
        actions.back,
        onSave = if (state.dirty && enabled) actions.save else null,
        askDiscard = state.askDiscard,
        onDiscard = { actions.ui { it.copy(draft = null, original = null, askDiscard = false) } },
        onKeep = { actions.ui { it.copy(askDiscard = false) } },
    ) {
        codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        ShifaInputField(
            d.label,
            { v -> actions.edit { it.copy(label = v) } },
            tr(Res.string.label),
            Modifier.fillMaxWidth(),
            supportingText = if (state.labelMissing) tr(Res.string.labelRequired) else tr(Res.string.exampleMainClinic),
            isError = state.labelMissing,
            enabled = enabled,
        )
        ShifaInputField(d.clinic, { v ->
            actions.edit {
                it.copy(clinic = v)
            }
        }, tr(Res.string.clinic), Modifier.fillMaxWidth(), enabled = enabled)
        ShifaInputField(d.street, { v ->
            actions.edit {
                it.copy(street = v)
            }
        }, tr(Res.string.streetAddress), Modifier.fillMaxWidth(), enabled = enabled)
        ShifaInputField(d.city, { v ->
            actions.edit {
                it.copy(city = v)
            }
        }, tr(Res.string.city), Modifier.fillMaxWidth(), enabled = enabled)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(Res.string.setAsPrimary),
                Modifier.weight(1f),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Primary,
            )
            // The primary can't be switched off here; making another one primary moves it.
            ShifaSwitch(d.isPrimary, { on -> if (enabled && !d.wasPrimary) actions.edit { it.copy(isPrimary = on) } })
        }
        if (d.wasPrimary) {
            Text(
                tr(Res.string.primaryLocationHint),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        ShifaPrimaryButton(
            tr(Res.string.save),
            actions.save,
            Modifier.fillMaxWidth(),
            enabled = state.dirty && enabled,
            loading = state.saving,
        )
        if (d.id != null) {
            ShifaSecondaryButton(
                tr(Res.string.deleteLocation),
                { actions.ui { it.copy(askDelete = true) } },
                Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
    if (state.askDelete) {
        ProfileSheet({ if (!state.saving) actions.ui { it.copy(askDelete = false) } }) {
            ShifaDialogSheet(
                tr(Res.string.deleteLocation),
                tr(Res.string.delete),
                actions.delete,
                body = tr(Res.string.deleteLocationConfirm, d.label),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                confirmLoading = state.saving,
                cancelText = tr(Res.string.cancel),
                onCancel = { if (!state.saving) actions.ui { it.copy(askDelete = false) } },
            )
        }
    }
}
