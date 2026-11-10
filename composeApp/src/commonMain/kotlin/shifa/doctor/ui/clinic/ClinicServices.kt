package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.model.CatalogItem
import shifa.doctor.data.model.CatalogItemPatch
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.NewCatalogItem
import shifa.doctor.data.model.PlanServiceOption
import shifa.doctor.data.model.mayEditRevenueShare
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.data.treatmentplans.treatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.all
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.clearFilters
import shifa.doctor.generated.resources.clinicServiceActivate
import shifa.doctor.generated.resources.clinicServiceActive
import shifa.doctor.generated.resources.clinicServiceAddTitle
import shifa.doctor.generated.resources.clinicServiceAdded
import shifa.doctor.generated.resources.clinicServiceAllDoctorsToggle
import shifa.doctor.generated.resources.clinicServiceCodeLabel
import shifa.doctor.generated.resources.clinicServiceCodeTaken
import shifa.doctor.generated.resources.clinicServiceCurrencyLabel
import shifa.doctor.generated.resources.clinicServiceDeactivate
import shifa.doctor.generated.resources.clinicServiceDeactivated
import shifa.doctor.generated.resources.clinicServiceDoctorsLoadFailed
import shifa.doctor.generated.resources.clinicServiceEditTitle
import shifa.doctor.generated.resources.clinicServiceInactiveBadge
import shifa.doctor.generated.resources.clinicServiceNameRequired
import shifa.doctor.generated.resources.clinicServiceOptions
import shifa.doctor.generated.resources.clinicServicePickDoctors
import shifa.doctor.generated.resources.clinicServicePickDoctorsError
import shifa.doctor.generated.resources.clinicServicePriceInCurrency
import shifa.doctor.generated.resources.clinicServicePriceInvalid
import shifa.doctor.generated.resources.clinicServicePriceZero
import shifa.doctor.generated.resources.clinicServiceReactivated
import shifa.doctor.generated.resources.clinicServiceSave
import shifa.doctor.generated.resources.clinicServiceSaveFailed
import shifa.doctor.generated.resources.clinicServiceTitleLabel
import shifa.doctor.generated.resources.clinicServicesAssignmentAll
import shifa.doctor.generated.resources.clinicServicesAssignmentNone
import shifa.doctor.generated.resources.clinicServicesDoctorOnlyHint
import shifa.doctor.generated.resources.clinicServicesEditingUnavailable
import shifa.doctor.generated.resources.clinicServicesEmpty
import shifa.doctor.generated.resources.clinicServicesLoadFailed
import shifa.doctor.generated.resources.clinicServicesSearchHint
import shifa.doctor.generated.resources.clinicServicesSourceClinic
import shifa.doctor.generated.resources.clinicServicesSourceDoctor
import shifa.doctor.generated.resources.clinicTableNoFilteredResults
import shifa.doctor.generated.resources.clinicWorkspaceNoDoctors
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.discardChangesBody
import shifa.doctor.generated.resources.discardChangesTitle
import shifa.doctor.generated.resources.edit
import shifa.doctor.generated.resources.ic_edit_18
import shifa.doctor.generated.resources.ic_eye_off_20
import shifa.doctor.generated.resources.ic_info_16
import shifa.doctor.generated.resources.ic_layers_22
import shifa.doctor.generated.resources.ic_more_v_16
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.serviceSaved
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.patients.Choice
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaCheckboxRow
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSwitchRow
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.Money
import shifa.doctor.utils.tr

enum class ServiceStatusFilter { All, Active, Inactive }

