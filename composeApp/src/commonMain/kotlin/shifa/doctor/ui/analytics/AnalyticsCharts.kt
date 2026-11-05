package shifa.doctor.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.ConsultationTypes
import shifa.doctor.data.model.TrendPoint
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.analyticsNoData
import shifa.doctor.generated.resources.appointmentsInRange
import shifa.doctor.generated.resources.appointmentsLast7Days
import shifa.doctor.generated.resources.donutSummary
import shifa.doctor.generated.resources.inPerson
import shifa.doctor.generated.resources.trendSummary
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.visitTypeDistribution
import shifa.doctor.generated.resources.visitsCount
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The trend's y axis (board `DaHome`, Flutter's rule): the top 20 % over the highest day (10 when all are 0), steps of 1 up to 5, else a fifth. */
internal fun trendAxis(max: Int): Pair<Int, Int> {
    val top = if (max > 0) ceil(max * 1.2).toInt() else 10
    val step = if (top <= 5) 1 else ceil(top / 5.0).toInt()
    return top to step
}

/** Every n-th date label, about 7 in all (flow 02/05 §8: 90 labels can't fit). */
internal fun labelEvery(points: Int) = maxOf(1, ceil(points / 7.0).toInt())

/** In-person and video shares that add up to 100 (flow 02/05 §8: Flutter could show 101 %). */
internal fun shares(types: ConsultationTypes): Pair<Int, Int> {
    if (types.total == 0) return 0 to 0
    val inPerson = (types.inPerson * 100.0 / types.total).roundToInt()
    return inPerson to 100 - inPerson
}

/**
 * "Appointments (…)" (ANALYTICS-03, flow 02/05 §3, board `DaHome`): a curved line over the days, grid and counts on the
 * left, every n-th "MM/DD" below; a tap on a day shows "6 Nov · 7 visits". The title names the days drawn (Flutter
 * always said "Last 7 Days"); all zero → "No data yet". Read out as a summary.
 */
@Composable
internal fun TrendBlock(
    trend: Result<List<TrendPoint>>?,
    // The period asked for: the title while loading, empty or failed (the days drawn once they are in).
    requested: AnalyticsRange,
    // The practice date: "Last 7 Days" only for the seven days ending today.
    today: kotlinx.datetime.LocalDate,
    loading: Boolean,
    onRetry: () -> Unit,
) {
    val points = trend?.getOrNull().orEmpty().takeUnless { loading }.orEmpty()
    val drawn = points.mapNotNull { p -> p.day?.let { it to p.count } }
    val span = if (drawn.isEmpty()) requested else AnalyticsRange(drawn.first().first, drawn.last().first)
    val title =
        if (span == AnalyticsRange.lastDays(today)) {
            tr(Res.string.appointmentsLast7Days)
        } else {
            tr(Res.string.appointmentsInRange, rangeLabel(span))
        }
    Block(title) {
        when {
            // Loading first: a new range's read outranks the last one's failure.
            trend == null || loading ->
                ShifaSkeleton(
                    Modifier.fillMaxWidth().height(ShifaSize.S180),
                    ShifaRadius.ControlMd,
                )
            trend.isFailure -> LoadFailed(onRetry)
            drawn.isEmpty() || drawn.all { it.second == 0 } -> NoData()
            else -> {
                val max = drawn.maxOf { it.second }
                val summary = tr(Res.string.trendSummary, rangeLabel(span), max.toString())
                val visits =
                    drawn.map {
                            (d, n) ->
                        "${d.dayOfMonth} ${tr(shortMonth(d.monthNumber))} · ${trPlural(Res.plurals.visitsCount, n, n)}"
                    }
                TrendChart(
                    drawn.map { it.second },
                    drawn.map {
                            (d, _) ->
                        "${two(d.monthNumber)}/${two(d.dayOfMonth)}"
                    },
                    visits,
                    summary,
                )
            }
        }
    }
}

private fun two(n: Int) = n.toString().padStart(2, '0')

