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
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.subscription.DoctorTier
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.ProfileEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.contactDetailsSaved
import shifa.doctor.generated.resources.contactSavedAddressFailed
import shifa.doctor.generated.resources.currentPasswordIncorrect
import shifa.doctor.generated.resources.currentPasswordIsRequired
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidIban
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.locationSaved
import shifa.doctor.generated.resources.newPasswordConfirmationMismatchError
import shifa.doctor.generated.resources.passwordUpdatedSuccessfully
import shifa.doctor.generated.resources.paymentAndInvoicingSaved
import shifa.doctor.generated.resources.phoneNumberRequired
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice
import java.io.File

// PROF-04: Contact & clinic address, Subscription & billing (invoicing), Security (password).
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFormsViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()

    // Path ending → (status, body) for writes; reads answer the fixture.
    private val fail = mutableMapOf<String, Pair<HttpStatusCode, String>>()

    // The address at a confirmed pin (BE-15); none unless a test sets one.
    private var geocode = """{"results":[]}"""
    private var geocodeDelayMs = 0L
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (path.endsWith("geocode/reverse")) {
                kotlinx.coroutines.delay(geocodeDelayMs)
                return@MockEngine respond(geocode, HttpStatusCode.OK, json)
            }
            if (req.method == HttpMethod.Get) return@MockEngine respond(me, HttpStatusCode.OK, json)
            val body = req.body.toByteArray().decodeToString()
            sent += "${path.substringAfter("/api/")} $body"
            fail.entries.firstOrNull { path.endsWith(it.key) }?.let {
                    (_, f) ->
                return@MockEngine respond(f.second, f.first, json)
            }
            respond(if (path.endsWith("reset-password")) """{"message":"ok"}""" else body, HttpStatusCode.OK, json)
        }
    private val repo = ProfileRepository { createHttpClient({ null }, engine) }
    private val geocoder =
        shifa.doctor.data.locations.GeocodeRepository(
            { createHttpClient({ null }, engine) },
            { "en" },
        )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { repo.refresh() }
    }

    @After fun tearDown() {
        ShellNotice.pending.value = null
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `contact starts grouped from the saved profile, with the address and coordinates`() {
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        val s = classToTest.state.value
        assertThat(s.form.phone).isEqualTo("+998 90 000 00 00")
        assertThat(s.form.email).isEqualTo("demo.doctor@shifa.dev")
        assertThat(s.form.city).isEqualTo("Tashkent")
        assertThat(s.form.latitude).isEqualTo(41.3111)
        assertThat(s.dirty).isFalse()
    }

    @Test
    fun `phone and e-mail are checked before anything is sent`() {
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.edit { it.copy(phone = " ", email = "name@domain") }
        classToTest.save()
        assertThat(classToTest.state.value.phoneError).isEqualTo(Res.string.phoneNumberRequired)
        assertThat(classToTest.state.value.emailError).isEqualTo(Res.string.invalidEmail)
        classToTest.edit { it.copy(phone = "12345", email = "") }
        classToTest.save()
        assertThat(classToTest.state.value.phoneError).isEqualTo(Res.string.invalidPhone)
        assertThat(classToTest.state.value.emailError).isNull()
        assertThat(sent).isEmpty()
    }

    @Test
    fun `contact saves whole and E164, the address only its changed keys, then closes`() {
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.edit {
            it.copy(
                phone = "(90) 123-45-67",
                email = " New@Shifa.dev ",
                street = "Amir Temur 12, 3rd floor",
            )
        }
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(sent).containsExactly(
            """doctors/me/contact {"phone":"+998901234567","email":"new@shifa.dev"}""",
            """doctors/me/profile {"locationStreetAddress":"Amir Temur 12, 3rd floor"}""",
        ).inOrder()
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.contactDetailsSaved)
        assertThat(repo.me.value?.contact?.phone).isEqualTo("+998901234567")
    }

    @Test
    fun `only the address changed - one profile PATCH, a cleared field is sent empty`() {
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.edit { it.copy(city = "") }
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(sent).containsExactly("""doctors/me/profile {"locationCity":""}""")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.locationSaved)
    }

    @Test
    fun `a failed address after a saved contact says so and keeps only the address dirty`() {
        fail["doctors/me/profile"] = HttpStatusCode.InternalServerError to """{"status":500,"code":"INTERNAL","requestId":"req-41"}"""
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.edit { it.copy(email = "x@shifa.dev", region = "Tashkent") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        val s = classToTest.state.value
        assertThat(s.banner?.text).isEqualTo(Res.string.contactSavedAddressFailed)
        assertThat(s.banner?.requestId).isEqualTo("req-41")
        assertThat(s.done).isFalse()
        assertThat(s.saved.email).isEqualTo("x@shifa.dev")
        assertThat(s.saved.region).isEmpty()
        assertThat(s.dirty).isTrue()
        fail.clear()
        classToTest.save() // the retry sends only the address
        settle { classToTest.state.value.done }
        assertThat(sent.last()).isEqualTo("""doctors/me/profile {"locationRegion":"Tashkent"}""")
        assertThat(sent.count { it.startsWith("doctors/me/contact") }).isEqualTo(1)
    }

    @Test
    fun `the map opens on the saved pin, a tap moves it, Confirm fills the form and Save sends both coordinates`() {
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.openMap()
        assertThat(classToTest.state.value.mapPin).isEqualTo(41.3111 to 69.2797)
        assertThat(classToTest.back()).isFalse() // an unmoved pin: Back closes the map, not the form
        assertThat(classToTest.state.value.mapPin).isNull()
        classToTest.openMap()
        classToTest.movePin(41.2856, 69.2034)
        assertThat(classToTest.back()).isFalse() // a moved pin asks first
        assertThat(classToTest.state.value.askDiscardPin).isTrue()
        classToTest.discardPin()
        assertThat(classToTest.state.value.mapPin).isNull()
        assertThat(classToTest.state.value.dirty).isFalse()
        classToTest.openMap()
        classToTest.movePin(41.2856, 69.2034)
        classToTest.confirmMap()
        assertThat(classToTest.state.value.form.latitude).isEqualTo(41.2856)
        settle { !classToTest.state.value.findingAddress }
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(sent).containsExactly("""doctors/me/profile {"latitude":41.2856,"longitude":69.2034}""")
    }

    @Test
    fun `with no saved pin the map opens on Tashkent`() {
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.edit { it.copy(latitude = null, longitude = null) }
        classToTest.openMap()
        assertThat(classToTest.state.value.mapPin).isEqualTo(41.2995 to 69.2401)
    }

    @Test
    fun `billing shows the plan, cleans the IBAN, echoes what it doesn't edit and stays`() {
        val classToTest = BillingViewModel(repo).also { created += it }.apply { open() }
        assertThat(classToTest.state.value.tier).isEqualTo(DoctorTier.Premium)
        classToTest.edit { it.copy(name = " Dr Karimova ", iban = "de89 3704 0044 0532 0130 00") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        assertThat(sent.single()).isEqualTo(
            // Nulls are left out; the backend sets the whole block, so a missing key is a cleared one.
            """doctors/me/billing {"billingName":"Dr Karimova","iban":"DE89370400440532013000"}""",
        )
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.paymentAndInvoicingSaved)
        assertThat(classToTest.state.value.dirty).isFalse()
        assertThat(classToTest.state.value.done).isFalse()
    }

    @Test
    fun `IBAN or account number rules`() {
        val ok = listOf("DE89370400440532013000", "UZ12345678901234567890", "20208000900123456001")
        val bad = listOf("DE8937", "1234", "DEXX370400440532013000", "UZ12-3456")
        ok.forEach { assertThat(BillingViewModel.isIbanOrAccount(it)).isTrue() }
        bad.forEach { assertThat(BillingViewModel.isIbanOrAccount(BillingViewModel.cleanIban(it))).isFalse() }
        val classToTest = BillingViewModel(repo).also { created += it }.apply { open() }
        classToTest.edit { it.copy(iban = "1234") }
        classToTest.save()
        assertThat(classToTest.state.value.ibanError).isEqualTo(Res.string.invalidIban)
        assertThat(sent).isEmpty()
    }

    @Test
    fun `password - checks first, sends as typed, clears on success`() {
        val classToTest =
            SecurityViewModel(
                AuthRepository { createHttpClient({ null }, engine) },
            ).also { created += it }
        classToTest.type { it.copy(new = "Abcdef1!", confirm = "Abcdef1?") }
        classToTest.change()
        assertThat(classToTest.state.value.currentError).isEqualTo(Res.string.currentPasswordIsRequired)
        assertThat(classToTest.state.value.confirmError).isEqualTo(Res.string.newPasswordConfirmationMismatchError)
        classToTest.type { it.copy(current = " old pass ", confirm = "Abcdef1!") }
        classToTest.change()
        settle { !classToTest.state.value.saving }
        assertThat(
            sent.single(),
        ).isEqualTo("""auth/reset-password {"currentPassword":" old pass ","newPassword":"Abcdef1!"}""")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.passwordUpdatedSuccessfully)
        assertThat(classToTest.state.value.dirty).isFalse()
    }

    @Test
    fun `password - a 400 means the current one is wrong, it is cleared`() {
        fail["auth/reset-password"] = HttpStatusCode.BadRequest to """{"status":400,"code":"BAD_REQUEST","requestId":"req-9"}"""
        val classToTest =
            SecurityViewModel(
                AuthRepository { createHttpClient({ null }, engine) },
            ).also { created += it }
        classToTest.type { it.copy(current = "wrong", new = "Abcdef1!", confirm = "Abcdef1!") }
        classToTest.change()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.currentError).isEqualTo(Res.string.currentPasswordIncorrect)
        assertThat(classToTest.state.value.current).isEmpty()
        assertThat(classToTest.state.value.new).isEqualTo("Abcdef1!")
    }

    @Test
    fun `typed passwords ask before leaving, and nothing survives a close`() {
        val classToTest =
            SecurityViewModel(
                AuthRepository { createHttpClient({ null }, engine) },
            ).also { created += it }
        assertThat(classToTest.back()).isTrue()
        classToTest.type { it.copy(current = "x") }
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        classToTest.discard()
        assertThat(classToTest.state.value.done).isTrue()
        classToTest.closed()
        assertThat(classToTest.state.value).isEqualTo(SecurityUiState())
    }

    @Test
    fun `Change password is off until the request can succeed, and a clean Back forgets everything`() {
        val classToTest =
            SecurityViewModel(
                AuthRepository { createHttpClient({ null }, engine) },
            ).also { created += it }
        classToTest.type { it.copy(current = "x", new = "Abcdef1!", confirm = "Abcdef1") }
        assertThat(classToTest.state.value.canChange).isFalse()
        assertThat(classToTest.state.value.confirmMismatch).isFalse() // still typing
        classToTest.type { it.copy(confirm = "Abcdef1?") }
        assertThat(classToTest.state.value.confirmMismatch).isTrue()
        classToTest.type { it.copy(confirm = "Abcdef1!") }
        assertThat(classToTest.state.value.canChange).isTrue()
        classToTest.type { SecurityUiState(showNew = true) }
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value).isEqualTo(SecurityUiState())
    }

    @Test
    fun `billing keeps a Stripe account that arrived after the screen opened`() {
        val classToTest = BillingViewModel(repo).also { created += it }.apply { open() }
        runBlocking {
            repo.updateBilling(shifa.doctor.data.model.DoctorBilling(stripeConnectAccountId = "acct_1234"))
        }
        sent.clear()
        classToTest.edit { it.copy(taxId = "305123456") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        assertThat(sent.single()).contains(""""stripeConnectAccountId":"acct_1234"""")
    }

    @Test
    fun `contact saves and failures are tracked with the changed field names only`() {
        val tracker = RecordingTracker()
        val classToTest = ContactViewModel(repo, tracker).also { created += it }.apply { open() }
        classToTest.edit { it.copy(email = "new@shifa.dev", city = "Samarkand") }
        classToTest.save()
        settle { classToTest.state.value.done }
        fail["doctors/me/contact"] = HttpStatusCode.InternalServerError to """{"status":500,"code":"INTERNAL"}"""
        classToTest.closed()
        classToTest.open()
        classToTest.edit { it.copy(email = "other@shifa.dev") }
        classToTest.save()
        settle { tracker.events.size == 2 }
        assertThat(tracker.events)
            .containsExactly(
                ProfileEvents.SectionSaved("contact", listOf("email", "address")),
                ProfileEvents.SectionSaveFailed("contact", "server"),
            ).inOrder()
    }

    @Test
    fun `invoicing saves and discards are tracked`() {
        val tracker = RecordingTracker()
        val classToTest = BillingViewModel(repo, tracker = tracker).also { created += it }.apply { open() }
        classToTest.edit { it.copy(taxId = "305123456") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        classToTest.edit { it.copy(name = "x") }
        classToTest.discard()
        assertThat(tracker.events)
            .containsExactly(
                ProfileEvents.SectionSaved("invoicing", listOf("tax_id")),
                ProfileEvents.EditDiscarded("invoicing"),
            ).inOrder()
    }

    @Test
    fun `a password change is tracked by its outcome`() {
        val tracker = RecordingTracker()
        val classToTest =
            SecurityViewModel(AuthRepository { createHttpClient({ null }, engine) }, tracker).also { created += it }
        fail["auth/reset-password"] = HttpStatusCode.BadRequest to """{"status":400,"code":"BAD_REQUEST"}"""
        classToTest.type { it.copy(current = "wrong", new = "Abcdef1!", confirm = "Abcdef1!") }
        classToTest.change()
        settle { !classToTest.state.value.saving }
        fail.clear()
        classToTest.type { it.copy(current = "right") }
        classToTest.change()
        settle { tracker.events.size == 2 }
        assertThat(tracker.events)
            .containsExactly(
                ProfileEvents.PasswordChangeFailed("wrong_current_password"),
                ProfileEvents.PasswordChanged,
            ).inOrder()
    }

    @Test
    fun `a confirmed pin fills the address fields from the address there`() {
        geocode =
            """{"results":[{"locationCountry":"Uzbekistan","locationCity":"Tashkent","locationDistrict":"Chilanzar district",""" +
            """"locationPostalCode":"100000","locationStreetAddress":"Seoul Street"}]}"""
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        classToTest.openMap()
        classToTest.movePin(41.2988, 69.2396)
        classToTest.confirmMap()
        settle { classToTest.state.value.form.street == "Seoul Street" }
        val f = classToTest.state.value.form
        assertThat(listOf(f.country, f.region, f.district, f.city, f.postal))
            .containsExactly("Uzbekistan", "", "Chilanzar district", "Tashkent", "100000").inOrder()
        assertThat(f.latitude to f.longitude).isEqualTo(41.2988 to 69.2396)
    }

    @Test
    fun `a street typed while the address is looked up wins, and Save waits for the lookup`() {
        geocode = """{"results":[{"locationCity":"Samarkand","locationStreetAddress":"Seoul Street"}]}"""
        geocodeDelayMs = 300
        val classToTest = ContactViewModel(repo, geocode = geocoder).also { created += it }.apply { open() }
        val before = classToTest.state.value.form
        classToTest.openMap()
        classToTest.movePin(41.2988, 69.2396)
        classToTest.confirmMap()
        assertThat(classToTest.state.value.findingAddress).isTrue()
        classToTest.save() // ignored until the address is in
        classToTest.edit { it.copy(street = "Amir Temur 1") }
        settle { !classToTest.state.value.findingAddress }
        assertThat(sent).isEmpty()
        assertThat(classToTest.state.value.form.street).isEqualTo("Amir Temur 1")
        assertThat(classToTest.state.value.form.city).isEqualTo(before.city) // nothing from the late answer
    }
}
