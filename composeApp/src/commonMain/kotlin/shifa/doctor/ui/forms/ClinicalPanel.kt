package shifa.doctor.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinical.ClinicalEngineRepository
import shifa.doctor.data.clinical.clinicalEngineRepository
import shifa.doctor.data.model.ClinicalChip
import shifa.doctor.data.model.ClinicalChipSelection
import shifa.doctor.data.model.ClinicalDisease
import shifa.doctor.data.model.ClinicalField
import shifa.doctor.data.model.ClinicalGroup
import shifa.doctor.data.model.ClinicalSharedTemplate
import shifa.doctor.data.model.ClinicalTopDiagnosis
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.DocumentsEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.apply
import shifa.doctor.generated.resources.change
import shifa.doctor.generated.resources.chooseDisease
import shifa.doctor.generated.resources.clinicalEngine
import shifa.doctor.generated.resources.clinicalEngineHint
import shifa.doctor.generated.resources.complaints
import shifa.doctor.generated.resources.diseaseDevelopment
import shifa.doctor.generated.resources.diseaseGroups
import shifa.doctor.generated.resources.engineApplyFailed
import shifa.doctor.generated.resources.engineLoadFailed
import shifa.doctor.generated.resources.frequentDiagnoses
import shifa.doctor.generated.resources.objectiveExam
import shifa.doctor.generated.resources.occlusionBiteType
import shifa.doctor.generated.resources.oralCavityTexts
import shifa.doctor.generated.resources.recommendationsInstructions
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchPhrases
import shifa.doctor.generated.resources.treatmentStage1
import shifa.doctor.generated.resources.treatmentStage2
import shifa.doctor.generated.resources.xrayTexts
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.tr

/** What the engine gives the form: the disease, the field texts, and the picks the form saves with it. */
class EngineResult(
    val disease: ClinicalDisease,
    val fields: Map<String, String>,
    val selections: List<ClinicalChipSelection>,
)

data class ClinicalPanelUiState(
    val loading: Boolean = true,
    val error: CodeBanner? = null,
    val groups: List<ClinicalGroup> = emptyList(),
    val top: List<ClinicalTopDiagnosis> = emptyList(),
    val occlusion: List<ClinicalChip> = emptyList(),
    val mucosa: List<ClinicalSharedTemplate> = emptyList(),
    val xray: List<ClinicalSharedTemplate> = emptyList(),
    val disease: ClinicalDisease? = null,
    // Picked chips (disease, searched, occlusion or `shared.*`) and their variable values.
    val selected: Map<String, Map<String, String>> = emptyMap(),
    // Chips added from the search that aren't the disease's own.
    val extra: List<ClinicalChip> = emptyList(),
    val query: String = "",
    val results: List<ClinicalChip> = emptyList(),
    // The disease picker sheet: groups, then the picked group's diseases.
    val picking: Boolean = false,
    val group: ClinicalGroup? = null,
    val groupDiseases: List<ClinicalDisease>? = null,
    val applying: Boolean = false,
    // A finished Apply for the form to take (then cleared).
    val result: EngineResult? = null,
) {
    val selections: List<ClinicalChipSelection> get() = selected.map { (id, vars) -> ClinicalChipSelection(id, vars) }

    /** The disease's and the searched chips of one field, by priority. */
    fun chips(field: String): List<ClinicalChip> =
        (disease?.chips.orEmpty() + extra)
            .filter { it.field == field }
            .distinctBy { it.chipId }
            .sortedBy { it.priority }

    /** Shared texts, the ones for the disease's group first (`correlates`). */
    fun shared(list: List<ClinicalSharedTemplate>): List<ClinicalSharedTemplate> =
        list.sortedWith(compareBy({ disease?.groupId !in it.correlates }, { it.priority }))
}

/**
 * CLIN-02 (02 §5.3; board `DaPatients` form editor "Clinical engine"): a disease from the catalogue (group, then
 * disease; the doctor's frequent ones as shortcuts), its phrase chips per form field (all picked when the disease is
 * chosen, as Flutter), `[X]` values, chip search, occlusion chips and the shared oral-cavity / X-ray texts. **Apply**
 * (the board's button) synthesises the texts and hands them to the form once: Flutter re-filled the form on every
 * toggle, over what the doctor had typed. A saved form's disease and picks come back without synthesising.
 */