@Composable
private fun TrendChart(
    counts: List<Int>,
    labels: List<String>,
    tips: List<String>,
    summary: String,
) {
    val measurer = rememberTextMeasurer()
    val axis = ShifaTextStyles.caption().copy(color = ShifaColors.Text.Tertiary)
    val tipStyle = ShifaTextStyles.tag().copy(color = ShifaColors.Text.Inverse)
    val line = ShifaColors.Icon.Brand
    val grid = ShifaColors.Border.Default
    val dotRing = ShifaColors.Bg.Surface
    val tipBg = ShifaColors.Text.Primary
    var tip by remember(counts) { mutableStateOf<Int?>(null) }
    val (top, step) = trendAxis(counts.max())
    val every = labelEvery(counts.size)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(ShifaSize.S180)
            .semantics { contentDescription = summary }
            .pointerInput(counts) {
                detectTapGestures { at ->
                    val left = LEFT.toPx()
                    val w = size.width - left
                    val x = { i: Int -> left + if (counts.size == 1) w / 2 else i * w / (counts.size - 1) }
                    val nearest = counts.indices.minBy { abs(x(it) - at.x) }
                    tip = if (tip == nearest) null else nearest
                }
            },
    ) {
        val left = LEFT.toPx()
        // Room under the line for the dates at the current text size (a fixed inset let them hit the axis's 0).
        val bottom = size.height - measurer.measure("00/00", axis).size.height - GAP.toPx()
        val w = size.width - left

        fun x(i: Int) = left + if (counts.size == 1) w / 2 else i * w / (counts.size - 1)

        fun y(v: Int) = bottom - v.toFloat() / top * (bottom - TOP_GAP.toPx())
        // Grid lines and their counts.
        for (v in 0..top step step) {
            drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), ShifaSize.S1.toPx())
            val t = measurer.measure(v.toString(), axis)
            drawText(t, topLeft = Offset(left - GAP.toPx() - t.size.width, y(v) - t.size.height / 2))
        }
        // The line: smooth curves through the days (control points half way, as the board's path).
        val path = Path()
        counts.forEachIndexed { i, v ->
            if (i == 0) {
                path.moveTo(x(0), y(v))
            } else {
                val cx = (x(i - 1) + x(i)) / 2
                path.cubicTo(cx, y(counts[i - 1]), cx, y(v), x(i), y(v))
            }
        }
        drawPath(path, line, style = Stroke(LINE.toPx(), cap = StrokeCap.Round))
        counts.forEachIndexed { i, v ->
            val r = (if (tip == i) DOT_ACTIVE else DOT).toPx()
            drawCircle(dotRing, r + RING.toPx(), Offset(x(i), y(v)))
            drawCircle(line, r, Offset(x(i), y(v)))
        }
        // Every n-th date and the last, kept inside the chart; a date that would touch the one before it is left out
        // (large text), and the last always shows, in place of the one before it if they touch.
        val shown = mutableListOf<Pair<androidx.compose.ui.text.TextLayoutResult, Float>>()
        val gap = GAP.toPx()

        fun touches(lx: Float) = shown.isNotEmpty() && shown.last().second + shown.last().first.size.width + gap > lx
        counts.indices.filter { it % every == 0 || it == counts.lastIndex }.forEach { i ->
            val t = measurer.measure(labels[i], axis)
            val lx = (x(i) - t.size.width / 2).coerceIn(0f, maxOf(0f, size.width - t.size.width))
            if (i == counts.lastIndex) {
                while (touches(lx)) shown.removeAt(shown.lastIndex)
            } else if (touches(lx)) {
                return@forEach
            }
            shown += t to lx
        }
        shown.forEach { (t, lx) -> drawText(t, topLeft = Offset(lx, size.height - t.size.height)) }
        tip?.let { i ->
            val pad = TIP_PAD.toPx()
            // Never wider than the chart (large text, long translations): it wraps instead.
            val t =
                measurer.measure(
                    tips[i],
                    tipStyle,
                    constraints = Constraints(maxWidth = maxOf(1, (size.width - pad * 2).toInt())),
                )
            val boxW = t.size.width + pad * 2
            val boxH = t.size.height + pad * 2
            val bx = (x(i) - boxW / 2).coerceIn(0f, maxOf(0f, size.width - boxW))
            val by = (y(counts[i]) - boxH - TIP_GAP.toPx()).coerceAtLeast(0f)
            drawRoundRect(tipBg, Offset(bx, by), Size(boxW, boxH), CornerRadius(TIP_RADIUS.toPx()))
            drawText(t, topLeft = Offset(bx + pad, by + pad))
        }
    }
}

