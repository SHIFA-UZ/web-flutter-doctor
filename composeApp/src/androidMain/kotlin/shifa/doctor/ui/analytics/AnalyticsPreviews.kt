package shifa.doctor.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import shifa.doctor.data.analytics.AnalyticsSnapshot
import shifa.doctor.data.model.AnalyticsOverview
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.ConsultationTypes
import shifa.doctor.data.model.Engagement
import shifa.doctor.data.model.TrendPoint
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize

private val today = LocalDate(2026, 11, 5)

internal val previewSnapshot =
    AnalyticsSnapshot(
        Result.success(AnalyticsOverview(6, 4, 1, 2)),
        Result.success(
            listOf(3, 5, 2, 7, 4, 0, 6).mapIndexed {
                    i,
                    n,
                ->
                TrendPoint("2026-10-${30 + i}".let { if (i > 1) "2026-11-0${i - 1}" else it }, n)
            },
        ),
        Result.success(ConsultationTypes(8, 17)),
        Result.success(Engagement(41, 12)),
    )

private fun state(snapshot: AnalyticsSnapshot?) =
    AnalyticsUiState(
        AnalyticsRange.lastDays(today),
        today,
        snapshot?.overview,
        snapshot?.trend,
        snapshot?.types,
        snapshot?.engagement,
    )

@Composable
private fun Frame(content: @Composable () -> Unit) =
    PreviewTheme {
        androidx.compose.foundation.layout.Box(
            Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16),
        ) { content() }
    }

// ANALYTICS-02: PREMIUM, loaded: KPIs and engagement.
@Composable
internal fun AnalyticsLoadedPreview() =
    Frame {
        AnalyticsContent(state(previewSnapshot), true, {}, {}, {}, AnalyticsActions())
    }

// ANALYTICS-02: the first read: skeleton values; the plan not known yet, so no teaser.
@Composable
internal fun AnalyticsLoadingPreview() = Frame { AnalyticsContent(state(null), null, {}, {}, {}, AnalyticsActions()) }

// ANALYTICS-02: BASIC/PRO with the KPIs failed: "Could not load" + Retry, and the Premium teaser.
@Composable
internal fun AnalyticsTeaserPreview() =
    Frame {
        AnalyticsContent(
            state(previewSnapshot.copy(overview = Result.failure(Exception()))),
            false,
            {},
            {},
            {},
            AnalyticsActions(),
        )
    }

// ANALYTICS-03: 30 days with a day's tooltip off, and no visit types yet.
@Composable
internal fun AnalyticsChartsPreview() =
    Frame {
        AnalyticsContent(
            AnalyticsUiState(
                AnalyticsRange.lastDays(today, 30),
                today,
                previewSnapshot.overview,
                Result.success(
                    (0 until 30).map { i ->
                        val d = today.minus(kotlinx.datetime.DatePeriod(days = 29 - i))
                        TrendPoint(d.toString(), listOf(3, 5, 2, 7, 4, 0, 6, 8, 1)[i % 9])
                    },
                ),
                Result.success(ConsultationTypes(0, 0)),
                previewSnapshot.engagement,
            ),
            true,
            {},
            {},
            {},
            AnalyticsActions(),
        )
    }

// ANALYTICS-03: 200 % text and 14 days: the ring's shares move to the legend, the last date never overlaps.
@Composable
internal fun AnalyticsLargeTextPreview() =
    Frame {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 2f),
        ) {
            AnalyticsContent(
                AnalyticsUiState(
                    AnalyticsRange.lastDays(today, 14),
                    today,
                    previewSnapshot.overview,
                    Result.success(
                        (0 until 14).map { i ->
                            TrendPoint(
                                today.minus(kotlinx.datetime.DatePeriod(days = 13 - i)).toString(),
                                listOf(3, 5, 2, 7)[i % 4],
                            )
                        },
                    ),
                    previewSnapshot.types,
                    previewSnapshot.engagement,
                ),
                true,
                {},
                {},
                {},
                AnalyticsActions(),
            )
        }
    }
