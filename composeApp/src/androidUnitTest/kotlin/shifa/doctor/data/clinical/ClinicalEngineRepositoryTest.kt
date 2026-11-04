@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.clinical

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ClinicalChipSelection
import shifa.doctor.data.model.LocalizedName

class ClinicalEngineRepositoryTest {
    private val calls = mutableListOf<String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            calls += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery} ${req.body.toByteArray().decodeToString()}"
            val path = req.url.encodedPath.removePrefix("/api/clinical-engine/")
            when (path) {
                // Dev's shapes; one group without names (Flutter's cast threw and lost the list), one without an id.
                "groups" ->
                    respond(
                        """[{"groupId":"grp.pulpitis","sortOrder":2,"names":{"ru":"Пульпит","uz":"Pulpit","en":"Pulpitis"}},
                           {"groupId":"grp.caries","sortOrder":1},{"sortOrder":3,"names":{"ru":"x"}}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                "diseases/dis.003" ->
                    respond(
                        """{"diseaseId":"dis.003","number":3,"groupId":"grp.caries","slug":"moderate_caries","icdCodes":["K02.1"],"names":{"ru":"Средний кариес"},
                           "chips":[null,{"chipId":"dis.003.morbi.1","field":"morbi","labels":{"ru":"Реакция [X] месяцев"},"variables":["X"],"priority":50},{"chipId":7,"labels":"broken"}]}""",
                        HttpStatusCode.OK,
                        json,
                    )
                "occlusion/chips" ->
                    respond(
                        """[{"chipId":"occ.edge","angleClass":null,"icdHint":"K07.2","labels":{"ru":"Прямой"},"variables":[],"priority":20},{"chipId":"occ.norm","angleClass":"I","labels":{"ru":"Норма"},"priority":10}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                "shared/xray" ->
                    respond(
                        """[{"templateId":"xray.caries","type":"xray","field":"xray","labels":{"ru":"Очаг"},"priority":50,"correlates":["grp.caries"]}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                "synthesize" ->
                    respond(
                        """{"fields":{"morbi":"Реакция 3 месяцев","complaints":"Боль"}}""",
                        HttpStatusCode.OK,
                        json,
                    )
                "record-usage" -> respond("", HttpStatusCode.OK)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }
    private val classToTest = ClinicalEngineRepository { createHttpClient({ null }, engine) }

    @Test
    fun `names fall back to Russian per language, missing ones read as empty`() {
        val name = LocalizedName(ru = "Кариес", uz = "Karies", en = null)
        assertThat(name.forLocale("uz")).isEqualTo("Karies")
        assertThat(name.forLocale("uz-Cyrl")).isEqualTo("Karies")
        assertThat(name.forLocale("en")).isEqualTo("Кариес")
        assertThat(name.forLocale("ru")).isEqualTo("Кариес")
        assertThat(LocalizedName().forLocale("en")).isEmpty()
    }

    @Test
    fun `groups read leniently, in order, once per sign-in`() =
        runBlocking<Unit> {
            assertThat(
                classToTest.groups().getOrThrow().map { it.groupId },
            ).containsExactly("grp.caries", "grp.pulpitis").inOrder()
            classToTest.groups()
            assertThat(calls.count { it.contains("/groups?") }).isEqualTo(1)
            classToTest.clear()
            classToTest.groups()
            assertThat(calls.count { it.contains("/groups?") }).isEqualTo(2)
        }

    @Test
    fun `a disease with its chips and variables, occlusion chips by priority, shared templates as chips`() =
        runBlocking<Unit> {
            val disease = classToTest.disease("dis.003").getOrThrow()
            assertThat(disease.chips.single().variables).containsExactly("X")
            assertThat(disease.icdCodes).containsExactly("K02.1")
            val occlusion = classToTest.occlusionChips().getOrThrow()
            assertThat(occlusion.map { it.chipId }).containsExactly("occ.norm", "occ.edge").inOrder()
            assertThat(occlusion.last().icdHint).isEqualTo("K07.2")
            val xray = classToTest.sharedTemplates("xray").getOrThrow().single()
            assertThat(xray.chipId).isEqualTo("shared.xray.caries")
            assertThat(xray.correlates).containsExactly("grp.caries")
        }

    @Test
    fun `synthesize sends the locale, disease and picks, and usage is recorded by disease`() =
        runBlocking<Unit> {
            val fields =
                classToTest.synthesize(
                    "ru",
                    "dis.003",
                    listOf(ClinicalChipSelection("dis.003.morbi.1", mapOf("X" to "3"))),
                ).getOrThrow()
            assertThat(fields).containsEntry("morbi", "Реакция 3 месяцев")
            assertThat(calls.last()).isEqualTo(
                """POST /api/clinical-engine/synthesize? {"locale":"ru","diseaseId":"dis.003","selections":[{"chipId":"dis.003.morbi.1","variables":{"X":"3"}}]}""",
            )
            assertThat(classToTest.recordUsage("dis.003").isSuccess).isTrue()
            assertThat(calls.last()).isEqualTo("""POST /api/clinical-engine/record-usage? {"diseaseId":"dis.003"}""")
            classToTest.searchChips(" bo ", "uz")
            assertThat(calls.last()).startsWith("GET /api/clinical-engine/chips/search?q=bo&locale=uz&limit=15")
            classToTest.topDiagnoses()
            assertThat(calls.last()).startsWith("GET /api/clinical-engine/doctors/me/top-diagnoses?limit=5")
        }
}
