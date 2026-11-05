package shifa.doctor.ui.intelligence

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSessionStore
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.intelDownloadEmpty

@OptIn(ExperimentalCoroutinesApi::class)
class IntelligenceViewModelTest {
    private var clock = Instant.parse("2026-11-05T05:00:00Z")
    private val store = ChatSessionStore(MapSettings(), { "1" }, { clock })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun opened() = IntelligenceViewModel(store, { clock }).also { it.load() }

    private fun talk(text: String) {
        clock = Instant.fromEpochMilliseconds(clock.toEpochMilliseconds() + 60_000)
        store.replaceMessages(
            store.state.value.activeId!!,
            listOf(
                ChatMessage(ChatMessage.ROLE_USER, text, clock),
                ChatMessage(
                    ChatMessage.ROLE_ASSISTANT,
                    "Ok.",
                    clock,
                    "search_patients",
                    JsonObject(mapOf("cardType" to JsonPrimitive("patient_search"))),
                ),
            ),
        )
    }

    @Test
    fun `cold start shows the welcome, back leaves from there and returns to it from a conversation`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.conversationOpen).isFalse()
        assertThat(classToTest.back()).isTrue()
        talk("Help me find a patient.")
        assertThat(classToTest.state.value.conversationOpen).isTrue()
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.conversationOpen).isFalse()
        // New conversation reuses the empty chat: no pile of "New chat"s.
        classToTest.newConversation()
        classToTest.newConversation()
        assertThat(store.state.value.sessions.count { it.messages.isEmpty() }).isEqualTo(1)
    }

    @Test
    fun `history searches every message, select opens a chat and closes the sheet`() {
        val classToTest = opened()
        talk("Find Ada Lovelace")
        classToTest.newConversation()
        talk("Summarize my schedule for today.")
        classToTest.history(true)
        classToTest.query("lovelace")
        assertThat(classToTest.state.value.listed.map { it.displayTitle }).containsExactly("Find Ada Lovelace")
        classToTest.select(classToTest.state.value.listed.single().id)
        assertThat(classToTest.state.value.history).isFalse()
        assertThat(classToTest.state.value.query).isEmpty()
        assertThat(classToTest.state.value.active!!.displayTitle).isEqualTo("Find Ada Lovelace")
    }

    @Test
    fun `rename keeps the time and refuses blank or unchanged titles, delete of the open chat opens the newest`() {
        val classToTest = opened()
        talk("Find Ada")
        val chat = classToTest.state.value.active!!
        classToTest.askRename(chat)
        assertThat(classToTest.state.value.renameText).isEqualTo("Find Ada")
        assertThat(classToTest.state.value.canRename).isFalse()
        classToTest.renameText("  ")
        assertThat(classToTest.state.value.canRename).isFalse()
        classToTest.renameText("Ada booking")
        classToTest.rename()
        val renamed = store.state.value.sessions.first { it.id == chat.id }
        assertThat(renamed.displayTitle).isEqualTo("Ada booking")
        assertThat(renamed.updatedAt).isEqualTo(chat.updatedAt)
        assertThat(classToTest.state.value.renaming).isNull()

        classToTest.askDelete(renamed)
        classToTest.delete()
        assertThat(store.state.value.sessions.none { it.id == chat.id }).isTrue()
        assertThat(classToTest.state.value.active).isNotNull()
    }

    @Test
    fun `export names the file and holds the conversation, an empty chat says there is nothing yet`() {
        val classToTest = opened()
        assertThat(classToTest.export()).isNull()
        assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelDownloadEmpty)
        talk("Find Ada")
        val file = classToTest.export()!!
        assertThat(file.fileName).startsWith("shifa-intelligence-chat_")
        assertThat(file.fileName).endsWith(".txt")
        assertThat(file.fileName).doesNotContain(":")
        val text = file.bytes.decodeToString()
        assertThat(text).startsWith("Shifa Intelligence conversation export\n")
        assertThat(text).contains("--- [0] USER @ ")
        assertThat(text).contains("toolName: search_patients")
        assertThat(text).contains("--- JSON (machine-readable) ---")
    }
}
