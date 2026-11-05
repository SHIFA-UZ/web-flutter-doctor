package shifa.doctor.ui.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.intelligence.CardText
import shifa.doctor.data.intelligence.CardVisit
import shifa.doctor.data.intelligence.IntelCard
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allPluralStringResources
import shifa.doctor.generated.resources.allStringResources
import shifa.doctor.generated.resources.intelAppointmentsCount
import shifa.doctor.generated.resources.intelCardBrief
import shifa.doctor.generated.resources.intelCardFreeSlots
import shifa.doctor.generated.resources.intelCardMostVisited
import shifa.doctor.generated.resources.intelCardPatientAppointments
import shifa.doctor.generated.resources.intelCardPatients
import shifa.doctor.generated.resources.intelCardPatterns
import shifa.doctor.generated.resources.intelCardSchedule
import shifa.doctor.generated.resources.intelDidYouMean
import shifa.doctor.generated.resources.intelNoAppointmentsToday
import shifa.doctor.generated.resources.intelNoExactMatchFor
import shifa.doctor.generated.resources.intelNoFreeSlots
import shifa.doctor.generated.resources.intelNoPatientAppointmentsInRange
import shifa.doctor.generated.resources.intelNoPatientsMatched
import shifa.doctor.generated.resources.intelOpenCalendar
import shifa.doctor.generated.resources.moreCount
import shifa.doctor.generated.resources.shifaIntelligence
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.visit.Patterns
import shifa.doctor.ui.widgets.AiResponseText
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.utils.LocalAppLocale
import shifa.doctor.utils.LocalUzbekCyrillic
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

/**
 * A tool card in the thread (INTEL-05, 02 §4.4, flows 03/07–03/13, board `DaIntel` card): a bordered box with the
 * brand title and the card's body. "Open calendar" leaves Intelligence for the calendar on the card's day. Hint
 * sentences, range labels and date labels the backend sends in English show as sent until INTEL-10 localises them.
 */
@Composable
internal fun ToolCard(
    toolName: String?,
    payload: JsonObject,
    onOpenCalendar: (LocalDate?) -> Unit = {},
) {
    val card = IntelCard.of(toolName, payload)
    CompositionLocalProvider(LocalCardText provides rememberCardText()) { CardBox(card, onOpenCalendar) }
}

@Composable
private fun CardBox(
    card: IntelCard,
    onOpenCalendar: (LocalDate?) -> Unit,
) {
    val ct = LocalCardText.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
            .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.ControlMd)
            .padding(ShifaSize.S14),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        Text(
            tr(title(card)),
            Modifier.semantics { heading() },
            style = ShifaTextStyles.contextName(),
            color = ShifaColors.Text.BrandStrong,
        )
        when (card) {
            is IntelCard.Schedule -> ScheduleBody(card) { onOpenCalendar(card.date) }
            is IntelCard.PatientAppointments ->
                PatientAppointmentsBody(card) {
                    onOpenCalendar(card.visits.firstNotNullOfOrNull { it.date })
                }
            is IntelCard.FreeSlots -> FreeSlotsBody(card) { onOpenCalendar(card.date) }
            is IntelCard.PatientSearch -> SearchBody(card)
            is IntelCard.MostVisited ->
                if (card.patients.isEmpty()) {
                    Line(card.hint?.let(ct::localizeHint) ?: DASH)
                } else {
                    card.patients.forEach { (name, count) ->
                        Line(
                            listOfNotNull(
                                ct.patientLabel(name),
                                count?.toString(),
                            ).joinToString(" · "),
                        )
                    }
                }
            is IntelCard.Patterns ->
                if (card.patterns.isEmpty()) {
                    Line(
                        card.hint?.let(ct::localizeHint) ?: DASH,
                    )
                } else {
                    Patterns(card.patterns, caption = false)
                }
            is IntelCard.Brief -> AiResponseText(card.text)
            is IntelCard.Other -> card.line?.let { Line(ct.localizeHint(it)) }
            is IntelCard.Failed -> Line(ct.localizeHint(card.line))
        }
    }
}

