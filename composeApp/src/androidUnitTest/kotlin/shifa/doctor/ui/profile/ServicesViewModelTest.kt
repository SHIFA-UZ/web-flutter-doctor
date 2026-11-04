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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.services.ServicesRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.fieldRequired
import shifa.doctor.generated.resources.serviceManagedByClinic
import shifa.doctor.generated.resources.servicePriceDuplicate
import shifa.doctor.generated.resources.servicePriceInvalid
import shifa.doctor.generated.resources.servicePricesInvalid

@OptIn(ExperimentalCoroutinesApi::class)
class ServicesViewModelTest {
    // dev 2026-11-04, shortened
    private val services =
        """[{"id":2,"title":"Follow-up visit","description":null,"isActive":true,"isFreeConsultation":false,"paymentType":"PAY_LATER","clinicManaged":false,"groupId":null,"groupName":null,"groupSortOrder":null,"prices":[{"id":1,"amountMinor":10000000,"currency":"UZS","locationId":2}]},
            {"id":9,"title":"Cleaning","isActive":true,"isFreeConsultation":false,"clinicManaged":true,"prices":[]}]"""
    private val sent = mutableListOf<String>()
    private var failServices = false
    private var deleteStatus = HttpStatusCode.NoContent
    private var failGroupWrite = -1 // the index of the group write to fail, -1 for none
    private var groupWrites = 0
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath.substringAfter("/api/")
            when {
                req.method == HttpMethod.Get && path == "doctors/me/services" ->
                    if (failServices) {
                        respond(
                            """{"status":500,"code":"INTERNAL","requestId":"req-2"}""",
                            HttpStatusCode.InternalServerError,
                            json,
                        )
                    } else {
                        respond(services, HttpStatusCode.OK, json)
                    }
                req.method == HttpMethod.Get && path == "doctors/me/service-groups" ->
                    respond(
                        """[{"id":5,"name":"Ortho","sortOrder":2},{"id":4,"name":"Basics","sortOrder":1}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                req.method == HttpMethod.Get ->
                    respond(
                        """[{"id":2,"label":"Shifa Dev Clinic","locationCity":"Tashkent"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                req.method == HttpMethod.Delete -> {
                    sent += "DELETE $path"
                    if (deleteStatus == HttpStatusCode.NoContent) {
                        respond("", deleteStatus)
                    } else {
                        respond("""{"status":404,"code":"NOT_FOUND"}""", deleteStatus, json)
                    }
                }
                path.startsWith("doctors/me/service-groups") && groupWrites++ == failGroupWrite -> {
                    sent += "FAILED ${req.method.value} $path"
                    respond(
                        """{"status":500,"code":"INTERNAL","requestId":"req-g"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                }
                else -> {
                    val body = req.body.toByteArray().decodeToString()
                    sent += "${req.method.value} $path $body"
                    respond(body.dropLast(1) + ""","id":77}""", HttpStatusCode.OK, json)
                }
            }
        }
    private val repo = ServicesRepository { createHttpClient({ null }, engine) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun loaded() =
        ServicesViewModel(
            repo,
            shifa.doctor.data.locations.LocationsRepository { createHttpClient({ null }, engine) },
        ).also {
                vm ->
            settle { !vm.state.value.loading }
        }

    @Test
    fun `services, groups in sort order and locations load together`() {
        val vm = loaded()
        settle { vm.state.value.locations.isNotEmpty() } // the shared list arrives on its own
        val s = vm.state.value
        assertThat(s.services.map { it.title }).containsExactly("Follow-up visit", "Cleaning")
        assertThat(s.groups.map { it.name }).containsExactly("Basics", "Ortho").inOrder()
        assertThat(s.locations.single().label).isEqualTo("Shifa Dev Clinic")
    }

    @Test
    fun `a failed load keeps its request id for the error state`() {
        failServices = true
        val s = loaded().state.value
        assertThat(s.error?.requestId).isEqualTo("req-2")
    }

    @Test
    fun `a clinic-managed service doesn't open, it says where to edit it`() {
        val classToTest = loaded()
        classToTest.open(classToTest.state.value.services[1])
        assertThat(classToTest.state.value.editing).isFalse()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.serviceManagedByClinic)
    }

    @Test
    fun `the editor checks title, amounts and duplicates before sending`() {
        val classToTest = loaded()
        classToTest.open(null)
        classToTest.save()
        assertThat(classToTest.state.value.titleError).isEqualTo(Res.string.fieldRequired)
        assertThat(classToTest.state.value.pricesError).isEqualTo(Res.string.servicePricesInvalid)
        classToTest.edit { it.copy(title = "Braces", prices = listOf(PriceRow("12.5", "UZS"))) }
        classToTest.save()
        assertThat(classToTest.state.value.rowErrors[0]).isEqualTo(Res.string.servicePriceInvalid) // UZS has no tiyin
        classToTest.edit { it.copy(prices = listOf(PriceRow("100 000", "UZS"), PriceRow("200000", "UZS"))) }
        classToTest.save()
        assertThat(classToTest.state.value.pricesError).isEqualTo(Res.string.servicePriceDuplicate)
        assertThat(sent).isEmpty()
    }

    @Test
    fun `a new service is created with prices in minor units and joins the list`() {
        val classToTest = loaded()
        classToTest.open(null)
        classToTest.edit {
            it.copy(
                title = " Braces ",
                groupId = 5,
                prices = listOf(PriceRow("1 500 000", "UZS"), PriceRow("25.50", "USD", 2), PriceRow("", "EUR")),
            )
        }
        classToTest.save()
        settle { !classToTest.state.value.editing }
        assertThat(sent.single()).isEqualTo(
            """POST doctors/me/services {"title":"Braces","isActive":true,"isFreeConsultation":false,"groupId":5,"prices":[{"amountMinor":150000000,"currency":"UZS"},{"amountMinor":2550,"currency":"USD","locationId":2}]}""",
        )
        assertThat(classToTest.state.value.services.map { it.title }).contains("Braces")
    }

    @Test
    fun `an edit keeps the saved prices, free drops them, and delete asks first`() {
        val classToTest = loaded()
        classToTest.open(classToTest.state.value.services[0])
        assertThat(classToTest.state.value.draft?.prices).containsExactly(PriceRow("100000", "UZS", 2, 10000000))
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.editing).isFalse() // nothing changed: back to the list
        classToTest.open(classToTest.state.value.services[0])
        classToTest.edit { it.copy(free = true) }
        classToTest.save()
        settle { !classToTest.state.value.editing }
        assertThat(
            sent.single(),
        ).startsWith(
            """PATCH doctors/me/services/2 {"title":"Follow-up visit","isActive":true,"isFreeConsultation":true,"prices":[]""",
        )
        classToTest.ui { s -> s.copy(askDelete = s.services.first()) }
        classToTest.delete()
        settle { sent.size == 2 }
        assertThat(sent.last()).isEqualTo("DELETE doctors/me/services/2")
    }

    @Test
    fun `a stored fractional UZS price goes back unchanged when its row isn't edited`() {
        val classToTest = loaded()
        val odd =
            classToTest.state.value.services[0].copy(
                prices = listOf(shifa.doctor.data.model.ServicePrice(12550, "UZS")),
            )
        classToTest.open(odd)
        classToTest.edit { it.copy(title = "Follow-up") }
        classToTest.save()
        settle { !classToTest.state.value.editing }
        assertThat(sent.single()).contains(""""amountMinor":12550""")
    }

    @Test
    fun `a delete of a service already gone counts as done, and closes only its own editor`() {
        val classToTest = loaded()
        deleteStatus = HttpStatusCode.NotFound
        classToTest.open(classToTest.state.value.services[0])
        classToTest.ui { s -> s.copy(askDelete = s.services.first()) }
        classToTest.delete()
        settle { classToTest.state.value.services.size == 1 }
        assertThat(classToTest.state.value.editing).isFalse()
    }

    @Test
    fun `groups - delete, then create and reorder with the list's order, then everything reloads`() {
        val classToTest = loaded()
        classToTest.openGroups()
        assertThat(classToTest.state.value.groupsDraft?.map { it.name }).containsExactly("Basics", "Ortho").inOrder()
        classToTest.editGroups {
            it.reversed().filter {
                    g ->
                g.name == "Ortho"
            } + shifa.doctor.data.model.ServiceGroup(name = "")
        }
        classToTest.saveGroups()
        assertThat(classToTest.state.value.groupErrors).containsExactly(1) // a new group needs a name
        classToTest.editGroups { it.mapIndexed { i, g -> if (i == 1) g.copy(name = " Implants ") else g } }
        classToTest.saveGroups()
        settle { !classToTest.state.value.editing }
        assertThat(sent).containsExactly(
            "DELETE doctors/me/service-groups/4",
            "PATCH doctors/me/service-groups/5 {\"name\":\"Ortho\",\"sortOrder\":1}",
            "POST doctors/me/service-groups {\"name\":\"Implants\",\"sortOrder\":2}",
        ).inOrder()
    }

    @Test
    fun `a retry after a partial failure doesn't delete or recreate a group that was created`() {
        val classToTest = loaded()
        classToTest.openGroups()
        failGroupWrite = 1 // the POST of "A" succeeds (id 77), the PATCH after it fails
        classToTest.editGroups {
            listOf(shifa.doctor.data.model.ServiceGroup(name = "A")) +
                it.map {
                        g ->
                    g.copy(name = g.name + "!")
                }
        }
        classToTest.saveGroups()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-g")
        assertThat(classToTest.state.value.groupsDraft?.first()?.id).isEqualTo(77)
        failGroupWrite = -1
        sent.clear()
        classToTest.saveGroups()
        settle { !classToTest.state.value.editing }
        assertThat(sent.none { it.startsWith("POST") || it.startsWith("DELETE") }).isTrue()
    }
}