/** The add / edit sheet (the board's "svc" sheet): [catalogItemId] null for a new service. */
data class ServiceEditor(
    val catalogItemId: Long? = null,
    val title: String = "",
    val code: String = "",
    val price: String = "",
    // A new service takes the clinic's currency; an edited one keeps its own (the board has no currency picker).
    val currency: String = "UZS",
    val active: Boolean = true,
    val all: Boolean = true,
    val doctorIds: Set<Long> = emptySet(),
    val tried: Boolean = false,
    val busy: Boolean = false,
    val error: CodeBanner? = null,
    val dirty: Boolean = false,
    val askDiscard: Boolean = false,
    // The stored price of an edited service: kept as it is while its field and currency are untouched (UZS tiyin
    // aren't shown).
    val storedMinor: Long? = null,
    val storedCurrency: String? = null,
) {
    // A price of 0 or more (a free service; flow 09/04 §8, the backend's minimum) in the currency's units: UZS whole
    // sums (the app's money rule), others "1500,50" too; "1 500 000" reads.
    val priceMinor: Long?
        get() =
            storedMinor?.takeIf { currency == storedCurrency && price == priceText(it, currency) }
                ?: Money.parse(price, currency)
    val titleOk: Boolean get() = title.isNotBlank()
    val doctorsOk: Boolean get() = all || doctorIds.isNotEmpty()
}

data class ClinicServicesUiState(
    // The unified list (clinic catalogue and the doctors' own services); null until read.
    val services: List<PlanServiceOption>? = null,
    val error: CodeBanner? = null,
    // The clinic catalogue, for editing (who an item applies to); without it clinic rows can't be edited.
    val catalog: List<CatalogItem>? = null,
    val catalogFailed: Boolean = false,
    val query: String = "",
    val status: ServiceStatusFilter = ServiceStatusFilter.All,
    val editor: ServiceEditor? = null,
    // The clinic's doctors for "Select doctors" (members with a doctor profile); read when the editor first needs them.
    val doctors: List<ClinicMember>? = null,
    val doctorsFailed: Boolean = false,
    val menu: PlanServiceOption? = null,
    val toggling: Boolean = false,
    val banner: CodeBanner? = null,
    // "Defined by the doctor in their profile · read-only here" after a doctor row's info button.
    val doctorHint: Boolean = false,
) {
    /** A clinic row's catalogue item; none while the last catalogue read failed (it may be out of date). */
    fun item(option: PlanServiceOption): CatalogItem? =
        option.catalogItemId?.takeUnless { catalogFailed }?.let { id -> catalog?.firstOrNull { it.id == id } }

    /** Status chip, then search over title, code, catalogue number and doctors' names; by title (flow 09/04 §3–7). */
    fun shown(): List<PlanServiceOption> {
        val q = query.trim().lowercase()
        return services.orEmpty()
            .filter {
                status == ServiceStatusFilter.All || (status == ServiceStatusFilter.Active) == it.active
            }.filter { s ->
                q.isEmpty() ||
                    (
                        listOfNotNull(
                            s.title,
                            s.code,
                            s.catalogItemId?.toString(),
                        ) + s.offeredByDoctorNames.filterNotNull()
                    )
                        .any { it.lowercase().contains(q) }
            }.sortedBy { it.title.lowercase() }
    }

    val filtered: Boolean get() = query.isNotBlank() || status != ServiceStatusFilter.All

    /** Another clinic service already has this code (case aside). */
    fun codeTaken(e: ServiceEditor): Boolean {
        val code = e.code.trim()
        return code.isNotEmpty() &&
            catalog.orEmpty().any { it.id != e.catalogItemId && it.code?.equals(code, ignoreCase = true) == true }
    }
}

/**
 * Services (CLINIC-07, 09 §2 row 4, flow 09/04, board `DaClinic` Services): the clinic's catalogue and the doctors'
 * own services in one list (`GET /treatment-plans/plan-services`), the catalogue itself for editing
 * (`GET /treatment-plans/catalog-items`), add / edit (`POST`, `PATCH …/catalog-items/{id}`) and (de)activate. Any
 * member may edit: the backend checks only clinic access (checked 2026-11-06), so the board's rule stands.
 */
