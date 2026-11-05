package shifa.doctor.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.video.VideoRepository
import shifa.doctor.data.video.videoJoinError
import shifa.doctor.data.video.videoRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.callErrorOccurred
import shifa.doctor.generated.resources.videoCallEnded
import shifa.doctor.utils.VideoCall
import shifa.doctor.utils.VideoCallState
import shifa.doctor.utils.currentInstant

/** Where a video visit is: before joining (the pre-join page), joining, in the call, or left it (the visit open). */
enum class VideoStage { PreJoin, Joining, InCall, Left }

data class VideoVisitState(
    val stage: VideoStage = VideoStage.PreJoin,
    // The camera and the microphone allowed: null until asked. The microphone is needed; without the camera the call
    // goes on with it off (flow 05/10 §6).
    val camera: Boolean? = null,
    val mic: Boolean? = null,
    val error: StringResource? = null,
    val call: VideoCallState = VideoCallState(),
    // "Leave call?" asked.
    val confirmLeave: Boolean = false,
) {
    val canJoin: Boolean get() = mic == true

    // Joining or in the call: the screen stays awake, Back asks to leave the call first.
    val live: Boolean get() = stage == VideoStage.Joining || stage == VideoStage.InCall
}

/**
 * A video visit's call (VIDEO-02, flows 05/10 and 05/11, board `DaVisit` "VIDEO PRE-JOIN" and the video pane), owned by
 * the visit's ViewModel so it survives opening the patient's record mid-call. A join asks `POST /video/token` and then
 * Daily, with the doctor's camera and microphone as chosen; leaving frees the call and the visit stays open (Rejoin
 * makes a new one once the token is there); a dropped call says so with Rejoin; going back from the visit or ending it
 * frees it ([release]), and a late token answer is then dropped. Leaving the call never completes the visit.
 */
class VideoVisit(
    private val appointmentId: Long,
    private val scope: CoroutineScope,
    private val calls: () -> VideoCall,
    private val window: () -> Pair<Instant?, Instant?>,
    private val repo: VideoRepository = videoRepository,
    private val now: () -> Instant = ::currentInstant,
) {
    private val _state = MutableStateFlow(VideoVisitState())
    val state: StateFlow<VideoVisitState> = _state.asStateFlow()

    var call: VideoCall = calls()
        private set

    // The current call was released (Leave, Back, End); a released call is never joined.
    private var released = false
    private var watching: Job? = null
    private var joining: Job? = null

    // The join on its way started from "left" (a Rejoin), so a refusal goes back there, not to the pre-join page.
    private var wasLeft = false

    // The doctor's choices (the switches): applied to each call as far as the permissions allow.
    private var wantCamera = true
    private var wantMic = true

    init {
        watch()
    }

    private fun watch() {
        watching?.cancel()
        val current = call
        watching =
            scope.launch {
                current.state.collect { c ->
                    val before = _state.value
                    _state.update { s ->
                        val joining = s.stage == VideoStage.Joining
                        // A join the SDK refused: back where the doctor was, with the generic error.
                        val refused = joining && c.failed
                        // A call that dropped (Daily left or failed) while in it: left, with Rejoin.
                        val dropped =
                            s.stage == VideoStage.InCall && (c.failed || c.phase == VideoCallState.Phase.Left)
                        val stage =
                            when {
                                joining && c.phase == VideoCallState.Phase.Joined -> VideoStage.InCall
                                refused -> if (wasLeft) VideoStage.Left else VideoStage.PreJoin
                                dropped -> VideoStage.Left
                                else -> s.stage
                            }
                        val error =
                            when {
                                refused -> Res.string.callErrorOccurred
                                dropped -> if (c.failed) Res.string.callErrorOccurred else Res.string.videoCallEnded
                                else -> s.error
                            }
                        s.copy(call = c, stage = stage, error = error)
                    }
                    // A refused join or a dropped call ends this call: it is freed (no media left running behind
                    // "You left the call"), and on the pre-join page a fresh one shows the preview again.
                    val after = _state.value
                    val refused =
                        before.stage == VideoStage.Joining && after.stage in
                            setOf(
                                VideoStage.PreJoin,
                                VideoStage.Left,
                            )
                    val dropped = before.stage == VideoStage.InCall && after.stage == VideoStage.Left
                    val ended = refused || dropped
                    if (ended) {
                        free()
                        if (after.stage == VideoStage.PreJoin) renew()
                    }
                }
            }
    }

    /** A new call for the pre-join page, with the doctor's choices. */
    private fun renew() {
        call = calls()
        released = false
        watch()
        apply()
    }

    /** The doctor's choices on the current call, as far as the permissions allow. */
    private fun apply() {
        val s = _state.value
        call.setCamera(wantCamera && s.camera == true)
        call.setMic(wantMic && s.mic == true)
    }

    /** The camera and microphone answers (on open, and each time the app comes back: Settings may have changed them). */
    fun permissions(
        camera: Boolean,
        mic: Boolean,
    ) {
        val before = _state.value
        _state.update { it.copy(camera = camera, mic = mic) }
        if (before.stage != VideoStage.PreJoin || (before.camera == camera && before.mic == mic)) return
        // The preview follows the doctor's choices as far as allowed: a newly granted device doesn't switch the other
        // one back on, and without the camera the call goes on with it off.
        apply()
    }

    /** Join call / Rejoin: the room's token, then Daily; a refusal is explained (VIDEO-01). */
    fun join() {
        val s = _state.value
        if (s.live || !s.canJoin) return
        wasLeft = s.stage == VideoStage.Left
        _state.update { it.copy(stage = VideoStage.Joining, error = null) }
        joining =
            scope.launch {
                val token =
                    repo.token(appointmentId).getOrElse { e ->
                        val (start, end) = window()
                        return@launch _state.update {
                            it.copy(
                                stage = if (wasLeft) VideoStage.Left else VideoStage.PreJoin,
                                error = videoJoinError(e, start, end, now()),
                            )
                        }
                    }
                // A Rejoin's new call is made only now: a refused token leaves nothing to free.
                if (released) {
                    call = calls()
                    released = false
                    watch()
                }
                call.join(
                    token.roomUrl,
                    token.token,
                    camera = wantCamera && s.camera == true,
                    mic = wantMic && s.mic == true,
                )
            }
    }

    fun toggleCamera() {
        if (_state.value.camera != true) return
        wantCamera = !_state.value.call.cameraOn
        call.setCamera(wantCamera)
    }

    fun toggleMic() {
        if (_state.value.mic != true) return
        wantMic = !_state.value.call.micOn
        call.setMic(wantMic)
    }

    fun flip() = call.flipCamera()

    fun askLeave(ask: Boolean) = _state.update { it.copy(confirmLeave = ask) }

    /** Leave call: the call is freed, the visit stays open (Rejoin, or End appointment). */
    fun leave() {
        free()
        _state.update { it.copy(stage = VideoStage.Left, confirmLeave = false, error = null) }
    }

    /** Going back from the visit or ending it: the call (and a token on its way) is freed whatever the stage. */
    fun release() {
        free()
        _state.update { it.copy(stage = VideoStage.Left, confirmLeave = false) }
    }

    private fun free() {
        joining?.cancel()
        joining = null
        if (released) return
        released = true
        watching?.cancel()
        call.release()
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
