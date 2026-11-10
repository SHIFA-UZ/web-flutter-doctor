package shifa.doctor.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.forms.DentalCode
import shifa.doctor.data.forms.Dentition
import shifa.doctor.data.forms.Icd10Hit
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.addReturnVisit
import shifa.doctor.generated.resources.addRow
import shifa.doctor.generated.resources.address
import shifa.doctor.generated.resources.age
import shifa.doctor.generated.resources.anamnesis
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.clinicalEngine
import shifa.doctor.generated.resources.clinicalFindingsConclusion
import shifa.doctor.generated.resources.complaints
import shifa.doctor.generated.resources.date
import shifa.doctor.generated.resources.dentalChart
import shifa.doctor.generated.resources.dentalCodesLegend
import shifa.doctor.generated.resources.dentalDentitionPermanent
import shifa.doctor.generated.resources.dentalDentitionPrimary
import shifa.doctor.generated.resources.diagnosis
import shifa.doctor.generated.resources.doctor
import shifa.doctor.generated.resources.doctorSetOnSave
import shifa.doctor.generated.resources.examinationAndTreatment
import shifa.doctor.generated.resources.form0252
import shifa.doctor.generated.resources.formGeneral
import shifa.doctor.generated.resources.formNeedsDiagnosis
import shifa.doctor.generated.resources.formNotSignedYet
import shifa.doctor.generated.resources.formSavedSuccessfully
import shifa.doctor.generated.resources.formSignatureSavedFirst
import shifa.doctor.generated.resources.formSignedAt
import shifa.doctor.generated.resources.fullName
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.historyOtherTeethHidden
import shifa.doctor.generated.resources.ic_edit_16
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.icd10SearchHint
import shifa.doctor.generated.resources.icdSearchFailed
import shifa.doctor.generated.resources.job
import shifa.doctor.generated.resources.lowerJaw
import shifa.doctor.generated.resources.moreDetailsOnAbove
import shifa.doctor.generated.resources.noMatches
import shifa.doctor.generated.resources.noReturnVisitsAddedYet
import shifa.doctor.generated.resources.occlusionBiteType
import shifa.doctor.generated.resources.oralCavityCondition
import shifa.doctor.generated.resources.otherIllnessesAndComplications
import shifa.doctor.generated.resources.patientFormSaveAgainToRefreshPdf
import shifa.doctor.generated.resources.patientFormSignatureReceived
import shifa.doctor.generated.resources.patientId
import shifa.doctor.generated.resources.patientSignaturePending
import shifa.doctor.generated.resources.recommendationsInstructions
import shifa.doctor.generated.resources.remove
import shifa.doctor.generated.resources.removeRow
import shifa.doctor.generated.resources.requestPatientFormSignature
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.returnVisit
import shifa.doctor.generated.resources.returnVisits
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.saveForm
import shifa.doctor.generated.resources.tapToAddFindings
import shifa.doctor.generated.resources.treatment
import shifa.doctor.generated.resources.treatmentResultProgress
import shifa.doctor.generated.resources.upperJaw
import shifa.doctor.generated.resources.visualCheckup
import shifa.doctor.generated.resources.xrayLabExaminationData
import shifa.doctor.ui.calendar.MonthPicker
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.patients.ChronicWarning
import shifa.doctor.ui.patients.genderLabel
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.speech.LocalDictation
import shifa.doctor.ui.speech.SpeechOrPlainField
import shifa.doctor.ui.speech.appendTranscript
import shifa.doctor.ui.speech.rememberDictation
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.SegmentedTabs
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