class ClinicServicesViewModel(
    private val clinicId: Long,
    private val currency: String = "UZS",
    private val repo: TreatmentPlansRepository = treatmentPlansRepository,
    private val clinics: ClinicsRepository = clinicsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicServicesUiState())
    val state: StateFlow<ClinicServicesUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Both lists; rows already shown stay until the new ones arrive (no flicker after a save). */
    fun load() {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val catalog = async { repo.catalogItems(clinicId) }
            val services = repo.planServices(clinicId)
            val items = catalog.await()
            _state.update { s ->
                s.copy(
                    services = services.getOrNull() ?: s.services,
                    error = services.exceptionOrNull()?.let { failure(it, Res.string.clinicServicesLoadFailed) },
                    catalog = items.getOrNull() ?: s.catalog,
                    catalogFailed = items.isFailure,
                )
            }
        }
    }

    fun query(text: String) = _state.update { it.copy(query = text) }

    fun status(filter: ServiceStatusFilter) = _state.update { it.copy(status = filter) }

    fun clearFilters() = _state.update { it.copy(query = "", status = ServiceStatusFilter.All) }

    fun openAdd() = openEditor(ServiceEditor(currency = currency))

    /** Edit a clinic row (its catalogue item: who it applies to, the code, the price). */
    fun openEdit(option: PlanServiceOption) {
        val item = _state.value.item(option) ?: return
        _state.update { it.copy(menu = null) }
        openEditor(
            ServiceEditor(
                item.id,
                item.title,
                item.code.orEmpty(),
                priceText(item.defaultPriceMinor, item.currency),
                item.currency,
                item.active,
                item.appliesToAllDoctors,
                item.assignedDoctorProfileIds.toSet(),
                storedMinor = item.defaultPriceMinor,
                storedCurrency = item.currency,
            ),
        )
    }

    private fun openEditor(editor: ServiceEditor) {
        _state.update { it.copy(editor = editor, banner = null, doctorHint = false) }
        if (_state.value.doctors == null) loadDoctors()
    }

    /** The clinic's doctors (members with a doctor profile) for "Select doctors". */
    fun loadDoctors() {
        _state.update { it.copy(doctorsFailed = false) }
        viewModelScope.launch {
            clinics.members(clinicId).fold(
                { list ->
                    val doctors =
                        list.filter { it.doctorProfileId != null && mayEditRevenueShare(it.role) }
                            .sortedBy { it.displayName.orEmpty().lowercase() }
                    _state.update { it.copy(doctors = doctors) }
                },
                { _state.update { it.copy(doctorsFailed = true) } },
            )
        }
    }

    fun edit(change: (ServiceEditor) -> ServiceEditor) =
        _state.update { s -> s.copy(editor = s.editor?.let { e -> change(e).copy(dirty = true, error = null) }) }

    /** Close the sheet; with changes, "Discard changes?" first; never while saving. */
    fun close() =
        _state.update { s ->
            val e = s.editor
            when {
                e == null || e.busy -> s
                e.dirty -> s.copy(editor = e.copy(askDiscard = true))
                else -> s.copy(editor = null)
            }
        }

    fun discard() = _state.update { it.copy(editor = null) }

    fun keepEditing() = _state.update { s -> s.copy(editor = s.editor?.copy(askDiscard = false)) }

    fun save() {
        val s = _state.value
        val e = s.editor ?: return
        if (e.busy) return
        val price = e.priceMinor
        if (!e.titleOk || price == null || s.codeTaken(e) || !e.doctorsOk) {
            _state.update { it.copy(editor = e.copy(tried = true)) }
            return
        }
        _state.update { it.copy(editor = e.copy(tried = true, busy = true, error = null)) }
        val code = e.code.trim().uppercase()
        val ids = if (e.all) emptyList() else e.doctorIds.sorted()
        viewModelScope.launch {
            val id = e.catalogItemId
            val result =
                if (id == null) {
                    repo.createCatalogItem(
                        NewCatalogItem(
                            clinicId,
                            code.ifEmpty { null },
                            e.title.trim(),
                            price,
                            e.currency,
                            e.active,
                            e.all,
                            ids,
                        ),
                    )
                } else {
                    // Every field, so the backend keeps what the sheet shows; "" clears the code.
                    repo.patchCatalogItem(
                        id,
                        CatalogItemPatch(
                            code = code,
                            title = e.title.trim(),
                            defaultPriceMinor = price,
                            currency = e.currency,
                            active = e.active,
                            appliesToAllDoctors = if (e.all) true else null,
                            assignedDoctorProfileIds = if (e.all) null else ids,
                        ),
                    )
                }
            result.fold(
                {
                    val done = if (id == null) Res.string.clinicServiceAdded else Res.string.serviceSaved
                    _state.update { it.copy(editor = null, banner = CodeBanner(done, error = false)) }
                    load()
                },
                { err ->
                    _state.update { st ->
                        st.copy(
                            editor =
                                st.editor?.copy(
                                    busy = false,
                                    error = failure(err, Res.string.clinicServiceSaveFailed),
                                ),
                        )
                    }
                },
            )
        }
    }

    fun menu(option: PlanServiceOption?) = _state.update { it.copy(menu = option) }

    /** Deactivate or reactivate a clinic row at once (reversible, no question; flow 09/04 §7). */
    fun toggle(option: PlanServiceOption) {
        val id = option.catalogItemId ?: return
        if (_state.value.toggling) return
        _state.update { it.copy(menu = null, toggling = true, banner = null) }
        viewModelScope.launch {
            repo.patchCatalogItem(id, CatalogItemPatch(active = !option.active)).fold(
                {
                    val done =
                        if (option.active) Res.string.clinicServiceDeactivated else Res.string.clinicServiceReactivated
                    _state.update { s -> s.copy(toggling = false, banner = CodeBanner(done, error = false)) }
                    load()
                },
                { e ->
                    _state.update { it.copy(toggling = false, banner = failure(e, Res.string.clinicServiceSaveFailed)) }
                },
            )
        }
    }

    fun doctorHint(show: Boolean) = _state.update { it.copy(doctorHint = show) }

    fun dismiss() = _state.update { it.copy(banner = null) }

    private fun failure(
        e: Throwable,
        fallback: StringResource,
    ) = CodeBanner(e.toUserMessage(fallback), requestId = e.requestId)
}

