@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.schedule

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
import shifa.doctor.data.locations.LocationsRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.locationDeleteBlocked
import shifa.doctor.generated.resources.locationSaved
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class LocationsViewModelTest {
    private var list = """[{"id":1,"label":"Main","clinic":"Shifa","locationCity":"Tashkent","isPrimary":true},{"id":2,"label":"Evening","isPrimary":false}]"""
    private val writes = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var deleteStatus = HttpStatusCode.NoContent
    private var reads = 0
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (req.method) {
                HttpMethod.Get -> {
                    reads++
                    respond(list, HttpStatusCode.OK, json)
                }
                HttpMethod.Delete -> {
                    writes += "DELETE ${req.url.encodedPath}"
                    if (deleteStatus == HttpStatusCode.NoContent) {
                        respond("", deleteStatus)
                    } else {
                        respond("""{"status":409,"code":"CONFLICT","requestId":"req-loc"}""", deleteStatus, json)
                    }
                }
                else -> {
                    writes += "${req.method.value} ${req.url.encodedPath} ${req.body.toByteArray().decodeToString()}"
                    respond("""{"id":3,"label":"x","isPrimary":false}""", HttpStatusCode.OK, json)
                }
            }
        }
    private val repo = LocationsRepository { createHttpClient({ null }, engine) }
    private val created = mutableListOf<LocationsViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() =
        LocationsViewModel(repo).also { vm ->
            vm.load()
            created += vm
            settle { vm.state.value.locations != null }
        }

    @Test
    fun `a label is required, blanks are sent as empty so the backend clears them`() {
        val classToTest = opened()
        classToTest.open(classToTest.state.value.locations!![0])
        classToTest.edit { it.copy(label = "  ") }
        classToTest.save()
        assertThat(classToTest.state.value.labelMissing).isTrue()
        assertThat(writes).isEmpty()
        classToTest.edit { it.copy(label = " Main Clinic ", clinic = "") }
        classToTest.save()
        settle { classToTest.state.value.draft == null }
        assertThat(writes.single()).isEqualTo(
            """PATCH /api/doctors/me/locations/1 {"label":"Main Clinic","clinic":"","address":"","locationStreetAddress":"","locationCity":"Tashkent","isPrimary":true}""",
        )
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.locationSaved)
        assertThat(reads).isAtLeast(2) // the list is read again (primary may have moved)
    }

    @Test
    fun `a new location posts, and the first one ever is primary`() {
        list = "[]"
        val classToTest = opened()
        classToTest.open(null)
        assertThat(classToTest.state.value.draft?.isPrimary).isTrue()
        classToTest.edit { it.copy(label = "Home") }
        classToTest.save()
        settle { writes.isNotEmpty() }
        assertThat(writes.single()).startsWith("POST /api/doctors/me/locations {\"label\":\"Home\"")
    }

    @Test
    fun `a refused delete says why in one message, back with changes asks first`() {
        val classToTest = opened()
        classToTest.open(classToTest.state.value.locations!![1])
        deleteStatus = HttpStatusCode.Conflict
        classToTest.delete()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.locationDeleteBlocked)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-loc")
        assertThat(classToTest.state.value.draft).isNotNull()

        classToTest.edit { it.copy(city = "Samarkand") }
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
    }

    @Test
    fun `a failed refresh over a shown list keeps the list and says so`() {
        val classToTest = opened()
        list = "broken"
        classToTest.load()
        settle { classToTest.state.value.error != null && !classToTest.state.value.refreshing }
        assertThat(classToTest.state.value.locations).hasSize(2)
        assertThat(classToTest.state.value.error).isNotNull()
    }

    @Test
    fun `the map pin goes into the form and is saved with the location, Back with a moved pin asks`() {
        val classToTest = opened()
        classToTest.open(classToTest.state.value.locations!![1])
        classToTest.openMap()
        assertThat(classToTest.state.value.map?.pin).isEqualTo(41.2995 to 69.2401) // no pin yet: Tashkent's centre
        classToTest.movePin(41.31, 69.28)
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.map?.asking).isTrue()
        classToTest.back() // closes the question, the map stays
        classToTest.confirmMap()
        assertThat(classToTest.state.value.map).isNull()
        classToTest.save()
        settle { writes.isNotEmpty() }
        assertThat(writes.single()).contains(""""latitude":41.31,"longitude":69.28""")
    }
}
