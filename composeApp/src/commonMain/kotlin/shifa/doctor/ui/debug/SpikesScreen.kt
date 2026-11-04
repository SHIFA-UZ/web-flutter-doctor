package shifa.doctor.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import shifa.doctor.generated.resources.Res
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.utils.MapPin
import shifa.doctor.utils.PdfView
import shifa.doctor.utils.currentPushToken
import shifa.doctor.utils.rememberAudioPlayer
import shifa.doctor.utils.rememberMicPermission
import shifa.doctor.utils.rememberVoiceRecorder
import shifa.doctor.utils.writeCacheFile

/** Set by the iOS host for `-spikeAutoRun`: record 3 s, stop and play without taps (simulator check, no UI driver). */
var spikeAutoRun = false

// Tashkent, Amir Temur square: the spike's pin.
private const val LAT = 41.3111
private const val LNG = 69.2797
private const val AUTO_RUN_MS = 3_000L

/**
 * CORE-07 native spikes, debug builds only (English, not translated): AAC record → play, PDF render, FCM token,
 * map pin. The
 * debug menu (CORE-08) keeps it; it never ships in a release build.
 */
@Composable
fun SpikesScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        AudioSpike()
        ShifaSection("PDF render") {
            var pdf by remember { mutableStateOf<ByteArray?>(null) }
            LaunchedEffect(Unit) { pdf = Res.readBytes("files/spike.pdf") }
            pdf?.let {
                PdfView(it, Modifier.fillMaxWidth().height(ShifaSize.S320)) { Text("PDF did not open") }
            }
        }
        ShifaSection("FCM token") {
            var token by remember { mutableStateOf("Asking Firebase…") }
            // Only the length and the tail: a push token is not shown or logged whole.
            LaunchedEffect(Unit) {
                token = currentPushToken()?.let { "Received, ${it.length} chars, …${it.takeLast(6)}" } ?: "None"
            }
            Text(token, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
        ShifaSection("Map pin") {
            var picked by remember { mutableStateOf("$LAT, $LNG") }
            MapPin(LAT, LNG, Modifier.fillMaxWidth().height(ShifaSize.S240)) { lat, lng -> picked = "$lat, $lng" }
            Text(picked, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
    }
}

@Composable
private fun AudioSpike() =
    ShifaSection("AAC record and play") {
        val recorder = rememberVoiceRecorder()
        val player = rememberAudioPlayer()
        var recording by remember { mutableStateOf(false) }
        var file by remember { mutableStateOf<String?>(null) }
        var status by remember { mutableStateOf("Idle") }
        val askMic =
            rememberMicPermission { granted ->
                if (!granted) {
                    status = "Microphone denied"
                } else if (player.release().let { recorder.start() }) { // never resume the previous recording
                    recording = true
                    status = "Recording…"
                } else {
                    status = "Recorder did not start"
                }
            }
        if (spikeAutoRun) {
            LaunchedEffect(Unit) { askMic() }
        }
        // The auto-run stops 3 s after recording really started (not after the permission prompt).
        LaunchedEffect(recording) {
            if (spikeAutoRun && recording) {
                delay(AUTO_RUN_MS)
                recording = false
                val bytes = recorder.stop()
                status = if (bytes == null) "Nothing recorded" else "Recorded ${bytes.size} bytes (.m4a)"
                file = bytes?.let { writeCacheFile("spike.m4a", it) }
                file?.let(player::toggle)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            if (!recording) {
                ShifaPrimaryButton("Record", askMic)
            } else {
                ShifaPrimaryButton("Stop", {
                    recording = false
                    val bytes = recorder.stop()
                    status = if (bytes == null) "Nothing recorded" else "Recorded ${bytes.size} bytes (.m4a)"
                    file = bytes?.let { writeCacheFile("spike.m4a", it) }
                })
            }
            file?.let {
                    url ->
                ShifaSecondaryButton(if (player.playback?.playing == true) "Pause" else "Play", { player.toggle(url) })
            }
        }
        val playback = player.playback
        Text(
            if (playback == null) status else "$status · ${playback.position}s / ${playback.duration}s",
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