data class FormEditorActions(
    val back: () -> Unit = {},
    val retry: () -> Unit = {},
    val edit: ((PatientForm) -> PatientForm) -> Unit = {},
    val ui: ((FormEditorUiState) -> FormEditorUiState) -> Unit = {},
    val searchIcd: (String) -> Unit = {},
    val pickIcd: (Icd10Hit) -> Unit = {},
    val clearIcd: () -> Unit = {},
    val setCell: (jaw: String, row: Int, cell: Int, code: String) -> Unit = { _, _, _, _ -> },
    val addRow: (String) -> Unit = {},
    val removeRow: (String) -> Unit = {},
    val dentition: (Dentition) -> Unit = {},
    val openReturn: (Int) -> Unit = {},
    val removeReturn: (Int) -> Unit = {},
    val requestSignature: () -> Unit = {},
    val save: () -> Unit = {},
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FormEditorScreen(
    patientId: Long,
    formId: Long?,
    opened: Long,
    onClose: () -> Unit,
    presetIcd: Icd10Hit? = null,
    // CLINIC-06: the clinic scope of the patient the form is for.
    clinicId: Long? = null,
) {
    val viewModel =
        viewModel(
            key = "form-$patientId-$formId-$opened",
        ) { FormEditorViewModel(patientId, formId, presetIcd = presetIcd, clinicId = clinicId) }
    val clinical = viewModel(key = "form-clinical-$patientId-$formId-$opened") { ClinicalPanelViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val engine by clinical.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    // The engine starts with the form's saved disease and picks, once the form is there.
    val form = state.form
    LaunchedEffect(form != null) { form?.let { clinical.start(it.clinicalDiseaseId, it.clinicalChipSelections) } }
    LaunchedEffect(engine.result) {
        engine.result?.let {
            viewModel.applyEngine(it)
            clinical.consumed()
        }
    }
    LaunchedEffect(state.done) {
        if (state.done) {
            onClose()
            // After leaving (a navigation clears success notices), so it stays.
            ShellNotice.pending.value = Notice(Res.string.formSavedSuccessfully, success = true)
        }
    }
    val back = { if (viewModel.back()) onClose() }
    BackHandler(onBack = back)
    val locale = languageStorage.language.value.backendCode
    // Dictation for the form's text areas (AI-06).
    CompositionLocalProvider(LocalDictation provides rememberDictation()) {
        FormEditorContent(
            state,
            engine,
            locale,
            FormEditorActions(
                back = back,
                retry = viewModel::load,
                edit = viewModel::edit,
                ui = viewModel::ui,
                searchIcd = viewModel::searchIcd,
                pickIcd = viewModel::pickIcd,
                clearIcd = viewModel::clearIcd,
                setCell = viewModel::setCell,
                addRow = viewModel::addRow,
                removeRow = viewModel::removeRow,
                dentition = viewModel::setDentition,
                openReturn = viewModel::openReturn,
                removeReturn = viewModel::removeReturn,
                requestSignature = viewModel::requestSignature,
                save = viewModel::save,
            ),
            ClinicalPanelActions(
                retry = clinical::retry,
                openPicker = clinical::openPicker,
                pickDisease = clinical::pickDisease,
                toggle = clinical::toggle,
                variable = clinical::setVariable,
                search = clinical::search,
                add = clinical::addFromSearch,
                apply = clinical::apply,
            ),
        )
    }
    if (engine.picking) {
        ClinicalPickerSheet(
            engine,
            locale,
            clinical::pickGroup,
            clinical::pickDisease,
            { clinical.openPicker() },
            clinical::closePicker,
        )
    }
    state.returnEdit?.let {
        ReturnVisitSheet(it, state.today, viewModel::saveReturn) {
            viewModel.ui {
                    s ->
                s.copy(returnEdit = null)
            }
        }
    }
    if (state.askDiscard) {
        DiscardChangesSheet(onDiscard = onClose, onKeep = { viewModel.ui { it.copy(askDiscard = false) } })
    }
    state.patient?.chronic?.takeIf { state.chronicWarning }?.let {
        ChronicWarning(shifa.doctor.ui.patients.chronicLabel(it)) {
            viewModel.ui {
                    s ->
                s.copy(chronicWarning = false)
            }
        }
    }
}

/** The editor's sections, for the chip bar that jumps to them (the board's). */
private enum class Section(
    val label: StringResource,
) {
    General(Res.string.formGeneral),
    Engine(Res.string.clinicalEngine),
    Anamnesis(Res.string.anamnesis),
    Chart(Res.string.dentalChart),
    Treatment(Res.string.examinationAndTreatment),
    Returns(Res.string.returnVisits),
    Signature(Res.string.requestPatientFormSignature),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormEditorContent(
    state: FormEditorUiState,
    engine: ClinicalPanelUiState,
    locale: String,
    actions: FormEditorActions,
    engineActions: ClinicalPanelActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).imePadding()) {
    ShifaSubHeader(tr(Res.string.form0252), Modifier.statusBarsPadding(), onBack = actions.back)
    val form = state.form
    val error = state.error
    when {
        form != null -> {
            val list = rememberLazyListState()
            val scope = rememberCoroutineScope()
            // Header block, then one item per section (the chip bar scrolls to it).
            LazyRow(
                contentPadding = PaddingValues(horizontal = ShifaSpacing.Inset.Md),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            ) {
                itemsIndexed(Section.entries) { i, section ->
                    ShifaChip(tr(section.label), false, { scope.launch { list.animateScrollToItem(i + 1) } })
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                state = list,
                contentPadding = PaddingValues(ShifaSpacing.Inset.Md),
                verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                        // Dictation's messages (AI-06).
                        val dictation = LocalDictation.current
                        dictation?.state?.collectAsStateWithLifecycle()?.value?.banner?.let { b ->
                            codeBanner(b) { dictation.dismiss() }?.invoke()
                        }
                        Head(form, state)
                    }
                }
                item { General(form, state, actions) }
                item { ClinicalPanel(engine, locale, engineActions) }
                item {
                    Card(tr(Res.string.anamnesis)) {
                        Area(
                            form.complaints,
                            { it.complaints },
                            tr(Res.string.complaints),
                            3,
                            actions,
                        ) { f, v -> f.copy(complaints = v) }
                        Area(
                            form.otherIllnesses,
                            { it.otherIllnesses },
                            tr(Res.string.otherIllnessesAndComplications),
                            3,
                            actions,
                        ) { f, v -> f.copy(otherIllnesses = v) }
                        Area(
                            form.moreDetails,
                            { it.moreDetails },
                            tr(Res.string.moreDetailsOnAbove),
                            4,
                            actions,
                        ) { f, v -> f.copy(moreDetails = v) }
                        Area(
                            form.visualCheckup,
                            { it.visualCheckup },
                            tr(Res.string.visualCheckup),
                            3,
                            actions,
                        ) { f, v -> f.copy(visualCheckup = v) }
                        if (state.tried && !state.valid) {
                            Text(
                                tr(Res.string.formNeedsDiagnosis),
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Error,
                            )
                        }
                    }
                }
                item { DentalChartEditor(form, state, actions) }
                item {
                    Card(tr(Res.string.examinationAndTreatment)) {
                        Area(
                            form.occlusion,
                            { it.occlusion },
                            tr(Res.string.occlusionBiteType),
                            1,
                            actions,
                        ) { f, v -> f.copy(occlusion = v) }
                        Area(
                            form.oralCavityCondition,
                            { it.oralCavityCondition },
                            tr(Res.string.oralCavityCondition),
                            3,
                            actions,
                        ) { f, v -> f.copy(oralCavityCondition = v) }
                        Area(
                            form.xrayLabData,
                            { it.xrayLabData },
                            tr(Res.string.xrayLabExaminationData),
                            3,
                            actions,
                        ) { f, v -> f.copy(xrayLabData = v) }
                        Area(
                            form.treatment,
                            { it.treatment },
                            tr(Res.string.treatment),
                            3,
                            actions,
                        ) { f, v -> f.copy(treatment = v) }
                        Area(
                            form.treatmentResult,
                            { it.treatmentResult },
                            tr(Res.string.treatmentResultProgress),
                            3,
                            actions,
                        ) { f, v -> f.copy(treatmentResult = v) }
                        Area(
                            form.recommendations,
                            { it.recommendations },
                            tr(Res.string.recommendationsInstructions),
                            3,
                            actions,
                        ) { f, v -> f.copy(recommendations = v) }
                        Row {
                            Text(
                                tr(Res.string.doctor),
                                Modifier.weight(1f),
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Secondary,
                            )
                            Text(
                                form.doctorName ?: tr(Res.string.doctorSetOnSave, state.doctorName),
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Primary,
                            )
                        }
                    }
                }
                item { ReturnVisits(form, actions) }
                item { Signature(form, state, actions) }
            }
            // Above the action bar, not in the list: a failed save, a sent signature request or a form that can't be
            // saved yet shows wherever the doctor has scrolled to (in the list it sat off-screen at the top).
            Column(
                Modifier.padding(horizontal = ShifaSpacing.Inset.Md),
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
                if (state.tried && !state.valid) {
                    Text(
                        tr(Res.string.formNeedsDiagnosis),
                        Modifier.padding(top = ShifaSize.S8),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Error,
                    )
                }
            }
            // The board's action bar: Cancel and Save Form.
            Row(
                Modifier.fillMaxWidth().background(
                    ShifaColors.Bg.Surface,
                ).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                ShifaSecondaryButton(tr(Res.string.cancel), actions.back, Modifier.weight(1f), enabled = !state.saving)
                ShifaPrimaryButton(tr(Res.string.saveForm), actions.save, Modifier.weight(1f), loading = state.saving)
            }
        }
        error != null && !state.loading ->
            ShifaLoadError(tr(error.text), actions.retry, Modifier.weight(1f), requestId = error.requestId)
        else -> Box(Modifier.padding(ShifaSpacing.Inset.Md)) { ShifaListSkeleton(count = 4) }
    }
}

/** The read-only header (flow §3.2): patient id, date, name, gender (from the patient), age. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Head(
    form: PatientForm,
    state: FormEditorUiState,
) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    listOfNotNull(
        tr(Res.string.patientId) to (state.patient?.displayId ?: form.patientId.toString()),
        tr(Res.string.date) to displayDate(form.day ?: state.today),
        tr(Res.string.fullName) to form.fullName,
        genderLabel(form.gender)?.let { tr(Res.string.gender) to it },
        form.age?.let { tr(Res.string.age) to it.toString() },
    ).forEach { (label, value) ->
        Column(
            Modifier.background(
                ShifaColors.Bg.Surface,
                ShifaRadius.ControlMd,
            ).padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S8),
        ) {
            Text(label, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            Text(value, style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
        }
    }
}

@Composable
private fun General(
    form: PatientForm,
    state: FormEditorUiState,
    actions: FormEditorActions,
) = Card(tr(Res.string.formGeneral)) {
    Area(form.address, { it.address }, tr(Res.string.address), 2, actions) { f, v -> f.copy(address = v) }
    Area(form.job, { it.job }, tr(Res.string.job), 1, actions) { f, v -> f.copy(job = v) }
    Area(form.diagnosis, { it.diagnosis }, tr(Res.string.diagnosis), 3, actions) { f, v -> f.copy(diagnosis = v) }
    // ICD-10: the picked code as a chip with ×, then the search (250 ms; results under the field, flow §3.4).
    Text("ICD-10", style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    val code = form.diagnosisCode
    if (!code.isNullOrBlank()) {
        Row(
            Modifier.background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.Full).padding(start = ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                listOfNotNull(code, form.diagnosisDisplay).joinToString(" — "),
                Modifier.weight(1f, fill = false),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ShifaIconButton(
                Res.drawable.ic_x_16,
                tr(Res.string.remove),
                actions.clearIcd,
                variant = ShifaIconButtonVariant.Ghost,
            )
        }
    }
    ShifaSearchField(state.icdQuery, actions.searchIcd, tr(Res.string.icd10SearchHint), Modifier.fillMaxWidth())
    val results = state.icdResults
    when {
        state.icdBusy -> ShifaListSkeleton(count = 2)
        state.icdFailed ->
            Text(
                tr(Res.string.icdSearchFailed),
                Modifier.clickable { actions.searchIcd(state.icdQuery) },
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Error,
            )
        results == null -> Unit
        results.isEmpty() ->
            Text(
                tr(Res.string.noMatches),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        else ->
            Column(Modifier.heightIn(max = ShifaSize.S240).background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    results.forEach { hit ->
                        Column(Modifier.fillMaxWidth().clickable { actions.pickIcd(hit) }.padding(ShifaSize.S10)) {
                            Text(
                                "${hit.code} — ${hit.title}",
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Primary,
                            )
                            hit.subtitle?.let {
                                Text(
                                    it,
                                    style = ShifaTextStyles.caption(),
                                    color = ShifaColors.Text.Secondary,
                                )
                            }
                        }
                    }
                }
            }
    }
}

/** The 025-2's chart (flow §3.7): dentition, codes legend, per jaw the history rows, tooth numbers and editable rows. */
@Composable
private fun DentalChartEditor(
    form: PatientForm,
    state: FormEditorUiState,
    actions: FormEditorActions,
) = Card(tr(Res.string.dentalChart)) {
    val d = state.dentition
    SegmentedTabs(
        listOf(TabItem(tr(Res.string.dentalDentitionPermanent)), TabItem(tr(Res.string.dentalDentitionPrimary))),
        if (d == Dentition.Primary) 1 else 0,
        { actions.dentition(if (it == 1) Dentition.Primary else Dentition.Permanent) },
        block = true,
    )
    Text(tr(Res.string.dentalCodesLegend), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    val cells = DentalChartCodec.cellsPerRow(d)
    listOf(
        Triple(
            DentalChartCodec.TOP,
            Res.string.upperJaw,
            DentalChartCodec.upperTeeth(d),
        ),
        Triple(
            DentalChartCodec.BOTTOM,
            Res.string.lowerJaw,
            DentalChartCodec.lowerTeeth(d),
        ),
    ).forEach { (jaw, label, teeth) ->
        Text(tr(label), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
        val rows = if (jaw == DentalChartCodec.TOP) state.topRows else state.bottomRows
        // The grid scrolls sideways on a phone (16 cells), history above the numbers for the upper jaw.
        Column(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
        ) {
            if (DentalChartCodec.historyRows(form.dentalChart, jaw).any { it.dentition != null && it.dentition != d }) {
                Text(
                    tr(Res.string.historyOtherTeethHidden),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            DentalChartCodec.visibleHistoryRows(form.dentalChart, jaw).forEach { h ->
                Text(
                    listOf(
                        DentalChartCodec.displayHistoryDate(h.date),
                        h.doctor.orEmpty(),
                    ).filter(String::isNotBlank).joinToString(" · "),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                    h.cells.take(cells).forEach { v -> Cell(v, editable = false) {} }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                teeth.forEach { n ->
                    Text(
                        n,
                        Modifier.width(CELL),
                        style = ShifaTextStyles.toothNumber(),
                        color = ShifaColors.Text.Primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // A tooth's own finding (FDI key, from older forms or a visit's chart): shown, edited on the visit (DENTAL-01).
            val toothValues = teeth.map { form.dentalChart[it].orEmpty() }
            if (toothValues.any(String::isNotBlank)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                    toothValues.forEach { v -> Cell(v, editable = false) {} }
                }
            }
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                    (0 until cells).forEach { c ->
                        Cell(
                            form.dentalChart["${jaw}_${r}_$c"].orEmpty(),
                            editable = true,
                        ) { code -> actions.setCell(jaw, r, c, code) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            ShifaActionButton(
                { actions.addRow(jaw) },
                text = tr(Res.string.addRow),
                variant = ShifaActionVariant.Secondary,
            )
            if (rows.size > 1) {
                ShifaActionButton(
                    { actions.removeRow(jaw) },
                    text = tr(Res.string.removeRow),
                    variant = ShifaActionVariant.Secondary,
                )
            }
        }
    }
}

/** One chart cell; an editable one opens the codes (blank first). */
@Composable
private fun Cell(
    value: String,
    editable: Boolean,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(CELL)
            .background(if (editable) ShifaColors.Bg.Surface else ShifaColors.Bg.Canvas, ShifaRadius.Sm)
            .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.Sm)
            .then(if (editable) Modifier.clickable { open = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary, maxLines = 1)
        if (editable) {
            DropdownMenu(open, { open = false }) {
                (listOf("") + DentalCode.entries.map { it.code }).forEach { code ->
                    DropdownMenuItem({ Text(code.ifEmpty { "—" }) }, {
                        open = false
                        onPick(code)
                    })
                }
            }
        }
    }
}

@Composable
private fun ReturnVisits(
    form: PatientForm,
    actions: FormEditorActions,
) = Card(tr(Res.string.returnVisits)) {
    if (form.followups.isEmpty()) {
        Text(
            tr(Res.string.noReturnVisitsAddedYet),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    form.followups.forEachIndexed { i, f ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Text(
                f.day?.let(::displayDate) ?: f.date,
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Primary,
            )
            Text(
                f.clinicalFindings.ifBlank { tr(Res.string.tapToAddFindings) },
                Modifier.weight(1f),
                style = ShifaTextStyles.metaSmall(),
                color = if (f.clinicalFindings.isBlank()) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ShifaIconButton(Res.drawable.ic_edit_16, tr(Res.string.returnVisit), {
                actions.openReturn(i)
            }, variant = ShifaIconButtonVariant.Ghost)
            ShifaIconButton(Res.drawable.ic_trash_22, tr(Res.string.remove), {
                actions.removeReturn(i)
            }, variant = ShifaIconButtonVariant.Ghost)
        }
    }
    ShifaActionButton(
        { actions.openReturn(-1) },
        text = tr(Res.string.addReturnVisit),
        variant = ShifaActionVariant.Tint,
    )
}

/** The patient's signature (flow §3.11): received, requested, or the request (the form is saved first). */
@Composable
private fun Signature(
    form: PatientForm,
    state: FormEditorUiState,
    actions: FormEditorActions,
) = Card(tr(Res.string.requestPatientFormSignature)) {
    when {
        form.signed -> {
            Text(
                tr(Res.string.patientFormSignatureReceived),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            Text(
                tr(
                    Res.string.formSignedAt,
                    form.patientSignedAt?.take(10)?.let {
                        DentalChartCodec.displayHistoryDate(it)
                    }.orEmpty(),
                ),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            Text(
                tr(Res.string.patientFormSaveAgainToRefreshPdf),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        form.signatureRequested ->
            Text(
                tr(Res.string.patientSignaturePending),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Warning,
            )
        else -> {
            Text(
                tr(Res.string.formNotSignedYet),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            if (form.id == null) {
                Text(
                    tr(Res.string.formSignatureSavedFirst),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            ShifaSecondaryButton(
                tr(Res.string.requestPatientFormSignature),
                actions.requestSignature,
                size = ShifaButtonSize.Compact,
                loading = state.signing,
            )
        }
    }
}

/** A free-text field of the form; an emptied field is saved as nothing. */
@Composable
private fun Area(
    value: String?,
    get: (PatientForm) -> String?,
    label: String,
    lines: Int,
    actions: FormEditorActions,
    set: (PatientForm, String?) -> PatientForm,
) = if (lines > 2) {
    // Notes get the kit's tall comment box and the mic (AI-06); a short field (address, job, bite) stays one line.
    SpeechOrPlainField(
        value.orEmpty(),
        { v -> actions.edit { set(it, v.ifEmpty { null }) } },
        label,
        label,
        { t -> actions.edit { set(it, appendTranscript(get(it).orEmpty(), t)) } },
        Modifier.fillMaxWidth(),
        multiline = true,
    )
} else {
    ShifaInputField(
        value.orEmpty(),
        { v -> actions.edit { set(it, v.ifEmpty { null }) } },
        label,
        Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Card(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Text(title, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
    content()
}

/** Add or edit a return visit: its date (any day) and the findings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReturnVisitSheet(
    edit: ReturnEdit,
    today: LocalDate,
    onSave: (ReturnEdit) -> Unit,
    onClose: () -> Unit,
) = ProfileSheet(onClose) {
    var date by remember { mutableStateOf(edit.date) }
    var month by remember { mutableStateOf(LocalDate(edit.date.year, edit.date.month, 1)) }
    var text by remember { mutableStateOf(edit.text) }
    ShifaTray(tr(Res.string.returnVisit), onClose) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) {
            MonthPicker(month, date, today, { null }, { date = it }, { month = month.plus(DatePeriod(months = it)) })
            ShifaInputField(text, {
                text = it
            }, tr(Res.string.clinicalFindingsConclusion), Modifier.fillMaxWidth(), multiline = true)
        }
        ShifaPrimaryButton(
            tr(Res.string.save),
            { onSave(edit.copy(date = date, text = text)) },
            Modifier.fillMaxWidth(),
        )
    }
}

private val CELL = ShifaSize.S28
