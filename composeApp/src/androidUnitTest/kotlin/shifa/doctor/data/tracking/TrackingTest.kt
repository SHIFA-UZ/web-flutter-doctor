package shifa.doctor.data.tracking

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.tracking.events.SessionTrackEvents
import shifa.doctor.data.tracking.events.ShellEvents

class TrackingTest {
    private val sink = RecordingSink()
    private val stored = MapSettings()
    private var environment =
        TrackingEnvironment(releaseBuild = true, demo = false, productionBackend = true, web = false)

    @Before
    fun setUp() {
        Tracking.clearForTests()
        Tracking.environment = { environment }
        Tracking.settings = TrackingSettings { stored }
        Tracking.language = { "uz" }
    }

    @After
    fun tearDown() {
        Tracking.clearForTests()
    }

    private fun started() {
        Tracking.start(sink)
        sink.calls.clear()
    }

    @Test
    fun `before a sink is attached nothing happens and nothing is read`() {
        // The real seams: a platform Settings, DemoMode and BackendChoice would all fail here if touched.
        Tracking.clearForTests()

        Tracking.track(ShellEvents.MoreOpened)
        Tracking.screen(TrackScreen.Home, mapOf("x" to 1))
        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.reset()
        Tracking.register("app_language", "ru")
        Tracking.flush()

        assertThat(Tracking.refresh()).isEqualTo(TrackingStatus(TrackingOffReason.NoSink))
        assertThat(Tracking.status.value.on).isFalse()
        assertThat(sink.calls).isEmpty()
    }

    @Test
    fun `start tells the SDK on or off first, then registers the super properties`() {
        Tracking.start(sink)

        assertThat(sink.calls).containsExactly("optOut false", "register {app=doctor, app_language=uz}").inOrder()
        assertThat(Tracking.status.value).isEqualTo(TrackingStatus(null))
    }

    @Test
    fun `a start that must not track never opts the SDK in`() {
        environment = environment.copy(productionBackend = false)

        Tracking.start(sink)
        Tracking.track(ShellEvents.MoreOpened)

        assertThat(sink.calls).containsExactly("optOut true", "register {app=doctor, app_language=uz}").inOrder()
        assertThat(Tracking.status.value.reason).isEqualTo(TrackingOffReason.DevelopmentBackend)
    }

    @Test
    fun `an event goes to the sink with its properties, enums as words`() {
        started()

        Tracking.track(SessionTrackEvents.SessionEnded(SessionEndReason.PinLockout))

        assertThat(sink.calls).containsExactly("capture session_ended {reason=pin_lockout} {} {}")
    }

    @Test
    fun `a screen view sends its fixed id, nulls and objects dropped`() {
        started()

        Tracking.screen(TrackScreen.EmailCode, mapOf("mode" to "sms_sign_in", "x" to null, "route" to Any()))

        assertThat(sink.calls).containsExactly("screen email_code {mode=sms_sign_in}")
    }

    @Test
    fun `identify uses the role's prefix and adds app to the person properties`() {
        started()

        Tracking.identify(TrackingRole.Staff, " 42 ", mapOf("tier" to null, "has_clinic" to true), mapOf("a" to 1))

        assertThat(sink.calls).containsExactly("identify staff_42 {app=doctor, has_clinic=true} {a=1}")
    }

    @Test
    fun `a blank user id is never identified`() {
        started()

        Tracking.identify(TrackingRole.Doctor, " ")

        assertThat(sink.calls).isEmpty()
    }

    @Test
    fun `a different user is reset first, the same one is not`() {
        started()

        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.identify(TrackingRole.Doctor, "8")

        assertThat(sink.calls)
            .containsExactly(
                "identify doctor_7 {app=doctor} {}",
                "identify doctor_7 {app=doctor} {}",
                "reset",
                "register {app=doctor, app_language=uz}",
                "identify doctor_8 {app=doctor} {}",
            ).inOrder()
    }

    @Test
    fun `a doctor and a staff member with the same number are different users`() {
        started()

        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.identify(TrackingRole.Staff, "7")

        assertThat(sink.calls).contains("reset")
    }

    @Test
    fun `a user left in the SDK by an earlier launch is reset before the next one`() {
        started()
        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.clearForTests()
        setUp()
        started()

        Tracking.identify(TrackingRole.Doctor, "8")

        assertThat(sink.calls.first()).isEqualTo("reset")
    }

    @Test
    fun `reset puts every super property back, since the SDK's reset drops them`() {
        started()
        Tracking.register("app_language", "ru")
        sink.calls.clear()

        Tracking.reset()

        assertThat(sink.calls).containsExactly("reset", "register {app=doctor, app_language=ru}").inOrder()
    }

