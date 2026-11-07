package shifa.doctor.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.Patient
import shifa.doctor.data.profile.Gender
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.address
import shifa.doctor.generated.resources.allergies
import shifa.doctor.generated.resources.birthDate
import shifa.doctor.generated.resources.bloodGroup
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.chronicDisease
import shifa.doctor.generated.resources.chronicDisease_asthma
import shifa.doctor.generated.resources.chronicDisease_cancer
import shifa.doctor.generated.resources.chronicDisease_chronicKidneyDisease
import shifa.doctor.generated.resources.chronicDisease_chronicLiverDisease
import shifa.doctor.generated.resources.chronicDisease_copd
import shifa.doctor.generated.resources.chronicDisease_crohnsDisease
import shifa.doctor.generated.resources.chronicDisease_diabetesType1
import shifa.doctor.generated.resources.chronicDisease_diabetesType2
import shifa.doctor.generated.resources.chronicDisease_epilepsy
import shifa.doctor.generated.resources.chronicDisease_heartDisease
import shifa.doctor.generated.resources.chronicDisease_hemophilia
import shifa.doctor.generated.resources.chronicDisease_hivAids
import shifa.doctor.generated.resources.chronicDisease_hypertension
import shifa.doctor.generated.resources.chronicDisease_lupus
import shifa.doctor.generated.resources.chronicDisease_multipleSclerosis
import shifa.doctor.generated.resources.chronicDisease_none
import shifa.doctor.generated.resources.chronicDisease_other
import shifa.doctor.generated.resources.chronicDisease_parkinsonsDisease
import shifa.doctor.generated.resources.chronicDisease_rheumatoidArthritis
import shifa.doctor.generated.resources.chronicDisease_sickleCellDisease
import shifa.doctor.generated.resources.chronicDisease_thalassemia
import shifa.doctor.generated.resources.chronicDisease_ulcerativeColitis
import shifa.doctor.generated.resources.chronicWarningNote
import shifa.doctor.generated.resources.editDetails
import shifa.doctor.generated.resources.email
import shifa.doctor.generated.resources.female
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.generalInformation
import shifa.doctor.generated.resources.ic_edit_16
import shifa.doctor.generated.resources.ic_phone_22
import shifa.doctor.generated.resources.language
import shifa.doctor.generated.resources.male
import shifa.doctor.generated.resources.noKnownAllergies
import shifa.doctor.generated.resources.notSpecified
import shifa.doctor.generated.resources.other
import shifa.doctor.generated.resources.patientId
import shifa.doctor.generated.resources.remove
import shifa.doctor.generated.resources.removeLastPhoneBody
import shifa.doctor.generated.resources.removeLastPhoneTitle
import shifa.doctor.generated.resources.save
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

/** The 22 conditions Flutter offered (flow 06/05 §3); the English name is what the backend stores. */
enum class ChronicDisease(
    val wire: String,
    val label: StringResource,
) {
    None("None", Res.string.chronicDisease_none),
    DiabetesType1("Diabetes (Type 1)", Res.string.chronicDisease_diabetesType1),
    DiabetesType2("Diabetes (Type 2)", Res.string.chronicDisease_diabetesType2),
    HivAids("HIV/AIDS", Res.string.chronicDisease_hivAids),
    Hypertension("Hypertension", Res.string.chronicDisease_hypertension),
    HeartDisease("Heart Disease", Res.string.chronicDisease_heartDisease),
    ChronicKidneyDisease("Chronic Kidney Disease", Res.string.chronicDisease_chronicKidneyDisease),
    ChronicLiverDisease("Chronic Liver Disease", Res.string.chronicDisease_chronicLiverDisease),
    Asthma("Asthma", Res.string.chronicDisease_asthma),
    Copd("COPD", Res.string.chronicDisease_copd),
    Cancer("Cancer", Res.string.chronicDisease_cancer),
    Epilepsy("Epilepsy", Res.string.chronicDisease_epilepsy),
    MultipleSclerosis("Multiple Sclerosis", Res.string.chronicDisease_multipleSclerosis),
    ParkinsonsDisease("Parkinson's Disease", Res.string.chronicDisease_parkinsonsDisease),
    RheumatoidArthritis("Rheumatoid Arthritis", Res.string.chronicDisease_rheumatoidArthritis),
    Lupus("Lupus", Res.string.chronicDisease_lupus),
    CrohnsDisease("Crohn's Disease", Res.string.chronicDisease_crohnsDisease),
    UlcerativeColitis("Ulcerative Colitis", Res.string.chronicDisease_ulcerativeColitis),
    Hemophilia("Hemophilia", Res.string.chronicDisease_hemophilia),
    SickleCellDisease("Sickle Cell Disease", Res.string.chronicDisease_sickleCellDisease),
    Thalassemia("Thalassemia", Res.string.chronicDisease_thalassemia),
    Other("Other", Res.string.chronicDisease_other),
    ;

    companion object {
        fun of(raw: String?): ChronicDisease? =
            raw?.trim()?.let { v -> entries.firstOrNull { it.wire.equals(v, ignoreCase = true) } }
    }
}

/** A stored condition in the app's language; a value outside the list as written. */
@Composable
internal fun chronicLabel(raw: String): String = ChronicDisease.of(raw)?.let { tr(it.label) } ?: raw