/** What the Services content can do (the section wires them to [ClinicServicesViewModel]; previews leave them empty). */
class ServicesActions(
    val load: () -> Unit = {},
    val query: (String) -> Unit = {},
    val status: (ServiceStatusFilter) -> Unit = {},
    val clearFilters: () -> Unit = {},
    val add: () -> Unit = {},
    val edit: (PlanServiceOption) -> Unit = {},
    val change: ((ServiceEditor) -> ServiceEditor) -> Unit = {},
    val save: () -> Unit = {},
    val close: () -> Unit = {},
    val discard: () -> Unit = {},
    val keepEditing: () -> Unit = {},
    val loadDoctors: () -> Unit = {},
    val menu: (PlanServiceOption?) -> Unit = {},
    val toggle: (PlanServiceOption) -> Unit = {},
    val doctorHint: (Boolean) -> Unit = {},
    val dismiss: () -> Unit = {},
)

private fun ClinicServicesViewModel.actions() =
    ServicesActions(
        ::load, ::query, ::status, ::clearFilters, ::openAdd, ::openEdit, ::edit, ::save, ::close, ::discard,
        ::keepEditing, ::loadDoctors, ::menu, ::toggle, ::doctorHint, ::dismiss,
    )

@Composable
internal fun ColumnScope.ServicesSection(
    clinic: MyClinic,
    viewModel: ClinicServicesViewModel =
        viewModel(key = "clinic-services-${clinic.clinicId}") {
            ClinicServicesViewModel(clinic.clinicId, clinic.currency?.takeIf { it.isNotBlank() } ?: "UZS")
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back in front: read again (a colleague may have changed a price); not on top of the first read.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.services != null) viewModel.load() }
    ServicesContent(state, viewModel.actions())
}

/**
 * Search and "Add clinic service", All / Active / Inactive, one card per service (title — grey italic when inactive —
 * code, source, price, who offers it; ⋮ for clinic rows, ⓘ for a doctor's own). Board `DaClinic` Services (phone).
 */
