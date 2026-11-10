package shifa.doctor.data.tracking

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.clinics.ClinicsState
import shifa.doctor.data.model.DoctorMe
import shifa.doctor.data.model.DoctorProfile
import shifa.doctor.data.model.DoctorSubscription
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.session.ROLE_CLINIC_STAFF
import shifa.doctor.data.session.ROLE_DOCTOR
import shifa.doctor.data.session.Session
import shifa.doctor.data.storage.AppLanguage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingSessionTest {
    private val sink = RecordingSink()
    private val stored = MapSettings()
    private var environment =
        TrackingEnvironment(releaseBuild = true, demo = false, productionBackend = true, web = false)

    private val session = MutableStateFlow<Session?>(null)
    private val ended = MutableSharedFlow<SessionEndReason>(replay = 1)
    private val profile = MutableStateFlow<DoctorMe?>(null)
    private val zone = MutableStateFlow<String?>("Asia/Tashkent")
    private val clinics =
        MutableStateFlow<ClinicsState?>(
            ClinicsState(listOf(MyClinic(1, membershipRole = "CLINIC_ADMIN")), known = true),
        )
    private val language = MutableStateFlow(AppLanguage.Uzbek)

    private val doctor7 = Session(token("7", ROLE_DOCTOR), ROLE_DOCTOR)
    private val me = DoctorMe(DoctorProfile(profession = "Dentist"), subscription = DoctorSubscription(tier = "PRO"))

    @Before
    fun setUp() {
        Tracking.clearForTests()
        Tracking.environment = { environment }
        Tracking.settings = TrackingSettings { stored }
        Tracking.language = { "uz" }
        Tracking.start(sink)
        sink.calls.clear()
    }

    @After
    fun tearDown() {
        Tracking.clearForTests()
    }

    private fun TestScope.running(launchToken: String? = null): TrackingSession {
        val classToTest = TrackingSession(session, ended, profile, zone, clinics, language, launchToken)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { classToTest.run() }
        return classToTest
    }

    private val doctor7Identify =
        "identify doctor_7 {app=doctor, role=doctor, tier=pro, profession=Dentist, time_zone=Asia/Tashkent, " +
            "language=uz, has_clinic=true, clinic_role=clinic_admin} {}"

    @Test
    fun `a restored session starts once and is identified when the profile loads`() =
        runTest {
            session.value = doctor7
            running(launchToken = doctor7.token)

            assertThat(sink.calls).containsExactly("capture session_started {role=doctor, restored=true} {} {}")

            profile.value = me
            session.value = doctor7.copy() // the same session again: nothing new

            assertThat(sink.calls).containsExactly(
                "capture session_started {role=doctor, restored=true} {} {}",
                doctor7Identify,
            ).inOrder()
        }

    @Test
    fun `a sign-in is not a restore`() =
        runTest {
            running(launchToken = null)
            session.value = doctor7

            assertThat(sink.calls).containsExactly("capture session_started {role=doctor, restored=false} {} {}")
        }

    @Test
    fun `every session end is sent before the reset`() =
        runTest {
            running()
            SessionEndReason.entries.forEach { reason ->
                sink.calls.clear()
                session.value = doctor7
                profile.value = me
                session.value = null
                profile.value = null
                ended.emit(reason)

                assertThat(sink.calls).containsExactly(
                    "capture session_started {role=doctor, restored=false} {} {}",
                    doctor7Identify,
                    "capture session_ended {reason=${reason.trackName()}} {} {}",
                    "reset",
                    "register {app=doctor, app_language=uz}",
                ).inOrder()
            }
        }

    @Test
    fun `a reason already replayed when it starts is not sent again`() =
        runTest {
            ended.emit(SessionEndReason.SignedOut)
            running()

            assertThat(sink.calls).isEmpty()
        }

    @Test
    fun `another user is identified afresh, and a changed property identifies again`() =
        runTest {
            running()
            session.value = doctor7
            profile.value = me
            sink.calls.clear()

            language.value = AppLanguage.Russian
            assertThat(sink.calls.single()).contains("language=ru")
            sink.calls.clear()

            session.value = Session(token("8", ROLE_DOCTOR), ROLE_DOCTOR)

            assertThat(sink.calls).containsExactly(
                "capture session_started {role=doctor, restored=false} {} {}",
                "reset", // the core drops doctor_7 before doctor_8
                "register {app=doctor, app_language=uz}",
                doctor7Identify.replace("doctor_7", "doctor_8").replace("language=uz", "language=ru"),
            ).inOrder()
        }

    @Test
    fun `clinic staff are identified without a doctor profile`() =
        runTest {
            running()
            clinics.value = null
            session.value = Session(token("9", ROLE_CLINIC_STAFF), ROLE_CLINIC_STAFF)

            assertThat(sink.calls).containsExactly(
                "capture session_started {role=staff, restored=false} {} {}",
                "identify staff_9 {app=doctor, role=staff, time_zone=Asia/Tashkent, language=uz, has_clinic=true} {}",
            ).inOrder()
        }

    @Test
    fun `an admin token is never identified or tracked`() =
        runTest {
            running()
            session.value = Session(token("1", "ADMIN"), "ADMIN")
            profile.value = me

            assertThat(sink.calls).isEmpty()
        }

    @Test
    fun `tracking back on identifies again`() =
        runTest {
            running()
            session.value = doctor7
            profile.value = me
            sink.calls.clear()

            environment = environment.copy(demo = true)
            Tracking.refresh()
            environment = environment.copy(demo = false)
            Tracking.refresh()

            assertThat(sink.calls).containsExactly(
                "optOut true",
                "reset",
                "register {app=doctor, app_language=uz}",
                "optOut false",
                doctor7Identify,
            ).inOrder()
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun token(
        sub: String,
        role: String,
    ): String {
        val enc = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        return "${enc.encode("{}".encodeToByteArray())}.${enc.encode(
            """{"sub":"$sub","role":"$role","exp":4000000000}""".encodeToByteArray(),
        )}.sig"
    }
}
