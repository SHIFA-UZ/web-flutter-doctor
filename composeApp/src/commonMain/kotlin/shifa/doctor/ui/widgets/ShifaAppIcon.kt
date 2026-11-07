package shifa.doctor.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.shifa_mark
import shifa.doctor.ui.theme.ShifaColors

/**
 * `.sh-appicon--doctor` (WEB-03): the doctor app's icon, the white Shifa mark on the charcoal gradient, corners 22.37 %
 * of the side. The design's grain overlay is left out (invisible at sidebar and header sizes).
 */
@Composable
fun ShifaAppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
) = Box(
    modifier
        .size(size)
        .clip(RoundedCornerShape(percent = 22))
        .background(
            // linear-gradient(150deg, video preview 0 %, charcoal 55 %, media 100 %): top-left to bottom-right.
            Brush.linearGradient(
                0f to ShifaColors.Raw.VideoPreview,
                0.55f to ShifaColors.Text.Primary,
                1f to ShifaColors.Bg.Media,
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        ),
    contentAlignment = Alignment.Center,
) {
    Image(painterResource(Res.drawable.shifa_mark), contentDescription = null, modifier = Modifier.fillMaxWidth(MARK))
}

private const val MARK = 0.55f
