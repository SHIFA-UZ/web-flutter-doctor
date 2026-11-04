package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** The microphone to an AAC-LC .m4a, 32 kbps, 16 kHz, mono (08 §6.3), in a temp file. One recording at a time. */
interface VoiceRecorder {
    /** False when it couldn't start. */
    fun start(): Boolean

    /** The recording, or null when there's none; the temp file is deleted either way. */
    fun stop(): ByteArray?
}

/** A recorder for this screen; leaving the screen drops an unfinished recording. */
@Composable
expect fun rememberVoiceRecorder(): VoiceRecorder

/** Asks for the microphone; [onResult] gets whether it's granted. */
@Composable
expect fun rememberMicPermission(onResult: (Boolean) -> Unit): () -> Unit
