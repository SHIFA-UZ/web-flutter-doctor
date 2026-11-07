@file:Suppress("ktlint:standard:max-line-length") // sample copy in previews

package shifa.doctor.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_calendar_22
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_clinic_16
import shifa.doctor.generated.resources.ic_clock_16
import shifa.doctor.generated.resources.ic_heart_16
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.generated.resources.ic_trend_16
import shifa.doctor.generated.resources.ic_video_16
import shifa.doctor.ui.theme.Gallery
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing

private enum class Briefing { Idle, Loading, Ready, Error }

// HeroStates, phone: the hero with its record open, so the briefing row shows; [started] reads "Now" and rejoins.
@Composable
private fun Hero(
    briefing: Briefing,
    started: Boolean = false,
) = NextPatientHero(
    eyebrow = "Next patient",
    countdown = if (started) "Now" else "in 3 min",
    started = started,
    name = "Dilnoza Yusupova",
    initials = "DY",
    meta = listOf(HeroMeta(Res.drawable.ic_video_16, "Video call"), HeroMeta(Res.drawable.ic_clock_16, "10:30–11:00")),
    recordLabel = "Medical record",
    flagCount = "3",
    recordOpen = true,
    recordToggleLabel = "Hide",
    onToggleRecord = {},
    primary = { ShifaPrimaryButton(if (started) "Rejoin Call" else "Start", {}, it, size = ShifaButtonSize.Compact) },
    secondary = {
        val loading = briefing == Briefing.Loading
        ShifaSecondaryButton(if (loading) "Preparing…" else "Briefing", {
        }, it, loading = loading, size = ShifaButtonSize.Compact)
    },
    flags = {
        AlertChip("Penicillin allergy", tone = AlertTone.Err, icon = Res.drawable.ic_alert_16)
        AlertChip("Hypertension", tone = AlertTone.Warn, icon = Res.drawable.ic_heart_16)
        AlertChip("Blood pressure rising", tone = AlertTone.Warn, icon = Res.drawable.ic_trend_16)
        when (briefing) {
            Briefing.Idle ->
                AlertChip("AI briefing available", tone = AlertTone.Ai, icon = Res.drawable.ic_sparkles_16, onClick = {
                })
            Briefing.Loading -> AlertChip("Preparing briefing…", tone = AlertTone.Neutral)
            Briefing.Ready ->
                AlertChip(
                    "AI briefing ready",
                    tone = AlertTone.Ai,
                    icon = Res.drawable.ic_check_16,
                    onClick = {},
                )
            Briefing.Error ->
                AlertChip("Briefing didn't load. Try again.", tone = AlertTone.Err, icon = Res.drawable.ic_alert_circle_16, onClick = {
                })
        }
    },
)

@Preview
@Composable
internal fun HeroIdlePreview() = Gallery(background = ShifaColors.Bg.Canvas) { Hero(Briefing.Idle) }

@Preview
@Composable
internal fun HeroLoadingPreview() = Gallery(background = ShifaColors.Bg.Canvas) { Hero(Briefing.Loading) }

@Preview
@Composable
internal fun HeroReadyPreview() = Gallery(background = ShifaColors.Bg.Canvas) { Hero(Briefing.Ready) }

@Preview
@Composable
internal fun HeroStartedPreview() = Gallery(background = ShifaColors.Bg.Canvas) { Hero(Briefing.Ready, started = true) }

@Preview
@Composable
internal fun HeroErrorPreview() = Gallery(background = ShifaColors.Bg.Canvas) { Hero(Briefing.Error) }

@Preview
@Composable
internal fun HeroEmptyPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        NextPatientHeroEmpty(
            "No more patients today",
            "Your next appointment is tomorrow at 09:00.",
            "Open Calendar",
            {},
        )
    }

private const val RISK_BODY = "Home readings averaged 152/95 over the last 14 days, up from 138/88."

// InsightStates: suggestion (actions + dismiss), risk (source on the right), briefing, loading, voice, outcome.
@Preview
@Composable
internal fun InsightSuggestionPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        AiInsightCard(
            InsightKind.Suggestion,
            "Jasur Tursunov is due for a follow-up",
            "From the visit plan",
            body = "His last visit was 6 weeks ago; the plan asked for a check in 4.",
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
                    ShifaPrimaryButton("Send Reminder", {}, size = ShifaButtonSize.Compact)
                    ShifaSecondaryButton("Book", {}, size = ShifaButtonSize.Compact)
                }
            },
            dismiss = { ShifaLink("Dismiss", {}, variant = ShifaLinkVariant.Quiet) },
        )
    }

@Preview
@Composable
internal fun InsightRiskPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        AiInsightCard(InsightKind.Risk, "Blood pressure rising", "From 14 readings", body = RISK_BODY)
    }

@Preview
@Composable
internal fun InsightBriefingPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        AiInsightCard(
            InsightKind.Briefing,
            "Visit briefing",
            "From 3 documents",
            bullets = BRIEFING,
            disclaimer = DISCLAIMER,
        )
    }

