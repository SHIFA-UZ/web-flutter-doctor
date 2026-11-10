package shifa.doctor.data.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** docs/posthog/00-plan.md §3: every combination the owner's decision names. */
class TrackingStatusTest {
    private val phone = TrackingEnvironment(releaseBuild = true, demo = false, productionBackend = true, web = false)
    private val web = phone.copy(web = true)

    private fun reason(
        environment: TrackingEnvironment,
        optedOut: Boolean = false,
        webHost: String? = null,
    ) = trackingStatus(environment, optedOut, webHost).reason

    @Test
    fun `a release build on the production backend tracks`() {
        assertThat(trackingStatus(phone, optedOut = false, webHost = null)).isEqualTo(TrackingStatus(null))
        assertThat(trackingStatus(phone, optedOut = false, webHost = null).on).isTrue()
    }

    @Test
    fun `a release build on the development backend does not`() {
        assertThat(reason(phone.copy(productionBackend = false))).isEqualTo(TrackingOffReason.DevelopmentBackend)
    }

    @Test
    fun `a debug build does not, also when pointed at production`() {
        assertThat(reason(phone.copy(releaseBuild = false))).isEqualTo(TrackingOffReason.DebugBuild)
        assertThat(reason(phone.copy(releaseBuild = false, productionBackend = false)))
            .isEqualTo(TrackingOffReason.DebugBuild)
    }

    @Test
    fun `demo mode does not`() {
        assertThat(reason(phone.copy(demo = true))).isEqualTo(TrackingOffReason.DemoMode)
    }

    @Test
    fun `an opted-out doctor is not tracked`() {
        assertThat(reason(phone, optedOut = true)).isEqualTo(TrackingOffReason.OptedOut)
    }

    @Test
    fun `the production web site tracks`() {
        assertThat(reason(web, webHost = "doctor.shifa.uz")).isNull()
    }

    @Test
    fun `the development web site never does, whatever backend it talks to`() {
        assertThat(reason(web.copy(productionBackend = false), webHost = "doctor-dev.shifa.uz"))
            .isEqualTo(TrackingOffReason.DevelopmentBackend)
        assertThat(reason(web, webHost = "doctor-dev.shifa.uz")).isEqualTo(TrackingOffReason.NotProductionSite)
    }

    @Test
    fun `any other web host, or an unknown one, does not`() {
        assertThat(reason(web, webHost = "localhost")).isEqualTo(TrackingOffReason.NotProductionSite)
        assertThat(reason(web, webHost = null)).isEqualTo(TrackingOffReason.NotProductionSite)
    }

    @Test
    fun `the hostname means nothing on a phone`() {
        assertThat(reason(phone, webHost = "doctor-dev.shifa.uz")).isNull()
    }

    @Test
    fun `each reason has its own id for the debug menu`() {
        val ids = TrackingOffReason.entries.map { it.id }
        assertThat(ids).containsNoDuplicates()
        ids.forEach { assertThat(it).matches("[a-z]+(_[a-z]+)*") }
    }
}