@Composable
internal fun ColumnScope.ServicesContent(
    state: ClinicServicesUiState,
    actions: ServicesActions,
) {
    SectionBanner(state.banner) { actions.dismiss() }
    if (state.doctorHint) {
        ShifaBanner(
            tr(Res.string.clinicServicesDoctorOnlyHint),
            Res.drawable.ic_info_16,
            ShifaBannerType.Success,
            onClose = { actions.doctorHint(false) },
        )
    }
    ShifaInputField(state.query, actions.query, tr(Res.string.clinicServicesSearchHint), Modifier.fillMaxWidth())
    ShifaPrimaryButton(
        tr(Res.string.clinicServiceAddTitle),
        actions.add,
        icon = Res.drawable.ic_plus_16,
        size = ShifaButtonSize.Compact,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        ServiceStatusFilter.entries.forEach { f -> ShifaChip(statusLabel(f), state.status == f, { actions.status(f) }) }
    }
    val services = state.services
    // A failed read says so, also over a list kept from before.
    state.error?.let { e ->
        // Nothing read yet: the failure takes the list's place; over a list read before, a banner above it.
        if (services == null) {
            ShifaLoadError(tr(e.text), actions.load, requestId = e.requestId)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                codeBanner(e)?.let { it() }
                ShifaLink(tr(Res.string.retry), actions.load)
            }
        }
    }
    when {
        services == null && state.error != null -> Unit
        services == null ->
            repeat(SKELETON_ROWS) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S28)) }
        services.isEmpty() -> EmptyState(tr(Res.string.clinicServicesEmpty), Res.drawable.ic_layers_22)
        else -> {
            // The unified list read, the catalogue didn't: clinic rows can't be edited until it's read.
            if (state.catalogFailed) {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                    codeBanner(CodeBanner(Res.string.clinicServicesEditingUnavailable))?.invoke()
                    ShifaLink(tr(Res.string.retry), actions.load)
                }
            }
            val shown = state.shown()
            if (shown.isEmpty()) {
                Text(
                    tr(Res.string.clinicTableNoFilteredResults),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
                if (state.filtered) ShifaLink(tr(Res.string.clearFilters), actions.clearFilters)
            }
            shown.forEach { s -> ServiceCard(s, state, actions) }
        }
    }
    state.menu?.let { MenuSheet(it, state, actions) }
    state.editor?.let { EditorSheet(it, state, actions) }
}

@Composable
private fun statusLabel(f: ServiceStatusFilter) =
    tr(
        when (f) {
            ServiceStatusFilter.All -> Res.string.all
            ServiceStatusFilter.Active -> Res.string.clinicServiceActive
            ServiceStatusFilter.Inactive -> Res.string.clinicServiceInactiveBadge
        },
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCard(
    s: PlanServiceOption,
    state: ClinicServicesUiState,
    actions: ServicesActions,
) = Row(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    verticalAlignment = Alignment.Top,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.title,
                style = ShifaTextStyles.panelTitle(),
                color = if (s.active) ShifaColors.Text.Primary else ShifaColors.Text.Tertiary,
                fontStyle = if (s.active) FontStyle.Normal else FontStyle.Italic,
            )
            s.code?.takeIf { it.isNotBlank() }?.let { ProfileChip(it, ShifaColors.Bg.Canvas, ShifaColors.Text.Primary) }
            if (s.isCatalog) {
                ProfileChip(
                    tr(Res.string.clinicServicesSourceClinic),
                    ShifaColors.Bg.SuccessSubtle,
                    ShifaColors.Text.Primary,
                )
            } else {
                ProfileChip(
                    tr(Res.string.clinicServicesSourceDoctor),
                    ShifaColors.Bg.InfoSubtle,
                    ShifaColors.Text.Primary,
                )
            }
            if (!s.active) {
                ProfileChip(tr(Res.string.clinicServiceInactiveBadge), ShifaColors.Bg.Canvas, ShifaColors.Text.Primary)
            }
        }
        Text(
            Money.format(s.defaultPriceMinor, s.currency),
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
        )
        Text(
            doctorsLine(s, state.item(s)),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    when {
        // Clinic rows are edited through the catalogue; without it (failed read) they have no menu.
        s.isCatalog && state.item(s) != null ->
            ShifaIconButton(
                Res.drawable.ic_more_v_16,
                tr(Res.string.clinicServiceOptions),
                { actions.menu(s) },
                variant = ShifaIconButtonVariant.Secondary,
            )
        !s.isCatalog ->
            ShifaIconButton(
                Res.drawable.ic_info_16,
                tr(Res.string.clinicServicesDoctorOnlyHint),
                { actions.doctorHint(true) },
                variant = ShifaIconButtonVariant.Secondary,
            )
    }
}

