package shifa.doctor.ui.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.stringResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.loading
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing

/**
 * Not in Figma: a placeholder block that pulses while its content loads. Canvas grey, so it reads on surface cards.
 * Still in previews and screenshot tests, like the chat's waveform.
 */
@Composable
fun ShifaSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = ShifaRadius.Sm,
) {
    Box(modifier.background(shimmerBrush(), shape))
}

/**
 * A card-shaped placeholder: avatar, then [lines] text bars, in the cards' chrome. [avatar] matches the card it
 * stands in for (appointment 42, doctor 48 / 68, task tile 48); [width] fixes it for a carousel.
 */
@Composable
fun ShifaCardSkeleton(
    modifier: Modifier = Modifier,
    avatar: Dp = ShifaSize.S40,
    lines: Int = 3,
    width: Dp? = null,
    shadow: Boolean = true,
) = CardChrome(width?.let { modifier.width(it) } ?: modifier, onClick = null, shadow = shadow) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaSkeleton(Modifier.size(avatar), ShifaRadius.Full)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            repeat(lines) {
                // The title bar is taller; the rest narrow down like real card lines.
                ShifaSkeleton(
                    Modifier
                        .fillMaxWidth(LINE_WIDTHS[it % LINE_WIDTHS.size])
                        .height(if (it == 0) ShifaSize.S16 else ShifaSize.S12),
                )
            }
        }
    }
}

/** [count] card placeholders in a column, [gap] apart, for a list's first load. Screen readers hear "Loading". */
@Composable
fun ShifaListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3,
    gap: Dp = ShifaSpacing.Gap.Sm,
    shadow: Boolean = true,
    card: @Composable () -> Unit = { ShifaCardSkeleton(shadow = shadow) },
) {
    val description = stringResource(Res.string.loading)
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) { repeat(count) { card() } }
}

/** A chat's first load: bubbles alternating sent (end) and received (start), in the bubbles' shapes. */
@Composable
fun ShifaChatSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.loading)
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        CHAT_WIDTHS.forEachIndexed { i, width ->
            val sent = i % 2 == 1
            Box(Modifier.fillMaxWidth(), contentAlignment = if (sent) Alignment.CenterEnd else Alignment.CenterStart) {
                ShifaSkeleton(
                    Modifier.fillMaxWidth(width).height(ShifaDimens.ChatSkeletonHeight),
                    if (sent) SentShape else ReceivedShape,
                )
            }
        }
    }
}

/** Time slots loading: a row of chip-shaped placeholders, as [ShifaChipGrid] lays the chips out. */
@Composable
fun ShifaChipsSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 8,
) = ShifaChipGrid(List(count) { it }, modifier) {
    ShifaSkeleton(Modifier.height(ShifaDimens.ChipSkeletonHeight), ShifaRadius.Full)
}

// `.dr-shimmer`: canvas → disabled → canvas sweeping sideways every 1.4 s; still in previews and screenshot tests.
@Composable
private fun shimmerBrush(): Brush {
    val x =
        if (LocalInspectionMode.current) {
            0f
        } else {
            val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
                -SHIMMER_TRAVEL_PX,
                SHIMMER_TRAVEL_PX,
                infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing)),
                label = "x",
            )
            shift
        }
    return Brush.linearGradient(
        listOf(ShifaColors.Bg.Canvas, ShifaColors.Bg.Disabled, ShifaColors.Bg.Canvas),
        start = Offset(x, 0f),
        end = Offset(x + 2 * SHIMMER_TRAVEL_PX, 0f),
        tileMode = TileMode.Mirror,
    )
}

private val LINE_WIDTHS = listOf(0.6f, 0.85f, 0.45f)
private val CHAT_WIDTHS = listOf(0.7f, 0.55f, 0.8f, 0.45f)
private const val SHIMMER_MS = 1400 // `@keyframes dr-shimmer`
private const val SHIMMER_TRAVEL_PX = 400f
