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
