package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_calendar_22
import shifa.doctor.generated.resources.ic_calendar_bold_22
import shifa.doctor.generated.resources.ic_chat_22
import shifa.doctor.generated.resources.ic_chat_bold_22
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_download_16
import shifa.doctor.generated.resources.ic_gear_22
import shifa.doctor.generated.resources.ic_heart_16
import shifa.doctor.generated.resources.ic_home_22
import shifa.doctor.generated.resources.ic_home_bold_22
import shifa.doctor.generated.resources.ic_menu_22
import shifa.doctor.generated.resources.ic_pill_16
import shifa.doctor.generated.resources.ic_pin_16
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.ic_users_bold_22
import shifa.doctor.generated.resources.ic_video_16
import shifa.doctor.generated.resources.ic_wallet_22
import shifa.doctor.ui.navigation.NavBarItem
import shifa.doctor.ui.navigation.ShifaNavBar
import shifa.doctor.ui.theme.Gallery
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing

// `.sh-tag` tones, `.dr-status` tones (word, icon-only disc, menu), `.dr-alert` tones.
@Preview
@Composable
internal fun BadgesPreview() =
    Gallery {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        ) {
            ShifaTag("Main Clinic", type = ShifaTagType.Primary, icon = Res.drawable.ic_pin_16)
            ShifaTag("Video Call", icon = Res.drawable.ic_video_16)
            ShifaTag("30 minutes", type = ShifaTagType.Tertiary)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        ) {
            StatusTone.entries.forEach { StatusBadge(it.name, tone = it) }
            StatusBadge("Paid", tone = StatusTone.Brand, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            StatusBadge("Completed", tone = StatusTone.Brand, icon = Res.drawable.ic_check_16)
            StatusBadge("Pending payment", tone = StatusTone.Warn, icon = Res.drawable.ic_alert_16)
            StatusBadge("In progress", tone = StatusTone.Solid, icon = Res.drawable.ic_video_16)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        ) {
            AlertChip("Penicillin allergy", tone = AlertTone.Err, icon = Res.drawable.ic_alert_16)
            AlertChip("Hypertension", tone = AlertTone.Warn, icon = Res.drawable.ic_heart_16)
            AlertChip("Pregnant, 24 weeks", tone = AlertTone.Info, icon = Res.drawable.ic_pill_16)
            AlertChip("AI briefing available", tone = AlertTone.Ai, icon = Res.drawable.ic_sparkles_16, onClick = {})
            AlertChip("Last visit 12 Oct", tone = AlertTone.Neutral)
        }
    }

// Cards: `.sh-card`, avatar with the brand fallback, `.sh-status` discs, list rows, document tiles, task cards.
@Preview
@Composable
internal fun CardsPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            ShifaAvatar(null, ShifaSize.S48)
            ShifaStatusDisc(ShifaStatusTone.Ok, "Done")
            ShifaStatusDisc(ShifaStatusTone.Warn, "Attention")
            ShifaStatusDisc(ShifaStatusTone.Err, "Failed")
        }
        ShifaDocumentCard("Blood test results", "PDF · 2 pages · 18 Oct", pdf = true, onClick = {})
        ShifaDocumentCard(
            "Chest X-ray",
            "Image · 18 Oct",
            trailing = {
                ShifaIconButton(
                    Res.drawable.ic_download_16,
                    "Download",
                    {},
                    variant = ShifaIconButtonVariant.Secondary,
                )
            },
        )
        ShifaTaskCard("Blood pressure check", "Twice a day · due 18:00", completed = 4, total = 7)
        ShifaTaskCard("Amlodipine 5 mg", "Every morning", type = ShifaTaskType.Medicine, tag = {
            StatusBadge("Overdue", tone = StatusTone.Err)
        })
        ShifaTaskCard("Walk 30 minutes", "Daily", type = ShifaTaskType.Exercise)
        ShifaSection("Settings") {
            ShifaSettingsItem("Notifications", {}, icon = Res.drawable.ic_gear_22, trailing = { ShifaSwitch(true, {}) })
            ShifaSettingsItem("Language", {}, subtitle = "English")
            ShifaSettingsItem("Log out", {}, destructive = true, divider = false)
        }
    }

// `.dr-kpi` tones with deltas, selected, and `.dr-progress` tones.
@Preview
@Composable
internal fun KpisPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            KpiCard(
                "Patients today",
                "12",
                Res.drawable.ic_users_22,
                Modifier.weight(1f),
                delta = "+3",
                deltaNote = "vs last week",
            )
            KpiCard(
                "Revenue",
                "4.2M",
                Res.drawable.ic_wallet_22,
                Modifier.weight(1f),
                tone = KpiTone.Info,
                delta = "−8%",
                deltaUp = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            KpiCard("Unpaid", "3", Res.drawable.ic_wallet_22, Modifier.weight(1f), tone = KpiTone.Warn)
            KpiCard(
                "No-shows",
                "1",
                Res.drawable.ic_calendar_22,
                Modifier.weight(
                    1f,
                ),
                tone = KpiTone.Err,
                selected = true,
                onClick = {
                },
            )
        }
        ShifaProgressBar(0.6f)
        ShifaProgressBar(0.35f, tone = ProgressTone.Warn)
        ShifaProgressBar(0.9f, tone = ProgressTone.Err)
    }

