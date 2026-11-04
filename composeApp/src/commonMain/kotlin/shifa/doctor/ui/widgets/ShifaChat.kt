package shifa.doctor.ui.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clear
import shifa.doctor.generated.resources.copy
import shifa.doctor.generated.resources.ic_copy_16
import shifa.doctor.generated.resources.ic_mic_22
import shifa.doctor.generated.resources.ic_refresh_16
import shifa.doctor.generated.resources.ic_share_16
import shifa.doctor.generated.resources.ic_x_22
import shifa.doctor.generated.resources.pause
import shifa.doctor.generated.resources.play
import shifa.doctor.generated.resources.recordVoiceMessage
import shifa.doctor.generated.resources.regenerate
import shifa.doctor.generated.resources.share
import shifa.doctor.generated.resources.stopRecording
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.theme.lineBox
import kotlin.math.PI
import kotlin.math.sin

/**
 * Figma chat-bar. Empty: placeholder + mic ([onMic] starts recording). Typed: text + clear.
 * [recording]: mic + a moving waveform, and stop on the right ([onMic] stops). Figma's waveform is static.
 */
@Composable
fun ShifaChatBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
    recording: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val active = recording || focused || value.isNotEmpty()
    val style = ShifaTextStyles.chatInput()
    Row(
        modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Surface, ShifaRadius.Full)
            .border(
                ShifaSize.S2,
                if (active) ShifaColors.Border.Focus else ShifaColors.Border.Default,
                ShifaRadius.Full,
            )
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recording) {
            Icon(painterResource(Res.drawable.ic_mic_22), null, tint = ShifaColors.Icon.Brand)
            RecordingWaveform(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Stop,
                stringResource(Res.string.stopRecording),
                Modifier.clip(ShifaRadius.Full).clickable(onClick = onMic),
                tint = ShifaColors.Icon.Primary,
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = style.copy(color = ShifaColors.Text.Primary),
                cursorBrush = SolidColor(ShifaColors.Border.Focus),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = style, color = ShifaColors.Text.Tertiary)
                        innerTextField()
                    }
                },
            )
            if (value.isEmpty()) {
                BarIcon(Res.drawable.ic_mic_22, stringResource(Res.string.recordVoiceMessage), onMic)
            } else {
                BarIcon(Res.drawable.ic_x_22, stringResource(Res.string.clear)) { onValueChange("") }
            }
        }
    }
}

/** Bars that swell in a wave while the mic is on; still in previews and tests (frame 0). */
@Composable
private fun RecordingWaveform(modifier: Modifier = Modifier) {
    val phase = wavePhase()
    val color = ShifaColors.Icon.Primary
    val stroke = ShifaSize.S2
    val gap = ShifaSpacing.Gap.Xs
    Canvas(modifier.height(ShifaSize.S20)) {
        val step = (stroke + gap).toPx()
        val bars = (size.width / step).toInt()
        val x0 = (size.width - bars * step + gap.toPx()) / 2 + stroke.toPx() / 2
        repeat(bars) { i ->
            val wave = (1 + sin(phase + i * WAVE_SPREAD)) / 2
            val half = size.height * (WAVE_MIN + (1 - WAVE_MIN) * wave) / 2
            val x = x0 + i * step
            drawLine(color, Offset(x, center.y - half), Offset(x, center.y + half), stroke.toPx(), StrokeCap.Round)
        }
    }
}

/**
 * 0..2π, looping every [WAVE_PERIOD_MS]. Static in previews and screenshot tests, where an infinite animation never
 * lets the UI idle (as ShifaButtons' spinner).
 */
@Composable
private fun wavePhase(): Float {
    if (LocalInspectionMode.current) return 0f
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    return phase
}

private const val WAVE_PERIOD_MS = 1200
private const val WAVE_SPREAD = 0.7f
private const val WAVE_MIN = 0.25f

@Composable
private fun BarIcon(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
) = Icon(
    painterResource(icon),
    contentDescription,
    Modifier.clip(ShifaRadius.Full).clickable(onClick = onClick),
    tint = ShifaColors.Icon.Primary,
)

// Shifa AI's replies are ShifaChatReplyBubble, without a fill (Figma 1673:14621).
enum class ShifaChatBubbleType { Received, Sent }

/**
 * Figma chat-bubble, type = received / sent. Sent aligns end, received start.
 * Not in Figma: [time], when it was sent, under the bubble (on every bubble type).
 */
@Composable
fun ShifaChatBubble(
    text: String,
    type: ShifaChatBubbleType,
    modifier: Modifier = Modifier,
    time: String? = null,
) = Bubble(type, modifier, time) {
    BubbleText(text, if (type == ShifaChatBubbleType.Sent) TextAlign.End else TextAlign.Start)
}

/**
 * Figma chat-bubble, type = image: [image] clipped to ShifaRadius.Sm, in a sent bubble unless [received].
 * Not in Figma: a [caption] under it, and the received look.
 */
@Composable
fun ShifaChatImageBubble(
    modifier: Modifier = Modifier,
    received: Boolean = false,
    caption: String? = null,
    time: String? = null,
    image: @Composable () -> Unit,
) = Bubble(if (received) ShifaChatBubbleType.Received else ShifaChatBubbleType.Sent, modifier, time) {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        Box(Modifier.clip(ShifaRadius.Sm)) { image() }
        caption?.let { BubbleText(it, if (received) TextAlign.Start else TextAlign.End) }
    }
}

