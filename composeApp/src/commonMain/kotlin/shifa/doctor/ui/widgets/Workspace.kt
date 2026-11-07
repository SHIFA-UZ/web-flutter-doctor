package shifa.doctor.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.generated.resources.ic_clinic_22
import shifa.doctor.generated.resources.ic_minus_16
import shifa.doctor.generated.resources.ic_pin_40
import shifa.doctor.generated.resources.notIncluded
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.tr

/**
 * `.dr-slider`: a 0–100 value (revenue share) with its number field in [valueField], the split bar and the legend
 * ("Doctor 70 %" / "Clinic 30 %").
 */
@OptIn(ExperimentalMaterial3Api::class) // custom thumb and track
@Composable
fun ShifaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    leftLabel: String,
    rightLabel: String,
    modifier: Modifier = Modifier,
    valueField: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).height(ShifaSize.S28),
                colors =
                    SliderDefaults.colors(
                        thumbColor = ShifaColors.Bg.Surface,
                        activeTrackColor = ShifaColors.Interactive.Primary,
                        inactiveTrackColor = ShifaColors.Border.Default,
                    ),
                thumb = {
                    Box(
                        Modifier
                            .size(ShifaSize.S22)
                            .dropShadow(ShifaRadius.Full, ShifaShadow.Dropdown)
                            .background(ShifaColors.Bg.Surface, ShifaRadius.Full)
                            .border(ShifaSize.S2, ShifaColors.Interactive.Primary, ShifaRadius.Full),
                    )
                },
                track = { state ->
                    val fraction = state.value / 100f
                    Box(
                        Modifier.fillMaxWidth().height(
                            ShifaSize.S6,
                        ).clip(ShifaRadius.Full).background(ShifaColors.Border.Default),
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ShifaColors.Interactive.Primary))
                    }
                },
            )
            valueField?.let { Box(Modifier.width(ShifaSize.S88)) { it() } }
        }
        Box(Modifier.fillMaxWidth().height(ShifaSize.S8).clip(ShifaRadius.Full).background(ShifaColors.Bg.Canvas)) {
            Box(Modifier.fillMaxWidth(value / 100f).fillMaxHeight().background(ShifaColors.Interactive.Primary))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(leftLabel, style = ShifaTextStyles.legend(), color = ShifaColors.Text.Primary)
            Text(rightLabel, style = ShifaTextStyles.legend(), color = ShifaColors.Text.Secondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * `.dr-plan`: a treatment plan in the plans list: title, patient line, status badge in [status], visit progress,
 * the money line (paid, owed in red) and [actions].
 */
@Composable
fun TreatmentPlanCard(
    title: String,
    who: String,
    progressLabel: String,
    progress: Float,
    paid: String,
    total: String,
    modifier: Modifier = Modifier,
    owed: String? = null,
    status: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Surface)
            .then(
                if (onClick != null) {
                    Modifier.pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = ShifaTextStyles.panelTitle(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(who, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary)
            }
            status?.invoke()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaProgressBar(progress, Modifier.weight(1f))
            Text(progressLabel, style = ShifaTextStyles.legend(), color = ShifaColors.Text.Primary)
        }
        // The amounts wrap as whole phrases on a phone (UZS amounts are long).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
        ) {
            Text(
                paid,
                style = ShifaTextStyles.dialogBody().copy(fontWeight = FontWeight.Bold),
                color = ShifaColors.Text.Primary,
            )
            Text(total, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
            owed?.let {
                Text(
                    it,
                    style = ShifaTextStyles.dialogBody().copy(fontWeight = FontWeight.Bold),
                    color = ShifaColors.Text.Error,
                )
            }
        }
        actions?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                it()
            }
        }
    }
}

/**
 * `.dr-inst__row`: one installment of a plan (number, date field, amount field, remove). [sum] is the running
 * check below the rows; [sumOff] turns it red when the installments do not add up to the plan total.
 */
@Composable
fun InstallmentRow(
    number: Int,
    date: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    remove: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Phone layout: number | date over amount | remove spanning both rows.
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$number",
            Modifier.width(ShifaSize.S24),
            style = ShifaTextStyles.tableHead(),
            color = ShifaColors.Text.Secondary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            date()
            amount()
        }
        remove()
    }
}

/** `.dr-inst__sum`: the installments' total against the plan's. */
@Composable
fun InstallmentSum(
    text: String,
    off: Boolean,
    modifier: Modifier = Modifier,
) = Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
    verticalAlignment = Alignment.CenterVertically,
) {
    val color = if (off) ShifaColors.Text.Error else ShifaColors.Text.BrandStrong
    Icon(painterResource(if (off) Res.drawable.ic_minus_16 else Res.drawable.ic_check_16), null, tint = color)
    Text(text, style = ShifaTextStyles.tableHead(), color = color)
}

/** A line of a [TierCard]: [included] gets the teal tick, the rest a grey dash in tertiary text. */
data class TierFeature(
    val text: String,
    val included: Boolean = true,
)

