package shifa.doctor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect

// Also rendered by the screenshot tests in androidUnitTest. Robolectric doesn't start the Compose resources
// ContentProvider, so inspection mode is forced to let the effect set the context.
@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalInspectionMode provides true) {
        PreviewContextConfigurationEffect()
        ShifaTheme(content)
    }

// A 402 dp column of specimens, the phone frame the boards use.
@Composable
internal fun Gallery(
    background: Color = ShifaColors.Bg.Surface,
    content: @Composable ColumnScope.() -> Unit,
) = PreviewTheme {
    Column(
        Modifier.width(402.dp).background(background).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        content = content,
    )
}

// The token gallery (CORE-02): every colour, text style, radius and shadow of docs/design/shifa-ds.css, so a
// screenshot diff shows any token change. Debug builds have no in-app gallery; this replaces it.

@Composable
private fun Swatches(
    title: String,
    colors: List<Pair<String, Color>>,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = ShifaColors.Text.Primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        colors.forEach { (name, color) ->
            Column(Modifier.width(ShifaSize.S80).padding(bottom = ShifaSpacing.Stack.Sm)) {
                Box(
                    Modifier
                        .size(ShifaSize.S80, ShifaSize.S36)
                        .background(color, ShifaRadius.Sm)
                        .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.Sm),
                )
                Text(name, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
            }
        }
    }
}

@Preview
@Composable
internal fun ColorsPreview() =
    Gallery {
        with(ShifaColors.Text) {
            Swatches(
                "Text",
                listOf(
                    "Primary" to Primary, "Secondary" to Secondary, "Tertiary" to Tertiary, "Disabled" to Disabled,
                    "Inverse" to Inverse, "Brand" to Brand, "BrandStrong" to BrandStrong, "Link" to Link,
                    "Error" to Error, "Warning" to Warning, "WarningStrong" to WarningStrong, "Info" to Info,
                ),
            )
        }
        with(ShifaColors.Icon) {
            Swatches(
                "Icon",
                listOf(
                    "Primary" to Primary,
                    "Secondary" to Secondary,
                    "Disabled" to Disabled,
                    "Inverse" to Inverse,
                    "Brand" to Brand,
                    "Error" to Error,
                    "Warning" to Warning,
                    "Info" to Info,
                ),
            )
        }
        with(ShifaColors.Bg) {
            Swatches(
                "Bg",
                listOf(
                    "Canvas" to Canvas, "Surface" to Surface, "Disabled" to Disabled, "Brand" to Brand,
                    "SuccessSubtle" to SuccessSubtle, "WarningSubtle" to WarningSubtle, "ErrorSubtle" to ErrorSubtle,
                    "InfoSubtle" to InfoSubtle, "Overlay" to Overlay, "Media" to Media, "Hover" to Hover,
                    "Selected" to Selected,
                ),
            )
        }
        with(ShifaColors.Border) {
            Swatches(
                "Border",
                listOf(
                    "Default" to Default,
                    "Disabled" to Disabled,
                    "Brand" to Brand,
                    "Focus" to Focus,
                    "Error" to Error,
                    "Warning" to Warning,
                    "Success" to Success,
                ),
            )
        }
        with(ShifaColors.Interactive) {
            Swatches(
                "Interactive",
                listOf(
                    "Primary" to Primary,
                    "OnPrimary" to OnPrimary,
                    "DisabledBg" to DisabledBg,
                    "Destructive" to Destructive,
                ),
            )
        }
        with(ShifaColors.Chart) {
            Swatches(
                "Chart",
                listOf(
                    "Series1" to Series1,
                    "Series2" to Series2,
                    "Series3" to Series3,
                    "Series4" to Series4,
                    "Grid" to Grid,
                ),
            )
        }
    }

@Preview
@Composable
internal fun AlphaAndRawPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        with(ShifaColors.Alpha) {
            Swatches(
                "Alpha",
                listOf(
                    "White15" to White15, "White20" to White20, "White25" to White25, "White35" to White35,
                    "White40" to White40, "White55" to White55, "White65" to White65, "White70" to White70,
                    "White90" to White90, "Teal75" to Teal75, "Teal25" to Teal25, "Teal15" to Teal15,
                    "Black5" to Black5, "Black25" to Black25, "Black50" to Black50,
                ),
            )
        }
        with(ShifaColors.Raw) {
            Swatches(
                "Raw",
                listOf(
                    "SplashBlob" to SplashBlob,
                    "SplashLogo" to SplashLogo,
                    "GlassTile" to GlassTile,
                    "VideoPreview" to VideoPreview,
                    "MapLand" to MapLand,
                    "MapPark" to MapPark,
                    "MapWater" to MapWater,
                    "MapStreet" to MapStreet,
                ),
            )
        }
    }

