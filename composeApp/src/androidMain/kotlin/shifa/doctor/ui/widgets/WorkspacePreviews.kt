@file:Suppress("ktlint:standard:max-line-length") // sample copy in previews

package shifa.doctor.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_camera_22
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_mic_off_22
import shifa.doctor.generated.resources.ic_more_v_16
import shifa.doctor.generated.resources.ic_phone_off_22
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.ic_search_22
import shifa.doctor.generated.resources.ic_trash_16
import shifa.doctor.ui.theme.Gallery
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing

private val PATIENT_CELLS = listOf("Last visit" to "18 Oct", "Next" to "Today, 10:30", "Balance" to "0 UZS")

// Table board, phone: patient rows as cards (one selected, one with a status), and the pager.
@Preview
@Composable
internal fun TablePreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        DataTableCard(
            main = { PersonCell("Dilnoza Yusupova", "+998 90 123 45 67", "DY") },
            cells = PATIENT_CELLS,
            trailing = {
                ShifaIconButton(
                    Res.drawable.ic_more_v_16,
                    "More",
                    {},
                    variant = ShifaIconButtonVariant.Ghost,
                )
            },
            onClick = {},
        )
        DataTableCard(
            main = { PersonCell("Jasur Tursunov", "ID 10482", "JT") },
            cells = listOf("Last visit" to "2 Sep", "Next" to "—", "Balance" to "−450 000 UZS"),
            selected = true,
            trailing = { StatusBadge("Unpaid", tone = StatusTone.Warn) },
            onClick = {},
        )
        ShifaPager("21–40 of 128", page = 2, pageCount = 7, onPage = {
        }, previousDescription = "Previous page", nextDescription = "Next page")
    }

// TableStates, phone: loading (skeleton rows in the row shape).
@Preview
@Composable
internal fun TableLoadingPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        DataTableSkeleton("Loading patients", rows = 4)
    }

// TableStates, phone: filtered empty (Clear Filters resets search and chips).
@Preview
@Composable
internal fun TableEmptyPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        DataTableEmpty("No patients match these filters", Res.drawable.ic_search_22, "Clear Filters", {})
    }

// DaCalendar, phone: the day strip and the agenda (booked, video, now, free, blocked, cancelled, with chips).
@Preview
@Composable
internal fun AgendaPreview() =
    Gallery {
        val days =
            listOf("Mon" to "3", "Tue" to "4", "Wed" to "5", "Thu" to "6", "Fri" to "7", "Sat" to "8", "Sun" to "9")
        DayStrip(days.map { DayStripItem(it.first, it.second) }, selected = 1, onSelect = {})
        AgendaSlot("09:00", "Malika Ergasheva", AgendaEventKind.Booked, subtitle = "Check-up · 30 min", onClick = {}, chips = {
            StatusBadge("Paid", tone = StatusTone.Brand, icon = Res.drawable.ic_check_16)
        })
        AgendaSlot("09:30", "Dilnoza Yusupova", AgendaEventKind.Now, subtitle = "Video call · 30 min", onClick = {})
        AgendaSlot("10:00", "Jasur Tursunov", AgendaEventKind.Video, subtitle = "Follow-up · 20 min", onClick = {}, chips = {
            StatusBadge("Pending payment", tone = StatusTone.Warn, icon = Res.drawable.ic_alert_16)
        })
        AgendaSlot("10:30", "Free", AgendaEventKind.Free, subtitle = "Tap to book", onClick = {})
        AgendaSlot("12:00", "Lunch", AgendaEventKind.Blocked)
        AgendaSlot("14:00", "Aziz Karimov", AgendaEventKind.Booked, subtitle = "Cancelled by patient", cancelled = true)
    }

// DaCalendar: the month grid with loads, today, selected, past, outside and day-off cells.
@Preview
@Composable
internal fun MonthCalendarPreview() =
    Gallery {
        val days =
            List(35) { i ->
                val n = i - 5
                when {
                    n < 1 -> MonthDay("${26 + i}", outside = true)
                    n > 30 -> MonthDay("${n - 30}", outside = true)
                    else ->
                        MonthDay(
                            "$n",
                            load = listOf(DayLoad.None, DayLoad.Light, DayLoad.Busy, DayLoad.Blocked)[n % 4],
                            today = n == 4,
                            selected = n == 12,
                            past = n < 4,
                            off = i % 7 == 6,
                        )
                }
            }
        MonthCalendar(
            "November 2026",
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"),
            days,
            onDay = {},
            onPrevious = {},
            onNext = {},
            previousDescription = "Previous month",
            nextDescription = "Next month",
            legend =
                listOf(
                    DayLoad.Light to "Light",
                    DayLoad.Busy to "Busy",
                    DayLoad.Blocked to "Blocked",
                ),
        )
    }

