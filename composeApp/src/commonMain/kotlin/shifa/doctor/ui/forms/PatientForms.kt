package shifa.doctor.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.forms.formsRepository
import shifa.doctor.data.model.FormTemplates
import shifa.doctor.data.model.PatientForm
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.createForm
import shifa.doctor.generated.resources.form0252
import shifa.doctor.generated.resources.form0252Description
import shifa.doctor.generated.resources.formNoPdf
import shifa.doctor.generated.resources.formSignatureRequestedBadge
import shifa.doctor.generated.resources.formSignedBadge
import shifa.doctor.generated.resources.formTemplateNamed
import shifa.doctor.generated.resources.formsCount
import shifa.doctor.generated.resources.formsLoadFailed
import shifa.doctor.generated.resources.formsTab
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_clipboard_18
import shifa.doctor.generated.resources.ic_clipboard_22
import shifa.doctor.generated.resources.noFormsYet
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.selectFormTemplate
import shifa.doctor.ui.documents.LARGE_TEXT
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

data class PatientFormsUiState(
    // Null until the first read.
    val forms: List<PatientForm>? = null,
    val error: CodeBanner? = null,
    val banner: CodeBanner? = null,
    // The template sheet is open.
    val picking: Boolean = false,
) {
    /** Each linked document's form, for the documents tab's "Edit form". */
    val byDocument: Map<Long, Long>
        get() = forms.orEmpty().mapNotNull { f -> f.documentId?.let { d -> f.id?.let { d to it } } }.toMap()

    /** No forms could be read: the documents tab says why its form PDFs have no Edit. */
    val failed: Boolean get() = error != null
}

/**
 * FORM-05 (flow 06/09, spec 06 §2.7, §3.1): a patient's forms in the record's Forms tab, newest first. The list is the
 * repository's cache, so a form saved in the editor shows here on return without another read; the tab still reads
 * each time it shows (another doctor of the clinic may have added one).
 */
class PatientFormsViewModel(
    private val patientId: Long,
    private val repo: FormsRepository = formsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PatientFormsUiState())
    val state: StateFlow<PatientFormsUiState> = _state.asStateFlow()

    private var reading: Job? = null

    init {
        viewModelScope.launch {
            repo.forms(patientId).filterNotNull().collect {
                    list ->
                _state.update { it.copy(forms = list, error = null) }
            }
        }
    }

    /** A failed read is the tab's error with Retry, never an empty list (flow §6: a duplicate form could follow). */
    fun load() {
        reading?.cancel()
        _state.update { it.copy(error = null) }
        reading =
            viewModelScope.launch {
                repo.list(patientId).fold(
                    // An unchanged list doesn't pass the cache's flow: the failure shown is cleared here.
                    onSuccess = {
                        _state.update {
                            it.copy(
                                error = null,
                                banner =
                                    it.banner?.takeIf {
                                            b ->
                                        !b.error
                                    },
                            )
                        }
                    },
                    onFailure = { e ->
                        val banner = CodeBanner(e.toUserMessage(Res.string.formsLoadFailed), requestId = e.requestId)
                        _state.update { if (it.forms != null) it.copy(banner = banner) else it.copy(error = banner) }
                    },
                )
            }
    }

    fun ui(change: (PatientFormsUiState) -> PatientFormsUiState) = _state.update(change)
}

data class FormsActions(
    val retry: () -> Unit = {},
    // Opens the template sheet.
    val create: () -> Unit = {},
    val open: (PatientForm) -> Unit = {},
    val ui: ((PatientFormsUiState) -> PatientFormsUiState) -> Unit = {},
)

/** A form's name: "Form 025-2", or "Form {id}" for a template this app doesn't know. */
@Composable
internal fun formName(templateId: String): String =
    if (templateId == FormTemplates.F025_2) tr(Res.string.form0252) else tr(Res.string.formTemplateNamed, templateId)

