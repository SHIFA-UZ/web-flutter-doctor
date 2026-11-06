package shifa.doctor.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.navReports
import shifa.doctor.generated.resources.reportsScreenSubtitle
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.tr

/**
 * Reports (REPORT-01, 08 §7, flow 08/14, board `DaProfile` reports): Home's "Clinic performance" card with the SMS
 * reminders for the range, on its own page (More → Reports as a tab root, Profile → Reports pushed with Back). Its
 * range is its own: Flutter shared it with Home, so changing it here changed Home (flow §13 flags it).
 */
@Composable
fun ReportsScreen(
    today: LocalDate,
    advanced: Boolean?,
    actions: AnalyticsActions,
    // Pushed from Profile: a Back header; as the More tab's root, the page title.
    onBack: (() -> Unit)? = null,
    viewModel: AnalyticsViewModel =
        viewModel(key = "reports-analytics") { AnalyticsViewModel(today, withSms = true) },
) {
    // The ViewModel outlives the page: each visit reads again, on the practice day as it is now (a new day, the
    // profile's zone arriving after the first read).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday(today)
        viewModel.refresh()
    }
    ReportsContent(onBack, viewModel::refresh) {
        AnalyticsSection(today, advanced, actions, viewModel, sms = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportsContent(
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    card: @Composable () -> Unit,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    if (onBack != null) ShifaSubHeader(tr(Res.string.navReports), Modifier.statusBarsPadding(), onBack = onBack)
    // Each block shows its own loading; the pull only starts the reads again.
    PullToRefreshBox(false, onRefresh, Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (onBack == null) Modifier.statusBarsPadding() else Modifier)
                // As a tab root, clear of the floating tab bar (like the other tab roots).
                .padding(
                    start = ShifaSpacing.Inset.Md,
                    end = ShifaSpacing.Inset.Md,
                    top = ShifaSpacing.Inset.Md,
                    bottom = if (onBack == null) ShifaSize.S120 else ShifaSpacing.Inset.Md,
                ),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                if (onBack == null) {
                    Text(
                        tr(Res.string.navReports),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        color = ShifaColors.Text.Primary,
                    )
                }
                Text(
                    tr(Res.string.reportsScreenSubtitle),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            card()
        }
    }
}
