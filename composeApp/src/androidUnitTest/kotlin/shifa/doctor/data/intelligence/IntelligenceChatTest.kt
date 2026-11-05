@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.intelligence

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

// Flutter's test/intelligence_chat_models_test.dart, then the store (intelligence_chat_store.dart semantics + the Fix).
class IntelligenceChatTest {
    private val at = Instant.parse("2026-10-18T12:00:00Z")

    private fun msg(
        role: String,
        content: String,
        tool: String? = null,
        card: JsonObject? = null,
    ) = ChatMessage(role, content, at, tool, card)

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
        JsonObject(
            pairs.associate { (k, v) ->
                k to
                    when (v) {
                        is JsonObject, is JsonArray -> v as kotlinx.serialization.json.JsonElement
                        is List<*> -> JsonArray(v.map { it as JsonObject })
                        is Number -> JsonPrimitive(v)
                        else -> JsonPrimitive(v as String?)
                    }
            },
        )

    @Test
    fun `Uzbek date fragments stay Uzbek instead of flipping to English`() {
        assertThat(ChatTitles.detectReplyLanguage("24-oktabr soat 8:00 ga")).isEqualTo("UZ")
        assertThat(ChatTitles.detectReplyLanguage("8:30 to'g'ri keladi.")).isEqualTo("UZ")
        assertThat(ChatTitles.detectReplyLanguage("oktyabr 8:30 ga")).isEqualTo("UZ")
        assertThat(ChatTitles.detectReplyLanguage("8:30")).isNull()
        assertThat(ChatTitles.detectReplyLanguage("What is my schedule for today")).isEqualTo("EN")
        assertThat(ChatTitles.detectReplyLanguage("Сколько приёмов сегодня?")).isEqualTo("RU")
        assertThat(ChatTitles.detectReplyLanguage("Бугун қабул борми?")).isEqualTo("UZ")
    }

    @Test
    fun `Uzbek Salom Shifa is a wake phrase and strips like Hey Shifa`() {
        assertThat(ChatTitles.containsWakePhrase("Salom Shifa")).isTrue()
        assertThat(ChatTitles.containsWakePhrase("salom shifa")).isTrue()
        assertThat(ChatTitles.containsWakePhrase("Assalomu alaykum, Shifa")).isTrue()
        assertThat(ChatTitles.containsWakePhrase("Салом Шифа")).isTrue()
        assertThat(ChatTitles.containsWakePhrase("Hey Shifa")).isTrue()
        assertThat(ChatTitles.containsWakePhrase("hey she fa")).isTrue()
        assertThat(ChatTitles.containsWakePhrase("bugun bemorlar")).isFalse()
        assertThat(ChatTitles.stripWakePhrase("Salom Shifa")).isEmpty()
        assertThat(ChatTitles.stripWakePhrase("Salom Shifa, bugun bo'sh slotlar")).isEqualTo("bugun bo'sh slotlar")
        assertThat(ChatTitles.stripWakePhrase("Привет Шифа, расписание")).isEqualTo("расписание")
    }

    @Test
    fun `starter prompts and wake phrases become short titles`() {
        assertThat(ChatTitles.shortTitle("Summarize my schedule for today.")).isEqualTo("Today's schedule")
        assertThat(ChatTitles.shortTitle("Hey Shifa, summarize my schedule for today")).isEqualTo("Today's schedule")
        assertThat(ChatTitles.shortTitle("Salom Shifa, summarize my schedule for today")).isEqualTo("Today's schedule")
        assertThat(ChatTitles.shortTitle("What is my schedule today?")).isEqualTo("Today's schedule")
        assertThat(ChatTitles.shortTitle("Help me find a patient.")).isEqualTo("Find a patient")
        assertThat(ChatTitles.shortTitle("Help me manage an appointment — find a patient and propose free slots."))
            .isEqualTo("Manage appointments")
        assertThat(ChatTitles.shortTitle("Помогите найти пациента.")).isEqualTo("Найти пациента")
        assertThat(
            ChatTitles.shortTitle("Klinik qaydlarimni koʻrib chiqishga yordam bering"),
        ).isEqualTo("Klinik qaydlar")
    }

