package shifa.doctor.ui.forms

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
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinical.ClinicalEngineRepository
import shifa.doctor.data.clinical.clinicalEngineRepository
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.UploadFile
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.forms.Dentition
import shifa.doctor.data.forms.Form0252Pdf
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.forms.Icd10Hit
import shifa.doctor.data.forms.formsRepository
import shifa.doctor.data.model.ClinicalField
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.data.model.FormFollowup
import shifa.doctor.data.model.FormTemplates
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.engineApplied
import shifa.doctor.generated.resources.formDocumentFailed
import shifa.doctor.generated.resources.formLoadFailed
import shifa.doctor.generated.resources.formPdfFailed
import shifa.doctor.generated.resources.formSaveFailed
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.generated.resources.previousChartUnavailable
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.patients.chronicWarned
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.doctorDisplayName

/** A return visit being added (index -1) or edited in its sheet. */
data class ReturnEdit(
    val index: Int,
    val date: LocalDate,
    val text: String,
)

/** Where a save stopped, so Retry goes on from there (the form is never saved twice, its PDF never uploaded twice). */
enum class SaveStep { Form, Pdf, Document, Link }

data class FormEditorUiState(
    val loading: Boolean = true,
    val error: CodeBanner? = null,
    val patient: Patient? = null,
    val form: PatientForm? = null,
    // What the form was when opened or last saved (unsaved changes ask before leaving).
    val original: PatientForm? = null,
    // Editable dental rows per jaw (an added row has no cells until one is set).
    val topRows: List<Int> = listOf(0),
    val bottomRows: List<Int> = listOf(0),
    val today: LocalDate = LocalDate(1970, 1, 1),
    val doctorName: String = "",
    val icdQuery: String = "",
    val icdResults: List<Icd10Hit>? = null,
    val icdBusy: Boolean = false,
    // The search failed (not "no matches"): the screen offers Retry.
    val icdFailed: Boolean = false,
    val returnEdit: ReturnEdit? = null,
    val tried: Boolean = false,
    val saving: Boolean = false,
    // The step a failed save stopped at (Retry continues it).
    val failedAt: SaveStep? = null,
    val signing: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val chronicWarning: Boolean = false,
    // Saved: the screen goes back.
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != null && form != original

    /** Flow 06/10 Fix: at least a diagnosis or complaints. */
    val valid: Boolean get() = !form?.diagnosis.isNullOrBlank() || !form?.complaints.isNullOrBlank()

    val dentition: Dentition get() = DentalChartCodec.dentition(form?.dentalChart.orEmpty())
}

/**
 * FORM-04 (flow 06/10, spec 06 §3.4–3.7; board `DaPatients` "Form 025-2 editor"): a new 025-2 (today on the practice
 * clock; the patient's latest 025-2's chart carried over as history, DentalChartCodec.prefillFromLatest) or an
 * existing one. **Saving** is one pipeline: the form (POST/PUT) → its PDF (FORM-03) → the document (`FORM_025_2`:
 * the linked one's file replaced, else uploaded and linked) → the disease's usage (CLIN-01). A step that fails says
 * which and Retry goes on from there: the form isn't saved twice and an uploaded PDF isn't uploaded again (flow §8,
 * Flutter could do both).
 */
