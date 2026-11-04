package shifa.doctor.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.ic_arrow_l_20
import shifa.doctor.generated.resources.language
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaFontSize
import shifa.doctor.ui.theme.ShifaLetterSpacing
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.LanguageToggle
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.utils.tr

/**
 * The frame every sign-in screen shares (DaAuth board): a bar with Back (when there is somewhere to go back to), an
 * optional bar title and the language pill; the [banner] slot right under it (forced-logout reasons and errors,
 * flow 01/02 §3); then the content, which scrolls so the keyboard and 200 % text never hide the buttons. At most
 * 560 dp wide (tablets).
 */
@Composable
fun AuthFrame(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    barTitle: String? = null,
    showLanguage: Boolean = true,
    centered: Boolean = true,
    banner: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Box(
    modifier.fillMaxSize().background(ShifaColors.Bg.Surface).safeDrawingPadding().imePadding(),
    Alignment.TopCenter,
) {
    Column(
        Modifier.widthIn(
            max = ShifaDimens.Layout.FormNarrow,
        ).fillMaxSize(), // phones use the full width; 520 is the web card
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(
                min = ShifaSize.S40,
            ).padding(horizontal = ShifaSpacing.Inset.Lg, vertical = ShifaSize.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                ShifaIconButton(
                    Res.drawable.ic_arrow_l_20,
                    tr(Res.string.back),
                    onBack,
                    variant = ShifaIconButtonVariant.Secondary,
                )
            } else {
                Spacer(Modifier.size(ShifaSize.S40))
            }
            Text(
                barTitle.orEmpty(),
                Modifier.weight(1f),
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
                textAlign = TextAlign.Center,
            )
            if (showLanguage) LanguagePill() else Spacer(Modifier.size(ShifaSize.S40))
        }
        // The banner sits right under the bar (flow 01/02 §3), outside the centred form.
        banner?.let {
            Box(
                Modifier.padding(start = ShifaSpacing.Inset.Lg, end = ShifaSpacing.Inset.Lg, bottom = ShifaSize.S8),
            ) { it() }
        }
        // DaAuth "justify": centred on Sign In and the code steps, from the top on the long registration forms.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(start = ShifaSpacing.Inset.Lg, end = ShifaSpacing.Inset.Lg, bottom = ShifaSize.S32),
                verticalArrangement =
                    Arrangement.spacedBy(
                        ShifaSize.S16,
                        if (centered) Alignment.CenterVertically else Alignment.Top,
                    ),
            ) { content() }
        }
    }
}

/** The "S" mark above an auth title ([size] 80 on Sign In, 120 on Verify). Decorative. */
@Composable
fun AuthLogo(size: Dp = ShifaSize.S80) =
    Box(
        Modifier
            .clearAndSetSemantics { }
            .size(size)
            .clip(ShifaRadius.Xl)
            .background(ShifaColors.Interactive.Primary),
        contentAlignment = Alignment.Center,
    ) { Text("S", style = ShifaTextStyles.splashMark(), color = ShifaColors.Text.Inverse) }

/** The screen title: 28 bold centred on Sign In / Create account, 24 bold left on the e-mail and code steps. */
@Composable
fun AuthTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = ShifaFontSize.F28,
    align: TextAlign = TextAlign.Center,
) = Text(
    text,
    modifier.fillMaxWidth().semantics { heading() },
    style =
        ShifaTextStyles.panelTitle().copy(
            fontSize = size,
            lineHeight = size * TITLE_LINE,
            letterSpacing = ShifaLetterSpacing.Tight,
        ),
    color = ShifaColors.Text.Primary,
    textAlign = align,
)

private const val TITLE_LINE = 1.15f

/** The UI language and how to change it; `App` provides the real one, previews get English and a no-op. */
class LanguageSwitch(
    val current: AppLanguage,
    val set: (AppLanguage) -> Unit,
)

val LocalLanguageSwitch = staticCompositionLocalOf { LanguageSwitch(AppLanguage.English) {} }

/** Flow 01/15: EN / UZ / ЎЗ / RU; picking one re-renders at once and counts as an explicit choice. */
@Composable
fun LanguagePill() {
    val switch = LocalLanguageSwitch.current
    val current = switch.current
    var open by remember { mutableStateOf(false) }
    Box {
        LanguageToggle(current.short, tr(Res.string.language), { open = true })
        DropdownMenu(open, { open = false }, containerColor = ShifaColors.Bg.Surface) {
            ShifaOptionList(
                AppLanguage.entries.map { OptionItem(it.short, selected = it == current) },
                { i ->
                    switch.set(AppLanguage.entries[i])
                    open = false
                },
            )
        }
    }
}

/** The pill's code: EN, UZ, ЎЗ, RU (01 §8). */
val AppLanguage.short: String
    get() =
        when (this) {
            AppLanguage.English -> "EN"
            AppLanguage.Uzbek -> "UZ"
            AppLanguage.UzbekCyrillic -> "ЎЗ"
            AppLanguage.Russian -> "RU"
        }