/** "All doctors at this clinic", the names, or "No doctors assigned" (a catalogue item's own setting when read). */
@Composable
private fun doctorsLine(
    s: PlanServiceOption,
    item: CatalogItem?,
): String {
    val names = s.offeredByDoctorNames.filterNotNull().filter { it.isNotBlank() }
    return when {
        item?.appliesToAllDoctors == true -> tr(Res.string.clinicServicesAssignmentAll)
        names.isEmpty() -> tr(Res.string.clinicServicesAssignmentNone)
        else -> names.joinToString(", ")
    }
}

@Composable
private fun MenuSheet(
    s: PlanServiceOption,
    state: ClinicServicesUiState,
    actions: ServicesActions,
) = ProfileSheet({ actions.menu(null) }) {
    ShifaTray(s.title, { actions.menu(null) }) {
        ShifaSettingsItem(tr(Res.string.edit), { actions.edit(s) }, icon = Res.drawable.ic_edit_18)
        ShifaSettingsItem(
            tr(if (s.active) Res.string.clinicServiceDeactivate else Res.string.clinicServiceActivate),
            { if (!state.toggling) actions.toggle(s) },
            icon = Res.drawable.ic_eye_off_20,
            destructive = s.active,
            divider = false,
        )
    }
}

@Composable
private fun EditorSheet(
    e: ServiceEditor,
    state: ClinicServicesUiState,
    actions: ServicesActions,
    // A drag or a scrim tap closes only an untouched, idle sheet (the check must stay side-effect free: the sheet also
    // asks it while the keyboard resizes it); with changes, Cancel and Back ask "Discard changes?".
) = ProfileSheet({ actions.close() }, confirmHide = { !e.busy && !e.dirty }) {
    if (e.askDiscard) {
        ShifaDialogSheet(
            tr(Res.string.discardChangesTitle),
            tr(Res.string.discard),
            actions.discard,
            body = tr(Res.string.discardChangesBody),
            tone = DialogTone.Destructive,
            cancelText = tr(Res.string.cancel),
            onCancel = actions.keepEditing,
        )
        return@ProfileSheet
    }
    ServiceEditorForm(e, state, actions)
}

