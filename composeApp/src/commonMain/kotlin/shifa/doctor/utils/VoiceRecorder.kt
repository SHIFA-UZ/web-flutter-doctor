package shifa.doctor.utils

import androidx.compose.runtime.Composable
import kotlin.random.Random

/** The microphone to an AAC-LC .m4a, 32 kbps, 16 kHz, mono (08 §6.3), in a temp file. One recording at a time. */
interface VoiceRecorder {
    /** False when it couldn't start. */
    fun start(): Boolean

    /** The recording, or null when there's none; the temp file is deleted either way. */
    fun stop(): ByteArray?

    /** How loud the microphone is now (AI-03: the recording pill's wave): [levelFromDb]; 0 when not recording. */
    fun level(): Float

    /**
     * The current or last recording's id, made at [start] (02 §0): sent with its transcription so the backend's logs
     * tie the two together. Null before the first recording.
     */
    val recordingId: String?

    /** The last finished recording's length in whole seconds (0 if none). */
    val lastDurationSeconds: Int
}

/** A new recording's id: "rec-" and 16 random hex digits. */
fun newRecordingId(): String = "rec-" + Random.nextLong().toULong().toString(16).padStart(16, '0')

/**
 * A level in dBFS as a bar height (02 §3.5, Flutter's `dbToBarHeight`): −55 dB and below is 0.08 (a quiet room still
 * shows a line), −8 dB and above is 1, linear between; an unreadable value is 0.1.
 */
fun levelFromDb(db: Double): Float {
    if (db.isNaN() || db.isInfinite()) return UNREADABLE
    val t = ((db - QUIET_DB) / (LOUD_DB - QUIET_DB)).coerceIn(0.0, 1.0)
    return (FLOOR + t * (1 - FLOOR)).toFloat()
}

private const val QUIET_DB = -55.0
private const val LOUD_DB = -8.0
private const val FLOOR = 0.08
private const val UNREADABLE = 0.1f

/** A recorder for this screen; leaving the screen drops an unfinished recording. */
@Composable
expect fun rememberVoiceRecorder(): VoiceRecorder

/** Asks for the microphone; [onResult] gets whether it's granted. */
@Composable
expect fun rememberMicPermission(onResult: (Boolean) -> Unit): () -> Unit
