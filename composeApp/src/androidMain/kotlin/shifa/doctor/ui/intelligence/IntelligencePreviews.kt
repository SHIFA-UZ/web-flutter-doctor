@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.intelligence

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.data.intelligence.ChatStoreState
import shifa.doctor.ui.theme.PreviewTheme

internal val previewNow = Instant.parse("2026-11-05T09:12:00Z")

private fun chat(
    id: String,
    question: String,
    answer: String,
    minutesAgo: Long,
    patient: Boolean = false,
): ChatSession {
    val at = Instant.fromEpochMilliseconds(previewNow.toEpochMilliseconds() - minutesAgo * 60_000)
    val card =
        if (patient) {
            JsonObject(
                mapOf(
                    "cardType" to JsonPrimitive("patient_search"),
                    "matches" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "patientId" to JsonPrimitive(7),
                                        "patientName" to JsonPrimitive("Jasur Karimov"),
                                    ),
                                ),
                            ),
                        ),
                ),
            )
        } else {
            null
        }
    return ChatSession(
        id,
        ChatSession.UNTITLED,
        at,
        at,
        listOf(
            ChatMessage(
                ChatMessage.ROLE_USER,
                question,
                at,
            ),
            ChatMessage(
                ChatMessage.ROLE_ASSISTANT,
                answer,
                at,
                card?.let {
                    "search_patients"
                },
                card,
            ),
        ),
    )
}

internal val previewChats =
    listOf(
        ChatSession("empty", ChatSession.UNTITLED, previewNow, previewNow, emptyList()),
        chat("a", "Help me find a patient.", "Found Jasur Karimov, 48, last visit 21 October.", 4, patient = true),
        chat("b", "Summarize my schedule for today.", "You have five visits today; the first is at 09:00.", 190),
        chat(
            "c",
            "Which patients missed their blood pressure check-ins this week and need a call from me today?",
            "Two patients missed check-ins.",
            60 * 30,
        ),
        chat(
            "d",
            "Draft a follow-up plan for the lower left molar pain",
            "Here is a plan: X-ray, pulp test, review in 1 week.",
            60 * 24 * 9,
        ),
    )

private val previewStore = ChatStoreState(previewChats, "a", loaded = true)

// INTEL-02: the app bar in a conversation (options shown) and the history sheet, the open chat tinted.
@Composable
internal fun IntelligenceHistoryPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.height(shifa.doctor.ui.theme.ShifaSize.S80),
            ) {
                IntelligenceContent(IntelligenceUiState(previewStore), IntelligenceActions(), previewNow)
            }
            HistoryTray(IntelligenceUiState(previewStore), IntelligenceActions(), previewNow)
        }
    }

// INTEL-02: a search with no match; the welcome's app bar (no options).
@Composable
internal fun IntelligenceHistoryEmptyPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.height(shifa.doctor.ui.theme.ShifaSize.S80),
            ) {
                IntelligenceContent(
                    IntelligenceUiState(previewStore.copy(activeId = "empty")),
                    IntelligenceActions(),
                    previewNow,
                )
            }
            HistoryTray(IntelligenceUiState(previewStore, query = "zzz"), IntelligenceActions(), previewNow)
        }
    }
