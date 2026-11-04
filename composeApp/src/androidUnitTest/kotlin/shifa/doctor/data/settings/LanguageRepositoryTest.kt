package shifa.doctor.data.settings

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt

class LanguageRepositoryTest {
    private var sent = 0
    private var onRequest: () -> Unit = {}
    private val engine =
        MockEngine {
            sent++
            onRequest()
            respond("", HttpStatusCode.NoContent)
        }
    private val client = createHttpClient({ jwt(4_000_000_000L) }, engine)
    private val repo = LanguageRepository { client }

    @Test
    fun `each change is sent once`() =
        runTest {
            repo.sync("uz")
            repo.sync("uz")
            repo.sync("ru")
            assertThat(sent).isEqualTo(2)
        }

    @Test
    fun `a logout while a sync is in flight makes the next session sync again`() =
        runTest {
            onRequest = { repo.reset() } // logout lands before the reply
            repo.sync("uz")
            onRequest = {}
            repo.sync("uz")
            assertThat(sent).isEqualTo(2)
        }
}
