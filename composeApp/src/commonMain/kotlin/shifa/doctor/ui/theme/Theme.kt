package shifa.doctor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// The design defines light mode only (docs/design). Stock M3 components read these roles;
// our widgets use ShifaColors directly (1:1 with the stylesheet names).
private val LightColorScheme =
    lightColorScheme(
        primary = ShifaColors.Interactive.Primary,
        onPrimary = ShifaColors.Interactive.OnPrimary,
        primaryContainer = ShifaColors.Bg.SuccessSubtle,
        onPrimaryContainer = ShifaColors.Text.Brand,
        secondary = ShifaColors.Interactive.Primary,
        onSecondary = ShifaColors.Interactive.OnPrimary,
        secondaryContainer = ShifaColors.Bg.SuccessSubtle,
        onSecondaryContainer = ShifaColors.Text.Brand,
        background = ShifaColors.Bg.Canvas,
        onBackground = ShifaColors.Text.Primary,
        surface = ShifaColors.Bg.Surface,
        onSurface = ShifaColors.Text.Primary,
        surfaceVariant = ShifaColors.Bg.Canvas,
        onSurfaceVariant = ShifaColors.Text.Tertiary,
        surfaceContainerLowest = ShifaColors.Bg.Surface,
        surfaceContainerLow = ShifaColors.Bg.Surface,
        surfaceContainer = ShifaColors.Bg.Surface,
        surfaceContainerHigh = ShifaColors.Bg.Surface,
        surfaceContainerHighest = ShifaColors.Bg.Surface,
        inverseSurface = ShifaColors.Text.Primary,
        inverseOnSurface = ShifaColors.Text.Inverse,
        outline = ShifaColors.Border.Default,
        outlineVariant = ShifaColors.Border.Default,
        error = ShifaColors.Text.Error,
        onError = ShifaColors.Text.Inverse,
        errorContainer = ShifaColors.Bg.ErrorSubtle,
        onErrorContainer = ShifaColors.Text.Error,
        scrim = ShifaColors.Bg.Overlay,
    )

@Composable
fun ShifaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = shifaTypography(),
        shapes = ShifaShapes,
        content = content,
    )
}