/** The add / edit form in its dialog sheet (the board's "svc" sheet); hosted by [EditorSheet], shown by previews. */
@Composable
internal fun ServiceEditorForm(
    e: ServiceEditor,
    state: ClinicServicesUiState,
    actions: ServicesActions,
) {
    ShifaDialogSheet(
        tr(if (e.catalogItemId == null) Res.string.clinicServiceAddTitle else Res.string.clinicServiceEditTitle),
        tr(Res.string.clinicServiceSave),
        actions.save,
        cancelText = tr(Res.string.cancel),
        onCancel = actions.close,
        confirmLoading = e.busy,
    ) {
        val enabled = !e.busy
        val titleBad = e.tried && !e.titleOk
        ShifaInputField(
            e.title,
            { v -> actions.change { it.copy(title = v.take(TITLE_MAX)) } },
            tr(Res.string.clinicServiceTitleLabel),
            Modifier.fillMaxWidth(),
            supportingText = if (titleBad) tr(Res.string.clinicServiceNameRequired) else null,
            isError = titleBad,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        val taken = state.codeTaken(e)
        ShifaInputField(
            e.code,
            { v -> actions.change { it.copy(code = v.uppercase().take(CODE_MAX)) } },
            tr(Res.string.clinicServiceCodeLabel),
            Modifier.fillMaxWidth(),
            supportingText = if (taken) tr(Res.string.clinicServiceCodeTaken) else null,
            isError = taken,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        // Flow 09/04 §3: the currency beside the price (UZS · USD · EUR · RUB, plus a service's own if it's another).
        val currencies = (CURRENCIES + e.currency.uppercase()).distinct()
        Choice(
            tr(Res.string.clinicServiceCurrencyLabel),
            currencies,
            currencies.indexOf(e.currency.uppercase()),
            enabled,
        ) { i ->
            actions.change { it.copy(currency = currencies[i]) }
        }
        val priceBad = e.tried && e.priceMinor == null
        ShifaInputField(
            e.price,
            { v -> actions.change { it.copy(price = v.filter { c -> c.isDigit() || c in " .," }.take(PRICE_MAX)) } },
            tr(Res.string.clinicServicePriceInCurrency, e.currency),
            Modifier.fillMaxWidth(),
            supportingText =
                when {
                    priceBad -> tr(Res.string.clinicServicePriceInvalid)
                    e.priceMinor == 0L -> tr(Res.string.clinicServicePriceZero)
                    else -> null
                },
            isError = priceBad,
            enabled = enabled,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (e.currency.equals("UZS", true)) KeyboardType.Number else KeyboardType.Decimal,
                ),
        )
        ShifaSwitchRow(tr(Res.string.clinicServiceActive), e.active, { v ->
            if (enabled) {
                actions.change {
                    it.copy(active = v)
                }
            }
        })
        ShifaSwitchRow(
            tr(Res.string.clinicServiceAllDoctorsToggle),
            e.all,
            { v -> if (enabled) actions.change { it.copy(all = v) } },
        )
        if (!e.all) DoctorChecks(e, state, actions, enabled)
        e.error?.let { err -> codeBanner(err)?.let { it() } }
    }
}

@Composable
private fun ColumnScope.DoctorChecks(
    e: ServiceEditor,
    state: ClinicServicesUiState,
    actions: ServicesActions,
    enabled: Boolean,
) {
    Text(
        tr(Res.string.clinicServicePickDoctors),
        style = ShifaTextStyles.buttonCompact(),
        color = ShifaColors.Text.Primary,
    )
    val doctors = state.doctors
    when {
        state.doctorsFailed && doctors == null ->
            ShifaLoadError(tr(Res.string.clinicServiceDoctorsLoadFailed), actions.loadDoctors, compact = true)
        doctors == null -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S16))
        doctors.isEmpty() ->
            Text(
                tr(Res.string.clinicWorkspaceNoDoctors),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        else ->
            doctors.forEach { d ->
                val id = d.doctorProfileId ?: return@forEach
                val on = id in e.doctorIds
                ShifaCheckboxRow(
                    d.displayName?.takeIf { it.isNotBlank() } ?: roleLabel(d.role),
                    if (on) ToggleableState.On else ToggleableState.Off,
                    {
                        if (enabled) {
                            actions.change {
                                it.copy(
                                    doctorIds = if (on) it.doctorIds - id else it.doctorIds + id,
                                )
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
    }
    if (e.tried && !e.doctorsOk) {
        Text(
            tr(Res.string.clinicServicePickDoctorsError),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Error,
        )
    }
}

/** A stored price as the field shows it: whole sums for UZS (tiyin rounded, as shown everywhere), else "12.50". */
internal fun priceText(
    minor: Long,
    currency: String,
): String =
    if (currency.equals("UZS", ignoreCase = true)) {
        ((minor + 50) / 100).toString()
    } else if (minor % 100 == 0L) {
        (minor / 100).toString()
    } else {
        "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
    }

private val CURRENCIES = listOf("UZS", "USD", "EUR", "RUB")
private const val TITLE_MAX = 200
private const val CODE_MAX = 32
private const val PRICE_MAX = 20
