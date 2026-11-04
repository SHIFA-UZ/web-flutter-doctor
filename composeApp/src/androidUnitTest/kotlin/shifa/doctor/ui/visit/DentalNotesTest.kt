@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.dental.DentalRepository
import shifa.doctor.data.forms.Dentition
import shifa.doctor.data.services.ServicesRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.dentalDocSaveFailed
import shifa.doctor.generated.resources.dentalLoadFailed

class DentalNotesTest {
    private val calls = mutableListOf<String>()
    private var saved = """{"version":2,"dentition":"permanent","teeth":{"16":[{"serviceId":2,"title":"Filling","amountMinor":60000000,"currency":"UZS"}]},"discountPercent":5,"notes":"Earlier"}"""
    private var readStatus = HttpStatusCode.OK
    private var saveStatus = HttpStatusCode.OK
    private val services =
        """[{"id":1,"title":"Free first consultation","isActive":true,"isFreeConsultation":true,"prices":[]},
           {"id":2,"title":"Filling","isActive":true,"groupName":"Fillings","groupSortOrder":2,"prices":[{"amountMinor":0,"currency":"UZS"},{"amountMinor":60000000,"currency":"uzs"}]},
           {"id":3,"title":"Crown","isActive":true,"groupName":"Prosthetics","groupSortOrder":1,"prices":[{"amountMinor":25000,"currency":"USD"}]},
           {"id":4,"title":"Old","isActive":false,"prices":[]}]"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            val body = req.body.toByteArray().decodeToString()
            calls += "${req.method.value} $path $body".trim()
            when {
                path.endsWith("dental-documentation") && req.method.value == "PUT" ->
                    respond("""{"status":${saveStatus.value}}""", saveStatus, json)
                path.endsWith("dental-documentation") -> respond(saved, readStatus, json)
                path == "doctors/me/services" -> respond(services, HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))

    @After fun tearDown() = scope.cancel()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(): DentalNotes =
        DentalNotes(
            7,
            scope,
            DentalRepository { createHttpClient({ null }, engine) },
            ServicesRepository { createHttpClient({ null }, engine) },
        ).also {
            it.load()
            settle { it.state.value.let { s -> (s.loaded || s.loadError != null) && s.services != null } }
        }

    private fun puts() = calls.filter { it.startsWith("PUT") }

    @Test
    fun `the saved record and the active services load, by group, priced as Flutter did`() {
        val s = opened().state.value
        assertThat(s.record.lines("16").single().title).isEqualTo("Filling")
        assertThat(s.discount).isEqualTo("5")
        assertThat(s.services?.map { it.id }).containsExactly(3L, 2L, 1L).inOrder()
        assertThat(puts()).isEmpty()
    }

    @Test
    fun `changes are saved once, 1200 ms after the last, as the version 2 payload`() {
        val classToTest = opened()
        val (filling, crown) = classToTest.state.value.services!!.let { it[1] to it[0] }
        classToTest.add("36", filling)
        scheduler.advanceTimeBy(1_000)
        classToTest.add("__general__", crown)
        classToTest.dentition(Dentition.Permanent)
        classToTest.discount("12,5%")
        classToTest.notes(" Lidocaine ")
        scheduler.advanceTimeBy(1_100)
        assertThat(puts()).isEmpty()
        scheduler.advanceTimeBy(200)
        settle { !classToTest.state.value.dirty }
        assertThat(classToTest.state.value.discount).isEqualTo("12,5")
        assertThat(puts()).containsExactly(
            """PUT appointments/7/dental-documentation {"version":2,"dentition":"permanent","teeth":{"16":[{"serviceId":2,"title":"Filling","amountMinor":60000000,"currency":"UZS"}],"36":[{"serviceId":2,"title":"Filling","amountMinor":60000000,"currency":"UZS"}],"__general__":[{"serviceId":3,"title":"Crown","amountMinor":25000,"currency":"USD"}]},"discountPercent":12.5,"notes":"Lidocaine"}""",
        )
        // Totals per currency, never the second one dropped.
        assertThat(classToTest.state.value.record.totals).containsExactly("UZS", 105_000_000L, "USD", 21_875L)
    }

    @Test
    fun `a record that couldn't be read can't be edited, so an empty one never replaces it`() {
        readStatus = HttpStatusCode.InternalServerError
        val classToTest = opened()
        assertThat(classToTest.state.value.loadError?.text).isEqualTo(Res.string.dentalLoadFailed)
        classToTest.notes("typed")
        classToTest.discount("10")
        assertThat(classToTest.state.value.dirty).isFalse()
        assertThat(runBlocking { classToTest.flush() }).isTrue()
        assertThat(puts()).isEmpty()
        // A body of another shape (not the empty `{}`) is a failure too, not an empty record to save over.
        readStatus = HttpStatusCode.OK
        val real = saved
        saved = "[]"
        assertThat(runBlocking { classToTest.loaded() }).isFalse()
        saved = """{"teeth":[{"title":"Filling"}]}"""
        assertThat(runBlocking { classToTest.loaded() }).isFalse()
        saved = real
        assertThat(runBlocking { classToTest.loaded() }).isTrue()
        assertThat(classToTest.state.value.record.notes).isEqualTo("Earlier")
    }

    @Test
    fun `leaving saves at once, a failure stays unsaved, a removed line and a discount over 100 are handled`() {
        val classToTest = opened()
        saveStatus = HttpStatusCode.InternalServerError
        classToTest.remove("16", 0)
        classToTest.discount("150")
        assertThat(classToTest.state.value.discountInvalid).isTrue()
        assertThat(runBlocking { classToTest.flush() }).isFalse()
        assertThat(classToTest.state.value.saveError?.text).isEqualTo(Res.string.dentalDocSaveFailed)
        saveStatus = HttpStatusCode.OK
        classToTest.append("From Shifa AI")
        assertThat(runBlocking { classToTest.flush() }).isTrue()
        assertThat(puts().last()).isEqualTo(
            """PUT appointments/7/dental-documentation {"version":2,"dentition":"permanent","teeth":{},"discountPercent":100.0,"notes":"Earlier\n\nFrom Shifa AI"}""",
        )
        // The flush cancelled the pending timer: nothing more is sent.
        scheduler.advanceTimeBy(5_000)
        assertThat(puts()).hasSize(2)
    }
}
