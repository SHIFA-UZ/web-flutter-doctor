package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.english
import shifa.doctor.generated.resources.russian
import shifa.doctor.generated.resources.selectLanguage
import shifa.doctor.generated.resources.uzbek
import shifa.doctor.generated.resources.uzbekCyrillic
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.tr

/**
 * SET-02 (flow 01/15 §3a, DaProfile `langSettings`): the four languages, each in its own name with the name in the
 * current language under it; a tap applies at once (no Save). The backend hears about it from `App` (`PUT
 * /doctors/me/language`, Cyrillic Uzbek as `uz`, deduplicated) and the next settings PATCH carries it too.
 */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val current by languageStorage.language.collectAsStateWithLifecycle()
    LanguageContent(current, languageStorage::set, onBack)
}

@Composable
fun LanguageContent(
    current: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(tr(Res.string.selectLanguage), Modifier.statusBarsPadding(), onBack = onBack)
    // Scrolls: four two-line rows don't fit a landscape phone at 200 % text.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md)) {
        ShifaSection(null) {
            ShifaOptionList(
                AppLanguage.entries.map { OptionItem(it.displayName, selected = it == current, sub = tr(it.label)) },
                { i -> onPick(AppLanguage.entries[i]) },
                role = Role.RadioButton,
            )
        }
    }
}

private val AppLanguage.label: StringResource
    get() =
        when (this) {
            AppLanguage.English -> Res.string.english
            AppLanguage.Uzbek -> Res.string.uzbek
            AppLanguage.UzbekCyrillic -> Res.string.uzbekCyrillic
            AppLanguage.Russian -> Res.string.russian
        }