class FormEditorViewModel(
    private val patientId: Long,
    private val formId: Long?,
    private val forms: FormsRepository = formsRepository,
    private val documents: DocumentsRepository = documentsRepository,
    private val patients: PatientsRepository = patientsRepository,
    private val engine: ClinicalEngineRepository = clinicalEngineRepository,
    private val clock: () -> PracticeClock = { PracticeClock(profileRepository.me.value?.profile?.timeZone) },
    private val doctor: () -> String = {
        profileRepository.me.value?.profile.let { doctorDisplayName(it?.firstName, it?.lastName, null) }
    },
    private val locale: () -> String = { languageStorage.language.value.backendCode },
    private val pdf: (PatientForm) -> ByteArray? = { Form0252Pdf().pdf(it) },
    private val warned: MutableSet<Long> = chronicWarned,
    // APPT-08: a new form opened from a visit with an AI-suggested ICD-10 code applied there.
    private val presetIcd: Icd10Hit? = null,
    // CLINIC-06: a patient opened in a clinic's scope is read, and the form's PDF stored, in that scope.
    private val clinicId: Long? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(FormEditorUiState())
    val state: StateFlow<FormEditorUiState> = _state.asStateFlow()
    private var searching: Job? = null

    // The PDF made and the document uploaded by a save that then failed: Retry reuses them.
    private var madePdf: ByteArray? = null
    private var uploadedDocument: Long? = null
    private var uploadedPdf: ByteArray? = null

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val patient = async { patients.patient(patientId, clinicId) }
            val list = forms.list(patientId)
            val p = patient.await()
            // A new form opens without the previous chart if only the forms list failed (flow §6); an existing one
            // needs it (it is read from the list).
            val failure = p.exceptionOrNull() ?: list.exceptionOrNull()?.takeIf { formId != null }
            if (failure != null) {
                return@launch _state.update {
                    it.copy(
                        loading = false,
                        error =
                            CodeBanner(
                                failure.toUserMessage(Res.string.formLoadFailed),
                                requestId = failure.requestId,
                            ),
                    )
                }
            }
            val all = list.getOrDefault(emptyList())
            val pat = p.getOrThrow()
            val today = clock().today()
            val name = doctor()
            val form =
                if (formId != null) {
                    all.firstOrNull { it.id == formId }?.let {
                        it.copy(
                            dentalChart = DentalChartCodec.migrateLegacy(it.dentalChart),
                        )
                    }
                } else {
                    // A new 025-2 from the patient (gender too: Flutter never set it) and the latest 025-2's chart.
                    PatientForm(
                        patientId = patientId,
                        templateId = FormTemplates.F025_2,
                        date = today.toString(),
                        fullName = pat.name,
                        gender = pat.gender?.trim()?.ifEmpty { null },
                        address = pat.formattedLocation,
                        age = pat.age(today),
                        dentalChart =
                            FormsRepository.latest0252(all)?.let {
                                // Its rows become history dated and signed by that form (Flutter's), not this visit.
                                DentalChartCodec.prefillFromLatest(
                                    it.dentalChart,
                                    it.date.take(10),
                                    it.doctorName.orEmpty(),
                                )
                            }
                                ?: emptyMap(),
                    )
                }
            if (form == null) {
                return@launch _state.update { it.copy(loading = false, error = CodeBanner(Res.string.formLoadFailed)) }
            }
            // The chronic-disease warning, once per patient per session (flow 06/04).
            val warn = pat.chronic != null && warned.add(pat.id)
            _state.update {
                it.copy(
                    loading = false,
                    patient = pat,
                    form = presetIcd?.takeIf { formId == null }?.let { form.withIcd(it) } ?: form,
                    original = form,
                    topRows = rows(form, DentalChartCodec.TOP),
                    bottomRows = rows(form, DentalChartCodec.BOTTOM),
                    today = today,
                    doctorName = name,
                    chronicWarning = warn,
                    banner =
                        list.exceptionOrNull()?.let { e ->
                            CodeBanner(Res.string.previousChartUnavailable, error = false, requestId = e.requestId)
                        },
                )
            }
        }
    }

    private fun rows(
        form: PatientForm,
        jaw: String,
    ) = DentalChartCodec.rowIndices(form.dentalChart, jaw).ifEmpty { listOf(0) }

    fun edit(change: (PatientForm) -> PatientForm) =
        _state.update { s ->
            if (s.saving || s.signing) s else s.copy(form = s.form?.let(change))
        }

    fun ui(change: (FormEditorUiState) -> FormEditorUiState) = _state.update(change)

    // ---- Dental chart ----

    fun setCell(
        jaw: String,
        row: Int,
        cell: Int,
        code: String,
    ) = edit { it.copy(dentalChart = DentalChartCodec.withCell(it.dentalChart, jaw, row, cell, code)) }

    fun addRow(jaw: String) =
        _state.update { s ->
            if (jaw == DentalChartCodec.TOP) {
                s.copy(
                    topRows = s.topRows + ((s.topRows.maxOrNull() ?: -1) + 1),
                )
            } else {
                s.copy(bottomRows = s.bottomRows + ((s.bottomRows.maxOrNull() ?: -1) + 1))
            }
        }

    /** The last editable row of [jaw] goes, with its cells; one row always stays. */
    fun removeRow(jaw: String) {
        val s = _state.value
        val rows = if (jaw == DentalChartCodec.TOP) s.topRows else s.bottomRows
        if (rows.size <= 1 || s.saving || s.signing) return
        val last = rows.last()
        _state.update {
            val form = it.form?.let { f -> f.copy(dentalChart = DentalChartCodec.withoutRow(f.dentalChart, jaw, last)) }
            if (jaw == DentalChartCodec.TOP) {
                it.copy(
                    form = form,
                    topRows = rows.dropLast(1),
                )
            } else {
                it.copy(form = form, bottomRows = rows.dropLast(1))
            }
        }
    }

    /** Flow §7: switching the teeth resets the editable rows (their cells would land on other teeth). */
    fun setDentition(d: Dentition) {
        val s = _state.value
        if (s.saving || s.signing || s.dentition == d) return
        edit { it.copy(dentalChart = DentalChartCodec.withDentition(it.dentalChart, d)) }
        _state.update { it.copy(topRows = listOf(0), bottomRows = listOf(0)) }
    }

    // ---- ICD-10 ----

    /** 250 ms after typing stops, in the app's language (06 §3.4), at most 20 hits. */
    fun searchIcd(query: String) {
        _state.update {
            it.copy(
                icdQuery = query,
                icdBusy = query.isNotBlank(),
                icdFailed = false,
                icdResults = if (query.isBlank()) null else it.icdResults,
            )
        }
        searching?.cancel()
        if (query.isBlank()) return
        searching =
            viewModelScope.launch {
                delay(ICD_DEBOUNCE)
                val hits = forms.searchIcd10(query, locale())
                _state.update {
                    if (it.icdQuery != query) {
                        it
                    } else {
                        it.copy(icdBusy = false, icdResults = hits.getOrNull(), icdFailed = hits.isFailure)
                    }
                }
            }
    }

    fun pickIcd(hit: Icd10Hit) {
        edit { it.withIcd(hit) }
        _state.update { it.copy(icdQuery = "", icdResults = null, icdBusy = false) }
    }

    fun clearIcd() = edit { it.copy(diagnosisCode = null, diagnosisDisplay = null, diagnosisSystem = null) }

    // ---- Clinical engine ----

    /**
     * Writes the engine's texts into their fields (only those it returned) and the disease as the diagnosis with its
     * first ICD code when the doctor has none yet or the engine wrote anything (Flutter's `_onClinicalEngineApplied`).
     */
    fun applyEngine(result: EngineResult) {
        val f = result.fields
        val name = result.disease.names.forLocale(locale())
        edit { form ->
            fun pick(
                key: String,
                old: String?,
            ) = f[key]?.trim()?.ifEmpty { null } ?: old
            val withTexts =
                form.copy(
                    complaints = pick(ClinicalField.COMPLAINTS, form.complaints),
                    moreDetails = pick(ClinicalField.MORBI, form.moreDetails),
                    visualCheckup = pick(ClinicalField.OBJECTIVE, form.visualCheckup),
                    occlusion = pick(ClinicalField.OCCLUSION, form.occlusion),
                    oralCavityCondition = pick(ClinicalField.ORAL_CAVITY, form.oralCavityCondition),
                    xrayLabData = pick(ClinicalField.XRAY, form.xrayLabData),
                    treatment = pick(ClinicalField.TREATMENT_1, form.treatment),
                    treatmentResult = pick(ClinicalField.TREATMENT_2, form.treatmentResult),
                    recommendations = pick(ClinicalField.RECOMMENDATIONS, form.recommendations),
                    clinicalDiseaseId = result.disease.diseaseId,
                    clinicalChipSelections = result.selections,
                )
            if (f.isNotEmpty() || form.diagnosis.isNullOrBlank()) {
                val code = result.disease.icdCodes.firstOrNull()
                withTexts.copy(
                    diagnosis = name,
                    diagnosisCode = code ?: withTexts.diagnosisCode,
                    diagnosisDisplay = if (code != null) name else withTexts.diagnosisDisplay,
                    diagnosisSystem = if (code != null) PatientForm.ICD10 else withTexts.diagnosisSystem,
                )
            } else {
                withTexts
            }
        }
        _state.update { it.copy(banner = CodeBanner(Res.string.engineApplied, error = false)) }
    }

    // ---- Return visits ----

    fun openReturn(index: Int) =
        _state.update { s ->
            val f = s.form?.followups?.getOrNull(index)
            s.copy(
                returnEdit = ReturnEdit(if (f == null) -1 else index, f?.day ?: s.today, f?.clinicalFindings.orEmpty()),
            )
        }

    fun saveReturn(edit: ReturnEdit) {
        edit { form ->
            val row =
                FormFollowup(edit.date.toString(), edit.text.trim(), form.followups.getOrNull(edit.index)?.doctorName)
            val list = form.followups.toMutableList()
            if (edit.index in list.indices) list[edit.index] = row else list += row
            form.copy(followups = list)
        }
        _state.update { it.copy(returnEdit = null) }
    }

    fun removeReturn(index: Int) = edit { it.copy(followups = it.followups.filterIndexed { i, _ -> i != index }) }

    // ---- Save ----

    /** Back: false while saving or while it asks to discard unsaved changes. */
    fun back(): Boolean {
        val s = _state.value
        if (s.saving || s.signing) return false
        if (s.dirty && !s.askDiscard) return false.also { _state.update { it.copy(askDiscard = true) } }
        return true
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.signing || s.form == null) return
        if (!s.valid) return _state.update { it.copy(tried = true) }
        _state.update { it.copy(saving = true, tried = true, banner = null) }
        // Changed since the failed step: the form is saved again and its PDF remade (the edits aren't lost).
        viewModelScope.launch { pipeline(from = if (s.dirty) SaveStep.Form else s.failedAt ?: SaveStep.Form) }
    }

    private suspend fun pipeline(from: SaveStep) {
        var form = _state.value.form ?: return

        fun failed(
            step: SaveStep,
            text: StringResource,
            e: Throwable?,
        ) = _state.update {
            it.copy(
                saving = false,
                failedAt = step,
                banner = CodeBanner(e?.toUserMessage(text) ?: text, requestId = e?.requestId),
            )
        }
        if (from == SaveStep.Form || form.id == null) {
            form =
                forms.save(form).getOrElse {
                    return failed(SaveStep.Form, Res.string.formSaveFailed, it)
                }
            madePdf = null
            // Saved: the id is kept whatever happens next, so Retry never posts the form again.
            _state.update { it.copy(form = form, original = form) }
        }
        val bytes =
            (if (from <= SaveStep.Pdf || madePdf == null) pdf(form) else madePdf)
                ?: return failed(SaveStep.Pdf, Res.string.formPdfFailed, null)
        madePdf = bytes
        val day = form.date.take(10)
        val file = UploadFile(bytes, "form_025-2_$day.pdf", DocumentFile.PDF)
        val linked = form.documentId
        if (linked != null) {
            documents.replaceFile(patientId, linked, file, clinicId).onFailure {
                return failed(SaveStep.Document, Res.string.formDocumentFailed, it)
            }
        } else {
            // A document uploaded by a save that failed later is reused (its file replaced if the PDF was remade).
            val earlier = uploadedDocument
            if (earlier != null && uploadedPdf !== bytes) {
                documents.replaceFile(patientId, earlier, file, clinicId).onFailure {
                    return failed(SaveStep.Document, Res.string.formDocumentFailed, it)
                }
            }
            val docId =
                earlier ?: documents
                    .upload(
                        patientId,
                        file,
                        "Form 025-2 ($day)",
                        DocumentCategory.FORM_025_2.name,
                        clinicId,
                    )
                    .getOrElse { return failed(SaveStep.Document, Res.string.formDocumentFailed, it) }
                    .id
            uploadedDocument = docId
            uploadedPdf = bytes
            form =
                forms.linkDocument(patientId, form.id!!, docId).getOrElse {
                    return failed(SaveStep.Link, Res.string.formDocumentFailed, it)
                }
        }
        // The doctor's frequent diagnoses (best effort: the form is saved either way). The disease is the edited
        // form's: the link's answer needn't carry it.
        (_state.value.form?.clinicalDiseaseId ?: form.clinicalDiseaseId)?.let { engine.recordUsage(it) }
        madePdf = null
        uploadedDocument = null
        uploadedPdf = null
        _state.update { it.copy(saving = false, failedAt = null, form = form, original = form, done = true) }
    }

    /** Asks the patient to sign; an unsaved form is saved first (as Flutter, silently: the form only, no PDF). */
    fun requestSignature() {
        val s = _state.value
        val form = s.form ?: return
        if (s.signing || s.saving) return
        val mustSave = form.id == null || s.dirty
        if (mustSave && !s.valid) return _state.update { it.copy(tried = true) }
        _state.update { it.copy(signing = true, banner = null) }
        viewModelScope.launch {
            val saved =
                if (mustSave) {
                    forms.save(form).getOrElse { e ->
                        return@launch _state.update {
                            it.copy(
                                signing = false,
                                banner =
                                    CodeBanner(
                                        e.toUserMessage(Res.string.formSaveFailed),
                                        requestId = e.requestId,
                                    ),
                            )
                        }
                    }
                } else {
                    form
                }
            if (mustSave) {
                // The form changed: a PDF kept from a failed save is stale, so Save makes it again.
                madePdf = null
                _state.update { it.copy(form = saved, original = saved, failedAt = it.failedAt?.let { SaveStep.Pdf }) }
            }
            forms.requestSignature(patientId, saved.id!!).fold(
                onSuccess = { f ->
                    _state.update {
                        it.copy(
                            signing = false,
                            form = f,
                            original = f,
                            banner = CodeBanner(Res.string.patientSignatureRequestSent, error = false),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            signing = false,
                            form = saved,
                            original = saved,
                            banner = CodeBanner(e.toUserMessage(Res.string.formSaveFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val ICD_DEBOUNCE = 250L
    }
}

/** The ICD-10 code and title picked for the diagnosis (the search's pick, or one applied in a visit). */
internal fun PatientForm.withIcd(hit: Icd10Hit) =
    copy(diagnosisCode = hit.code, diagnosisDisplay = hit.title, diagnosisSystem = PatientForm.ICD10)
