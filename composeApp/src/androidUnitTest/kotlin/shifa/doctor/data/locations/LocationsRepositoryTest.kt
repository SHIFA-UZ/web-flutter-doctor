@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.locations

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.LocationRequest

class LocationsRepositoryTest {
    private val before = """[{"id":1,"label":"Main","isPrimary":true},{"id":2,"label":"Evening","isPrimary":false}]"""
    private var getHold = CompletableDeferred(Unit)
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (req.method) {
                HttpMethod.Get -> {
                    getHold.await()
                    respond(before, HttpStatusCode.OK, json)
                }
                HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                else -> respond("""{"id":2,"label":"Evening","isPrimary":true}""", HttpStatusCode.OK, json)
            }
        }
    private val repo = LocationsRepository { createHttpClient({ null }, engine) }

    @Test
    fun `a save is applied at once - the new primary demotes the old`() =
        runBlocking<Unit> {
            repo.refresh()
            repo.save(2, LocationRequest("Evening", isPrimary = true)).getOrThrow()
            assertThat(
                repo.locations.value!!.map { it.id to it.isPrimary },
            ).containsExactly(2L to true, 1L to false).inOrder()
        }

    @Test
    fun `a slow read started before a delete can't bring the deleted location back`() =
        runBlocking<Unit> {
            repo.refresh()
            getHold = CompletableDeferred()
            val slow = async { repo.refresh() } // will answer with location 1 still there
            repo.delete(1).getOrThrow()
            getHold.complete(Unit)
            slow.await()
            // The primary went: the next one is promoted, and the old answer (with 1) is dropped.
            assertThat(repo.locations.value!!.map { it.id to it.isPrimary }).containsExactly(2L to true)
        }
}
