package shifa.doctor.ui.profile

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DoctorLocation
import shifa.doctor.data.model.DoctorService
import shifa.doctor.data.model.ServiceGroup
import shifa.doctor.data.model.ServicePrice
import shifa.doctor.data.model.UpsertService
import shifa.doctor.data.services.ServicesRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.active
import shifa.doctor.generated.resources.addGroup
import shifa.doctor.generated.resources.addPriceRow
import shifa.doctor.generated.resources.addService
import shifa.doctor.generated.resources.clinicServiceCurrencyLabel
import shifa.doctor.generated.resources.clinicServiceInactiveBadge
import shifa.doctor.generated.resources.clinicServicePriceLabel
import shifa.doctor.generated.resources.delete
import shifa.doctor.generated.resources.deleteGroupBody
import shifa.doctor.generated.resources.deleteGroupTitle
import shifa.doctor.generated.resources.deleteServiceTitle
import shifa.doctor.generated.resources.description
import shifa.doctor.generated.resources.editService
import shifa.doctor.generated.resources.failedToLoad
import shifa.doctor.generated.resources.fieldRequired
import shifa.doctor.generated.resources.freeRemovesPrices
import shifa.doctor.generated.resources.groupName
import shifa.doctor.generated.resources.ic_card_22
import shifa.doctor.generated.resources.ic_chev_d_20
import shifa.doctor.generated.resources.ic_chev_u_20
import shifa.doctor.generated.resources.ic_layers_22
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.moveDown
import shifa.doctor.generated.resources.moveUp
import shifa.doctor.generated.resources.newService
import shifa.doctor.generated.resources.noServicesYet
import shifa.doctor.generated.resources.priceScopeAllLocations
import shifa.doctor.generated.resources.priceScopeLabel
import shifa.doctor.generated.resources.removeItem
import shifa.doctor.generated.resources.removePriceRow
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.serviceDeleted
import shifa.doctor.generated.resources.serviceFreeConsultation
import shifa.doctor.generated.resources.serviceGroupLabel
import shifa.doctor.generated.resources.serviceGroupNone
import shifa.doctor.generated.resources.serviceGroups
import shifa.doctor.generated.resources.serviceGroupsHelper
import shifa.doctor.generated.resources.serviceGroupsSaved
import shifa.doctor.generated.resources.serviceManagedByClinic
import shifa.doctor.generated.resources.serviceManagedByClinicShort
import shifa.doctor.generated.resources.servicePriceDuplicate
import shifa.doctor.generated.resources.servicePriceInvalid
import shifa.doctor.generated.resources.servicePricesInvalid
import shifa.doctor.generated.resources.servicePricesSection
import shifa.doctor.generated.resources.serviceSaved
import shifa.doctor.generated.resources.serviceTitleLabel
import shifa.doctor.generated.resources.servicesPricing
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.utils.Money
import shifa.doctor.utils.tr

/** One price row as typed; [amount] is text until Save parses it with the money parser. */
data class PriceRow(
    val amount: String = "",
    val currency: String = "UZS",
    val locationId: Long? = null,
    // The saved amount: sent back as it was while the text still shows it, so a stored "125.50 UZS" isn't rounded.
    val savedMinor: Long? = null,
)

data class ServiceDraft(
    val id: Long? = null,
    val title: String = "",
    val description: String = "",
    val groupId: Long? = null,
    val free: Boolean = false,
    val active: Boolean = true,
    val prices: List<PriceRow> = listOf(PriceRow()),
)

