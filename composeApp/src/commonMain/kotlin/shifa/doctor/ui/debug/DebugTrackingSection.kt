package shifa.doctor.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem

/**
 * Tracking (A7, docs/posthog): whether this build would send (on, or off and why; a tap applies the rule again) and the
 * last events and screen views the app recorded, newest first. A debug build never sends, so this list is how to see
 * what a feature tracks.
 */
@Composable
internal fun DebugTrackingSection() {
    val status by Tracking.status.collectAsState()
    val recent by Tracking.recent.collectAsState()
    ShifaSection("Tracking") {
        ShifaSettingsItem(
            "Tracking",
            { Tracking.refresh() },
            subtitle = status.reason?.let { "Off: ${it.id}" } ?: "On: sent to PostHog",
            divider = recent.isNotEmpty(),
        )
        recent.forEachIndexed { i, line -> ShifaSettingsItem(line, {}, divider = i < recent.lastIndex) }
    }
}
