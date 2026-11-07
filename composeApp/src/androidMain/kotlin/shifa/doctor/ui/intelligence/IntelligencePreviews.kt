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
import shifa.doctor.generated.resources.intelSlotConflict
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
            ) { tool, card, at -> ToolCard(tool, card, live = at?.live == true) }
        }
        Composer(threadChat, turn, ThreadActions(), canSpeech = true)
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
        "list_patient_appointments" to """{"cardType":"patient_appointments","error":"Patient not found","spokenHint":"I could not find that patient.","appointments":[]}""",
    )

// INTEL-05: every read-only card (a cancelled visit muted), and one that failed, showing its spoken line.
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

private const val SLOTS =
    """"slots":[{"startAt":"2026-11-06T05:00:00Z","startLocal":"10:00","slotMinutes":30,"locationId":1},""" +
        """{"startAt":"2026-11-06T05:30:00Z","startLocal":"10:30","slotMinutes":30,"locationId":1},""" +
        """{"startAt":"2026-11-06T09:00:00Z","startLocal":"14:00","slotMinutes":30,"locationId":1}]"""

// INTEL-06 (flows 03/14–15): the app books (Confirm, other times open), the backend's workflow waits for "yes", a
// move whose slot was taken (409), and a booking Shifa made.
@Composable
internal fun BookingCardsPreview() =
    CardColumn {
        ToolCard(
            "propose_appointment",
            cardJson(
                """{"cardType":"propose_appointment","patientId":12,"patientName":"Jasur Karimov","date":"2026-11-06",""" +
                    """"preferredSlot":{"startAt":"2026-11-06T05:00:00Z","startLocal":"10:00","slotMinutes":30},$SLOTS}""",
            ),
            ui = CardUi(showOther = true),
            live = true,
        )
        ToolCard(
            "manage_booking_workflow",
            cardJson(
                """{"cardType":"propose_appointment","workflowId":"w1","patientId":12,"patientName":"Jasur Karimov",""" +
                    """"date":"2026-11-06","preferredSlot":{"startAt":"2026-11-06T05:30:00Z","startLocal":"10:30"},$SLOTS}""",
            ),
            live = true,
        )
        ToolCard(
            "propose_reschedule",
            cardJson(
                """{"cardType":"propose_reschedule","appointmentId":102,"patientName":"Jasur Karimov",""" +
                    """"currentDateLabel":"5 Nov 2026","currentStartLocal":"10:30","date":"2026-11-06",$SLOTS}""",
            ),
            ui = CardUi(error = shifa.doctor.ui.emailcode.CodeBanner(Res.string.intelSlotConflict)),
            live = true,
        )
        ToolCard(
            "manage_booking_workflow",
            cardJson(
                """{"cardType":"propose_appointment","workflowId":"w1","workflowStatus":"COMPLETED","booked":true,""" +
                    """"patientName":"Jasur Karimov","date":"2026-11-06","requestedTime":"10:30",""" +
                    """"spokenHint":"Booked Jasur Karimov at 10:30 on 2026-11-06."}""",
            ),
        )
    }

// INTEL-06 (flow 03/16 target card): a cancellation waiting for the doctor's answer, then what it cancelled.
@Composable
internal fun CancelCardsPreview() =
    CardColumn {
        ToolCard(
            "cancel_appointments",
            cardJson(
                """{"cardType":"cancel_preview","status":"AWAITING_CONFIRMATION","actionContext":{"type":"CANCEL","token":"t"},""" +
                    """"count":2,"skipped":[{"appointmentId":101}],"appointments":[""" +
                    """{"patientName":"Jasur Karimov","whenLabel":"Friday 6 November at 10:30"},""" +
                    """{"patientName":"Malika Tursunova","whenLabel":"Friday 6 November at 14:00"}]}""",
            ),
            live = true,
        )
        ToolCard(
            "cancel_appointments",
            cardJson(
                """{"cardType":"cancel_result","status":"PARTIAL_SUCCESS","cancelled":[""" +
                    """{"patientName":"Jasur Karimov","whenLabel":"Friday 6 November at 10:30","status":"CANCELLED"}],""" +
                    """"failed":[{"patientName":"Malika Tursunova","whenLabel":"Friday 6 November at 14:00","status":"IN_PROGRESS"}]}""",
            ),
        )
    }