data class ServicesUiState(
    val loading: Boolean = true,
    val error: CodeBanner? = null,
    val services: List<DoctorService> = emptyList(),
    val groups: List<ServiceGroup> = emptyList(),
    val locations: List<DoctorLocation> = emptyList(),
    val banner: CodeBanner? = null,
    // The editor: what it opened with, what it holds now, and its errors.
    val original: ServiceDraft? = null,
    val draft: ServiceDraft? = null,
    val titleError: StringResource? = null,
    val pricesError: StringResource? = null,
    val rowErrors: Map<Int, StringResource> = emptyMap(),
    val groupOpen: Boolean = false,
    val openRow: Pair<Int, Boolean>? = null, // row index, true = currency menu, false = scope menu
    val saving: Boolean = false,
    val askDiscard: Boolean = false,
    val askDelete: DoctorService? = null,
    // The groups editor (SVC-02): the rows as edited, in display order; null when closed.
    val groupsDraft: List<ServiceGroup>? = null,
    val groupErrors: Set<Int> = emptySet(),
    val askDeleteGroup: Int? = null,
) {
    val editing: Boolean get() = draft != null || groupsDraft != null
    val dirty: Boolean get() = if (groupsDraft != null) groupsDraft != groups else draft != original
}

/**
 * SVC-01 (flow 08/08, DaProfile `setServices`): the doctor's services with prices per currency and location, an
 * editor with checked writes (Flutter ignored the status), and a confirmed delete. Clinic-managed rows are read-only.
 */
