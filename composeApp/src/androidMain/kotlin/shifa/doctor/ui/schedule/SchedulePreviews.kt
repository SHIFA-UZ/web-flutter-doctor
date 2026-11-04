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
