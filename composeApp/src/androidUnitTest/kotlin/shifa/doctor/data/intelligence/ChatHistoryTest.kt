@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.intelligence

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.model.AiMessage

// The history the backend's prompt reads (Flutter `_assistantContentForApi`, `_focusPatientIdFromCards`): golden JSON.
class ChatHistoryTest {
    private val at = Instant.parse("2026-10-24T08:00:00Z")

    private fun card(json: String): JsonObject = apiJson.parseToJsonElement(json).jsonObject

    private fun user(text: String) = ChatMessage(ChatMessage.ROLE_USER, text, at)

    private fun ai(
        text: String,
        tool: String? = null,
        card: String? = null,
    ) = ChatMessage(ChatMessage.ROLE_ASSISTANT, text, at, tool, card?.let(::card))

    private val thread =
        listOf(
            user("Who do I see tomorrow?"),
            ai(
                " You have two visits tomorrow. ",
                "list_patient_appointments",
                """{"cardType":"patient_appointments","patientId":12,"queryContext":{"range":"TOMORROW","patientName":"Jasur Karimov"},"appointments":[{"appointmentId":101,"patientName":"Jasur"},{"appointmentId":102}]}""",
            ),
            user("Cancel the second one"),
            ai(
                "I can cancel 1 upcoming visit: Jasur Karimov. Say yes to cancel it. Nothing has been cancelled yet.",
                "manage_booking_workflow",
                """{"cardType":"cancel_preview","appointment":{"appointmentId":102.0},"actionContext":{"type":"CANCEL","token":"ct_7f3"}}""",
            ),
            user("Open my next visit"),
            ai(
                "",
                "open_appointment",
                """{"cardType":"open_appointment","actionContext":{"type":"FOCUS","appointmentId":103}}""",
            ),
        )

    @Test
    fun `the history matches the golden JSON, tags byte for byte`() {
        val sent = ChatHistory.messages(thread, "yes")
        val golden =
            """[{"role":"user","content":"Who do I see tomorrow?"},""" +
                """{"role":"assistant","content":"You have two visits tomorrow.\n<appointment_query>{\"range\":\"TOMORROW\",\"patientName\":\"Jasur Karimov\"}</appointment_query>\n<appointment_list>101,102</appointment_list>"},""" +
                """{"role":"user","content":"Cancel the second one"},""" +
                """{"role":"assistant","content":"I can cancel 1 upcoming visit: Jasur Karimov. Say yes to cancel it. Nothing has been cancelled yet.\n<appointment_list>102</appointment_list>\n<appointment_action>ct_7f3</appointment_action>"},""" +
                """{"role":"user","content":"Open my next visit"},""" +
                """{"role":"assistant","content":"<appointment_focus>103</appointment_focus>"},""" +
                """{"role":"user","content":"yes"}]"""
        assertThat(apiJson.encodeToString(ListSerializer(AiMessage.serializer()), sent)).isEqualTo(golden)
    }

    @Test
    fun `the focus patient prefers a clear search match over a later proposal`() {
        assertThat(ChatHistory.focusPatientId(thread)).isEqualTo(12)
        val search =
            listOf(
                user("Find Karimov"),
                ai(
                    "Two found.",
                    "search_patients",
                    """{"cardType":"patient_search","matches":[{"patientId":7,"patientName":"Jasur Karimov"},{"patientId":8,"patientName":"Malika Karimova"}]}""",
                ),
                user("Jasur Karimov, book him tomorrow"),
                ai("Here are times.", "propose_appointment", """{"cardType":"propose_appointment","patientId":8}"""),
            )
        // Several matches: the one whose name words the doctor said; it beats the later proposal for someone else.
        assertThat(ChatHistory.focusPatientId(search)).isEqualTo(7)
        // Nobody named: the proposal's patient.
        assertThat(ChatHistory.focusPatientId(search.take(2) + user("the first") + search[3])).isEqualTo(8)
        // Clinic-wide cards never focus a patient.
        assertThat(
            ChatHistory.focusPatientId(
                listOf(
                    user("My day?"),
                    ai(
                        "Busy.",
                        "get_today_schedule",
                        """{"cardType":"schedule","appointments":[{"appointmentId":1,"patientId":4}]}""",
                    ),
                ),
            ),
        ).isNull()
    }
}
