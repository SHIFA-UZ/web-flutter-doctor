package shifa.doctor.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.catalog.matching
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clearAll
import shifa.doctor.generated.resources.current
import shifa.doctor.generated.resources.done
import shifa.doctor.generated.resources.profession
import shifa.doctor.generated.resources.searchProfession
import shifa.doctor.generated.resources.searching
import shifa.doctor.ui.register.label
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaPickerSheet
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.utils.tr

/**
 * The profession sheet (PROF-05, DaAuth/DaProfile picker `prof`), shared by registration step 2 and Personal
 * information: search over both labels, several picks, "Clear all" and "Done"; the bundled catalogue with "Couldn't
 * load. Retry" above it when the backend fails. Host it in a `ModalBottomSheet`.
 */
@Composable
internal fun ProfessionPickerSheet(
    query: String,
    onQuery: (String) -> Unit,
    catalog: List<Profession>,
    selected: List<Profession>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onToggle: (Profession) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    // A saved profession the catalogue doesn't know (legacy free text) stays a ticked "Current: …" row on top, so it can
    // be unticked on its own (flow 08/02 §6).
    val legacy =
        if (catalog.isEmpty()) {
            emptyList()
        } else {
            selected.filter {
                    s ->
                catalog.none { it.english == s.english }
            }
        }
    val shownLegacy = legacy.matching(query)
    val options = shownLegacy + catalog.matching(query)
    val current = tr(Res.string.current)
    ShifaPickerSheet(
        tr(Res.string.profession),
        query,
        onQuery,
        tr(Res.string.searchProfession),
        options.mapIndexed { i, p ->
            OptionItem(
                if (i < shownLegacy.size) "$current: ${p.label()}" else p.label(),
                selected = selected.any { it.english == p.english },
            )
        },
        onPick = { onToggle(options[it]) },
        onClose = onDone,
        loadingText = if (loading) tr(Res.string.searching) else null,
        onRetry = onRetry.takeIf { failed },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                if (selected.isNotEmpty()) ShifaSecondaryButton(tr(Res.string.clearAll), onClear, Modifier.weight(1f))
                ShifaPrimaryButton(tr(Res.string.done), onDone, Modifier.weight(1f))
            }
        },
    )
}
