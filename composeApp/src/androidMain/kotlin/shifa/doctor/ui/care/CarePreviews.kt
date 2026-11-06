package shifa.doctor.ui.care

import androidx.compose.runtime.Composable
import shifa.doctor.data.model.CarePartnerHit
import shifa.doctor.data.model.CarePartnership
import shifa.doctor.data.model.CareProgress
import shifa.doctor.ui.theme.PreviewTheme

private val hits =
    listOf(
        CarePartnerHit(
            31,
            "Madina",
            "Rasulova",
            "Cardiologist",
            "Republican Cardiology Centre",
            "Uzbekistan",
            "Tashkent",
        ),
        CarePartnerHit(32, "Behzod", "Normatov", "Orthodontist", "Dentmaster", "Uzbekistan", "Bukhara"),
        CarePartnerHit(34, "Javlon", "Ismoilov"),
    )

private const val ME = 9001L

private fun partnership(
    id: Long,
    status: String,
    patient: String,
    initiator: Pair<Long, String>,
    partner: Pair<Long, String>,
    updated: String,
    message: String? = null,
) = CarePartnership(
    id, status, 11, patient, initiator.first, initiator.second, partner.first, partner.second, "Orthodontist review",
    message, null, "2026-10-20T06:00:00Z", updated,
)

private val list =
    listOf(
        partnership(
            702,
            "PENDING",
            "Jasur Karimov",
            32L to "Behzod Normatov",
            ME to "Aziza Karimova",
            "2026-11-05T13:20:00Z",
        ),
        partnership(
            701,
            "ACTIVE",
            "Dilnoza Rahimova",
            ME to "Aziza Karimova",
            31L to "Madina Rasulova",
            "2026-11-02T09:30:00Z",
        ),
        partnership(
            704,
            "PENDING",
            "Bekzod Aliyev",
            ME to "Aziza Karimova",
            34L to "Javlon Ismoilov",
            "2026-11-01T09:30:00Z",
        ),
        partnership(
            703,
            "COMPLETED",
            "Malika Tursunova",
            ME to "Aziza Karimova",
            33L to "Kamola Yuldasheva",
            "2026-10-10T08:00:00Z",
        ),
    )

@Composable
internal fun FindPartnerPreview() =
    PreviewTheme {
        FindPartnerContent(
            FindPartnerUiState(
                specialty = "card",
                hits = hits,
                searching = false,
                partners = setOf(32L),
                selected = hits[0],
                reason = "Cardiologist",
                message = "Chest pain on exertion, please assess.",
            ),
            "Dilnoza Rahimova",
            FindPartnerActions(),
        )
    }

@Composable
internal fun FindPartnerEmptyPreview() =
    PreviewTheme {
        FindPartnerContent(
            FindPartnerUiState(query = "zzz", hits = emptyList(), searching = false),
            "Dilnoza Rahimova",
            FindPartnerActions(),
        )
    }

@Composable
internal fun PartnershipsPreview() =
    PreviewTheme {
        PartnershipsContent(PartnershipsUiState(all = list), list, null, ME, PartnershipsActions())
    }

@Composable
internal fun PartnershipsEmptyPreview() =
    PreviewTheme {
        PartnershipsContent(
            PartnershipsUiState(
                all = emptyList(),
            ),
            emptyList(),
            "Jasur Karimov",
            ME,
            PartnershipsActions(find = {
            }),
        )
    }

@Composable
internal fun PartnershipInvitePreview() =
    PreviewTheme {
        PartnershipContent(
            PartnershipUiState(
                partnership = list[0].copy(message = "Crowding in the lower arch; braces assessment."),
                loading = false,
                progress = emptyList(),
            ),
            ME,
            PartnershipActions(),
        )
    }

@Composable
internal fun PartnershipActivePreview() =
    PreviewTheme {
        PartnershipContent(
            PartnershipUiState(
                partnership = list[1],
                loading = false,
                progress =
                    listOf(
                        CareProgress(
                            1,
                            31,
                            "Madina Rasulova",
                            "ECG normal; started a stress test plan.",
                            "2026-10-28T08:10:00Z",
                        ),
                        CareProgress(
                            2,
                            ME,
                            "Aziza Karimova",
                            "Thanks, I'll pause the extraction until it's done.",
                            "2026-11-02T09:30:00Z",
                        ),
                    ),
                draft = "Saw the patient today",
            ),
            ME,
            PartnershipActions(),
        )
    }
