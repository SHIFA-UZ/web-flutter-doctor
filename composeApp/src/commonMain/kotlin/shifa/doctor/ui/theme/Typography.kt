package shifa.doctor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.manrope_bold
import shifa.doctor.generated.resources.manrope_medium
import shifa.doctor.generated.resources.manrope_regular
import shifa.doctor.generated.resources.manrope_semibold

@Composable
fun manropeFontFamily() =
    FontFamily(
        Font(Res.font.manrope_regular, weight = FontWeight.Normal),
        Font(Res.font.manrope_medium, weight = FontWeight.Medium),
        Font(Res.font.manrope_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.manrope_bold, weight = FontWeight.Bold),
    )

private fun manrope(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Int = 0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    // Design line boxes: glyphs centered, no trim. See [lineBox] for line heights below the font's.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

// docs/design/tokens.json `type.groups` "Material slots" (the same metrics as the patient app).
@Composable
fun shifaTypography(): Typography {
    val family = manropeFontFamily()
    val base = Typography()
    return Typography(
        // Not used by the design: M3 metrics, Manrope family.
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        // page-header, main tab title
        headlineLarge = manrope(family, FontWeight.Bold, 32, 40, -1),
        // page-header, other / sub page title
        headlineSmall = manrope(family, FontWeight.Bold, 24, 28, -1),
        // section header ("Upcoming Appointment", "My Tasks")
        titleMedium = manrope(family, FontWeight.Bold, 20, 22, -1),
        // card title (doctor name, task name)
        titleSmall = manrope(family, FontWeight.Bold, 14, 16),
        // input-field value / placeholder
        bodyLarge = manrope(family, FontWeight.Medium, 14, 16),
        // card body ("Dentist - Clinic", time)
        bodyMedium = manrope(family, FontWeight.Medium, 12, 12),
        // card meta ("Due today"), usually Text.Tertiary
        bodySmall = manrope(family, FontWeight.Normal, 12, 12),
        // primary / secondary / tertiary button
        labelLarge = manrope(family, FontWeight.Bold, 16, 22),
        // action-button (pill)
        labelMedium = manrope(family, FontWeight.Bold, 12, 16),
        // notification time, attachment count
        labelSmall = manrope(family, FontWeight.Bold, 10, 12),
    )
}

// tokens.json "Shifa text styles" and "Doctor workspace": styles with no M3 slot.
object ShifaTextStyles {
    // input-field label, suggestive text; nav-bar tile label uses Medium (Bold when selected)
    @Composable
    fun caption() = manrope(manropeFontFamily(), FontWeight.Normal, 10, 14)

    // "Input text"
    @Composable
    fun inputText() = manrope(manropeFontFamily(), FontWeight.SemiBold, 16, 16)

    // chat-bar and search-field input / placeholder
    @Composable
    fun chatInput() = manrope(manropeFontFamily(), FontWeight.SemiBold, 14, 16)

    // profile name (Bold "Input text"); doctor, appointment and task card titles
    @Composable
    fun profileName() = manrope(manropeFontFamily(), FontWeight.Bold, 16, 16)

    // tray and pop-up title
    @Composable
    fun trayTitle() = manrope(manropeFontFamily(), FontWeight.Bold, 16, 22, -1)

    // tag
    @Composable
    fun tag() = manrope(manropeFontFamily(), FontWeight.Bold, 12, 12)

    // interactive-chip, Type=filter / Type=slot (Bold when active)
    @Composable
    fun chipFilter() = manrope(manropeFontFamily(), FontWeight.Medium, 12, 18)

    @Composable
    fun chipSlot() = manrope(manropeFontFamily(), FontWeight.Medium, 14, 18)

    // doctor's notes and bio
    @Composable
    fun chatMessage() = manrope(manropeFontFamily(), FontWeight.Normal, 12, 14)

    // chat-bubble message (semantic/type/input, Regular, line height 18)
    @Composable
    fun chatBubble() = manrope(manropeFontFamily(), FontWeight.Normal, 16, 18)

    // video call: waiting for the doctor (Bold 18, line height 28)
    @Composable
    fun callStatus() = manrope(manropeFontFamily(), FontWeight.Bold, 18, 28, -1)

    // notification body (Medium 12, line height 14)
    @Composable
    fun notificationBody() = manrope(manropeFontFamily(), FontWeight.Medium, 12, 14)

    // `.dr-tab`, `.dr-underline` and menu labels (Medium 14/16; Bold when selected)
    @Composable
    fun tabLabel() = manrope(manropeFontFamily(), FontWeight.Medium, 14, 16)

    // row and card meta lines (`.dr-*__meta`, Medium 12/14)
    @Composable
    fun metaSmall() = manrope(manropeFontFamily(), FontWeight.Medium, 12, 14)

    // dialog, empty-state, toast and insight text (Medium 14/20)
    @Composable
    fun dialogBody() = manrope(manropeFontFamily(), FontWeight.Medium, 14, 20)

    // panel, card and empty-state titles (`.dr-plan__title`, `.dr-empty__title`, `.dr-form__title`; Bold 16/22)
    @Composable
    fun panelTitle() = manrope(manropeFontFamily(), FontWeight.Bold, 16, 22)

    // OTP cell digit (Bold 24/28) and PIN pad key (SemiBold 24/28)
    @Composable
    fun otpDigit() = manrope(manropeFontFamily(), FontWeight.Bold, 24, 28)

    @Composable
    fun pinKey() = manrope(manropeFontFamily(), FontWeight.SemiBold, 24, 28)

    // count bubbles on tabs, nav items and avatars (Bold 10/16)
    @Composable
    fun badgeCount() = manrope(manropeFontFamily(), FontWeight.Bold, 10, 16)

    // legends and progress labels (`.dr-slider__legend`, `.dr-plan__progress`; SemiBold 12/16)
    @Composable
    fun legend() = manrope(manropeFontFamily(), FontWeight.SemiBold, 12, 16)

    // `.dr-context__name` (Bold 16/20)
    @Composable
    fun contextName() = manrope(manropeFontFamily(), FontWeight.Bold, 16, 20)

    // the date in the day strip (`.dr-daystrip b`, Bold 16/20)
    @Composable
    fun dayNumber() = manrope(manropeFontFamily(), FontWeight.Bold, 16, 20)

    // FDI number on a tooth (Bold 10/12)
    @Composable
    fun toothNumber() = manrope(manropeFontFamily(), FontWeight.Bold, 10, 12)

    // the hero's patient name at phone width (`.dr-hero__name`, Bold 20/24)
    @Composable
    fun heroNamePhone() = manrope(manropeFontFamily(), FontWeight.Bold, 20, 24, -1)

    // times in the timeline gutter (Bold 14/16, tabular numerals)
    @Composable
    fun timeLabel() = manrope(manropeFontFamily(), FontWeight.Bold, 14, 16).copy(fontFeatureSettings = "tnum")

    // `.sh-btn--compact` label (Bold 14/16)
    @Composable
    fun buttonCompact() = manrope(manropeFontFamily(), FontWeight.Bold, 14, 16)

    // Doctor workspace (`.kpi-value`, `.kpi-label`, …): KpiCard value with tabular numerals, and its label
    @Composable
    fun kpiValue() = manrope(manropeFontFamily(), FontWeight.Bold, 32, 36, -1).copy(fontFeatureSettings = "tnum")

    // `.dr-kpi__value` below 560: 24/28
    @Composable
    fun kpiValuePhone() = manrope(manropeFontFamily(), FontWeight.Bold, 24, 28, -1).copy(fontFeatureSettings = "tnum")

    @Composable
    fun kpiLabel() = manrope(manropeFontFamily(), FontWeight.Medium, 12, 16)

    // DataTable header and cell (tablet lists, finance ledgers)
    @Composable
    fun tableHead() = manrope(manropeFontFamily(), FontWeight.Bold, 12, 16)

    @Composable
    fun tableCell() = manrope(manropeFontFamily(), FontWeight.Medium, 14, 20)

    // sidebar / rail item (tablet shell)
    @Composable
    fun navItem() = manrope(manropeFontFamily(), FontWeight.Medium, 14, 20)

    // running text: notes, AI answers, legal pages
    @Composable
    fun bodyText() = manrope(manropeFontFamily(), FontWeight.Normal, 14, 20)
}

// The stylesheet's raw type scale (`--font-size-*`, `--line-height-*`, `--letter-spacing-*`), for the few places a
// design block sets a size without a named style. Prefer the named styles above.
object ShifaFontSize {
    val F10 = 10.sp
    val F12 = 12.sp
    val F14 = 14.sp
    val F16 = 16.sp
    val F18 = 18.sp
    val F20 = 20.sp
    val F24 = 24.sp
    val F28 = 28.sp
    val F32 = 32.sp
    val F40 = 40.sp
}

object ShifaLineHeight {
    val L12 = 12.sp
    val L14 = 14.sp
    val L16 = 16.sp
    val L18 = 18.sp
    val L20 = 20.sp
    val L22 = 22.sp
    val L24 = 24.sp
    val L26 = 26.sp
    val L28 = 28.sp
    val L32 = 32.sp
    val L44 = 44.sp
}

object ShifaLetterSpacing {
    val Tight = (-1).sp
    val Snug = (-0.5).sp
    val Caps = 0.04.em // section kickers in capitals
    val Code = 10.sp // the spaced digits of an invite key or code
}

/**
 * Sizes text to its design line boxes (lines × line height). Compose pads a line height smaller than the
 * font's own height (e.g. 12/12) back up to it; here the glyphs overflow the box instead, as in the design.
 */
fun Modifier.lineBox(style: TextStyle) =
    layout { measurable, constraints ->
        val text = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val line = style.lineHeight.roundToPx()
        // The padding is less than a line, so rounding down gives the line count.
        val lines = (text.height / line).coerceAtLeast(1)
        val height = (lines * line).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(text.width, height) { text.place(0, (height - text.height) / 2) }
    }
