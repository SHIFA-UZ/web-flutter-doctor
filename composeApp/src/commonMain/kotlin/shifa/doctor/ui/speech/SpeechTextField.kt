package shifa.doctor.ui.speech

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancelRecording
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_mic_16
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.processRecording
import shifa.doctor.generated.resources.speakToType
import shifa.doctor.generated.resources.transcriptionReportAction
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.utils.rememberMicPermission
import shifa.doctor.utils.rememberVoiceRecorder
import shifa.doctor.utils.tr

/** A screen's dictation: one recorder, in the screen's composition (leaving the screen drops a recording). */
@Composable
fun rememberDictation(): Dictation {
    val recorder = rememberVoiceRecorder()
    val scope = rememberCoroutineScope()
    return remember(recorder) { Dictation(scope, recorder) }
}

/** The screen's dictation for its fields ([SpeechOrPlainField]); none in previews and screenshots. */
val LocalDictation = staticCompositionLocalOf<Dictation?> { null }

/**
 * A [SpeechTextField] when the screen provides [LocalDictation], else the plain field. [append] adds a transcript to
 * the field's value in the screen's state (not to the text shown when the recording started).
 */
@Composable
fun SpeechOrPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: String,
    append: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    areaMinHeight: Dp = ShifaDimens.TextAreaHeight,
) {
    val dictation = LocalDictation.current
    if (dictation == null) {
        ShifaInputField(
            value,
            onValueChange,
            label,
            modifier,
            clearable = false,
            multiline = multiline,
            areaMinHeight = areaMinHeight,
        )
    } else {
        SpeechTextField(
            value,
            onValueChange,
            label,
            field,
            append,
            dictation,
            rememberSpeechAllowed(),
            modifier,
            multiline,
            areaMinHeight,
        )
    }
}

/** The plan includes [feature] (02 §7.3, 05/06 §6); follows the profile as it loads. */
@Composable
fun rememberAllowed(feature: DoctorFeature): Boolean {
    val me by profileRepository.me.collectAsStateWithLifecycle()
    return FeatureGate.of(me?.subscription).allows(feature)
}

/** The plan includes speech to text (02 §7.3: without it the mic isn't shown); follows the profile as it loads. */
@Composable
fun rememberSpeechAllowed(): Boolean {
    val me by profileRepository.me.collectAsStateWithLifecycle()
    return FeatureGate.of(me?.subscription).allows(DoctorFeature.SpeechToText)
}

/**
 * A text field with a mic (AI-06, flow 05/05 §3): the recording pill under it while it records (✕ drops it, ✓
 * transcribes), the transcript [append]ed to the field's value in the screen's state (a late answer can't overwrite
 * what was typed meanwhile, even when the field left the screen), and "Report" after it when the backend collects
 * reports. [field] names it among the screen's fields: one records at a time.
 */
@Composable
fun SpeechTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: String,
    append: (String) -> Unit,
    dictation: Dictation,
    allowed: Boolean,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    areaMinHeight: Dp = ShifaDimens.TextAreaHeight,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    val state by dictation.state.collectAsStateWithLifecycle()
    val add by rememberUpdatedState(append)
    val ask = rememberMicPermission { granted -> if (granted) dictation.start(field) else dictation.denied() }
    val mine = state.field == field
    ShifaInputField(
        value,
        onValueChange,
        label,
        Modifier.fillMaxWidth(),
        clearable = false,
        multiline = multiline,
        areaMinHeight = areaMinHeight,
        trailingIcon =
            if (allowed) {
                {
                    ShifaIconButton(
                        Res.drawable.ic_mic_16,
                        tr(Res.string.speakToType),
                        ask,
                        variant = ShifaIconButtonVariant.Secondary,
                        enabled = state.field == null,
                        selected = mine,
                    )
                }
            } else {
                null
            },
    )
    if (mine) {
        RecordingPill(state, dictation::cancel) {
            dictation.finish { _, text -> add(text) }
        }
    }
    if (state.reportable == field) ShifaLink(tr(Res.string.transcriptionReportAction), dictation::report)
}

/** The dark pill for dictation and the scribe: ✕, the live wave, the time, ✓ (a spinner while transcribing). */
@Composable
fun RecordingPill(
    state: DictationState,
    cancel: () -> Unit,
    done: () -> Unit,
) = RecordingPill(state.level, state.seconds, state.transcribing, cancel, done)

/**
 * The dark recording pill (AI-03, also CHAT-06's voice notes): ✕, the live wave of [level], the time in m:ss, ✓ (a
 * spinner while [busy]); [doneLabel] reads out ✓.
 */
@Composable
fun RecordingPill(
    level: Float,
    seconds: Int,
    busy: Boolean,
    cancel: () -> Unit,
    done: () -> Unit,
    doneLabel: String = tr(Res.string.processRecording),
) = Row(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Media, ShifaRadius.Full)
        .padding(horizontal = ShifaSize.S6, vertical = ShifaSize.S4),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    ShifaIconButton(
        Res.drawable.ic_x_16,
        tr(Res.string.cancelRecording),
        cancel,
        variant = ShifaIconButtonVariant.Secondary,
        enabled = !busy,
    )
    Wave(level, Modifier.weight(1f).height(ShifaSize.S28))
    Text(
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Inverse,
    )
    if (busy) {
        Box(Modifier.size(ShifaSize.S40), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.size(ShifaSize.S20),
                color = ShifaColors.Text.Inverse,
                strokeWidth = ShifaSize.S2,
            )
        }
    } else {
        ShifaIconButton(Res.drawable.ic_check_16, doneLabel, done)
    }
}

/**
 * The last [bars] levels as bars, newest on the right: 2-wide bars spread out ([barWidth]), or, without a width, bars
 * that share the row (the Intelligence composer's 22-bar wave, board `DaIntel`).
 */
@Composable
internal fun Wave(
    level: Float,
    modifier: Modifier,
    bars: Int = BARS,
    barWidth: Dp? = ShifaSize.S2,
) {
    val levels = remember(bars) { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    LaunchedEffect(level) {
        levels.removeAt(0)
        levels.add(level)
    }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (barWidth != null) Arrangement.SpaceBetween else Arrangement.spacedBy(ShifaSize.S4),
    ) {
        levels.forEach { l ->
            Box(
                (if (barWidth != null) Modifier.width(barWidth) else Modifier.weight(1f))
                    .fillMaxHeight(l.coerceIn(MIN_BAR, 1f))
                    .background(ShifaColors.Bg.Brand, if (barWidth != null) ShifaRadius.Full else ShifaRadius.Xs),
            )
        }
    }
}

private const val BARS = 40
private const val MIN_BAR = 0.08f
