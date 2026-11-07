package shifa.doctor.ui.visit

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.video.VideoRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.callErrorOccurred
import shifa.doctor.generated.resources.videoCallEnded
import shifa.doctor.generated.resources.videoCallJoinWindowClosedMessage
import shifa.doctor.utils.VideoCall
import shifa.doctor.utils.VideoCallState
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** A call that records what it was asked and changes state when told. */
private class FakeCall : VideoCall {
    override val state = MutableStateFlow(VideoCallState())
    val log = mutableListOf<String>()

    override fun startPreview() {
        log += "preview"
    }

    override fun join(
        roomUrl: String,
        token: String,
        camera: Boolean,
        mic: Boolean,
    ) {
        log += "join $roomUrl camera=$camera mic=$mic"
    }

    override fun setCamera(on: Boolean) {
        log += "camera $on"
        state.value = state.value.copy(cameraOn = on)
    }

    override fun setMic(on: Boolean) {
        log += "mic $on"
        state.value = state.value.copy(micOn = on)
    }

    override fun flipCamera() {
        log += "flip"
    }

    override fun release() {
        log += "release"
    }
}

class VideoVisitTest {
    private var status = HttpStatusCode.OK
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine {
            if (status == HttpStatusCode.OK) {
                respond("""{"token":"t","roomUrl":"https://shifa.daily.co/r","roomName":"r"}""", status, json)
            } else {
                respond("""{"status":403,"code":"FORBIDDEN"}""", status, json)
            }
        }
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(TestCoroutineScheduler()))
    private val made = java.util.concurrent.CopyOnWriteArrayList<FakeCall>()
    private val start = Instant.parse("2026-11-05T10:00:00Z")
    private var now = start

    @After fun tearDown() = scope.cancel()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() =
        VideoVisit(
            7,
            scope,
            { FakeCall().also { made += it } },
            { start to start + 1.hours },
            VideoRepository { createHttpClient({ null }, engine) },
            { now },
        )

    @Test
    fun `allowed, it previews, joins the room, and is in the call once Daily says joined`() {
        val classToTest = opened()
        classToTest.join()
        assertThat(made.single().log).isEmpty()
        classToTest.permissions(true, true)
        assertThat(made.single().log).containsExactly("camera true", "mic true").inOrder()
        classToTest.toggleMic()
        classToTest.join()
        // The microphone turned off before joining stays off: the join never turns it on.
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.Joining)
        settle { made.single().log.any { it.startsWith("join") } }
        assertThat(made.single().log.last()).isEqualTo("join https://shifa.daily.co/r camera=true mic=false")
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined, patientJoined = true)
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.InCall)
        assertThat(classToTest.state.value.call.patientJoined).isTrue()
    }

    @Test
    fun `a refused room is explained, a failed Daily join is the generic error, both back on the pre-join page`() {
        val classToTest = opened()
        classToTest.permissions(true, true)
        status = HttpStatusCode.Forbidden
        now = start + 2.hours
        classToTest.join()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.PreJoin)
        assertThat(classToTest.state.value.error).isEqualTo(Res.string.videoCallJoinWindowClosedMessage)
        status = HttpStatusCode.OK
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        made.single().state.value = VideoCallState(failed = true)
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.PreJoin)
        assertThat(classToTest.state.value.error).isEqualTo(Res.string.callErrorOccurred)
    }

    @Test
    fun `leaving frees the call and keeps the visit, Rejoin makes a new call, release is once`() {
        val classToTest = opened()
        classToTest.permissions(true, true)
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined)
        classToTest.askLeave(true)
        classToTest.leave()
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.Left)
        assertThat(classToTest.state.value.confirmLeave).isFalse()
        assertThat(made.single().log.last()).isEqualTo("release")
        // A refused Rejoin makes no new call (nothing left to free).
        status = HttpStatusCode.Forbidden
        classToTest.join()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.Left)
        assertThat(made).hasSize(1)
        status = HttpStatusCode.OK
        classToTest.join()
        settle { made.size == 2 && made[1].log.any { it.startsWith("join") } }
        made[1].state.value = VideoCallState(phase = VideoCallState.Phase.Joined)
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.InCall)
        // Ending the visit frees it; a second release does nothing.
        classToTest.release()
        classToTest.release()
        assertThat(made[1].log.count { it == "release" }).isEqualTo(1)
    }

    @Test
    fun `without the camera the call goes on with it off, the microphone is needed`() {
        val classToTest = opened()
        classToTest.permissions(false, false)
        classToTest.join()
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.PreJoin)
        // Granted in Settings: read on return, the microphone on, the camera stays off.
        classToTest.permissions(false, true)
        assertThat(made.single().log.takeLast(2)).containsExactly("camera false", "mic true").inOrder()
        classToTest.toggleCamera()
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        assertThat(made.single().log.last()).isEqualTo("join https://shifa.daily.co/r camera=false mic=true")
    }

    @Test
    fun `a call that drops says so with Rejoin, and Back while joining drops the token's answer`() {
        val classToTest = opened()
        classToTest.permissions(true, true)
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined)
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Left)
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.Left)
        assertThat(classToTest.state.value.error).isEqualTo(Res.string.videoCallEnded)
        // Back before the token answers: nothing joins after.
        val slow = opened()
        slow.permissions(true, true)
        status = HttpStatusCode.Forbidden
        slow.join()
        slow.release()
        Thread.sleep(200)
        assertThat(slow.state.value.stage).isEqualTo(VideoStage.Left)
        assertThat(made.last().log.none { it.startsWith("join") }).isTrue()
    }

    @Test
    fun `a device granted later keeps the other one's choice, and an SDK error frees the call`() {
        val classToTest = opened()
        classToTest.permissions(true, false)
        classToTest.toggleCamera()
        // The microphone granted in Settings: the camera the doctor turned off stays off.
        classToTest.permissions(true, true)
        assertThat(made.single().log.takeLast(2)).containsExactly("camera false", "mic true").inOrder()
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        assertThat(made.single().log.last()).isEqualTo("join https://shifa.daily.co/r camera=false mic=true")
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined)
        // An error in the call: freed, "left" with the error; Rejoin gets a new call.
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined, failed = true)
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.Left)
        assertThat(made.single().log.last()).isEqualTo("release")
        classToTest.join()
        settle { made.size == 2 && made[1].log.any { it.startsWith("join") } }
        assertThat(made[1].log.last()).isEqualTo("join https://shifa.daily.co/r camera=false mic=true")
    }

    @Test
    fun `a refused join on the pre-join page frees that call and shows a fresh preview`() {
        val classToTest = opened()
        classToTest.permissions(true, true)
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        made.single().state.value = VideoCallState(failed = true)
        assertThat(classToTest.state.value.stage).isEqualTo(VideoStage.PreJoin)
        assertThat(made[0].log.last()).isEqualTo("release")
        assertThat(made[1].log).containsExactly("camera true", "mic true").inOrder()
        assertThat(classToTest.call).isSameInstanceAs(made[1])
    }

    @Test
    fun `while the pre-join page shows, the patient's presence is asked every few seconds, in the window only`() {
        val scheduler = TestCoroutineScheduler()
        val presenceScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        var inRoom = false
        var answer = HttpStatusCode.OK
        val presenceEngine =
            MockEngine { req ->
                asked += req.url.encodedPath.substringAfter("/api/")
                if (answer == HttpStatusCode.OK) {
                    respond("""{"patientInRoom":$inRoom}""", answer, json)
                } else {
                    respond("""{"status":${answer.value}}""", answer, json)
                }
            }
        now = start - 10.minutes // before the window: nothing is asked
        val classToTest =
            VideoVisit(
                7,
                presenceScope,
                { FakeCall().also { made += it } },
                { start to start + 1.hours },
                VideoRepository { createHttpClient({ null }, presenceEngine) },
                { now },
            )
        classToTest.watchPresence(true)
        assertThat(asked).isEmpty()
        assertThat(classToTest.state.value.patientInRoom).isNull()

        now = start - 4.minutes // inside the window
        scheduler.advanceTimeBy(5_001)
        settle { classToTest.state.value.patientInRoom != null }
        assertThat(asked.single()).isEqualTo("video/appointments/7/presence")
        assertThat(classToTest.state.value.patientInRoom).isFalse()

        // Each answer comes back on the HTTP engine's thread, so the next wait starts a little after the clock's step.
        inRoom = true
        scheduler.advanceTimeBy(6_000)
        settle { classToTest.state.value.patientInRoom == true }
        assertThat(asked).hasSize(2)

        // Daily can't be asked: unknown, not "not here".
        answer = HttpStatusCode.ServiceUnavailable
        scheduler.advanceTimeBy(6_000)
        settle { classToTest.state.value.patientInRoom == null }
        assertThat(classToTest.state.value.patientInRoom).isNull()

        // The page goes: no more questions.
        classToTest.watchPresence(false)
        scheduler.advanceTimeBy(20_000)
        Thread.sleep(50)
        assertThat(asked).hasSize(3)
        presenceScope.cancel()
    }

    @Test
    fun `a backend without the presence route is asked once`() {
        val scheduler = TestCoroutineScheduler()
        val presenceScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
        val presenceEngine =
            MockEngine { req ->
                asked += req.url.encodedPath
                respond("""{"status":404}""", HttpStatusCode.NotFound, json)
            }
        val classToTest =
            VideoVisit(
                7,
                presenceScope,
                { FakeCall().also { made += it } },
                { start to start + 1.hours },
                VideoRepository { createHttpClient({ null }, presenceEngine) },
                { now },
            )
        classToTest.watchPresence(true)
        settle { asked.isNotEmpty() }
        scheduler.advanceTimeBy(30_000)
        Thread.sleep(50)
        assertThat(asked).hasSize(1)
        assertThat(classToTest.state.value.patientInRoom).isNull()
        presenceScope.cancel()
    }

    @Test
    fun `the waiting clock runs while the doctor is in the call alone and stops when the patient joins`() {
        val classToTest = opened()
        classToTest.permissions(true, true)
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        assertThat(classToTest.state.value.aloneSince).isNull()
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined)
        assertThat(classToTest.state.value.aloneSince).isEqualTo(now)
        val since = now
        now = start + 7.minutes
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined, micOn = false)
        assertThat(classToTest.state.value.aloneSince).isEqualTo(since) // still the first moment alone
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined, patientJoined = true)
        assertThat(classToTest.state.value.aloneSince).isNull()
    }

    @Test
    fun `once the patient has been in the call, a drop is not waiting for a no-show`() {
        val classToTest = opened()
        classToTest.permissions(true, true)
        classToTest.join()
        settle { made.single().log.any { it.startsWith("join") } }
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined, patientJoined = true)
        made.single().state.value = VideoCallState(phase = VideoCallState.Phase.Joined, patientJoined = false)
        assertThat(classToTest.state.value.patientSeen).isTrue()
        assertThat(classToTest.state.value.aloneSince).isNull()
    }
}