class ClinicalPanelViewModel(
    private val repo: ClinicalEngineRepository = clinicalEngineRepository,
    // Before [locale]: the tests pass the locale as a trailing lambda.
    private val tracker: Tracker = Tracking,
    private val locale: () -> String = { languageStorage.language.value.backendCode },
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicalPanelUiState())
    val state: StateFlow<ClinicalPanelUiState> = _state.asStateFlow()
    private var searching: Job? = null
    private var started = false

    /** Once per panel: the catalogue, and a saved form's disease and picks. */
    fun start(
        diseaseId: String? = null,
        selections: List<ClinicalChipSelection> = emptyList(),
    ) {
        if (started) return
        started = true
        load(diseaseId, selections)
    }

    // A saved form's disease and picks, kept until they are restored (a failed read can be retried).
    private var restoring: Pair<String, List<ClinicalChipSelection>>? = null
    private var applying: Job? = null

    fun retry() = restoring?.let { (id, picks) -> load(id, picks) } ?: load(null, emptyList())

    private fun load(
        diseaseId: String?,
        selections: List<ClinicalChipSelection>,
    ) {
        if (diseaseId != null) restoring = diseaseId to selections
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val groups = async { repo.groups() }
            val top = async { repo.topDiagnoses() }
            val occlusion = async { repo.occlusionChips() }
            val mucosa = async { repo.sharedTemplates("mucosa") }
            val xray = async { repo.sharedTemplates("xray") }
            val disease = diseaseId?.let { async { repo.disease(it) } }
            val g = groups.await()
            val d = disease?.await()
            // Picks found by search belong to other diseases ("dis.009.treatment_1.1"): their chips come from them.
            val own = d?.getOrNull()?.chips.orEmpty().map { it.chipId }.toSet()
            val strays =
                selections
                    .map { it.chipId }
                    .filter { it !in own && !it.startsWith("occ.") && !it.startsWith("shared.") }
            val extra =
                strays
                    .map { it.split('.').take(2).joinToString(".") }
                    .distinct()
                    .map { owner -> async { repo.disease(owner).getOrNull()?.chips.orEmpty() } }
                    .flatMap { it.await() }
                    .filter { it.chipId in strays }
            if (d?.isSuccess == true) restoring = null
            val failure = g.exceptionOrNull() ?: d?.exceptionOrNull()
            _state.update { s ->
                s.copy(
                    loading = false,
                    error =
                        failure?.let {
                            CodeBanner(
                                it.toUserMessage(Res.string.engineLoadFailed),
                                requestId = it.requestId,
                            )
                        },
                    groups = g.getOrDefault(emptyList()),
                    // The extras are shortcuts: without them the panel still works.
                    top = top.await().getOrDefault(emptyList()),
                    occlusion = occlusion.await().getOrDefault(emptyList()),
                    mucosa = mucosa.await().getOrDefault(emptyList()),
                    xray = xray.await().getOrDefault(emptyList()),
                    disease = d?.getOrNull() ?: s.disease,
                    extra = if (d?.isSuccess == true) extra else s.extra,
                    selected =
                        if (diseaseId != null) selections.associate { it.chipId to it.variables } else s.selected,
                )
            }
        }
    }

    fun openPicker() = _state.update { it.copy(picking = true, group = null, groupDiseases = null) }

    fun closePicker() = _state.update { it.copy(picking = false) }

    fun pickGroup(group: ClinicalGroup) {
        _state.update { it.copy(group = group, groupDiseases = null) }
        viewModelScope.launch {
            val result = repo.diseases(group.groupId)
            _state.update { s ->
                if (s.group != group) return@update s
                result.fold(
                    { s.copy(groupDiseases = it) },
                    {
                            e ->
                        s.copy(
                            group = null,
                            error = CodeBanner(e.toUserMessage(Res.string.engineLoadFailed), requestId = e.requestId),
                        )
                    },
                )
            }
        }
    }

    /** A disease chosen by the doctor: all of its chips picked (Flutter's `userInitiated`), the search cleared. */
    fun pickDisease(diseaseId: String) {
        // A new disease: a running Apply for the old one must not reach the form, nor a pending restore.
        applying?.cancel()
        restoring = null
        _state.update { it.copy(picking = false, loading = true, error = null, applying = false) }
        viewModelScope.launch {
            repo.disease(diseaseId).fold(
                onSuccess = { d ->
                    _state.update { s ->
                        val kept = s.selected.filterKeys { it.startsWith("occ.") || it.startsWith("shared.") }
                        s.copy(
                            loading = false,
                            disease = d,
                            extra = emptyList(),
                            query = "",
                            results = emptyList(),
                            selected = kept + d.chips.associate { it.chipId to emptyMap() },
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = CodeBanner(e.toUserMessage(Res.string.engineLoadFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    fun toggle(chipId: String) =
        _state.update { s ->
            s.copy(selected = if (chipId in s.selected) s.selected - chipId else s.selected + (chipId to emptyMap()))
        }

    fun setVariable(
        chipId: String,
        name: String,
        value: String,
    ) = _state.update { s ->
        val vars = s.selected[chipId] ?: return@update s
        s.copy(selected = s.selected + (chipId to vars + (name to value)))
    }

    /** Phrase search from 2 characters, 350 ms after typing stops (02 §5.3). */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searching?.cancel()
        if (query.trim().length < MIN_QUERY) return _state.update { it.copy(results = emptyList()) }
        searching =
            viewModelScope.launch {
                delay(DEBOUNCE)
                val hits = repo.searchChips(query, locale()).getOrDefault(emptyList())
                _state.update { if (it.query == query) it.copy(results = hits) else it }
            }
    }

    /** A searched chip joins the picks (and its field's panel when it isn't the disease's). */
    fun addFromSearch(chip: ClinicalChip) =
        _state.update { s ->
            val own = s.disease?.chips.orEmpty().any { it.chipId == chip.chipId }
            s.copy(
                extra = if (own) s.extra else (s.extra + chip).distinctBy { it.chipId },
                selected = s.selected + (chip.chipId to (s.selected[chip.chipId] ?: emptyMap())),
                query = "",
                results = emptyList(),
            )
        }

    fun apply() {
        val s = _state.value
        val disease = s.disease ?: return
        if (s.applying) return
        _state.update { it.copy(applying = true, error = null) }
        applying =
            viewModelScope.launch {
                val selections = s.selections
                repo.synthesize(locale(), disease.diseaseId, selections).fold(
                    onSuccess = {
                            fields ->
                        // Only for the disease still shown: a result for another one is dropped, not applied.
                        if (_state.value.disease?.diseaseId == disease.diseaseId) {
                            tracker.track(DocumentsEvents.ClinicalEngineApplied(countBucket(selections.size)))
                        }
                        _state.update {
                            // Only for the disease still shown.
                            if (it.disease?.diseaseId != disease.diseaseId) {
                                it.copy(applying = false)
                            } else {
                                it.copy(applying = false, result = EngineResult(disease, fields, selections))
                            }
                        }
                    },
                    onFailure = { e ->
                        tracker.track(DocumentsEvents.ClinicalEngineApplyFailed(e.toFailureReason()))
                        _state.update {
                            it.copy(
                                applying = false,
                                error =
                                    CodeBanner(
                                        e.toUserMessage(Res.string.engineApplyFailed),
                                        requestId = e.requestId,
                                    ),
                            )
                        }
                    },
                )
            }
    }

    /** The form took the result. */
    fun consumed() = _state.update { it.copy(result = null) }

    private companion object {
        const val MIN_QUERY = 2
        const val DEBOUNCE = 350L
    }
}

data class ClinicalPanelActions(
    val retry: () -> Unit = {},
    val openPicker: () -> Unit = {},
    val pickDisease: (String) -> Unit = {},
    val toggle: (String) -> Unit = {},
    val variable: (chipId: String, name: String, value: String) -> Unit = { _, _, _ -> },
    val search: (String) -> Unit = {},
    val add: (ClinicalChip) -> Unit = {},
    val apply: () -> Unit = {},
)

/** The engine's card in the 025-2 editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClinicalPanel(
    state: ClinicalPanelUiState,
    locale: String,
    actions: ClinicalPanelActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Text(tr(Res.string.clinicalEngine), style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
    state.error?.let { e ->
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            codeBanner(e)?.invoke()
            ShifaLink(tr(Res.string.retry), actions.retry)
        }
    }
    val disease = state.disease
    when {
        state.loading -> ShifaListSkeleton(count = 2)
        disease == null -> {
            Text(
                tr(Res.string.clinicalEngineHint),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            if (state.top.isNotEmpty()) {
                Text(
                    tr(Res.string.frequentDiagnoses),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    state.top.forEach {
                            t ->
                        ShifaChip(t.names.forLocale(locale), false, { actions.pickDisease(t.diseaseId) })
                    }
                }
            }
            ShifaPrimaryButton(tr(Res.string.chooseDisease), actions.openPicker, size = ShifaButtonSize.Compact)
        }
        else -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        disease.names.forLocale(locale),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Primary,
                    )
                    if (disease.icdCodes.isNotEmpty()) {
                        Text(
                            disease.icdCodes.joinToString(", "),
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                }
                ShifaLink(tr(Res.string.change), actions.openPicker)
            }
            ShifaSearchField(state.query, actions.search, tr(Res.string.searchPhrases), Modifier.fillMaxWidth())
            if (state.results.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    state.results.forEach { c -> ShifaChip(c.labels.forLocale(locale), false, { actions.add(c) }) }
                }
            }
            FIELDS.forEach { (field, title) ->
                val chips = state.chips(field)
                if (field == ClinicalField.OCCLUSION) ChipGroup(tr(title), state.occlusion, state, locale, actions)
                if (chips.isNotEmpty()) ChipGroup(tr(title), chips, state, locale, actions)
                if (field == ClinicalField.ORAL_CAVITY) {
                    SharedGroup(
                        tr(Res.string.oralCavityTexts),
                        state.shared(state.mucosa),
                        state,
                        locale,
                        actions,
                    )
                }
                if (field == ClinicalField.XRAY) {
                    SharedGroup(
                        tr(Res.string.xrayTexts),
                        state.shared(state.xray),
                        state,
                        locale,
                        actions,
                    )
                }
            }
            ShifaPrimaryButton(
                tr(Res.string.apply),
                actions.apply,
                size = ShifaButtonSize.Compact,
                loading = state.applying,
            )
        }
    }
}

/** One field's chips; a picked chip with `[X]` values shows a field per value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    title: String,
    chips: List<ClinicalChip>,
    state: ClinicalPanelUiState,
    locale: String,
    actions: ClinicalPanelActions,
) {
    if (chips.isEmpty()) return
    Text(title, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        chips.forEach {
                c ->
            ShifaChip(c.labels.forLocale(locale), c.chipId in state.selected, { actions.toggle(c.chipId) })
        }
    }
    chips.filter { it.chipId in state.selected && it.variables.isNotEmpty() }.forEach { c ->
        c.variables.forEach { name ->
            ShifaInputField(
                state.selected[c.chipId]?.get(name).orEmpty(),
                { actions.variable(c.chipId, name, it) },
                "$name (${c.labels.forLocale(locale)})",
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedGroup(
    title: String,
    texts: List<ClinicalSharedTemplate>,
    state: ClinicalPanelUiState,
    locale: String,
    actions: ClinicalPanelActions,
) {
    if (texts.isEmpty()) return
    Text(title, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        texts.forEach {
                t ->
            ShifaChip(t.labels.forLocale(locale), t.chipId in state.selected, { actions.toggle(t.chipId) })
        }
    }
}

/** The disease picker: groups, then the group's diseases with their ICD codes. */
@Composable
fun ClinicalPickerSheet(
    state: ClinicalPanelUiState,
    locale: String,
    pickGroup: (ClinicalGroup) -> Unit,
    pickDisease: (String) -> Unit,
    back: () -> Unit,
    close: () -> Unit,
) = ProfileSheet(close) {
    val group = state.group
    ShifaTray(group?.names?.forLocale(locale) ?: tr(Res.string.diseaseGroups), if (group != null) back else close) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            when {
                group == null ->
                    state.groups.forEach {
                            g ->
                        ShifaSettingsItem(g.names.forLocale(locale), { pickGroup(g) })
                    }
                state.groupDiseases == null -> ShifaListSkeleton(count = 4)
                else ->
                    state.groupDiseases.forEach { d ->
                        ShifaSettingsItem(d.names.forLocale(locale), {
                            pickDisease(d.diseaseId)
                        }, subtitle = d.icdCodes.joinToString(", ").ifEmpty { null })
                    }
            }
        }
    }
}

/** The engine's fields in the form's order, with the panel titles (Flutter's were hard-coded English). */
private val FIELDS: List<Pair<String, StringResource>> =
    listOf(
        ClinicalField.COMPLAINTS to Res.string.complaints,
        ClinicalField.MORBI to Res.string.diseaseDevelopment,
        ClinicalField.OBJECTIVE to Res.string.objectiveExam,
        ClinicalField.OCCLUSION to Res.string.occlusionBiteType,
        ClinicalField.ORAL_CAVITY to Res.string.oralCavityTexts,
        ClinicalField.XRAY to Res.string.xrayTexts,
        ClinicalField.TREATMENT_1 to Res.string.treatmentStage1,
        ClinicalField.TREATMENT_2 to Res.string.treatmentStage2,
        ClinicalField.RECOMMENDATIONS to Res.string.recommendationsInstructions,
    )
