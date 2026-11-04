package shifa.doctor.ui.speech

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.widgets.ShifaInputField

// AI-06: a notes field recording (the pill: cancel, wave, time, process), and one transcribing.
@Composable
internal fun SpeechRecordingPreview() =
    PreviewTheme {
        Column(Modifier.padding(ShifaSize.S16), verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
            ShifaInputField(
                "Patient reports",
                {},
                "Enter appointment notes...",
                Modifier.fillMaxWidth(),
                multiline = true,
            )
            RecordingPill(DictationState(field = "free", level = 0.6f, seconds = 42), {}, {})
            RecordingPill(DictationState(field = "free", transcribing = true, seconds = 42), {}, {})
        }
    }