    @Test
    fun `after a reset the next user is identified without a second reset`() {
        started()
        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.reset()
        sink.calls.clear()

        Tracking.identify(TrackingRole.Doctor, "8")

        assertThat(sink.calls).containsExactly("identify doctor_8 {app=doctor} {}")
    }

    @Test
    fun `a switch to the development backend opts out, resets and sends nothing more`() {
        started()
        Tracking.identify(TrackingRole.Doctor, "7")
        sink.calls.clear()

        environment = environment.copy(productionBackend = false)
        Tracking.refresh()
        Tracking.track(ShellEvents.MoreOpened)
        Tracking.screen(TrackScreen.Home)
        Tracking.identify(TrackingRole.Doctor, "7")
        Tracking.flush()

        assertThat(sink.calls)
            .containsExactly("optOut true", "reset", "register {app=doctor, app_language=uz}")
            .inOrder()
        assertThat(Tracking.status.value.reason).isEqualTo(TrackingOffReason.DevelopmentBackend)
    }

    @Test
    fun `demo mode is caught by the next call even when nobody refreshed`() {
        started()

        environment = environment.copy(demo = true)
        Tracking.track(ShellEvents.MoreOpened)

        assertThat(sink.calls.first()).isEqualTo("optOut true")
        assertThat(sink.calls.none { it.startsWith("capture") }).isTrue()
        assertThat(Tracking.status.value.reason).isEqualTo(TrackingOffReason.DemoMode)
    }

    @Test
    fun `back on production tracking resumes, anonymous until identified again`() {
        started()
        environment = environment.copy(productionBackend = false)
        Tracking.refresh()
        sink.calls.clear()

        environment = environment.copy(productionBackend = true)
        Tracking.track(ShellEvents.MoreOpened)

        assertThat(sink.calls).containsExactly("optOut false", "capture more_opened {} {} {}").inOrder()
    }

    @Test
    fun `a session end while off still resets the SDK`() {
        environment = environment.copy(releaseBuild = false)
        started()

        Tracking.reset()

        assertThat(sink.calls.first()).isEqualTo("reset")
    }

    @Test
    fun `tracking is allowed until the doctor opts out, and the choice is kept`() {
        assertThat(Tracking.optedOut).isFalse()
        started()
        Tracking.identify(TrackingRole.Doctor, "7")
        sink.calls.clear()

        Tracking.setOptOut(true)
        Tracking.track(ShellEvents.MoreOpened)

        assertThat(sink.calls)
            .containsExactly("optOut true", "reset", "register {app=doctor, app_language=uz}")
            .inOrder()
        assertThat(Tracking.status.value.reason).isEqualTo(TrackingOffReason.OptedOut)
        assertThat(Tracking.optedOut).isTrue()
        assertThat(stored.getBoolean("tracking_opt_out", false)).isTrue()
    }

    @Test
    fun `a saved opt-out holds from the start of the next launch`() {
        stored.putBoolean("tracking_opt_out", true)

        Tracking.start(sink)

        assertThat(sink.calls.first()).isEqualTo("optOut true")
        assertThat(Tracking.status.value.reason).isEqualTo(TrackingOffReason.OptedOut)
    }

    @Test
    fun `opting back in turns tracking on again`() {
        started()
        Tracking.setOptOut(true)
        sink.calls.clear()

        Tracking.setOptOut(false)
        Tracking.identify(TrackingRole.Doctor, "7")

        assertThat(sink.calls).containsExactly("optOut false", "identify doctor_7 {app=doctor} {}").inOrder()
        assertThat(Tracking.status.value.on).isTrue()
    }

    @Test
    fun `the development web site is off`() {
        environment = environment.copy(web = true)

        Tracking.start(sink, webHost = "doctor-dev.shifa.uz")
        Tracking.track(ShellEvents.MoreOpened)

        assertThat(sink.calls.none { it.startsWith("capture") }).isTrue()
        assertThat(Tracking.status.value.reason).isEqualTo(TrackingOffReason.NotProductionSite)
    }

    @Test
    fun `the recording tracker keeps events and screens in order`() {
        val tracker = RecordingTracker()

        tracker.track(ShellEvents.TabSelected("home", "bar"))
        tracker.screen(TrackScreen.Home)

        assertThat(tracker.events).containsExactly(ShellEvents.TabSelected("home", "bar"))
        assertThat(tracker.screens).containsExactly(TrackScreen.Home to emptyMap<String, Any>())
    }
}