    @Test
    fun `long titles trim on a word and previews use the latest message`() {
        val long = "Please help me find an alternative time for the consultation next week in the afternoon"
        val title = ChatTitles.shortTitle(long)
        assertThat(title).endsWith("…")
        assertThat(title.length).isLessThan(long.length)
        assertThat(title).isEqualTo("Please help me find an alternative time for the…")

        val session =
            ChatSession(
                "1",
                ChatSession.UNTITLED,
                Instant.parse("2026-10-18T00:00:00Z"),
                at,
                listOf(
                    msg("user", "Summarize my schedule for today."),
                    msg("assistant", "You have three visits this afternoon."),
                ),
            )
        assertThat(session.displayTitle).isEqualTo("Today's schedule")
        assertThat(session.preview).isEqualTo("You have three visits this afternoon.")
    }

    @Test
    fun `a renamed title is kept when messages change`() {
        val session =
            ChatSession(
                "1",
                "Appointment rescheduling",
                at,
                at,
                listOf(msg("user", "Hey Shifa how are you")),
                titleCustom = true,
            )
        val restored = ChatSession.decodeList(ChatSession.encodeList(listOf(session)), at).single()
        assertThat(restored).isEqualTo(session)
        assertThat(restored.displayTitle).isEqualTo("Appointment rescheduling")
        val updated = restored.copy(messages = restored.messages + msg("assistant", "I can help with that visit."))
        assertThat(updated.displayTitle).isEqualTo("Appointment rescheduling")
        assertThat(updated.preview).isEqualTo("I can help with that visit.")
    }

    @Test
    fun `patient link comes from a patient card, not the day schedule`() {
        val schedule =
            ChatSession(
                "s",
                ChatSession.UNTITLED,
                at,
                at,
                listOf(
                    msg("user", "Summarize my schedule for today."),
                    msg(
                        "assistant",
                        "Three visits today.",
                        "get_today_schedule",
                        obj(
                            "cardType" to "schedule",
                            "appointments" to listOf(obj("patientId" to 4, "patientName" to "Ada Lovelace")),
                        ),
                    ),
                ),
            )
        assertThat(schedule.patientLinked).isFalse()

        val search =
            ChatSession(
                "p",
                ChatSession.UNTITLED,
                at,
                at,
                listOf(
                    msg("user", "Help me find a patient."),
                    msg(
                        "assistant",
                        "Found Ada Lovelace.",
                        "search_patients",
                        obj(
                            "cardType" to "patient_search",
                            "matches" to listOf(obj("patientId" to 9, "patientName" to "Ada Lovelace")),
                        ),
                    ),
                ),
            )
        assertThat(search.patientLinked).isTrue()
        assertThat(search.patientMark.name).isEqualTo("Ada Lovelace")
        assertThat(search.matchesQuery("lovelace")).isTrue()
        assertThat(search.matchesQuery("schedule")).isFalse()

        val placeholder = obj("cardType" to "propose_appointment", "patientId" to 3, "patientName" to "Patient")
        assertThat(ChatContext.isPatientLinked(placeholder, "propose_appointment")).isTrue()
        assertThat(ChatContext.patientName(placeholder, "propose_appointment")).isNull()
    }

    @Test
    fun `older chats without titleCustom still decode, a bad chat or message is skipped`() {
        val raw =
            """[{"id":"old","title":"hey Shifa","createdAt":"2026-10-01T00:00:00.000Z","updatedAt":"2026-10-01T00:00:00.000Z",
            "messages":[{"role":"user","content":"how are you","at":"2026-10-01T00:00:00.000Z"},{"role":["x"]}]},
            {"id":"new","updatedAt":"2026-10-02T00:00:00Z","messages":[{"content":"hi","cardPayload":[1]}]},"junk"]"""
        val sessions = ChatSession.decodeList(raw, at)
        assertThat(sessions.map { it.id }).containsExactly("new", "old").inOrder()
        val old = sessions[1]
        assertThat(old.titleCustom).isFalse()
        assertThat(old.displayTitle).isEqualTo("how are you")
        assertThat(old.messages).hasSize(1)
        // A non-object card is dropped; a missing role is the doctor's.
        assertThat(sessions[0].messages.single().cardPayload).isNull()
        assertThat(sessions[0].messages.single().isUser).isTrue()
        assertThat(ChatSession.decodeList("not json", at)).isEmpty()
    }