/**
 * "Visit Type Distribution" (ANALYTICS-03, board `DaHome`): a ring of in-person (brand) over video (brand at 25 %), the
 * in-person share in its middle with the video share under it, and a legend with the counts beside it (below it when the
 * text is large, flow 02/05 §8). None → "No data yet".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypesBlock(
    types: Result<ConsultationTypes>?,
    onRetry: () -> Unit,
) = Block(tr(Res.string.visitTypeDistribution)) {
    val t = types?.getOrNull()
    when {
        types?.isFailure == true -> LoadFailed(onRetry)
        t == null -> ShifaSkeleton(Modifier.size(ShifaSize.S160), ShifaRadius.Full)
        t.total == 0 -> NoData()
        else -> {
            val (inPerson, video) = shares(t)
            val large = LocalDensity.current.fontScale >= LARGE_TEXT
            val summary = tr(Res.string.donutSummary, t.inPerson.toString(), t.video.toString())
            val arc = ShifaColors.Icon.Brand
            val track = ShifaColors.Alpha.Teal25
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S16),
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(ShifaSize.S160).semantics { contentDescription = summary }, Alignment.Center) {
                    Canvas(Modifier.size(ShifaSize.S160)) {
                        val width = RING_WIDTH.toPx()
                        val inset = width / 2
                        val ring = Size(size.width - width, size.height - width)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), ring, style = Stroke(width))
                        drawArc(
                            arc,
                            -90f,
                            360f * t.inPerson / t.total,
                            false,
                            Offset(inset, inset),
                            ring,
                            style = Stroke(width),
                        )
                    }
                    // At large text sizes the centre would spill over the ring (flow 02/05 §8): the shares go to the legend.
                    if (!large) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$inPerson %",
                                style = ShifaTextStyles.callStatus(),
                                color = ShifaColors.Text.BrandStrong,
                            )
                            Text(
                                tr(Res.string.inPerson),
                                style = ShifaTextStyles.legend(),
                                color = ShifaColors.Text.Primary,
                            )
                            Text(
                                "$video % ${tr(Res.string.videoCall)}",
                                style = ShifaTextStyles.caption(),
                                color = ShifaColors.Text.Secondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                    Legend(arc, tr(Res.string.inPerson), t.inPerson, inPerson.takeIf { large })
                    Legend(track, tr(Res.string.videoCall), t.video, video.takeIf { large })
                }
            }
        }
    }
}

@Composable
private fun Legend(
    color: Color,
    label: String,
    count: Int,
    // The share, when the ring's centre can't hold it (large text).
    percent: Int? = null,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(ShifaSize.S10).background(color, ShifaRadius.Xs))
    Text(
        listOfNotNull(label, count.toString(), percent?.let { "$it %" }).joinToString(" · "),
        style = ShifaTextStyles.legend(),
        color = ShifaColors.Text.Primary,
    )
}

// From this font scale the ring's centre text no longer fits its hole.
private const val LARGE_TEXT = 1.3f

@Composable
private fun NoData() =
    Box(Modifier.fillMaxWidth().height(ShifaSize.S160), Alignment.Center) {
        Text(tr(Res.string.analyticsNoData), style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary)
    }

// The chart's insets and marks (board `DaHome`'s 400×180 drawing: 36 for the counts, 18 for the dates).
private val LEFT = ShifaSize.S36
private val TOP_GAP = ShifaSize.S10
private val GAP = ShifaSize.S8
private val LINE = ShifaSize.S3
private val DOT = ShifaSize.S4
private val DOT_ACTIVE = ShifaSize.S6
private val RING = ShifaSize.S2
private val TIP_PAD = ShifaSize.S6
private val TIP_GAP = ShifaSize.S10
private val TIP_RADIUS = ShifaSize.S8
private val RING_WIDTH = ShifaSize.S28