private fun title(card: IntelCard): StringResource =
    when (card) {
        is IntelCard.Schedule -> Res.string.intelCardSchedule
        is IntelCard.PatientAppointments -> Res.string.intelCardPatientAppointments
        is IntelCard.FreeSlots -> Res.string.intelCardFreeSlots
        is IntelCard.PatientSearch -> Res.string.intelCardPatients
        is IntelCard.MostVisited -> Res.string.intelCardMostVisited
        is IntelCard.Patterns -> Res.string.intelCardPatterns
        is IntelCard.Brief -> Res.string.intelCardBrief
        is IntelCard.Other -> Res.string.shifaIntelligence
        is IntelCard.Failed ->
            when (card.type) {
                "schedule", "get_today_schedule" -> Res.string.intelCardSchedule
                "patient_appointments", "list_patient_appointments" -> Res.string.intelCardPatientAppointments
                "free_slots", "list_free_slots" -> Res.string.intelCardFreeSlots
                "patient_search", "search_patients" -> Res.string.intelCardPatients
                "most_visited", "get_most_visited_patients" -> Res.string.intelCardMostVisited
                "clinic_patterns", "get_clinic_patterns" -> Res.string.intelCardPatterns
                "patient_brief", "brief_patient" -> Res.string.intelCardBrief
                else -> Res.string.shifaIntelligence
            }
    }

/** Flow 03/07: "Appointments: 5", up to 8 rows (time · patient · status), "+N more", Open calendar. */
@Composable
private fun ColumnScope.ScheduleBody(
    card: IntelCard.Schedule,
    openCalendar: () -> Unit,
) {
    Line("${tr(Res.string.intelAppointmentsCount)}: ${card.count}")
    if (card.visits.isEmpty()) Line(tr(Res.string.intelNoAppointmentsToday), secondary = true)
    card.visits.forEach { v ->
        Row(Modifier.muted(v), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            Text(
                v.time,
                Modifier.width(ShifaSize.S52),
                style = ShifaTextStyles.aiText().copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                listOfNotNull(LocalCardText.current.patientLabel(v.patientName), statusText(v)).joinToString(" · "),
                Modifier.weight(1f),
                style = ShifaTextStyles.aiText(),
                color = ShifaColors.Text.Primary,
            )
        }
    }
    More(card.more)
    ShifaLink(tr(Res.string.intelOpenCalendar), openCalendar)
}

