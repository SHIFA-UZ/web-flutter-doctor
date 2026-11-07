package shifa.doctor.utils

/**
 * Plays Shifa's spoken replies (AI-04, 02 §3.4, flow 03/06): one clip at a time, from memory (the bytes of
 * `POST /ai/speak`: MP3, or WAV for Uzbek), no file written. [play] suspends until the clip ends, so the voice loop
 * waits on the player's own end instead of Flutter's guessed duration and polling.
 */
interface SpeechPlayer {
    /** Plays [audio]; true when it played to its end, false when it couldn't, or was stopped or cancelled first. */
    suspend fun play(audio: ByteArray): Boolean

    /** Stops the clip playing now (barge-in, leaving the screen); its [play] returns false. */
    fun stop()
}

expect fun createSpeechPlayer(): SpeechPlayer
