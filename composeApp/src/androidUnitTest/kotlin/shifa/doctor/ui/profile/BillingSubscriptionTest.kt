@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.subscription.DoctorTier
import shifa.doctor.data.subscription.SubscriptionRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.checkoutFailed
import shifa.doctor.generated.resources.planChanged
import shifa.doctor.generated.resources.planUnchanged
import shifa.doctor.stopAll
import java.io.File

// SUB-02: plans, checkout and Stripe Connect from Subscription & billing.
@OptIn(ExperimentalCoroutinesApi::class)
class BillingSubscriptionTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val fixture = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private var tier = "BASIC"
    private val me get() = fixture.replace(Regex(""""tier"\s*:\s*"[A-Z]+""""), """"tier":"$tier"""")
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var failCheckout = false
    private var failPlans = false
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath.substringAfter("/api/")
            when {
                path == "doctor/subscription/me" ->
                    respond(
                        """{"planCode":"BASIC","status":"ACTIVE","currentPeriodEnd":"2026-12-04T20:00:00Z"}""",
                        HttpStatusCode.OK,
                        json,
                    )
                path == "doctor/subscription/plans" && failPlans ->
                    respond(
                        """{"status":500,"code":"INTERNAL"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                path == "doctor/subscription/plans" ->
                    respond(
                        """[{"code":"BASIC","name":"Basic","priceMinor":9900,"currency":"EUR","interval":"MONTH"},{"code":"PRO","name":"Pro","priceMinor":19900,"currency":"EUR","interval":"MONTH"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                req.method == HttpMethod.Get -> respond(me, HttpStatusCode.OK, json)
                failCheckout ->
                    respond(
                        """{"status":502,"code":"PROVIDER_ERROR","requestId":"req-s"}""",
                        HttpStatusCode.BadGateway,
                        json,
                    )
                else -> {
                    sent += "$path ${req.body.toByteArray().decodeToString()}"
                    respond(
                        """{"checkoutUrl":"https://checkout.stripe.com/c/x","onboardingUrl":"https://connect.stripe.com/x"}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }
            }
        }
    private val repo = ProfileRepository { createHttpClient({ null }, engine) }
    private val subscriptions = SubscriptionRepository { createHttpClient({ null }, engine) }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { repo.refresh() }
    }

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() =
        BillingViewModel(repo, subscriptions, retryDelayMs = 0).also { created += it }.apply {
            open()
            settle { state.value.plans.isNotEmpty() }
        }

    @Test
    fun `plans load and an upgrade opens the Stripe checkout for that plan`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.tier).isEqualTo(DoctorTier.Basic)
        assertThat(classToTest.state.value.plans.map { it.code }).containsExactly("BASIC", "PRO")
        classToTest.upgrade(classToTest.state.value.plans[1])
        settle { classToTest.state.value.openUrl != null }
        assertThat(sent.single()).isEqualTo("""doctor/subscription/checkout {"planCode":"PRO","gateway":"STRIPE"}""")
        assertThat(classToTest.state.value.openUrl).isEqualTo("https://checkout.stripe.com/c/x")
    }

    @Test
    fun `back from the checkout the plan is read again - changed or not yet`() {
        val classToTest = opened()
        classToTest.upgrade(classToTest.state.value.plans[1])
        settle { classToTest.state.value.openUrl != null }
        classToTest.resumed() // the browser hasn't opened yet: nothing
        assertThat(classToTest.state.value.away).isNotNull()
        classToTest.urlOpened()
        tier = "PRO"
        classToTest.resumed()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.planChanged)
        assertThat(classToTest.state.value.banner?.args).containsExactly("PRO")
        assertThat(classToTest.state.value.tier).isEqualTo(DoctorTier.Pro)
        classToTest.upgrade(classToTest.state.value.plans[1].copy(code = "PRO"))
        settle { classToTest.state.value.openUrl != null }
        classToTest.urlOpened()
        classToTest.ui { it.copy(banner = null) }
        classToTest.resumed()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.planUnchanged)
    }

    @Test
    fun `Stripe Connect opens the onboarding, a failure says so with the request id`() {
        val classToTest = opened()
        classToTest.connect()
        settle { classToTest.state.value.openUrl != null }
        assertThat(sent.single()).isEqualTo("doctor/payments/stripe/connect/onboard {}")
        classToTest.urlOpened()
        failCheckout = true
        classToTest.connect()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.checkoutFailed)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-s")
    }

    @Test
    fun `the renewal date shows in the practice zone, failed plans offer a retry`() {
        val classToTest = opened()
        settle { classToTest.state.value.renewsOn != null }
        assertThat(classToTest.state.value.renewsOn).isEqualTo("05.12.2026") // 20:00 UTC is the next day in Tashkent
        failPlans = true
        classToTest.loadPlans()
        settle { classToTest.state.value.plansFailed }
        failPlans = false
        classToTest.loadPlans()
        settle { !classToTest.state.value.plansFailed }
        assertThat(classToTest.state.value.plans).hasSize(2)
    }

    @Test
    fun `an empty checkout URL is a failure, not a silent no-op`() {
        val classToTest =
            BillingViewModel(
                repo,
                SubscriptionRepository {
                    createHttpClient(
                        {
                            null
                        },
                        MockEngine {
                            respond(
                                """{"checkoutUrl":""}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    )
                },
                0,
            ).also { created += it }
        classToTest.open()
        classToTest.upgrade(shifa.doctor.data.subscription.SubscriptionPlan("PRO", "Pro"))
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.openUrl).isNull()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.checkoutFailed)
    }
}
