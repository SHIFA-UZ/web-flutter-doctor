package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// ponytail: no recording on the web yet (WEB-05 brings MediaRecorder); start() says it couldn't.
@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder =
    remember {
        object : VoiceRecorder {
            override fun start() = false

            override fun stop(): ByteArray? = null

            override fun level() = 0f

            override val recordingId: String? = null
            override val lastDurationSeconds = 0
        }
    }

@Composable
actual fun rememberMicPermission(onResult: (Boolean) -> Unit): () -> Unit = { onResult(false) }