/** The tab's rows in the record's lazy list (board `DaPatients` "Forms"). */
fun LazyListScope.formRows(
    state: PatientFormsUiState,
    actions: FormsActions,
    gap: Modifier,
) {
    item {
        Column(gap, verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                    Text(
                        tr(Res.string.formsTab),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                    state.forms?.let {
                        Text(
                            trPlural(Res.plurals.formsCount, it.size, it.size),
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                }
                // Only once the list is known (flow §6: no duplicate created over a list that failed to load).
                if (state.forms != null) {
                    ShifaPrimaryButton(tr(Res.string.createForm), actions.create, size = ShifaButtonSize.Compact)
                }
            }
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        }
    }
    val forms = state.forms
    val error = state.error
    when {
        error != null ->
            item {
                ShifaLoadError(tr(error.text), actions.retry, gap, requestId = error.requestId)
            }
        forms == null -> item { Box(gap) { ShifaListSkeleton(count = 3) } }
        forms.isEmpty() ->
            item {
                Column(
                    gap.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S28),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Box(
                        Modifier.size(ShifaSize.S48).background(ShifaColors.Bg.Canvas, ShifaRadius.Full),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(Res.drawable.ic_clipboard_22), null, tint = ShifaColors.Icon.Secondary)
                    }
                    Text(
                        tr(Res.string.noFormsYet),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                }
            }
        else -> items(forms, key = { "form-${it.id}" }) { f -> Box(gap) { FormCard(f) { actions.open(f) } } }
    }
}

/** A form (flow §3): icon, name and date, the signature and PDF badges, chevron; the whole card opens it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormCard(
    f: PatientForm,
    onOpen: () -> Unit,
) {
    val name = formName(f.templateId)
    val date = f.day?.let(::displayDate) ?: f.date
    val badges =
        listOfNotNull(
            when {
                f.signed -> Triple(Res.string.formSignedBadge, ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Primary)
                f.signatureRequested ->
                    Triple(
                        Res.string.formSignatureRequestedBadge,
                        ShifaColors.Bg.WarningSubtle,
                        ShifaColors.Text.Primary,
                    )
                else -> null
            },
            if (f.documentId == null) {
                Triple(
                    Res.string.formNoPdf,
                    ShifaColors.Bg.Canvas,
                    ShifaColors.Text.Primary,
                )
            } else {
                null
            },
        ).map { (text, bg, color) -> Triple(tr(text), bg, color) }
    // One announcement per card (flow §8: "Form 025-2, 12.03.2026, signed").
    val spoken = (listOf(name, date) + badges.map { it.first }).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            }.padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.WarningSubtle, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_clipboard_18), null, tint = ShifaColors.Icon.Primary)
        }
        // Large text: the badges go under the date instead of squeezing the name (flow §8).
        val stacked = LocalDensity.current.fontScale >= LARGE_TEXT
        val badgeRow =
            @Composable {
                FlowRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            ShifaSize.S4,
                            if (stacked) Alignment.Start else Alignment.End,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                ) {
                    badges.forEach { (text, bg, color) -> ProfileChip(text, bg, color) }
                }
            }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(name, style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
            Text(date, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            if (stacked && badges.isNotEmpty()) badgeRow()
        }
        if (!stacked) badgeRow()
        Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Secondary)
    }
}

/**
 * "Select Form Template" (flow §3, board `DaPatients` template menu). Kept with a single template, as the board shows
 * it (flow §13 leaves skipping it open): the sheet says what 025-2 is.
 */
@Composable
internal fun FormTemplateTray(
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) = ShifaTray(tr(Res.string.selectFormTemplate), onClose) {
    FormTemplates.all.forEachIndexed { i, id ->
        ShifaSettingsItem(
            formName(id),
            { onPick(id) },
            icon = Res.drawable.ic_clipboard_22,
            subtitle = if (id == FormTemplates.F025_2) tr(Res.string.form0252Description) else null,
            divider = i < FormTemplates.all.lastIndex,
        )
    }
}
