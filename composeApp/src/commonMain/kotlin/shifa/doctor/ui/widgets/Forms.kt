package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/**
 * `.dr-actionbar`: the one footer of every form, wizard, booking and visit screen. On phones the actions are full
 * width and stacked, the primary at the bottom; [state] is the left note ("Unsaved changes" / "No changes yet").
 * The screen puts it last in a column that fills the viewport, so it sits at the bottom.
 */
@Composable
fun ActionBar(
    modifier: Modifier = Modifier,
    state: String? = null,
    secondary: (@Composable (Modifier) -> Unit)? = null,
    primary: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier.fillMaxWidth().background(ShifaColors.Bg.Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
        val note = @Composable { m: Modifier ->
            state?.let {
                Row(
                    m,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(Res.drawable.ic_check_circle_16), null, tint = ShifaColors.Icon.Brand)
                    Text(it, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary)
                }
            }
        }
        val padding =
            Modifier
                .navigationBarsPadding()
                .padding(
                    start = ShifaSpacing.Inset.Md,
                    end = ShifaSpacing.Inset.Md,
                    top = ShifaSpacing.Inset.Sm,
                    bottom = ShifaSpacing.Inset.Md,
                )
        if (LocalWideLayout.current) {
            // `.dr-actionbar__inner` on web: at most `layout-form-wide`, the note at the left, the actions (at least
            // 160 wide) at the right, the primary last.
            Row(
                Modifier.widthIn(max = ShifaDimens.Layout.FormWide).fillMaxWidth().then(padding),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { note(Modifier) }
                secondary?.invoke(Modifier.widthIn(min = ShifaSize.S160))
                primary(Modifier.widthIn(min = ShifaSize.S160))
            }
        } else {
            Column(padding, verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
                note(Modifier)
                secondary?.invoke(Modifier.fillMaxWidth())
                primary(Modifier.fillMaxWidth())
            }
        }
    }
}

/** `.dr-form`: a white section of a form with its title, description and fields (one column on phones). */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().background(
            ShifaColors.Bg.Surface,
            ShifaRadius.ControlLg,
        ).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(title, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
            description?.let { Text(it, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md), content = content)
    }
}

/**
 * `.dr-stepper`: a multi-step flow's header ("Step 2 of 4 · Services", the bar). On phones the step names are
 * hidden; the current bar segment wears a teal halo.
 */
@Composable
fun Stepper(
    stepLabel: String,
    title: String,
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        Text(stepLabel, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Secondary)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            repeat(total) { i ->
                val fill = if (i <= current) ShifaColors.Interactive.Primary else ShifaColors.Border.Default
                val halo = if (i == current) ShifaColors.Alpha.Teal25 else Color.Transparent
                Box(
                    Modifier
                        .weight(1f)
                        .height(ShifaSize.S4)
                        .drawBehind {
                            val r = ShifaSize.S2.toPx()
                            drawRoundRect(
                                halo,
                                topLeft = androidx.compose.ui.geometry.Offset(-r, -r),
                                size = androidx.compose.ui.geometry.Size(size.width + 2 * r, size.height + 2 * r),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                            )
                        }.clip(ShifaRadius.Full)
                        .background(fill),
                )
            }
        }
    }
}