class ServicesViewModel(
    private val repo: ServicesRepository = ServicesRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ServicesUiState())
    val state: StateFlow<ServicesUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Services, groups and locations together; only the services are required (the others fall back to none). */
    fun load() {
        _state.update { it.copy(loading = it.services.isEmpty(), error = null) }
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() =
        coroutineScope {
            val groups = async { repo.groups().getOrDefault(emptyList()) }
            val locations = async { repo.locations().getOrDefault(emptyList()) }
            repo.services().fold(
                onSuccess = { list ->
                    _state.update {
                        it.copy(
                            loading = false,
                            services = list,
                            groups = groups.await(),
                            locations = locations.await(),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = CodeBanner(e.toUserMessage(Res.string.failedToLoad), requestId = e.requestId),
                        )
                    }
                },
            )
        }

    fun ui(change: (ServicesUiState) -> ServicesUiState) = _state.update(change)

    fun edit(change: (ServiceDraft) -> ServiceDraft) =
        _state.update { s ->
            s.draft?.let {
                s.copy(
                    draft = change(it),
                    titleError = null,
                    pricesError = null,
                    rowErrors = emptyMap(),
                )
            } ?: s
        }

    /** A clinic-managed row only says where it is edited (flow 08/08 §9). */
    fun open(service: DoctorService?) {
        if (service?.clinicManaged == true) {
            return _state.update { it.copy(banner = CodeBanner(Res.string.serviceManagedByClinic, error = false)) }
        }
        val draft =
            service?.let { s ->
                ServiceDraft(
                    s.id,
                    s.title,
                    s.description.orEmpty(),
                    s.groupId,
                    s.isFreeConsultation,
                    s.isActive,
                    s.prices.map {
                        PriceRow(amountText(it), it.currency, it.locationId, it.amountMinor)
                    }.ifEmpty { listOf(PriceRow()) },
                )
            } ?: ServiceDraft()
        _state.update { it.copy(original = draft, draft = draft, banner = null) }
    }

    /** Back in the editor: changes ask first; otherwise back to the list. True when the screen itself may close. */
    fun back(): Boolean {
        val s = _state.value
        if (!s.editing) return true
        when (backStep(s.saving, s.askDiscard, s.dirty)) {
            BackStep.Close -> closeEditor()
            BackStep.Ask -> _state.update { it.copy(askDiscard = true) }
            BackStep.CloseQuestion -> _state.update { it.copy(askDiscard = false) }
            BackStep.Stay -> Unit
        }
        return false
    }

    fun closeEditor() =
        _state.update {
            it.copy(
                groupsDraft = null,
                groupErrors = emptySet(),
                draft = null,
                original = null,
                askDiscard = false,
                titleError = null,
                pricesError = null,
                rowErrors = emptyMap(),
            )
        }

    fun save() {
        val s = _state.value
        val d = s.draft ?: return
        if (s.saving) return
        val title = d.title.trim()
        val titleError = Res.string.fieldRequired.takeIf { title.isEmpty() }
        // Blank rows are skipped; a row that isn't an amount or is 0 is marked.
        val rowErrors = mutableMapOf<Int, StringResource>()
        val prices =
            if (d.free) {
                emptyList()
            } else {
                d.prices.mapIndexedNotNull { i, row ->
                    if (row.amount.isBlank()) return@mapIndexedNotNull null
                    val kept = row.savedMinor?.takeIf { row.amount == amountText(ServicePrice(it, row.currency)) }
                    val minor = kept ?: Money.parse(row.amount, row.currency)
                    if (minor == null || minor <= 0) {
                        rowErrors[i] = Res.string.servicePriceInvalid
                        null
                    } else {
                        ServicePrice(minor, row.currency, row.locationId)
                    }
                }
            }
        val keys = prices.map { it.currency to it.locationId }
        val duplicate = keys.size != keys.distinct().size
        val pricesError =
            when {
                d.free || rowErrors.isNotEmpty() -> null
                prices.isEmpty() -> Res.string.servicePricesInvalid
                duplicate -> Res.string.servicePriceDuplicate
                else -> null
            }
        if (titleError != null || pricesError != null || rowErrors.isNotEmpty()) {
            return _state.update { it.copy(titleError = titleError, pricesError = pricesError, rowErrors = rowErrors) }
        }
        val body = UpsertService(title, d.description.trim().ifEmpty { null }, d.active, d.free, d.groupId, prices)
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            repo.save(d.id, body).fold(
                onSuccess = { saved ->
                    _state.update { st ->
                        val list =
                            if (d.id == null) {
                                st.services + saved
                            } else {
                                st.services.map { if (it.id == saved.id) saved else it }
                            }
                        st.copy(
                            saving = false,
                            services = list,
                            banner = CodeBanner(Res.string.serviceSaved, error = false),
                        )
                    }
                    closeEditor()
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    fun delete() {
        val service = _state.value.askDelete ?: return
        _state.update { it.copy(askDelete = null, banner = null) }
        viewModelScope.launch {
            val result = repo.delete(service.id)
            // Already gone (deleted elsewhere) counts as deleted (flow 08/08 §8).
            val gone = result.isSuccess || result.exceptionOrNull() is ApiException.NotFound
            _state.update { st ->
                if (gone) {
                    val closed =
                        if (st.draft?.id == service.id) {
                            st.copy(
                                draft = null,
                                original = null,
                                askDiscard = false,
                            )
                        } else {
                            st
                        }
                    closed.copy(
                        services = st.services.filterNot { it.id == service.id },
                        banner = CodeBanner(Res.string.serviceDeleted, error = false),
                    )
                } else {
                    val e = result.exceptionOrNull()!!
                    st.copy(banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId))
                }
            }
        }
    }

    /** Each time the screen shows: the list again (the shell keeps this ViewModel), quietly when one is on screen. */
    fun reload() {
        if (!_state.value.editing && !_state.value.loading) load()
    }

    /** SVC-02: the groups as they are, in sort order. */
    fun openGroups() = _state.update { it.copy(groupsDraft = it.groups, banner = null) }

    fun editGroups(change: (List<ServiceGroup>) -> List<ServiceGroup>) =
        _state.update { s ->
            // Not while saving: the requests use what Save saw.
            if (s.saving) s else s.groupsDraft?.let { s.copy(groupsDraft = change(it), groupErrors = emptySet()) } ?: s
        }

    /** "Delete group?" first (flow 08/08 §7); the row goes on Save. */
    fun deleteGroup() =
        _state.update { s ->
            val i = s.askDeleteGroup ?: return@update s
            s.copy(
                askDeleteGroup = null,
                groupsDraft =
                    s.groupsDraft?.filterIndexed {
                            j,
                            _,
                        ->
                        j != i
                    },
                groupErrors = emptySet(),
            )
        }

    /**
     * Deletes, then creates and renames, with the sort order taken from the list's order (1, 2, 3…); one request at a
     * time, stopping at the first failure. Then everything reloads (services show their new group names).
     */
    fun saveGroups() {
        val s = _state.value
        val rows = s.groupsDraft ?: return
        if (s.saving) return
        val empty = rows.indices.filter { rows[it].name.isBlank() }.toSet()
        if (empty.isNotEmpty()) return _state.update { it.copy(groupErrors = empty) }
        val ordered = rows.mapIndexed { i, g -> g.copy(name = g.name.trim(), sortOrder = i + 1) }.toMutableList()
        val removed = s.groups.mapNotNull { it.id }.filter { id -> ordered.none { it.id == id } }
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            var failure: Throwable? =
                removed.firstNotNullOfOrNull {
                        id ->
                    repo.deleteGroup(id).exceptionOrNull()?.takeUnless { it is ApiException.NotFound }
                }
            if (failure == null) {
                for ((i, g) in ordered.withIndex()) {
                    val unchanged = s.groups.any { it.id == g.id && it.name == g.name && it.sortOrder == g.sortOrder }
                    if (g.id != null && unchanged) continue
                    // A created group keeps its new id in the draft, so a retry after a later failure doesn't create it twice.
                    repo.saveGroup(g).fold(onSuccess = {
                            saved ->
                        ordered[i] = g.copy(id = saved.id)
                    }, onFailure = { failure = it })
                    if (failure != null) break
                }
            }
            // Reload before closing, so a reopened editor starts from the server's groups.
            fetch()
            val e = failure
            _state.update {
                if (e == null) {
                    it.copy(
                        saving = false,
                        groupsDraft = null,
                        groupErrors = emptySet(),
                        askDiscard = false,
                        banner = CodeBanner(Res.string.serviceGroupsSaved, error = false),
                    )
                } else {
                    it.copy(
                        saving = false,
                        groupsDraft = ordered,
                        banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                    )
                }
            }
        }
    }

    internal companion object {
        val CURRENCIES = listOf("UZS", "USD", "EUR", "RUB")

        /** A saved amount back to text the parser reads: "150000", "25.50". */
        fun amountText(price: ServicePrice): String {
            val whole = price.amountMinor / 100
            val cents = price.amountMinor % 100
            return if (price.currency.equals(
                    "UZS",
                    true,
                ) || cents == 0L
            ) {
                whole.toString()
            } else {
                "$whole.${cents.toString().padStart(2, '0')}"
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ServicesScreen(
    onClose: () -> Unit,
    viewModel: ServicesViewModel = viewModel { ServicesViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.reload() }
    BackHandler { if (viewModel.back()) onClose() }
    ServicesContent(
        state,
        ServicesActions(
            back = { if (viewModel.back()) onClose() },
            retry = viewModel::load,
            open = viewModel::open,
            ui = viewModel::ui,
            edit = viewModel::edit,
            save = viewModel::save,
            discard = viewModel::closeEditor,
            delete = viewModel::delete,
            groups = viewModel::openGroups,
            editGroups = viewModel::editGroups,
            saveGroups = viewModel::saveGroups,
            deleteGroup = viewModel::deleteGroup,
        ),
    )
}

class ServicesActions(
    val back: () -> Unit = {},
    val retry: () -> Unit = {},
    val open: (DoctorService?) -> Unit = {},
    val ui: ((ServicesUiState) -> ServicesUiState) -> Unit = {},
    val edit: ((ServiceDraft) -> ServiceDraft) -> Unit = {},
    val save: () -> Unit = {},
    val discard: () -> Unit = {},
    val delete: () -> Unit = {},
    val groups: () -> Unit = {},
    val editGroups: ((List<ServiceGroup>) -> List<ServiceGroup>) -> Unit = {},
    val saveGroups: () -> Unit = {},
    val deleteGroup: () -> Unit = {},
)

@Composable
fun ServicesContent(
    state: ServicesUiState,
    actions: ServicesActions,
) {
    state.groupsDraft?.let { return GroupsEditor(state, it, actions) }
    val draft = state.draft
    if (draft != null) return ServiceEditor(state, draft, actions)
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.servicesPricing), Modifier.statusBarsPadding(), onBack = actions.back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            val error = state.error
            when {
                state.loading -> ShifaListSkeleton(count = 4)
                error != null ->
                    EmptyState(tr(error.text), Res.drawable.ic_card_22, body = error.requestId) {
                        ShifaSecondaryButton(tr(Res.string.retry), actions.retry)
                    }
                state.services.isEmpty() -> {
                    GroupsRow(state, actions)
                    EmptyState(tr(Res.string.noServicesYet), Res.drawable.ic_card_22)
                }
                else -> {
                    GroupsRow(state, actions)
                    // Grouped as the backend orders them (group sort order, then created); "No group" first.
                    state.services.groupBy { it.groupName }.entries.sortedBy { it.key != null }.forEach {
                            (group, services) ->
                        ShifaSection(group ?: tr(Res.string.serviceGroupNone)) {
                            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                                services.forEach { ServiceRow(it, state.locations) { actions.open(it) } }
                            }
                        }
                    }
                }
            }
        }
        if (!state.loading && state.error == null) {
            ShifaPrimaryButton(
                tr(Res.string.addService),
                { actions.open(null) },
                Modifier.fillMaxWidth().navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
            )
        }
    }
}

@Composable
private fun ServiceRow(
    service: DoctorService,
    locations: List<DoctorLocation>,
    onClick: () -> Unit,
) = Column(
    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = ShifaSize.S4),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
) {
    val dim = if (service.isActive) ShifaColors.Text.Primary else ShifaColors.Text.Secondary
    Text(service.title, style = ShifaTextStyles.panelTitle(), color = dim)
    service.description?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val lines =
        if (service.isFreeConsultation) {
            listOf(tr(Res.string.serviceFreeConsultation))
        } else {
            service.prices.map {
                "${Money.format(
                    it.amountMinor,
                    it.currency,
                )} · ${scopeLabel(it.locationId, locations)}"
            }
        }
    lines.forEach { Text(it, style = ShifaTextStyles.dialogBody(), color = dim) }
    val tags =
        listOfNotNull(
            tr(Res.string.serviceManagedByClinicShort).takeIf { service.clinicManaged },
            tr(Res.string.clinicServiceInactiveBadge).takeIf { !service.isActive },
        )
    if (tags.isNotEmpty()) {
        Text(
            tags.joinToString(" · "),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Brand,
        )
    }
}