@Preview
@Composable
internal fun InsightLoadingPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        AiInsightSkeleton("Reading 3 documents…")
    }

@Preview
@Composable
internal fun InsightVoicePreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        listOf("Listening", "Thinking", "Speaking").forEach { phase ->
            VoiceSessionCard(phase, "“Summarise Dilnoza’s last three visits.”") {
                ShifaPrimaryButton("Stop", {}, size = ShifaButtonSize.Compact)
            }
        }
    }

@Preview
@Composable
internal fun InsightOutcomePreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        InsightOutcome("Reminder sent to Jasur Tursunov.", "Undo", {})
        InsightOutcome("Dismissed.", "Undo", {})
    }

// CopilotStates: docked empty with suggestions, streaming, answered (patient scope), minimised orb.
@Composable
private fun Copilot(
    messages: List<CopilotMessage> = emptyList(),
    streaming: String? = null,
    suggestions: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) = AiCopilot(
    title = "Shifa AI",
    subtitle = "Copilot",
    scope = "Dilnoza Yusupova",
    intro = "Ask about this patient's record, visits and documents.",
    disclaimer = DISCLAIMER,
    collapseDescription = "Minimise",
    onCollapse = {},
    composer = {
        CopilotComposer(
            field = { ShifaInputField("", {}, "Ask Shifa AI", Modifier.fillMaxWidth()) },
            micDescription = "Voice",
            sendDescription = "Send",
            onMic = {},
            onSend = {},
            sendEnabled = false,
        )
    },
    modifier = modifier,
    messages = messages,
    streaming = streaming,
    suggestions = suggestions,
)

private val QUESTION = CopilotMessage.User("What changed since her last visit?")

@Preview
@Composable
internal fun CopilotEmptyPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Copilot(suggestions = listOf("Summarise her record", "Recent lab results", "Draft a reply"))
    }

@Preview
@Composable
internal fun CopilotStreamingPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Copilot(listOf(QUESTION), streaming = "Since 12 October, her home blood pressure")
    }

@Preview
@Composable
internal fun CopilotAnsweredPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Copilot(listOf(QUESTION, CopilotMessage.Answer("Since 12 October", BRIEFING)))
    }

// CopilotStates "Phone · full-height tray": a long thread in a bounded sheet scrolls to the newest answer.
@Preview
@Composable
internal fun CopilotPhoneTrayPreview() =
    Gallery(background = ShifaColors.Bg.Overlay) {
        val answer = CopilotMessage.Answer("Since 12 October", BRIEFING)
        Copilot(List(3) { listOf(QUESTION, answer) }.flatten(), modifier = Modifier.height(ShifaSize.S320 * 2))
    }

@Preview
@Composable
internal fun CopilotMinimisedPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        AiOrbButton("Open Shifa AI", {})
    }

// TimelineStates, phone: now 10:27 (past dim, live halo + Start, free books on tap), after Start, empty day.
private fun timeline(started: Boolean) =
    listOf(
        TimelineEntry.Visit(
            "09:00",
            "09:30",
            "Malika Ergasheva",
            "ME",
            "Check-up",
            "In clinic",
            Res.drawable.ic_clinic_16,
            badge = { StatusBadge("Completed", tone = StatusTone.Brand, icon = Res.drawable.ic_check_16) },
            past = true,
            done = true,
        ),
        TimelineEntry.Visit(
            "10:30",
            "11:00",
            "Dilnoza Yusupova",
            "DY",
            "Follow-up",
            "Video call",
            Res.drawable.ic_video_16,
            badge =
                if (started) {
                    (
                        {
                            StatusBadge("In progress", tone = StatusTone.Solid, icon = Res.drawable.ic_video_16)
                        }
                    )
                } else {
                    null
                },
            now = true,
            startAction =
                if (started) {
                    null
                } else {
                    (
                        {
                            ShifaPrimaryButton(
                                "Start",
                                {},
                                it,
                                size = ShifaButtonSize.Compact,
                            )
                        }
                    )
                },
        ),
        TimelineEntry.Free("11:00", "12:00", "Free", "Tap to book", {}),
        TimelineEntry.Blocked("12:00", "13:00", "Lunch"),
        TimelineEntry.Visit(
            "14:00",
            "14:30",
            "Jasur Tursunov",
            "JT",
            "Consultation",
            "In clinic",
            Res.drawable.ic_clinic_16,
            badge = { StatusBadge("Pending payment", tone = StatusTone.Warn, icon = Res.drawable.ic_alert_16) },
            lockedNote = "Opens 10 min before",
        ),
    )

@Preview
@Composable
internal fun TimelineNowPreview() = Gallery { AppointmentTimeline(timeline(started = false)) }

@Preview
@Composable
internal fun TimelineStartedPreview() = Gallery { AppointmentTimeline(timeline(started = true)) }

@Preview
@Composable
internal fun TimelineEmptyPreview() =
    Gallery {
        EmptyState(
            "Your schedule is clear",
            Res.drawable.ic_calendar_22,
            body = "Open slots in your calendar so patients can book them.",
            action = { ShifaSecondaryButton("Open Calendar", {}) },
        )
    }
