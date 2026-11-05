package shifa.doctor.ui.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.PatientOption
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.newPatient
import shifa.doctor.generated.resources.noPatientsFound
import shifa.doctor.generated.resources.patientIdLabel
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchByNameOrId
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.tr

/** By name or id, as typed (flow 04/06 §3b, 07/13 §3). */
fun List<PatientOption>.matching(query: String): List<PatientOption> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    return filter { it.name.orEmpty().lowercase().contains(q) || it.id.toString().contains(q) }
}

/**
 * Picks one of the doctor's patients (booking, a new task): search by name or id, a tap picks and closes. [patients]
 * null shows skeletons; [onNew] adds "New patient" (booking only); [selectedId] ticks the current one.
 */
@Composable
internal fun PatientPickerSheet(
    title: String,
    patients: List<PatientOption>?,
    error: CodeBanner?,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (PatientOption) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onNew: (() -> Unit)? = null,
    selectedId: Long? = null,
) = ProfileSheet(onClose) {
    PatientPickerTray(title, patients, error, query, onQuery, onPick, onRetry, onClose, onNew, selectedId)
}

/** The sheet's content (previews draw it without the sheet). */
@Composable
internal fun PatientPickerTray(
    title: String,
    patients: List<PatientOption>?,
    error: CodeBanner?,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (PatientOption) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onNew: (() -> Unit)? = null,
    selectedId: Long? = null,
) = ShifaTray(title, onClose) {
    onNew?.let { ShifaLink(tr(Res.string.newPatient), it) }
    ShifaSearchField(query, onQuery, tr(Res.string.searchByNameOrId), Modifier.fillMaxWidth())
    when {
        error != null -> {
            codeBanner(error)?.invoke()
            ShifaLink(tr(Res.string.retry), onRetry)
        }
        patients == null -> ShifaListSkeleton(count = 4)
        patients.isEmpty() ->
            Text(
                tr(Res.string.noPatientsFound),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Secondary,
            )
        else ->
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                items(patients, key = { it.id ?: 0L }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(p) }
                            .padding(vertical = ShifaSpacing.Inset.Xs),
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShifaAvatar(p.photoUrl, ShifaSize.S40)
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name?.takeIf { it.isNotBlank() } ?: "#${p.id}",
                                style = ShifaTextStyles.dialogBody(),
                                color = ShifaColors.Text.Primary,
                            )
                            Text(
                                tr(Res.string.patientIdLabel, p.id.toString()),
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Secondary,
                            )
                        }
                        if (selectedId != null && p.id == selectedId) {
                            Icon(painterResource(Res.drawable.ic_check_16), null, tint = ShifaColors.Icon.Brand)
                        }
                    }
                }
            }
    }
}
