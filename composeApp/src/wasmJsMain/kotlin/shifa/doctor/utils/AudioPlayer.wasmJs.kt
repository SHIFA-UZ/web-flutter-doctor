package shifa.doctor.utils

// ponytail: voice messages don't play on the web until WEB-05 (an <audio> element); a tap reports the error.
actual fun createAudioPlayer(onError: () -> Unit): AudioPlayer =
    object : AudioPlayer {
        override val playback: Playback? = null

        override fun toggle(url: String) = onError()

        override fun tick() = Unit

        override fun release() = Unit
    }
