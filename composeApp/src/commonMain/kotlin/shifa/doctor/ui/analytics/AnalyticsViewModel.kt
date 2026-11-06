package shifa.doctor.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import shifa.doctor.data.analytics.AnalyticsRepository
import shifa.doctor.data.analytics.AnalyticsSnapshot
import shifa.doctor.data.analytics.all
import shifa.doctor.data.analytics.analyticsRepository
import shifa.doctor.data.analytics.dashboardCsv
import shifa.doctor.data.analytics.dashboardCsvName
import shifa.doctor.data.model.AnalyticsOverview
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.ConsultationTypes
import shifa.doctor.data.model.Engagement
import shifa.doctor.data.model.SmsUsage
import shifa.doctor.data.model.TrendPoint
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.exportFailed
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice

/** A CSV ready for the share sheet. */
data class AnalyticsExport(
    val fileName: String,
    val csv: String,
)

data class AnalyticsUiState(
    val range: AnalyticsRange,
    // The practice date: the range picker's bounds (a year back, 30 days ahead) and its presets.
    val today: LocalDate,
    // Each block on its own, null until its first answer (a skeleton); a refresh keeps it until the new one.
    val overview: Result<AnalyticsOverview>? = null,
    val trend: Result<List<TrendPoint>>? = null,
    val types: Result<ConsultationTypes>? = null,
    val engagement: Result<Engagement>? = null,
    // A new range: only the trend reads again (KPIs are today's).
    val trendLoading: Boolean = false,
    // REPORT-01: the SMS reminders for the range (Reports only; null there until read).
    val sms: Result<SmsUsage>? = null,
    val exporting: Boolean = false,
    val export: AnalyticsExport? = null,
) {
    /** All four loaded and readable, for the range shown: what Export can use without reading again. */
    val loaded: AnalyticsSnapshot?
        get() {
            val o = overview ?: return null
            val t = trend?.takeUnless { trendLoading } ?: return null
            val ty = types ?: return null
            val e = engagement ?: return null
            return AnalyticsSnapshot(o, t, ty, e).takeIf { it.complete() }
        }
}

private fun AnalyticsSnapshot.complete() =
    overview.isSuccess && trend.isSuccess && types.isSuccess && engagement.isSuccess

/**
 * "Clinic performance" (ANALYTICS-02, flow 02/05, 02/06): each block read on open and on refresh and shown as soon as
 * it answers (a slow one never holds the others back, flow 02/05 §5); a new range reads the trend again; today's KPIs
 * read again whenever Home's list of today's visits changes. Export builds the CSV from what is loaded, reading again
 * only when a block is missing, and fails as a whole (flow 02/06 §6) when one can't be read.
 */
class AnalyticsViewModel(
    today: LocalDate,
    private val repo: AnalyticsRepository = analyticsRepository,
    // REPORT-01: Reports also reads the SMS reminders for the range.
    private val withSms: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsUiState(AnalyticsRange.lastDays(today), today))
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()
    private val reads = mutableMapOf<String, Job>()

    init {
        refresh()
    }

    /** Open, pull-to-refresh, Refresh, Retry: every block, in place. */
    fun refresh() {
        refreshToday()
        readTrend(_state.value.range)
        read("types") { repo.consultationTypes().let { r -> _state.update { it.copy(types = r) } } }
        read("engagement") { repo.engagement().let { r -> _state.update { it.copy(engagement = r) } } }
        if (withSms) readSms(_state.value.range)
    }

    /** The range's SMS usage; a 403 means SMS reminders aren't on for the account (flow 08/14 §6), not a failure. */
    private fun readSms(range: AnalyticsRange) =
        read("sms") {
            val sms =
                repo.smsUsage(range).recoverCatching { e ->
                    if (e is ApiException.Forbidden) SmsUsage(smsRemindersAllowed = false) else throw e
                }
            _state.update { s -> if (s.range != range) s else s.copy(sms = sms) }
        }

    /**
     * Home read today's schedule again (coming back, a hero leaving its slot, a new day): today's counts, and the
     * practice date for the picker. On a new day a range the doctor never changed moves with it (its trend is read).
     */
    fun refreshToday(today: LocalDate = _state.value.today) {
        val s = _state.value
        if (today != s.today) {
            val untouched = s.range == AnalyticsRange.lastDays(s.today)
            _state.update { it.copy(today = today) }
            if (untouched) setRange(AnalyticsRange.lastDays(today))
        }
        read("overview") { repo.overview().let { r -> _state.update { it.copy(overview = r) } } }
    }

    /** A saved range: the label at once, the trend read for it. */
    fun setRange(range: AnalyticsRange) {
        if (range == _state.value.range) return
        // The SMS block shows its skeleton, never the old range's numbers under the new label.
        _state.update { it.copy(range = range, trendLoading = true, sms = if (withSms) null else it.sms) }
        readTrend(range)
        if (withSms) readSms(range)
    }

    private fun readTrend(range: AnalyticsRange) =
        read("trend") {
            val trend = repo.trend(range)
            _state.update { s -> if (s.range != range) s else s.copy(trend = trend, trendLoading = false) }
        }

    // One read per block at a time: a newer one cancels the older, so a late answer can't put old numbers back.
    private fun read(
        block: String,
        body: suspend () -> Unit,
    ) {
        reads[block]?.cancel()
        reads[block] = viewModelScope.launch { body() }
    }

    /**
     * Export (flow 02/06 §5): ignored while one runs; the range is the one at the tap. A read made for the export is
     * the export's own: it never replaces what the card shows (the range may have changed meanwhile).
     */
    fun export() {
        if (_state.value.exporting) return
        val range = _state.value.range
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val s = _state.value.loaded ?: repo.all(range)
            val csv =
                if (s.complete()) {
                    dashboardCsv(
                        range,
                        s.overview.getOrThrow(),
                        s.engagement.getOrThrow(),
                        s.types.getOrThrow(),
                        s.trend.getOrThrow(),
                    )
                } else {
                    null
                }
            if (csv == null) ShellNotice.pending.value = Notice(Res.string.exportFailed)
            _state.update {
                it.copy(exporting = false, export = csv?.let { c -> AnalyticsExport(dashboardCsvName(range), c) })
            }
        }
    }

    /** The share sheet was shown, or couldn't be. */
    fun exported(shown: Boolean) {
        if (!shown) ShellNotice.pending.value = Notice(Res.string.exportFailed)
        _state.update { it.copy(export = null) }
    }
}