/** Flow 03/08: the patient, the period, the count, up to 12 rows (day · time · status), "+N more", Open calendar. */
@Composable
private fun ColumnScope.PatientAppointmentsBody(
    card: IntelCard.PatientAppointments,
    openCalendar: () -> Unit,
) {
    card.patientName?.let { Line(it, bold = true) }
    card.rangeLabel?.let { Line(LocalCardText.current.rangeLabel(it), secondary = true) }
    Line("${tr(Res.string.intelAppointmentsCount)}: ${card.count}")
    if (card.visits.isEmpty()) Line(tr(Res.string.intelNoPatientAppointmentsInRange), secondary = true)
    card.visits.forEach { v ->
        Row(Modifier.muted(v), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            Text(
                // "6 Nov 2026": the year stays (a range can span years; flow 03/08 §4), the month short to fit.
                v.date?.let {
                    "${it.dayOfMonth} ${tr(
                        shortMonth(it.monthNumber),
                    )} ${it.year}"
                } ?: LocalCardText.current.dateLabel(fallback = v.dateLabel),
                Modifier.width(ShifaSize.S112),
                style = ShifaTextStyles.aiText().copy(fontWeight = FontWeight.SemiBold),
            )
            Text(v.time, Modifier.width(ShifaSize.S48), style = ShifaTextStyles.aiText())
            // Without a patient in the header, each row says whose visit it is.
            val status = statusText(v)
            Text(
                listOfNotNull(
                    v.patientName?.takeIf {
                        card.patientName == null
                    }?.let(LocalCardText.current::patientLabel),
                    status,
                ).joinToString(" · "),
                Modifier.weight(1f),
                style = ShifaTextStyles.aiText(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
    More(card.more)
    ShifaLink(tr(Res.string.intelOpenCalendar), openCalendar)
}

/** Flow 03/09: the day, up to 24 times as chips (not tappable yet), "+N more", Open calendar on that day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FreeSlotsBody(
    card: IntelCard.FreeSlots,
    openCalendar: () -> Unit,
) {
    (
        card.date?.let {
            longDate(it)
        } ?: card.dateLabel?.let { LocalCardText.current.fieldLabel(fallback = it) }
    )?.let { Line(it, bold = true) }
    if (card.slots.isEmpty()) {
        Line(card.hint?.let(LocalCardText.current::localizeHint) ?: tr(Res.string.intelNoFreeSlots))
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            card.slots.forEach {
                Text(
                    it,
                    Modifier
                        .border(ShifaSize.S1, ShifaColors.Alpha.Teal25, ShifaRadius.Full)
                        .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S6),
                    style = ShifaTextStyles.chipSlot(),
                    color = ShifaColors.Text.Primary,
                )
            }
        }
    }
    More(card.more)
    ShifaLink(tr(Res.string.intelOpenCalendar), openCalendar)
}

/** Flow 03/10: "Did you mean…?" for a fuzzy search, the matches with their phone; none → the hint. */
@Composable
private fun ColumnScope.SearchBody(card: IntelCard.PatientSearch) {
    if (card.matches.isEmpty()) {
        return Line(
            card.hint?.let(LocalCardText.current::localizeHint) ?: tr(Res.string.intelNoPatientsMatched),
        )
    }
    if (card.fuzzy) {
        Line(card.query?.let { tr(Res.string.intelNoExactMatchFor, it) } ?: tr(Res.string.intelDidYouMean), bold = true)
    }
    card.matches.forEach { m ->
        Line(listOfNotNull(LocalCardText.current.patientLabel(m.name), m.phone).joinToString(" · "))
    }
}

@Composable
private fun statusText(v: CardVisit): String? = v.status?.let { LocalCardText.current.statusLabel(it) }

// Cancelled visits are muted (flows 03/07–08 target).
private fun Modifier.muted(v: CardVisit) = if (v.status.equals("CANCELLED", ignoreCase = true)) alpha(MUTED) else this

@Composable
private fun More(count: Int) {
    if (count > 0) Line(tr(Res.string.moreCount, count.toString()), secondary = true)
}

@Composable
private fun Line(
    text: String,
    bold: Boolean = false,
    secondary: Boolean = false,
) = Text(
    text,
    style = if (bold) ShifaTextStyles.aiText().copy(fontWeight = FontWeight.SemiBold) else ShifaTextStyles.aiText(),
    color = if (secondary) ShifaColors.Text.Secondary else ShifaColors.Text.Primary,
)

private const val MUTED = 0.55f
private const val DASH = "—"

private val LocalCardText = staticCompositionLocalOf { CardText("en", { it }, { "$it" }) }

/**
 * The card-text localiser (INTEL-10) in the app's language: the keys it looks up resolved with `tr()` (Cyrillic
 * Uzbek included) and the short month names; its own Uzbek templates turn Cyrillic the same way.
 */
@Composable
internal fun rememberCardText(): CardText {
    val lang = LocalAppLocale.current.ifEmpty { "en" }
    val cyrillic = LocalUzbekCyrillic.current
    // Templated keys come back with their {name} placeholders (the resources number them %1$s…).
    val strings =
        CardText.KEYS.associateWith { key ->
            val names = CardText.PLACEHOLDERS[key].orEmpty().map { "{$it}" }.toTypedArray()
            Res.allStringResources[key]?.let { tr(it, *names) } ?: key
        }
    // Plurals for the three counts that stand for every CLDR form (CardText.pluralSample).
    val plurals =
        CardText.PLURAL_KEYS.mapValues { (key, names) ->
            val args = names.map { "{$it}" }.toTypedArray()
            listOf(1, 2, 5).associateWith {
                    n ->
                Res.allPluralStringResources[key]?.let { trPlural(it, n, *args) } ?: key
            }
        }
    val months = (1..12).map { tr(shortMonth(it)) }
    return remember(lang, cyrillic, strings, plurals, months) {
        CardText(lang, { strings[it] ?: it }, { months[it - 1] }, cyrillic) { key, count ->
            plurals[key]?.get(CardText.pluralSample(lang, count)) ?: key
        }
    }
}
