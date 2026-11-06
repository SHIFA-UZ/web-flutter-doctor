@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.forms

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinical.ClinicalEngineRepository
import shifa.doctor.data.model.ClinicalChipSelection
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicalPanelViewModelTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var hold: CompletableDeferred<Unit>? = null
    private var failDisease = false
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/clinical-engine/")
            calls += "${req.method.value} $path ${req.body.toByteArray().decodeToString()}"
            if (path == "synthesize") hold?.await()
            if (failDisease && path.startsWith("diseases/")) {
                return@MockEngine respond(
                    """{"status":500,"code":"X","requestId":"req-e"}""",
                    HttpStatusCode.InternalServerError,
                    json,
                )
            }
            val body =
                when (path) {
                    "groups" -> """[{"groupId":"grp.caries","sortOrder":1,"names":{"ru":"Кариес","en":"Caries"}}]"""
                    "groups/grp.caries/diseases" -> """[{"diseaseId":"dis.003","number":3,"groupId":"grp.caries","icdCodes":["K02.1"],"names":{"en":"Moderate caries"}}]"""
                    "diseases/dis.003" ->
                        """{"diseaseId":"dis.003","groupId":"grp.caries","icdCodes":["K02.1"],"names":{"en":"Moderate caries"},
                           "chips":[{"chipId":"dis.003.complaints.1","field":"complaints","labels":{"en":"Pain from sweets"}},
                                    {"chipId":"dis.003.morbi.1","field":"morbi","labels":{"en":"For [X] months"},"variables":["X"]}]}"""
                    "doctors/me/top-diagnoses" -> """[{"diseaseId":"dis.003","names":{"en":"Moderate caries"},"useCount":4}]"""
                    "occlusion/chips" -> """[{"chipId":"occ.norm","labels":{"en":"Normal bite"},"priority":10}]"""
                    "shared/mucosa" -> """[{"templateId":"mucosa.A","type":"mucosa","field":"oral_cavity","labels":{"en":"Normal"},"priority":10,"correlates":[]}]"""
                    "shared/xray" ->
                        """[{"templateId":"xray.perio","type":"xray","field":"xray","labels":{"en":"Perio"},"priority":10,"correlates":["grp.perio"]},
                           {"templateId":"xray.caries","type":"xray","field":"xray","labels":{"en":"Caries"},"priority":50,"correlates":["grp.caries"]}]"""
                    "diseases/dis.009" ->
                        """{"diseaseId":"dis.009","groupId":"grp.caries","names":{"en":"Other"},
                           "chips":[{"chipId":"dis.009.treatment_1.1","field":"treatment_1","labels":{"en":"Fluoride varnish"}}]}"""
                    "chips/search" -> """[{"chipId":"dis.009.treatment_1.1","field":"treatment_1","labels":{"en":"Fluoride varnish"}}]"""
                    "synthesize" -> """{"fields":{"complaints":"Pain from sweets.","morbi":"For 3 months."}}"""
                    else -> "[]"
                }
            respond(body, HttpStatusCode.OK, json)
        }
    private val created = mutableListOf<ClinicalPanelViewModel>()

    private val scheduler = TestCoroutineScheduler()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

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

    private fun opened(
        diseaseId: String? = null,
        selections: List<ClinicalChipSelection> = emptyList(),
    ) = ClinicalPanelViewModel(ClinicalEngineRepository { createHttpClient({ null }, engine) }) { "en" }.also {
        created += it
        it.start(diseaseId, selections)
        settle { !it.state.value.loading }
    }

    @Test
    fun `the catalogue loads once with the doctor's frequent diagnoses, and a group lists its diseases`() {
        val classToTest = opened()
        val s = classToTest.state.value
        assertThat(s.groups.single().groupId).isEqualTo("grp.caries")
        assertThat(s.top.single().diseaseId).isEqualTo("dis.003")
        assertThat(s.occlusion.single().chipId).isEqualTo("occ.norm")
        classToTest.start()
        assertThat(calls.count { it.startsWith("GET groups ") }).isEqualTo(1)
        classToTest.openPicker()
        classToTest.pickGroup(s.groups.single())
        settle { classToTest.state.value.groupDiseases != null }
        assertThat(classToTest.state.value.groupDiseases!!.single().icdCodes).containsExactly("K02.1")
    }

    @Test
    fun `a chosen disease picks all its chips and keeps occlusion picks, variables fill in, Apply hands the texts over once`() {
        val classToTest = opened()
        classToTest.toggle("occ.norm")
        classToTest.pickDisease("dis.003")
        settle { classToTest.state.value.disease != null }
        val s = classToTest.state.value
        assertThat(s.selected.keys).containsExactly("occ.norm", "dis.003.complaints.1", "dis.003.morbi.1")
        assertThat(s.picking).isFalse()
        classToTest.setVariable("dis.003.morbi.1", "X", "3")
        classToTest.toggle("dis.003.complaints.1")
        assertThat(classToTest.state.value.selected.keys).doesNotContain("dis.003.complaints.1")
        // The shared X-ray texts for the disease's group come first.
        assertThat(
            classToTest.state.value.shared(classToTest.state.value.xray).first().templateId,
        ).isEqualTo("xray.caries")
        assertThat(calls.none { it.startsWith("POST synthesize") }).isTrue()
        classToTest.apply()
        settle { classToTest.state.value.result != null }
        val result = classToTest.state.value.result!!
        assertThat(result.disease.diseaseId).isEqualTo("dis.003")
        assertThat(result.fields).containsEntry("morbi", "For 3 months.")
        assertThat(
            calls.last(),
        ).contains(
            "\"selections\":[{\"chipId\":\"occ.norm\",\"variables\":{}},{\"chipId\":\"dis.003.morbi.1\",\"variables\":{\"X\":\"3\"}}]",
        )
        classToTest.consumed()
        assertThat(classToTest.state.value.result).isNull()
    }

    @Test
    fun `the phrase search waits for two letters and a pause, and a found chip joins its field`() {
        val classToTest = opened()
        classToTest.pickDisease("dis.003")
        settle { classToTest.state.value.disease != null }
        classToTest.search("f")
        scheduler.advanceTimeBy(1_000)
        assertThat(calls.none { it.startsWith("GET chips/search") }).isTrue()
        classToTest.search("flu")
        scheduler.advanceTimeBy(300)
        assertThat(calls.none { it.startsWith("GET chips/search") }).isTrue()
        scheduler.advanceTimeBy(100)
        settle { classToTest.state.value.results.isNotEmpty() }
        val hit = classToTest.state.value.results.single()
        classToTest.addFromSearch(hit)
        val s = classToTest.state.value
        assertThat(s.chips("treatment_1").map { it.chipId }).containsExactly("dis.009.treatment_1.1")
        assertThat(s.selected.keys).contains("dis.009.treatment_1.1")
        assertThat(s.query).isEmpty()
    }

    @Test
    fun `a saved form's disease and picks come back as they were, without synthesising`() {
        val classToTest = opened("dis.003", listOf(ClinicalChipSelection("dis.003.morbi.1", mapOf("X" to "5"))))
        val s = classToTest.state.value
        assertThat(s.disease?.diseaseId).isEqualTo("dis.003")
        assertThat(s.selected).containsExactly("dis.003.morbi.1", mapOf("X" to "5"))
        assertThat(calls.none { it.startsWith("POST") }).isTrue()
    }

    @Test
    fun `an Apply still running when another disease is chosen never reaches the form`() {
        val classToTest = opened()
        classToTest.pickDisease("dis.003")
        settle { classToTest.state.value.disease != null }
        hold = CompletableDeferred()
        classToTest.apply()
        classToTest.pickDisease("dis.009")
        settle { classToTest.state.value.disease?.diseaseId == "dis.009" }
        hold!!.complete(Unit)
        Thread.sleep(200)
        assertThat(classToTest.state.value.result).isNull()
        assertThat(classToTest.state.value.applying).isFalse()
    }

    @Test
    fun `a saved form whose disease failed to load comes back on Retry, with a searched chip from another disease`() {
        failDisease = true
        val picks =
            listOf(
                ClinicalChipSelection("dis.003.morbi.1", mapOf("X" to "5")),
                ClinicalChipSelection("dis.009.treatment_1.1"),
            )
        val classToTest = opened("dis.003", picks)
        assertThat(classToTest.state.value.error).isNotNull()
        assertThat(classToTest.state.value.disease).isNull()
        failDisease = false
        classToTest.retry()
        settle { classToTest.state.value.disease != null && !classToTest.state.value.loading }
        val s = classToTest.state.value
        assertThat(s.disease?.diseaseId).isEqualTo("dis.003")
        assertThat(
            s.selected,
        ).containsExactly("dis.003.morbi.1", mapOf("X" to "5"), "dis.009.treatment_1.1", emptyMap<String, String>())
        // The searched chip shows again in its field, so it can be seen and removed.
        assertThat(s.chips("treatment_1").map { it.chipId }).containsExactly("dis.009.treatment_1.1")
    }
}
