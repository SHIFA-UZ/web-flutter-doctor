@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.chat

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.UserSearchResult
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.utils.PracticeClock

private val now = Instant.parse("2026-11-05T09:12:00Z")
private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Asia/Tashkent") })

private val conversations =
    listOf(
        Conversation(
            1,
            11,
            "Dilnoza Rahimova",
            null,
            false,
            "Much better, thank you. The headaches are gone.",
            Instant.parse("2026-11-05T08:40:00Z"),
            2,
        ),
        Conversation(2, 12, "Jasur Karimov", null, false, "voice_1.m4a", Instant.parse("2026-11-04T07:00:00Z"), 120),
        Conversation(3, 21, "Dr. med. Sardor Nazarov", null, true, null, null, 0),
        Conversation(
            4,
            14,
            "Bekzod Aliyev-Abdurakhmanov Islomjon o'g'li",
            null,
            false,
            "Please send the X-ray when you have it.",
            Instant.parse("2026-10-28T10:00:00Z"),
            0,
        ),
    )

// CHAT-02: four conversations: unread (bold, a pill, 99+), yesterday, a colleague without messages, an old one.
@Composable
internal fun ChatListPreview() =
    PreviewTheme {
        ChatListContent(ChatListUiState(conversations), ChatListActions(), clock)
    }

// CHAT-02: the first read.
@Composable
internal fun ChatListLoadingPreview() = PreviewTheme { ChatListContent(ChatListUiState(), ChatListActions(), clock) }

// CHAT-02: no conversations yet.
@Composable
internal fun ChatListEmptyPreview() =
    PreviewTheme {
        ChatListContent(ChatListUiState(emptyList()), ChatListActions(), clock)
    }

// CHAT-02: "dil" typed: the people found.
@Composable
internal fun ChatListSearchPreview() =
    PreviewTheme {
        ChatListContent(
            ChatListUiState(
                conversations,
                typed = "dil",
                results =
                    listOf(
                        UserSearchResult(11, "Dilnoza Rahimova"),
                        UserSearchResult(21, "Dr. med. Dilshod Umarov", isDoctor = true),
                    ),
            ),
            ChatListActions(),
            clock,
        )
    }

private fun msg(
    id: Long,
    at: String,
    text: String,
    mine: Boolean,
    status: shifa.doctor.data.model.MessageStatus = shifa.doctor.data.model.MessageStatus.Sent,
    type: shifa.doctor.data.model.MessageType = shifa.doctor.data.model.MessageType.Text,
    file: String? = null,
    size: Long? = null,
    url: String? = null,
) = shifa.doctor.data.model.Message(
    id, 1, if (mine) 1 else 2, !mine, type, text, url, file, size, null, status, mine,
    Instant.parse(
        at,
    ),
)

private val thread =
    listOf(
        msg(
            1,
            "2026-11-04T10:00:00Z",
            "Good morning! How are you feeling after the new dose?",
            true,
            shifa.doctor.data.model.MessageStatus.Read,
        ),
        msg(2, "2026-11-05T03:30:00Z", "Much better, thank you. The headaches are gone.", false),
        msg(
            3,
            "2026-11-05T03:31:00Z",
            "",
            false,
            type = shifa.doctor.data.model.MessageType.Document,
            file = "blood_test_results.pdf",
            size = 248_000,
            url = "https://api.shifa.uz/chat-attachments/blood.pdf",
        ),
        msg(4, "2026-11-05T03:40:00Z", "Great. Please read this before Friday: https://shifa.uz/guide", true),
    )

// CHAT-03: yesterday and today, theirs and mine with ticks, a link, a document (its bubble comes with CHAT-05), a text on its way and one that failed.
@Composable
internal fun ThreadPreview() =
    PreviewTheme {
        ThreadContent(
            ThreadUiState(
                conversations[0],
                thread,
                listOf(
                    PendingMessage(1, "See you on Friday.", Instant.parse("2026-11-05T09:10:00Z")),
                    PendingMessage(2, "Can you send the X-ray?", Instant.parse("2026-11-05T09:11:00Z"), failed = true),
                ),
                draft = "Thank",
            ),
            ThreadActions(),
            clock,
        )
    }

// CHAT-03: a new conversation.
@Composable
internal fun ThreadEmptyPreview() =
    PreviewTheme {
        ThreadContent(ThreadUiState(conversations[0], emptyList()), ThreadActions(), clock)
    }

// CHAT-03: the first read.
@Composable
internal fun ThreadLoadingPreview() =
    PreviewTheme {
        ThreadContent(ThreadUiState(conversations[2]), ThreadActions(), clock)
    }

// CHAT-04: a photo the patient sent (no URL here: "Image not available"; on the phone the picture), a photo of mine
// on its way with the uploading strip.
@Composable
internal fun ThreadImagesPreview() =
    PreviewTheme {
        ThreadContent(
            ThreadUiState(
                conversations[0],
                listOf(
                    msg(10, "2026-11-05T03:30:00Z", "Here is the rash", false),
                    msg(11, "2026-11-05T03:31:00Z", "", false, type = shifa.doctor.data.model.MessageType.Image),
                ),
                listOf(
                    PendingMessage(
                        3,
                        "",
                        Instant.parse("2026-11-05T09:10:00Z"),
                        image = ByteArray(0),
                        fileName = "x.jpg",
                    ),
                ),
            ),
            ThreadActions(),
            clock,
        )
    }