@Composable
private fun CardColumn(content: @Composable () -> Unit) =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(shifa.doctor.ui.theme.ShifaColors.Bg.Surface)
                .padding(shifa.doctor.ui.theme.ShifaSize.S16),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(
                    shifa.doctor.ui.theme.ShifaSize.S12,
                ),
        ) { content() }
    }

// INTEL-07 (flows 03/17–18): drafted facts with Document everything, documents with the backend's pick opened (a
// placeholder for the download), and a dental diagram.
@Composable
internal fun ClinicalCardsPreview() =
    CardColumn {
        ToolCard(
            "list_draft_visit_facts",
            cardJson(
                """{"cardType":"draft_visit_facts","appointmentId":102,"patientName":"Jasur Karimov","facts":[""" +
                    """{"factId":1,"factType":"MEDICATION","text":"Ibuprofen 400 mg after meals"},""" +
                    """{"factId":2,"factType":"SYMPTOM","text":"Pain in the lower left molar for three days"}]}""",
            ),
        )
        ToolCard(
            "list_patient_documents",
            cardJson(
                """{"cardType":"patient_documents","patientId":12,"autoOpenDocumentId":901,"documents":[""" +
                    """{"documentId":901,"title":"Complete blood count","date":"2026-10-22"},""" +
                    """{"documentId":903,"title":"Document","date":"2026-09-29"}]}""",
            ),
        )
        ToolCard(
            "open_dental_chart",
            cardJson(
                """{"cardType":"dental_chart","patientName":"Jasur Karimov","date":"6 Nov 2026","dentition":"permanent",""" +
                    """"documentation":{"teeth":{"36":[{"title":"Filling"}],"46":[{"title":"Cleaning"},{"title":"X-ray"}]}},""" +
                    """"toothServiceCounts":{"36":1,"46":2},"notes":"Recheck 36 in a month."}""",
            ),
        )
    }

// INTEL-08 (flow 03/19): a visit Shifa opened, and one that can't be opened (cancelled).
@Composable
internal fun OpenVisitCardsPreview() =
    CardColumn {
        listOf("CONFIRMED" to "inPerson", "CANCELLED" to "video").forEach { (status, route) ->
            ToolCard(
                "open_appointment",
                cardJson(
                    """{"cardType":"open_appointment","appointmentId":102,"route":"$route","appointment":{""" +
                        """"appointmentId":102,"patientName":"Jasur Karimov","location":"Shifa Demo Clinic",""" +
                        """"status":"$status","day":"2026-11-06","startLocal":"15:00","endLocal":"15:30"}}""",
                ),
            )
        }
    }

// INTEL-09 (board `DaIntel`, flows 03/04–05): the orb panel listening and speaking, the composer dictating, then idle
// with hands-free on, and the line below PRO.
@Composable
internal fun VoicePreview() =
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
            VoicePanel(
                VoiceUiState(
                    shifa.doctor.ui.widgets.VoicePhase.Listening,
                    level = 0.6f,
                    heard = true,
                    handsFree = true,
                ),
            ) {}
            VoicePanel(VoiceUiState(shifa.doctor.ui.widgets.VoicePhase.Speaking, handsFree = true)) {}
            val idle = ThreadUiState(draft = "")
            Composer(
                threadChat,
                idle,
                ThreadActions(),
                voice = VoiceUiState(shifa.doctor.ui.widgets.VoicePhase.Listening, level = 0.5f, dictating = true),
                canSpeech = true,
            )
            Composer(threadChat, idle, ThreadActions(), voice = VoiceUiState(handsFree = true), canSpeech = true)
            Composer(null, idle, ThreadActions(), canSpeech = false)
        }
    }
