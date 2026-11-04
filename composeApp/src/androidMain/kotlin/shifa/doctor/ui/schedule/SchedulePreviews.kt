package shifa.doctor.ui.schedule

import androidx.compose.runtime.Composable
import shifa.doctor.data.model.DoctorLocation
import shifa.doctor.ui.theme.PreviewTheme

private val places =
    listOf(
        DoctorLocation(
            1,
            "Main Clinic",
            "Shifa Medical Center",
            locationStreetAddress = "Amir Temur 12",
            locationCity = "Tashkent",
            locationCountry = "Uzbekistan",
            isPrimary = true,
        ),
        DoctorLocation(2, "Evening Clinic", locationCity = "Tashkent"),
    )

@Composable
internal fun LocationsPreview() = PreviewTheme { LocationsContent(LocationsUiState(places), LocationsActions()) }

@Composable
internal fun LocationEditorPreview() =
    PreviewTheme {
        val draft = LocationDraft.of(places[0]).copy(label = "")
        LocationsContent(
            LocationsUiState(places, draft = draft, original = LocationDraft.of(places[0]), labelMissing = true),
            LocationsActions(),
        )
    }

private val week =
    shifa.doctor.data.schedule.WeeklySchedule.of(
        (1..5).flatMap { d ->
            listOf(
                shifa.doctor.data.model.RuleDto(d, "09:00", "13:00", 30, 1),
                shifa.doctor.data.model.RuleDto(d, "14:00", "18:00", 30, 1),
            )
        } + shifa.doctor.data.model.RuleDto(6, "10:00", "14:00", 20, 1),
    )

private val clock =
    shifa.doctor.utils.PracticeClock("Asia/Tashkent", {
        kotlinx.datetime.Instant.parse("2026-11-04T05:15:00Z")
    }, { kotlinx.datetime.TimeZone.of("Asia/Tashkent") })

// SCHED-2: two locations, the week, the period being extended, one date with extra hours.
@Composable
internal fun SchedulePreview() =
    PreviewTheme {
        ScheduleContent(
            ScheduleUiState(
                clock,
                locations = places,
                locationId = 1,
                saved = week,
                week = week,
                periods = listOf(shifa.doctor.data.model.ValidityPeriodDto("2026-09-30", "2026-12-31")),
                newFrom = kotlinx.datetime.LocalDate(2027, 1, 1),
                newUntil = kotlinx.datetime.LocalDate(2027, 3, 1),
                dates =
                    listOf(
                        shifa.doctor.data.model.DateSpecificRuleDto(
                            "2026-12-27",
                            "2026-12-27",
                            "10:00",
                            "14:00",
                            30,
                            1,
                            7,
                        ),
                    ),
            ),
            ScheduleActions(),
        )
    }
