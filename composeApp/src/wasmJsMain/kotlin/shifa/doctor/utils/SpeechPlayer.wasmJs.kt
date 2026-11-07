package shifa.doctor.utils

// ponytail: Shifa's spoken replies stay silent on the web until WEB-05 (an <audio> element).
actual fun createSpeechPlayer(): SpeechPlayer =
    object : SpeechPlayer {
        override suspend fun play(audio: ByteArray) = false

        override fun stop() = Unit
    }