data class MedicalActions(
    val phones: (List<String>) -> Unit = {},
    val savePhones: () -> Unit = {},
    val allergies: (String) -> Unit = {},
    val saveAllergies: () -> Unit = {},
    val chronic: (ChronicDisease) -> Unit = {},
    val gender: (Gender?) -> Unit = {},
    val bloodGroup: (String?) -> Unit = {},
    val editDetails: () -> Unit = {},
)

/**
 * PAT-06 (flow 06/05, spec 06 §2.4; board `DaPatients` `patDetail` Medical info): what is known, read only, then the
 * fields kept here. Dropdowns save at once; phones and allergies save with their button (allergies also on Done).
 * Every input is locked while one save runs, under a thin bar.
 */
@Composable
internal fun MedicalInfo(
    p: Patient,
    state: PatientDetailUiState,
    actions: MedicalActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    val enabled = !state.saving
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr(Res.string.generalInformation),
            Modifier.weight(1f),
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
        )
        ShifaActionButton(
            actions.editDetails,
            text = tr(Res.string.editDetails),
            icon = Res.drawable.ic_edit_16,
            variant = ShifaActionVariant.Secondary,
            enabled = enabled,
        )
    }
    // The saving line (not in the board's states as a widget; the board draws a thin bar).
    Row(
        Modifier
            .fillMaxWidth()
            .height(ShifaSize.S2)
            .background(if (state.saving) ShifaColors.Icon.Brand else ShifaColors.Bg.Surface, ShifaRadius.Full),
    ) {}
    // Rows with nothing to show are left out (flow 06/05 §3.1.1).
    SummaryRow(tr(Res.string.patientId), p.displayId, false)
    p.birth?.let { SummaryRow(tr(Res.string.birthDate), displayDate(it), false) }
    p.email?.trim()?.takeIf(String::isNotEmpty)?.let { SummaryRow(tr(Res.string.email), it, false) }
    p.formattedLocation?.let { SummaryRow(tr(Res.string.address), it, false) }
    languageLabel(p.language)?.let { SummaryRow(tr(Res.string.language), it, false) }

    PhoneRows(state.phoneRows, state.badPhones, state.phoneTaken, enabled, actions.phones)
    if (state.phonesDirty) {
        ShifaSecondaryButton(tr(Res.string.save), actions.savePhones, enabled = enabled, size = ShifaButtonSize.Compact)
    }

    val diseases = ChronicDisease.entries
    val current = p.chronicDisease?.trim().orEmpty()
    val known = if (current.isEmpty()) ChronicDisease.None else ChronicDisease.of(current)
    // A stored value outside the list shows as written until it is changed (Flutter couldn't show it).
    val labels = diseases.map { tr(it.label) } + listOfNotNull(current.takeIf { known == null })
    Choice(
        tr(Res.string.chronicDisease),
        labels,
        known?.let(diseases::indexOf) ?: diseases.size,
        enabled,
    ) { i -> diseases.getOrNull(i)?.takeIf { it != known }?.let(actions.chronic) }
    if (p.chronic != null) {
        Text(tr(Res.string.chronicWarningNote), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Error)
    }

    val genders = listOf<Gender?>(null) + Gender.entries
    val gender = Gender.entries.firstOrNull { it.wire.equals(p.gender?.trim(), ignoreCase = true) }
    Choice(
        tr(Res.string.gender),
        genders.map {
            tr(
                when (it) {
                    null -> Res.string.notSpecified
                    Gender.Male -> Res.string.male
                    Gender.Female -> Res.string.female
                    Gender.Other -> Res.string.other
                },
            )
        },
        genders.indexOf(gender),
        enabled,
    ) { i -> if (genders[i] != gender) actions.gender(genders[i]) }

    val groups = listOf<String?>(null) + BLOOD_GROUPS
    val group = p.bloodGroup?.trim()?.takeIf { it in BLOOD_GROUPS }
    Choice(
        tr(Res.string.bloodGroup),
        groups.map { it ?: tr(Res.string.notSpecified) },
        groups.indexOf(group),
        enabled,
    ) { i -> if (groups[i] != group) actions.bloodGroup(groups[i]) }

    ShifaInputField(
        state.allergyText,
        actions.allergies,
        tr(Res.string.allergies),
        Modifier.fillMaxWidth(),
        supportingText = tr(Res.string.noKnownAllergies).takeIf { state.allergyText.isBlank() },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.saveAllergies() }),
    )
    if (state.allergiesDirty) {
        ShifaSecondaryButton(
            tr(Res.string.save),
            actions.saveAllergies,
            enabled = enabled,
            size = ShifaButtonSize.Compact,
        )
    }
}

/** Saving an empty phone list stops SMS reminders: asked first (flow 06/05 §8). */
@Composable
internal fun RemovePhones(
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) = ProfileSheet(onKeep) {
    ShifaDialogSheet(
        tr(Res.string.removeLastPhoneTitle),
        tr(Res.string.remove),
        onRemove,
        body = tr(Res.string.removeLastPhoneBody),
        tone = DialogTone.Destructive,
        icon = Res.drawable.ic_phone_22,
        cancelText = tr(Res.string.cancel),
        onCancel = onKeep,
    )
}
