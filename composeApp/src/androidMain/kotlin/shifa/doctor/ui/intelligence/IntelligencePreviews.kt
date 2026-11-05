@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.data.intelligence.ChatStoreState
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.intelTookTooLong
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

private val threadChat =
    ChatSession(
        "t",
        ChatSession.UNTITLED,
        previewNow,
        previewNow,
        listOf(
            ChatMessage(ChatMessage.ROLE_USER, "Find Jasur Karimov", Instant.parse("2026-11-04T15:02:00Z")),
            ChatMessage(
                ChatMessage.ROLE_ASSISTANT,
                "I found **Jasur Karimov**, 48.\n- Last visit: 21 October\n- Allergy: lidocaine",
                Instant.parse("2026-11-04T15:02:10Z"),
                "search_patients",
                JsonObject(
                    mapOf(
                        "cardType" to JsonPrimitive("patient_search"),
                        "spokenHint" to JsonPrimitive("One patient matched."),
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
                ),
            ),
        ),
    )

@Composable
private fun ThreadFrame(turn: ThreadUiState) =
    IntelligenceContent(
        IntelligenceUiState(ChatStoreState(listOf(threadChat), "t", loaded = true)),
        IntelligenceActions(),
        previewNow,
    ) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
            ThreadList(
                threadChat,
                turn,
                ThreadActions(),
                androidx.compose.ui.Modifier,
                previewNow,
            ) { tool, card -> ToolCard(tool, card) }
        }
        Composer(threadChat, turn, ThreadActions())
    }

// INTEL-04: a saved turn (rendered reply, the card), a reply streaming in, the patient line, the composer.
@Composable
internal fun ThreadStreamingPreview() =
    PreviewTheme {
        ThreadFrame(
            ThreadUiState(
                asking = "Book him tomorrow at 10:00",
                askedAt = previewNow,
                streaming = "Tomorrow at 10:00 is free. Shall I book",
            ),
        )
    }

// INTEL-04: "Reviewing…" before the first token; then a failed turn with Retry and a typed draft.
@Composable
internal fun ThreadErrorPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.height(shifa.doctor.ui.theme.ShifaSize.S120),
            ) {
                ThreadList(
                    null,
                    ThreadUiState(asking = "What is my schedule today?", askedAt = previewNow),
                    ThreadActions(),
                    now = previewNow,
                )
            }
            ThreadFrame(
                ThreadUiState(
                    asking = "What is my schedule today?",
                    askedAt = previewNow,
                    error =
                        shifa.doctor.ui.emailcode.CodeBanner(
                            Res.string.intelTookTooLong,
                        ),
                    draft = "and tomorrow?",
                ),
            )
        }
    }

// INTEL-03: the welcome in the morning: the greeting with the doctor's name, four starters, five recent chats.
@Composable
internal fun WelcomePreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(shifa.doctor.ui.theme.ShifaColors.Bg.Surface)
                .padding(shifa.doctor.ui.theme.ShifaSize.S16),
        ) {
            WelcomeBody("Dr. med. Aziza Karimova", previewChats, "a", previewNow, {
            }, {}, {}, kotlinx.datetime.TimeZone.of("Asia/Tashkent"))
        }
    }

// INTEL-03: the first visit (no chats yet) before the profile has loaded: no name, no recent section.
@Composable
internal fun WelcomeFirstPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(shifa.doctor.ui.theme.ShifaColors.Bg.Surface)
                .padding(shifa.doctor.ui.theme.ShifaSize.S16),
        ) {
            WelcomeBody(null, emptyList(), null, Instant.parse("2026-11-05T14:00:00Z"), {
            }, {}, {}, kotlinx.datetime.TimeZone.of("Asia/Tashkent"))
        }
    }

private fun cardJson(json: String) = shifa.doctor.data.api.apiJson.parseToJsonElement(json) as JsonObject

private val previewCards =
    listOf(
        "get_today_schedule" to
            """{"cardType":"schedule","date":"2026-11-05","count":3,"appointments":[{"appointmentId":101,"patientName":"Dilnoza Rahimova","startLocal":"09:00","status":"COMPLETED"},{"appointmentId":102,"patientName":"Jasur Karimov","startLocal":"10:30","status":"CONFIRMED"},{"appointmentId":103,"patientName":"Malika Tursunova","startLocal":"14:00","status":"CANCELLED"}]}""",
        "list_patient_appointments" to
            """{"cardType":"patient_appointments","patientName":"Jasur Karimov","rangeLabel":"Upcoming","count":2,"appointments":[{"date":"2026-11-06","startLocal":"10:00","status":"CONFIRMED"},{"date":"2026-11-20","startLocal":"15:30","status":"REQUESTED"}]}""",
        "list_free_slots" to
            """{"cardType":"free_slots","date":"2026-11-06","slots":[{"startLocal":"09:00"},{"startLocal":"09:30"},{"startLocal":"11:00"},{"startLocal":"14:30"},{"startLocal":"16:00"}]}""",
        "search_patients" to
            """{"cardType":"patient_search","fuzzy":true,"query":"Karimv","matches":[{"patientId":7,"patientName":"Jasur Karimov","phone":"+998 93 555 12 34"},{"patientId":8,"patientName":"Malika Karimova"}]}""",
        "get_most_visited_patients" to """{"cardType":"most_visited","patients":[{"patientName":"Dilnoza Rahimova","visitCount":12},{"patientName":"Jasur Karimov","visitCount":7}]}""",
        "get_clinic_patterns" to """{"cardType":"clinic_patterns","patterns":[{"key":"m","label":"Spoken medication followed by a remote-care task","factType":"MEDICATION","count":14}]}""",
        "brief_patient" to """{"cardType":"patient_brief","briefing":"## Snapshot\nFollow-up after **pulpitis** treatment.\n- Allergy: penicillin"}""",
        "propose_appointment" to """{"cardType":"propose_appointment","spokenHint":"Tomorrow at 10:00 is free. Shall I book it?"}""",
        "list_patient_appointments" to """{"cardType":"patient_appointments","error":"Patient not found","spokenHint":"I could not find that patient.","appointments":[]}""",
    )

// INTEL-05: every read-only card (a cancelled visit muted), and a card INTEL-06 will draw, showing its spoken line.
@Composable
internal fun CardsPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(shifa.doctor.ui.theme.ShifaColors.Bg.Surface)
                .padding(shifa.doctor.ui.theme.ShifaSize.S16),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(
                    shifa.doctor.ui.theme.ShifaSize.S12,
                ),
        ) {
            previewCards.forEach { (tool, json) -> ToolCard(tool, cardJson(json)) }
        }
    }