@Composable
private fun Specimen(
    name: String,
    style: TextStyle,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(name, Modifier.width(ShifaSize.S120), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Tertiary)
    Text("Dr. Aziza 1 234 567", style = style, color = ShifaColors.Text.Primary)
}

@Preview
@Composable
internal fun TypographyPreview() =
    Gallery {
        with(MaterialTheme.typography) {
            Specimen("headlineLarge", headlineLarge)
            Specimen("headlineSmall", headlineSmall)
            Specimen("titleMedium", titleMedium)
            Specimen("titleSmall", titleSmall)
            Specimen("bodyLarge", bodyLarge)
            Specimen("bodyMedium", bodyMedium)
            Specimen("bodySmall", bodySmall)
            Specimen("labelLarge", labelLarge)
            Specimen("labelMedium", labelMedium)
            Specimen("labelSmall", labelSmall)
        }
        with(ShifaTextStyles) {
            Specimen("caption", caption())
            Specimen("inputText", inputText())
            Specimen("chatInput", chatInput())
            Specimen("profileName", profileName())
            Specimen("trayTitle", trayTitle())
            Specimen("tag", tag())
            Specimen("chipFilter", chipFilter())
            Specimen("chipSlot", chipSlot())
            Specimen("chatMessage", chatMessage())
            Specimen("chatBubble", chatBubble())
            Specimen("callStatus", callStatus())
            Specimen("notificationBody", notificationBody())
            Specimen("kpiValue", kpiValue())
            Specimen("kpiLabel", kpiLabel())
            Specimen("tableHead", tableHead())
            Specimen("tableCell", tableCell())
            Specimen("navItem", navItem())
            Specimen("bodyText", bodyText())
        }
    }

@Composable
private fun ShapeTile(
    name: String,
    shape: Shape,
    shadow: Shadow? = null,
    size: Dp = ShifaSize.S64,
) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        Modifier
            .size(size)
            .then(if (shadow != null) Modifier.dropShadow(shape, shadow) else Modifier)
            .background(ShifaColors.Bg.Surface, shape)
            .border(ShifaSize.S1, ShifaColors.Border.Default, shape),
    )
    Text(name, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
}

@Preview
@Composable
internal fun ShapesPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Text("Radius", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            ShapeTile("Xs", ShifaRadius.Xs)
            ShapeTile("Sm", ShifaRadius.Sm)
            ShapeTile("ControlMd", ShifaRadius.ControlMd)
            ShapeTile("ControlLg", ShifaRadius.ControlLg)
            ShapeTile("Xl", ShifaRadius.Xl)
            ShapeTile("Full", ShifaRadius.Full)
        }
        Text("Shadow", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Inset.Lg),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Inset.Lg),
            modifier = Modifier.padding(ShifaSpacing.Inset.Sm),
        ) {
            ShapeTile("Default", ShifaRadius.ControlMd, ShifaShadow.Default)
            ShapeTile("Card", ShifaRadius.ControlMd, ShifaShadow.Card)
            ShapeTile("Tray", ShifaRadius.Xl, ShifaShadow.Tray)
            ShapeTile("Dropdown", ShifaRadius.ControlMd, ShifaShadow.Dropdown)
            ShapeTile("Section", ShifaRadius.ControlLg, ShifaShadow.Section)
            ShapeTile("NavBar", ShifaRadius.Xl, ShifaShadow.NavBar)
            ShapeTile("Fab", ShifaRadius.Full, ShifaShadow.Fab)
        }
        Text("Spacing (inset / gap / stack)", style = MaterialTheme.typography.titleSmall)
        listOf(
            "Inset" to
                listOf(
                    ShifaSpacing.Inset.Xs,
                    ShifaSpacing.Inset.Sm,
                    ShifaSpacing.Inset.Md,
                    ShifaSpacing.Inset.Lg,
                ),
            "Gap" to listOf(ShifaSpacing.Gap.Xs, ShifaSpacing.Gap.Sm, ShifaSpacing.Gap.Md, ShifaSpacing.Gap.Lg),
            "Stack" to listOf(ShifaSpacing.Stack.Xs, ShifaSpacing.Stack.Sm, ShifaSpacing.Stack.Md),
        ).forEach { (name, steps) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            ) {
                Text(name, Modifier.width(ShifaSize.S48), style = ShifaTextStyles.caption())
                steps.forEach { Box(Modifier.size(it, ShifaSize.S12).background(ShifaColors.Bg.Brand)) }
            }
        }
    }
