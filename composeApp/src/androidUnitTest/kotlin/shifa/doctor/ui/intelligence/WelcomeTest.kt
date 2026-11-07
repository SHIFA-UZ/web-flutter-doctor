package shifa.doctor.ui.intelligence

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.data.intelligence.ChatTitles
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.goodAfternoon
import shifa.doctor.generated.resources.goodEvening
import shifa.doctor.generated.resources.goodMorning
import shifa.doctor.generated.resources.intelPromptFindPatient
import shifa.doctor.generated.resources.intelPromptSchedule
import java.io.File

// Flutter's test/intelligence_welcome_test.dart (prompts, recent rows), as data checks; the screenshot covers the look.
class WelcomeTest {
    private val at = Instant.parse("2026-11-05T05:00:00Z")

    @Test
    fun `the greeting follows the phone's hour`() {
        assertThat(greetingFor(11)).isEqualTo(Res.string.goodMorning)
        assertThat(greetingFor(12)).isEqualTo(Res.string.goodAfternoon)
        assertThat(greetingFor(16)).isEqualTo(Res.string.goodAfternoon)
        assertThat(greetingFor(17)).isEqualTo(Res.string.goodEvening)
    }

    @Test
    fun `the starter cards send the documented prompts`() {
        assertThat(starters).hasSize(4)
        assertThat(starters[0].prompt).isEqualTo(Res.string.intelPromptSchedule)
        assertThat(starters[1].prompt).isEqualTo(Res.string.intelPromptFindPatient)
    }

    @Test
    fun `every starter prompt in every language becomes its short title`() {
        val keys =
            listOf(
                "intelPromptSchedule",
                "intelPromptFindPatient",
                "intelPromptManageAppointments",
                "intelPromptClinicalNotes",
            )
        for (lang in listOf("en", "ru", "uz")) {
            val json = File("../docs/kmp-spec/strings/$lang.json").readText()
            for (k in keys) {
                val prompt = Regex("\"$k\": \"([^\"]+)\"").find(json)!!.groupValues[1]
                val title = ChatTitles.shortTitle(prompt)
                assertThat(title).isNotEqualTo(prompt)
                assertThat(title).isIn(ChatTitles.starterTitles.values)
            }
        }
    }

    @Test
    fun `recent shows the five newest chats with something in them`() {
        fun chat(
            id: String,
            minutes: Long,
            empty: Boolean = false,
        ) = ChatSession(
            id,
            ChatSession.UNTITLED,
            at,
            Instant.fromEpochMilliseconds(at.toEpochMilliseconds() + minutes * 60_000),
            if (empty) emptyList() else listOf(ChatMessage(ChatMessage.ROLE_USER, "q$id", at)),
        )
        val all = listOf(chat("e", 100, empty = true)) + (1..7).map { chat("$it", it.toLong()) }
        assertThat(recentChats(all).map { it.id }).containsExactly("7", "6", "5", "4", "3").inOrder()
    }
}
