package shifa.doctor.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import shifa.doctor.data.api.httpLogger
import java.io.File

private class AndroidVoiceRecorder(
    private val context: Context,
) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override fun start(): Boolean {
        stop()
        val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        return runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            recorder = r
            file = out
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioChannels(1)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
        }.onFailure {
            httpLogger.log("FAILED to start recording: ${it::class.simpleName}")
            stop()
        }.isSuccess
    }

    override fun stop(): ByteArray? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        // stop() throws when nothing was recorded yet.
        val recorded = runCatching { r.stop() }.isSuccess
        r.release()
        return f?.let { (if (recorded) it.readBytes() else null).also { _ -> it.delete() } }
    }
}

private const val BIT_RATE = 32_000
private const val SAMPLE_RATE = 16_000

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val context = LocalContext.current.applicationContext
    val recorder = remember { AndroidVoiceRecorder(context) }
    DisposableEffect(recorder) { onDispose { recorder.stop() } }
    return recorder
}

@Composable
actual fun rememberMicPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result(it) }
    return {
        val mic = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(context, mic) == PackageManager.PERMISSION_GRANTED) {
            result(true)
        } else {
            launcher.launch(mic)
        }
    }
}