/**
 * Figma chat-bubble, type = shifa-ai (1673:15033): Shifa AI's reply, no fill, and under it the action-buttons Copy,
 * Regenerate and Share, each shown when its callback is set (none while the reply streams). Figma's "Searched for…"
 * line isn't shown: X1 doesn't say what it looked up. Not in Figma: [time], when it came, under the text.
 */
@Composable
fun ShifaChatReplyBubble(
    text: String,
    modifier: Modifier = Modifier,
    time: String? = null,
    onCopy: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) = ReplyBox(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        BubbleText(text, TextAlign.Start)
        time?.let { Text(it, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Tertiary) }
        if (onCopy != null || onRegenerate != null || onShare != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Inset.Md)) {
                onCopy?.let { ReplyAction(it, Res.drawable.ic_copy_16, Res.string.copy) }
                onRegenerate?.let { ReplyAction(it, Res.drawable.ic_refresh_16, Res.string.regenerate) }
                onShare?.let { ReplyAction(it, Res.drawable.ic_share_16, Res.string.share) }
            }
        }
    }
}

@Composable
private fun ReplyAction(
    onClick: () -> Unit,
    icon: DrawableResource,
    label: StringResource,
) = ShifaActionButton(
    onClick,
    icon = icon,
    contentDescription = stringResource(label),
    variant = ShifaActionVariant.Secondary,
)

// Shifa AI's reply has no bubble: the text keeps the bubbles' insets.
@Composable
private fun ReplyBox(
    modifier: Modifier,
    content: @Composable () -> Unit,
) = Box(
    modifier
        .fillMaxWidth()
        .padding(vertical = ShifaSpacing.Stack.Md)
        .widthIn(max = ShifaDimens.ChatBubbleMaxWidth)
        .padding(horizontal = ShifaSpacing.Inset.Md),
) { content() }

/** Shifa AI is thinking (09): three dots that pulse in turn, where its reply will be. Static in previews and tests. */
@Composable
fun ShifaChatThinkingBubble(modifier: Modifier = Modifier) =
    ReplyBox(modifier) {
        val phase = wavePhase()
        val color = ShifaColors.Text.Tertiary
        val dot = ShifaSize.S8
        val gap = ShifaSpacing.Gap.Xs
        Canvas(Modifier.height(ShifaSize.S20).width(dot * THINKING_DOTS + gap * (THINKING_DOTS - 1))) {
            val r = dot.toPx() / 2
            repeat(THINKING_DOTS) { i ->
                val pulse = (1 + sin(phase - i * WAVE_SPREAD)) / 2
                drawCircle(
                    color,
                    r,
                    Offset(r + i * (dot + gap).toPx(), center.y),
                    alpha = THINKING_MIN_ALPHA + (1 - THINKING_MIN_ALPHA) * pulse,
                )
            }
        }
    }

private const val THINKING_DOTS = 3
private const val THINKING_MIN_ALPHA = 0.25f

/**
 * Not in Figma: a voice message (08 §5.5.3, Flutter's look): play / pause, a progress bar, the position and the
 * length in m:ss. [position] and [duration] in seconds.
 */
@Composable
fun ShifaChatVoiceBubble(
    playing: Boolean,
    position: Int,
    duration: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    received: Boolean = false,
    time: String? = null,
) = Bubble(if (received) ShifaChatBubbleType.Received else ShifaChatBubbleType.Sent, modifier, time) {
    Row(
        Modifier.width(ShifaDimens.ChatVoiceWidth),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (playing) Res.string.pause else Res.string.play),
            Modifier.clip(ShifaRadius.Full).clickable(onClick = onToggle),
            tint = ShifaColors.Icon.Primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Xs)) {
            val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(ShifaSize.S4).background(ShifaColors.Alpha.Black5, ShifaRadius.Full)) {
                Box(
                    Modifier.fillMaxWidth(
                        progress,
                    ).fillMaxHeight().background(ShifaColors.Interactive.Primary, ShifaRadius.Full),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val style = ShifaTextStyles.caption()
                Text(clock(position), style = style, color = ShifaColors.Text.Tertiary)
                Text(clock(duration), style = style, color = ShifaColors.Text.Tertiary)
            }
        }
    }
}

/** m:ss. */
internal fun clock(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun BubbleText(
    text: String,
    align: TextAlign,
) {
    val style = ShifaTextStyles.chatBubble()
    Text(text, Modifier.lineBox(style), style = style, color = ShifaColors.Text.Primary, textAlign = align)
}

// Figma: the corner at the sender's side is square.
internal val ReceivedShape = ShifaRadius.ControlLg.copy(topStart = ZeroCornerSize)
internal val SentShape = ShifaRadius.ControlLg.copy(topEnd = ZeroCornerSize)

@Composable
private fun Bubble(
    type: ShifaChatBubbleType,
    modifier: Modifier,
    time: String? = null,
    content: @Composable () -> Unit,
) = Column(
    modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Xs),
    horizontalAlignment = if (type == ShifaChatBubbleType.Sent) Alignment.End else Alignment.Start,
) {
    Box(
        Modifier
            .widthIn(max = ShifaDimens.ChatBubbleMaxWidth)
            .then(
                when (type) {
                    ShifaChatBubbleType.Received -> Modifier.background(ShifaColors.Alpha.Teal25, ReceivedShape)
                    ShifaChatBubbleType.Sent -> Modifier.background(ShifaColors.Alpha.Black5, SentShape)
                },
            ).padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Stack.Md),
    ) { content() }
    time?.let { Text(it, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Tertiary) }
}
