package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.numberWithUnsignedInt
import platform.Foundation.timeIntervalSince1970
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import shifa.doctor.data.api.httpLogger

@OptIn(ExperimentalForeignApi::class)
private class IosVoiceRecorder : VoiceRecorder {
    private var recorder: AVAudioRecorder? = null
    private var startedAt = 0.0
    override var recordingId: String? = null
    override var lastDurationSeconds = 0

    override fun start(): Boolean {
        stop()
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setActive(true, error = null)
        val name = "voice_${(NSDate().timeIntervalSince1970 * 1000).toLong()}.m4a"
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
        val settings =
            mapOf<Any?, Any?>(
                AVFormatIDKey to NSNumber.numberWithUnsignedInt(kAudioFormatMPEG4AAC),
                AVSampleRateKey to 16_000.0,
                AVNumberOfChannelsKey to 1,
                AVEncoderBitRateKey to 32_000,
            )
        val r = AVAudioRecorder(url, settings, null)
        r.meteringEnabled = true
        if (!r.record()) {
            httpLogger.log("FAILED to start recording")
            return false
        }
        recorder = r
        recordingId = newRecordingId()
        startedAt = NSDate().timeIntervalSince1970
        return true
    }

    override fun stop(): ByteArray? {
        val r = recorder ?: return null
        recorder = null
        lastDurationSeconds = (NSDate().timeIntervalSince1970 - startedAt).toInt()
        r.stop()
        val bytes = NSData.dataWithContentsOfURL(r.url)?.toByteArray()
        NSFileManager.defaultManager.removeItemAtURL(r.url, null)
        return bytes?.takeIf { it.isNotEmpty() }
    }

    override fun level(): Float {
        val r = recorder ?: return 0f
        r.updateMeters()
        return levelFromDb(r.averagePowerForChannel(0u).toDouble())
    }
}

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { IosVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.stop() } }
    return recorder
}

@Composable
actual fun rememberMicPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val result by rememberUpdatedState(onResult)
    return remember {
        {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
                dispatch_async(dispatch_get_main_queue()) { result(granted) }
            }
        }
    }
}