/** "All locations (default)", "Label — City", or "Location #id" for one that no longer exists (flow 08/08 §4). */
@Composable
private fun scopeLabel(
    locationId: Long?,
    locations: List<DoctorLocation>,
): String {
    if (locationId == null) return tr(Res.string.priceScopeAllLocations)
    val l = locations.firstOrNull { it.id == locationId } ?: return "Location #$locationId"
    return listOfNotNull(l.label ?: l.clinic, l.locationCity).joinToString(" — ")
}

@Composable
private fun ServiceEditor(
    state: ServicesUiState,
    d: ServiceDraft,
    actions: ServicesActions,
) {
    val enabled = !state.saving
    ProfileForm(
        tr(if (d.id == null) Res.string.newService else Res.string.editService),
        actions.back,
        onSave = if (state.dirty && enabled) actions.save else null,
        askDiscard = state.askDiscard,
        onDiscard = actions.discard,
        onKeep = { actions.ui { it.copy(askDiscard = false) } },
    ) {
        codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        ShifaSection(null) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                ShifaInputField(
                    d.title,
                    { v -> actions.edit { it.copy(title = v.take(TITLE_MAX)) } },
                    tr(Res.string.serviceTitleLabel),
                    Modifier.fillMaxWidth(),
                    supportingText = state.titleError?.let { tr(it) },
                    isError = state.titleError != null,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                ShifaInputField(
                    d.description,
                    { v -> actions.edit { it.copy(description = v.take(DESCRIPTION_MAX)) } },
                    tr(Res.string.description),
                    Modifier.fillMaxWidth(),
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    multiline = true,
                )
                val groups = listOf<ServiceGroup?>(null) + state.groups
                ShifaDropdown(
                    tr(Res.string.serviceGroupLabel),
                    groups.map { it?.name ?: tr(Res.string.serviceGroupNone) },
                    (state.groups.firstOrNull { it.id == d.groupId }?.name) ?: tr(Res.string.serviceGroupNone),
                    onSelect = { i ->
                        actions.edit { it.copy(groupId = groups[i]?.id) }
                        actions.ui { it.copy(groupOpen = false) }
                    },
                    expanded = state.groupOpen,
                    onExpandedChange = { open -> actions.ui { it.copy(groupOpen = open) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(tr(Res.string.active), d.active) { v -> actions.edit { it.copy(active = v) } }
                SwitchRow(tr(Res.string.serviceFreeConsultation), d.free) { v -> actions.edit { it.copy(free = v) } }
                // Turning "free" on drops the prices on Save (flow 08/08 §9).
                if (d.free && state.original?.free == false && state.original.prices.any { it.amount.isNotBlank() }) {
                    Text(
                        tr(Res.string.freeRemovesPrices),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Warning,
                    )
                }
            }
        }
        if (!d.free) PricesSection(state, d, actions)
        if (d.id != null) {
            ShifaSecondaryButton(
                tr(Res.string.delete),
                { actions.ui { s -> s.copy(askDelete = s.services.firstOrNull { it.id == d.id }) } },
                Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
    state.askDelete?.let { service ->
        ProfileSheet({ actions.ui { it.copy(askDelete = null) } }) {
            ShifaDialogSheet(
                tr(Res.string.deleteServiceTitle),
                tr(Res.string.delete),
                actions.delete,
                body = service.title,
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                onCancel = { actions.ui { it.copy(askDelete = null) } },
            )
        }
    }
}

@Composable
private fun PricesSection(
    state: ServicesUiState,
    d: ServiceDraft,
    actions: ServicesActions,
) = ShifaSection(tr(Res.string.clinicServicePriceLabel)) {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        Text(
            tr(Res.string.servicePricesSection),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
        // Known codes plus any unknown one already saved, so an edit never drops it (08 §2).
        val currencies = (ServicesViewModel.CURRENCIES + d.prices.map { it.currency }).distinct()
        val scopes = listOf<DoctorLocation?>(null) + state.locations
        d.prices.forEachIndexed { i, row ->
            fun set(change: (PriceRow) -> PriceRow) =
                actions.edit { it.copy(prices = it.prices.mapIndexed { j, r -> if (j == i) change(r) else r }) }
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8), verticalAlignment = Alignment.Top) {
                    ShifaInputField(
                        row.amount,
                        { v -> set { it.copy(amount = v) } },
                        tr(Res.string.clinicServicePriceLabel),
                        Modifier.weight(1f),
                        supportingText = state.rowErrors[i]?.let { tr(it) },
                        isError = state.rowErrors[i] != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    ShifaDropdown(
                        tr(Res.string.clinicServiceCurrencyLabel),
                        currencies,
                        row.currency,
                        onSelect = { c ->
                            set { it.copy(currency = currencies[c]) }
                            actions.ui { it.copy(openRow = null) }
                        },
                        expanded = state.openRow == (i to true),
                        onExpandedChange = { open -> actions.ui { it.copy(openRow = (i to true).takeIf { open }) } },
                        modifier = Modifier.weight(0.7f),
                    )
                    if (d.prices.size > 1) {
                        ShifaIconButton(
                            Res.drawable.ic_trash_22,
                            tr(Res.string.removePriceRow),
                            { actions.edit { it.copy(prices = it.prices.filterIndexed { j, _ -> j != i }) } },
                            variant = ShifaIconButtonVariant.Ghost,
                        )
                    }
                }
                ShifaDropdown(
                    tr(Res.string.priceScopeLabel),
                    scopes.map { scopeLabel(it?.id, state.locations) },
                    scopeLabel(row.locationId, state.locations),
                    onSelect = { s ->
                        set { it.copy(locationId = scopes[s]?.id) }
                        actions.ui { it.copy(openRow = null) }
                    },
                    expanded = state.openRow == (i to false),
                    onExpandedChange = { open -> actions.ui { it.copy(openRow = (i to false).takeIf { open }) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.pricesError?.let { Text(tr(it), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Error) }
        ShifaSecondaryButton(
            tr(Res.string.addPriceRow),
            {
                actions.edit {
                    it.copy(
                        prices = it.prices + PriceRow(currency = it.prices.lastOrNull()?.currency ?: "UZS"),
                    )
                }
            },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
    ShifaSwitch(checked, onChange)
}

private const val TITLE_MAX = 80
private const val DESCRIPTION_MAX = 500

/** The entry to the groups editor, above the list (flow 08/08 §3.1 puts it in the app bar). */
@Composable
private fun GroupsRow(
    state: ServicesUiState,
    actions: ServicesActions,
) = ShifaSection(null) {
    ShifaSettingsItem(
        tr(Res.string.serviceGroups),
        actions.groups,
        icon = Res.drawable.ic_layers_22,
        subtitle = state.groups.joinToString(", ") { it.name }.ifEmpty { tr(Res.string.serviceGroupNone) },
        divider = false,
    )
}

@Composable
private fun GroupsEditor(
    state: ServicesUiState,
    rows: List<ServiceGroup>,
    actions: ServicesActions,
) {
    val enabled = !state.saving
    GroupsEditorForm(state, rows, actions, enabled)
    state.askDeleteGroup?.let { i ->
        ProfileSheet({ actions.ui { it.copy(askDeleteGroup = null) } }) {
            ShifaDialogSheet(
                tr(Res.string.deleteGroupTitle),
                tr(Res.string.delete),
                actions.deleteGroup,
                body =
                    listOfNotNull(
                        rows.getOrNull(i)?.name?.takeIf {
                            it.isNotBlank()
                        },
                        tr(Res.string.deleteGroupBody),
                    ).joinToString("\n\n"),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                onCancel = { actions.ui { it.copy(askDeleteGroup = null) } },
            )
        }
    }
}

@Composable
private fun GroupsEditorForm(
    state: ServicesUiState,
    rows: List<ServiceGroup>,
    actions: ServicesActions,
    enabled: Boolean,
) = ProfileForm(
    tr(Res.string.serviceGroups),
    actions.back,
    onSave = if (state.dirty && !state.saving) actions.saveGroups else null,
    askDiscard = state.askDiscard,
    onDiscard = actions.discard,
    onKeep = { actions.ui { it.copy(askDiscard = false) } },
) {
    codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
    ShifaSection(null) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            Text(
                tr(Res.string.serviceGroupsHelper),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            rows.forEachIndexed { i, group ->
                fun move(to: Int) = actions.editGroups { it.toMutableList().apply { add(to, removeAt(i)) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShifaInputField(
                        group.name,
                        {
                                v ->
                            actions.editGroups {
                                it.mapIndexed {
                                        j,
                                        g,
                                    ->
                                    if (j == i) g.copy(name = v.take(GROUP_NAME_MAX)) else g
                                }
                            }
                        },
                        tr(Res.string.groupName),
                        Modifier.weight(1f),
                        supportingText = tr(Res.string.fieldRequired).takeIf { i in state.groupErrors },
                        isError = i in state.groupErrors,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    ShifaIconButton(
                        Res.drawable.ic_chev_u_20,
                        tr(Res.string.moveUp, group.name),
                        { move(i - 1) },
                        variant = ShifaIconButtonVariant.Ghost,
                        enabled = enabled && i > 0,
                    )
                    ShifaIconButton(
                        Res.drawable.ic_chev_d_20,
                        tr(Res.string.moveDown, group.name),
                        { move(i + 1) },
                        variant = ShifaIconButtonVariant.Ghost,
                        enabled = enabled && i < rows.lastIndex,
                    )
                    ShifaIconButton(
                        Res.drawable.ic_trash_22,
                        tr(Res.string.removeItem, group.name),
                        { actions.ui { it.copy(askDeleteGroup = i) } },
                        variant = ShifaIconButtonVariant.Ghost,
                        enabled = enabled,
                    )
                }
            }
            ShifaSecondaryButton(
                tr(Res.string.addGroup),
                { actions.editGroups { it + ServiceGroup(name = "") } },
                Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
}

private const val GROUP_NAME_MAX = 60
