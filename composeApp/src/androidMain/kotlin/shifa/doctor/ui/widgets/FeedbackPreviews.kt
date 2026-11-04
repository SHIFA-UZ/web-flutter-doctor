@file:Suppress("ktlint:standard:max-line-length") // sample copy in previews

package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.ui.theme.Gallery
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize

private const val BODY = "Couldn't save the schedule. Check your connection and try again."

// `.sh-banner` tones, the offline strip (flow 01/19) and the language mini toggle (flow 01/15).
@Preview
@Composable
internal fun BannersPreview() =
    Gallery {
        ShifaBanner(BODY, Res.drawable.ic_alert_circle_16, ShifaBannerType.Error)
        ShifaBanner(
            "Your device's time zone differs from the schedule's.",
            Res.drawable.ic_alert_16,
            ShifaBannerType.Warning,
        )
        ShifaBanner("Schedule saved.", Res.drawable.ic_check_circle_16, ShifaBannerType.Success)
        OfflineStrip("You're offline. Showing saved data.")
        LanguageToggle("EN", "Language: English", {})
    }

// `.dr-toast` tones, with action and close.
@Preview
@Composable
internal fun ToastsPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        ShifaToast("Appointment booked for 10:30.", onClose = {})
        ShifaToast(BODY, tone = ToastTone.Err, action = "Retry")
        ShifaToast("Payment reminder sent.", tone = ToastTone.Info, action = "Undo", onClose = {})
        ShifaToast("Two slots overlap a block.", tone = ToastTone.Warn)
    }

// `.dr-empty` and the skeleton family.
@Preview
@Composable
internal fun EmptyAndSkeletonsPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        EmptyState(
            "No patients yet",
            Res.drawable.ic_users_22,
            body = "Patients you book or invite appear here.",
            action = { ShifaPrimaryButton("Add Patient", {}) },
        )
        ShifaListSkeleton(count = 2)
        ShifaChipsSkeleton()
        ShifaChatSkeleton()
    }

// DialogStates, phone: the destructive tray (tone disc, verb on the button, primary on top).
@Preview
@Composable
internal fun DialogDestructivePreview() =
    Gallery(background = ShifaColors.Bg.Overlay) {
        ShifaDialogSheet(
            "Delete this document?",
            "Delete",
            {},
            body = "“Blood test results, 18 October” will be removed from Jasur Tursunov’s record. This can’t be undone.",
            tone = DialogTone.Destructive,
            icon = Res.drawable.ic_trash_22,
            cancelText = "Cancel",
        )
    }

// DialogStates, phone: the form tray (body scrolls, actions stay put).
@Preview
@Composable
internal fun DialogFormPreview() =
    Gallery(background = ShifaColors.Bg.Overlay) {
        ShifaDialogSheet("Add Patient", "Add Patient", {}, cancelText = "Cancel") {
            val wide = Modifier.fillMaxWidth()
            ShifaInputField("", {}, "Full name", wide)
            ShifaInputField("", {}, "Phone number", wide)
            ShifaInputField("", {}, "Date of birth", wide)
        }
    }

// DialogStates: the AI briefing opened from the hero.
@Preview
@Composable
internal fun DialogBriefingPreview() =
    Gallery(background = ShifaColors.Bg.Overlay) {
        ShifaDialogSheet(
            "Visit Briefing",
            "Start Appointment",
            {},
            body = "Dilnoza Yusupova · Today, 10:30–11:00 · Video call",
            tone = DialogTone.Ai,
            icon = Res.drawable.ic_sparkles_22,
            cancelText = "Close",
        ) {
            AiInsightCard(
                InsightKind.Briefing,
                "From 3 documents",
                "",
                bullets = BRIEFING,
                disclaimer = DISCLAIMER,
            )
        }
    }

// `.sh-tray` (plain and the chat tray) and the pop-up.
@Preview
@Composable
internal fun TraysPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        ShifaChatTray({}, {}, suggestions = listOf("Summarise today", "Who needs follow-up")) {
            ShifaChatBar("", {}, "Ask Shifa AI", {})
        }
        ShifaTray("Block time", {}) { Box(Modifier.fillMaxWidth().height(ShifaSize.S120)) }
        ShifaPopUp("Pop-up header", {}) { Box(Modifier.fillMaxWidth().height(ShifaSize.S120)) }
    }

internal val BRIEFING =
    listOf(
        "Penicillin allergy recorded in her 2023 discharge letter.",
        "Home blood pressure readings have trended up over the last two weeks.",
        "She asked in chat whether her amlodipine dose should change.",
    )

internal const val DISCLAIMER =
    "Shifa AI provides general information only. It is not a substitute for professional medical advice, diagnosis, or treatment."