/** `.dr-tier`: one subscription plan. [current] wears the focus ring on teal-subtle; [action] sits at the bottom. */
@Composable
fun TierCard(
    name: String,
    price: String,
    period: String,
    features: List<TierFeature>,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlLg)
            .background(if (current) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Surface)
            .border(
                ShifaSize.S2,
                if (current) ShifaColors.Border.Focus else ShifaColors.Border.Default,
                ShifaRadius.ControlLg,
            )
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
            badge?.invoke()
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs)) {
            Text(price, style = MaterialTheme.typography.headlineSmall, color = ShifaColors.Text.Primary)
            Text(period, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs)) {
            val notIncluded = tr(Res.string.notIncluded)
            features.forEach { f ->
                Row(
                    Modifier.semantics(mergeDescendants = true) { if (!f.included) stateDescription = notIncluded },
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (f.included) Res.drawable.ic_check_16 else Res.drawable.ic_minus_16),
                        null,
                        tint = if (f.included) ShifaColors.Icon.Brand else ShifaColors.Icon.Disabled,
                    )
                    Text(
                        f.text,
                        style = ShifaTextStyles.dialogBody(),
                        color = if (f.included) ShifaColors.Text.Primary else ShifaColors.Text.Tertiary,
                    )
                }
            }
        }
        action?.invoke()
    }
}

/**
 * `.dr-context`: the clinic (or location) the workspace is showing, with the doctor's [role] there. Tapping opens
 * the switcher (a `ShifaTray` with a `ShifaOptionList`).
 */
@Composable
fun ContextSwitcher(
    name: String,
    meta: String,
    modifier: Modifier = Modifier,
    role: String? = null,
    icon: DrawableResource = Res.drawable.ic_clinic_22,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.SuccessSubtle)
            .then(
                if (onClick != null) {
                    Modifier.pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.Brand, ShifaRadius.ControlMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = ShifaColors.Icon.Inverse)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    Modifier.weight(1f, fill = false),
                    style = ShifaTextStyles.contextName(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onClick != null) {
                    Icon(
                        painterResource(Res.drawable.ic_chev_d_16),
                        null,
                        tint = ShifaColors.Icon.Primary,
                    )
                }
            }
            Text(
                meta,
                style = ShifaTextStyles.kpiLabel(),
                color = ShifaColors.Text.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // `.dr-context__role`: under the meta line, in the main column (the board; it sat on the right before).
            role?.let { Text(it, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary) }
        }
    }
}

/**
 * `.dr-map`: the map pin picker's frame. Until the native map lands (D-06, CORE-07) it draws the board's placeholder
 * map (land, a street grid) with the pin fixed in the centre; [search] floats on top and [sheet] (the address and
 * Confirm) docks at the bottom.
 */
@Composable
fun MapPicker(
    modifier: Modifier = Modifier,
    map: (@Composable BoxScope.() -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().heightIn(min = ShifaDimens.Layout.DialogSm).clip(ShifaRadius.ControlLg)) {
        if (map != null) {
            map()
        } else {
            val grid = ShifaColors.Alpha.White70
            Canvas(Modifier.fillMaxSize().background(ShifaColors.Raw.MapLand)) {
                val step = ShifaSize.S56.toPx()
                val w = ShifaSize.S2.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), w)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), w)
                    y += step
                }
            }
        }
        Icon(
            painterResource(Res.drawable.ic_pin_40),
            null,
            Modifier.align(Alignment.Center).padding(bottom = ShifaSize.S40),
            tint = ShifaColors.Interactive.Destructive,
        )
        search?.let { Box(Modifier.align(Alignment.TopCenter).padding(ShifaSpacing.Inset.Md)) { it() } }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .dropShadow(ShifaRadius.Xl, ShifaShadow.Tray)
                .background(ShifaColors.Bg.Surface, ShifaRadius.Xl)
                .padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            content = sheet,
        )
    }
}

/** A control of [CallControlBar]: `.dr-cbtn` (white-70 disc; [off] = white with a red icon; [end] = the red hang-up). */
data class CallControl(
    val icon: DrawableResource,
    val description: String,
    val onClick: () -> Unit,
    val off: Boolean = false,
    val end: Boolean = false,
)

/** `.dr-callbar`: the video room's controls in a dark pill (mute, camera, flip, notes, end). */
@Composable
fun CallControlBar(
    controls: List<CallControl>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(ShifaRadius.Full).background(ShifaColors.Alpha.Black50).padding(ShifaSpacing.Inset.Xs),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    ) {
        controls.forEach { c ->
            val (bg, tint) =
                when {
                    c.end -> ShifaColors.Interactive.Destructive to ShifaColors.Icon.Inverse
                    c.off -> ShifaColors.Bg.Surface to ShifaColors.Icon.Error
                    else -> ShifaColors.Alpha.White70 to ShifaColors.Icon.Primary
                }
            Box(
                Modifier
                    .size(if (c.end) ShifaSize.S64 else ShifaSize.S48, ShifaSize.S48)
                    .clip(ShifaRadius.Full)
                    .background(bg)
                    .pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button, onClickLabel = c.description, onClick = c.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(c.icon), c.description, tint = tint)
            }
        }
    }
}