// `.sh-section` variants and the page headers.
@Preview
@Composable
internal fun SectionsAndHeadersPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        ShifaMainHeader("Home", onNotifications = {}, notificationCount = 3)
        ShifaSubHeader("Patient", onBack = {}, onMore = {})
        ShifaSectionType.entries.forEach { type ->
            ShifaSection("Today's schedule", type = type, onMore = {}) {
                ShifaDocumentCard("Consultation notes", "PDF · 1 page", pdf = true)
            }
        }
    }

// `.dr-tabs` (pill, on a surface, block) and `.dr-underline`.
@Preview
@Composable
internal fun TabsPreview() =
    Gallery {
        SegmentedTabs(listOf(TabItem("All", 128), TabItem("Today", 12), TabItem("Favourites")), 1, {})
        SegmentedTabs(listOf(TabItem("Day"), TabItem("Week"), TabItem("Month")), 0, {}, onSurface = true)
        SegmentedTabs(listOf(TabItem("Male"), TabItem("Female"), TabItem("Not specified")), 2, {}, block = true)
        UnderlineTabs(listOf(TabItem("Overview"), TabItem("Documents", 4), TabItem("Forms"), TabItem("Plans")), 1, {})
    }

// `.sh-notif` (patient-style card kept for the inbox), `.sh-bubble-count`, `.sh-rating`.
@Preview
@Composable
internal fun NotificationsPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        ShifaNotificationType.entries.forEach {
            ShifaNotification("${it.name} title", "Dilnoza Yusupova booked 10:30 tomorrow.", it, time = "17:00")
        }
        ShifaNotification(
            "Unread",
            "New lab result for Jasur Tursunov.",
            ShifaNotificationType.Document,
            time = "17:00",
            unreadCount = 2,
        )
        ShifaMessageNotification("Malika Ergasheva", "Thank you, doctor!", "17:02", unreadCount = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            listOf(1, 20, 120).forEach { ShifaNotificationBubble(it) }
            ShifaRating(4)
        }
    }

// `.sh-chat`, `.sh-chatbar`, `.sh-wave`.
@Preview
@Composable
internal fun ChatPreview() =
    Gallery {
        ShifaChatBar("", {}, "Message", {})
        ShifaChatBar("Typed message", {}, "Message", {})
        ShifaChatBar("", {}, "Message", {}, recording = true)
        ShifaChatBubble("How are you feeling today?", ShifaChatBubbleType.Sent, time = "09:41")
        ShifaChatBubble("Much better, thank you.", ShifaChatBubbleType.Received, time = "09:43")
        ShifaChatImageBubble(received = true, time = "09:44") {
            Box(Modifier.size(ShifaSize.S120).background(ShifaColors.Bg.Media))
        }
        ShifaChatVoiceBubble(playing = true, position = 15, duration = 42, onToggle = {})
        ShifaChatThinkingBubble()
    }

// NavBar board: the doctor's five tabs, Home selected, Chat with its count.
@Preview
@Composable
internal fun NavBarPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        val items =
            listOf(
                NavBarItem("Home", Res.drawable.ic_home_22, Res.drawable.ic_home_bold_22),
                NavBarItem("Calendar", Res.drawable.ic_calendar_22, Res.drawable.ic_calendar_bold_22),
                NavBarItem("Patients", Res.drawable.ic_users_22, Res.drawable.ic_users_bold_22),
                NavBarItem(
                    "Chat",
                    Res.drawable.ic_chat_22,
                    Res.drawable.ic_chat_bold_22,
                    badge = 3,
                    description = "Chat, 3 unread",
                ),
                NavBarItem("More", Res.drawable.ic_menu_22),
            )
        ShifaNavBar(items, 0, {})
        ShifaNavBar(items, 3, {})
    }

// AI-02: an AI answer's markdown subset: headings with the brand bar, bullets, bold, a split long paragraph.
@Preview
@Composable
internal fun AiTextPreview() =
    Gallery {
        AiResponseText(
            "## Snapshot\nAdult with recurrent caries, last visit on **22 October**.\n\n" +
                "## Plan\n- Pain on **14**, plan restoration\n- Follow-up in **1 week**\n1. X-ray first",
        )
        AiResponseText(
            "The patient reports pain in the lower left molar for three days. Cold makes it worse and it wakes " +
                "her at night. Is it pulpitis? An X-ray and a pulp test should settle it before any treatment " +
                "is planned for the coming week.",
        )
    }