// DaVisit: the dental chart (FDI order) with planned, partial and done teeth, a count and the selected ring.
@Preview
@Composable
internal fun DentalChartPreview() =
    Gallery {
        DentalChart(
            marks =
                mapOf(
                    16 to ToothMark(ToothState.Planned, 2),
                    21 to ToothMark(ToothState.Done),
                    36 to ToothMark(ToothState.Partial, 3),
                    47 to ToothMark(ToothState.Done, 1),
                ),
            selected = 36,
            onTooth = {},
            legend =
                listOf(
                    ToothState.Planned to "Planned",
                    ToothState.Partial to "In progress",
                    ToothState.Done to "Done",
                ),
            describe = { "Tooth $it" },
        )
    }

// Forms: a form section and the sticky action bar (with and without a state line).
@Preview
@Composable
internal fun FormAndActionBarPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        FormSection("Working hours", description = "Patients can book inside these hours.") {
            ShifaInputField("09:00", {}, "Start", Modifier.fillMaxWidth())
            ShifaInputField("18:00", {}, "End", Modifier.fillMaxWidth())
        }
        ActionBar(state = "Unsaved changes", secondary = {
            ShifaSecondaryButton("Cancel", {}, it)
        }) { ShifaPrimaryButton("Save", {}, it) }
        ActionBar { ShifaPrimaryButton("Continue", {}, it) }
    }

// Plans: treatment plan card, installment rows with the sum check, subscription tiers.
@Preview
@Composable
internal fun PlansPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        TreatmentPlanCard(
            "Implant, tooth 36",
            "Jasur Tursunov",
            "Step 2 of 4",
            0.5f,
            paid = "3 000 000",
            total = "6 000 000 UZS",
            owed = "450 000 UZS overdue",
            status = { StatusBadge("Active", tone = StatusTone.Brand) },
            onClick = {},
        )
        InstallmentRow(
            1,
            date = { ShifaInputField("01.12.2026", {}, "Date") },
            amount = { ShifaInputField("1 500 000", {}, "Amount") },
            remove = {
                ShifaIconButton(
                    Res.drawable.ic_trash_16,
                    "Remove",
                    {},
                    variant = ShifaIconButtonVariant.Ghost,
                )
            },
        )
        InstallmentSum("Total 6 000 000 of 6 000 000 UZS", off = false)
        InstallmentSum("Total 5 500 000 of 6 000 000 UZS: 500 000 left", off = true)
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            TierCard(
                "Pro",
                "290 000",
                "UZS / month",
                listOf(
                    TierFeature("AI briefings"),
                    TierFeature("Video calls"),
                    TierFeature("Clinic team", included = false),
                ),
                Modifier.weight(1f),
                current = true,
                badge = { StatusBadge("Current", tone = StatusTone.Solid) },
            )
            TierCard(
                "Premium",
                "590 000",
                "UZS / month",
                listOf(TierFeature("AI briefings"), TierFeature("Video calls"), TierFeature("Clinic team")),
                Modifier.weight(1f),
                action = { ShifaPrimaryButton("Upgrade", {}, size = ShifaButtonSize.Compact) },
            )
        }
    }

// Workspace chrome: context switcher, map picker sheet, video call controls.
@Preview
@Composable
internal fun WorkspaceChromePreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        ContextSwitcher("Main Clinic", "Chilonzor, Tashkent", role = "Owner", onClick = {})
        MapPicker(Modifier.height(ShifaSize.S320), search = {
            ShifaSearchField("Chilonzor 9", {}, "Search address", Modifier.fillMaxWidth())
        }) {
            Text("Chilonzor 9, Tashkent", color = ShifaColors.Text.Primary)
            ShifaPrimaryButton("Use This Address", {}, Modifier.fillMaxWidth())
        }
        CallControlBar(
            listOf(
                CallControl(Res.drawable.ic_mic_off_22, "Unmute", {}, off = true),
                CallControl(Res.drawable.ic_camera_22, "Turn camera off", {}),
                CallControl(Res.drawable.ic_refresh_22, "Switch camera", {}),
                CallControl(Res.drawable.ic_phone_off_22, "End call", {}, end = true),
            ),
        )
    }