    @Test
    fun `the store opens on an empty chat, keeps its semantics and is per doctor`() {
        val settings = MapSettings()
        var doctor = "7"
        var clock = at
        val store = ChatSessionStore(settings, { doctor }, { clock })
        store.ensureLoaded()
        val welcome = store.state.value.active!!
        assertThat(welcome.messages).isEmpty()

        clock = Instant.parse("2026-10-18T12:05:00Z")
        store.replaceMessages(welcome.id, listOf(msg("user", "Help me find a patient.")))
        assertThat(settings.getStringOrNull("shifa_intelligence_chats_v1:7")).contains("Help me find a patient.")

        // A cold start opens a new empty chat, the history kept.
        val cold = ChatSessionStore(settings, { doctor }, { clock })
        cold.ensureLoaded()
        assertThat(cold.state.value.sessions).hasSize(2)
        assertThat(cold.state.value.active!!.messages).isEmpty()
        assertThat(cold.state.value.active!!.id).isNotEqualTo(welcome.id)

        // goToWelcome reuses the empty chat; rename keeps the order; delete of the open one opens the newest left.
        cold.goToWelcome()
        assertThat(cold.state.value.sessions).hasSize(2)
        cold.rename(welcome.id, "  Find Ada ")
        assertThat(cold.state.value.sessions.first { it.id == welcome.id }.displayTitle).isEqualTo("Find Ada")
        cold.rename(welcome.id, "  ")
        assertThat(cold.state.value.sessions.first { it.id == welcome.id }.title).isEqualTo("Find Ada")
        val open = cold.state.value.activeId!!
        cold.delete(open)
        assertThat(cold.state.value.activeId).isEqualTo(welcome.id)
        cold.delete(welcome.id)
        assertThat(cold.state.value.sessions).hasSize(1)
        assertThat(cold.state.value.active!!.messages).isEmpty()

        // Another doctor signing in reads their own chats.
        doctor = "8"
        cold.ensureLoaded()
        assertThat(cold.state.value.sessions).hasSize(1)
        assertThat(settings.hasKey("shifa_intelligence_chats_v1:8")).isTrue()
    }

    @Test
    fun `the store keeps at most 50 chats, never dropping the open one`() {
        var clock = at
        val store = ChatSessionStore(MapSettings(), { "1" }, { clock })
        store.ensureLoaded()
        val first = store.state.value.activeId!!
        store.replaceMessages(first, listOf(msg("user", "first")))
        repeat(60) {
            clock = Instant.fromEpochMilliseconds(clock.toEpochMilliseconds() + 60_000)
            val c = store.create()
            store.replaceMessages(c.id, listOf(msg("user", "chat $it")))
        }
        assertThat(store.state.value.sessions).hasSize(ChatSessionStore.MAX_SESSIONS)
        assertThat(store.state.value.sessions.map { it.id }.toSet()).hasSize(ChatSessionStore.MAX_SESSIONS)
        assertThat(store.state.value.sessions.none { it.id == first }).isTrue()
        // A new chat made at the same instant as the oldest ones is still kept and open.
        clock = at
        val made = store.create()
        assertThat(store.state.value.activeId).isEqualTo(made.id)
        assertThat(store.state.value.sessions.any { it.id == made.id }).isTrue()
        assertThat(store.state.value.sessions).hasSize(ChatSessionStore.MAX_SESSIONS)
    }

    @Test
    fun `review fixes - no-break spaces, late turns, list copies, chats without ids`() {
        // Dart's \s: a no-break space still separates the greeting and the name.
        assertThat(ChatTitles.stripWakePhrase("Привет\u00A0Шифа, расписание")).isEqualTo("расписание")
        assertThat(ChatTitles.shortTitle("Help\u00A0me find a patient.")).isEqualTo("Find a patient")

        val store = ChatSessionStore(MapSettings(), { "1" }, { at })
        store.ensureLoaded()
        val open = store.state.value.activeId!!
        // A turn for a chat that is gone changes nothing.
        store.replaceMessages("gone", listOf(msg("user", "late")))
        assertThat(store.state.value.activeId).isEqualTo(open)
        assertThat(store.state.value.active).isNotNull()
        // The stored messages are a copy.
        val mine = mutableListOf(msg("user", "first"))
        store.replaceMessages(open, mine)
        mine.clear()
        assertThat(store.state.value.active!!.messages).hasSize(1)

        // Chats stored without an id each get their own, so the cap and delete act on one.
        val raw = (1..51).joinToString(",", "[", "]") { "{}" }
        val decoded = ChatSession.decodeList(raw, at)
        assertThat(decoded.map { it.id }.toSet()).hasSize(51)
        val settings = MapSettings().apply { putString("shifa_intelligence_chats_v1:1", raw) }
        val capped = ChatSessionStore(settings, { "1" }, { at })
        capped.ensureLoaded()
        assertThat(capped.state.value.sessions).hasSize(ChatSessionStore.MAX_SESSIONS)
    }
}
