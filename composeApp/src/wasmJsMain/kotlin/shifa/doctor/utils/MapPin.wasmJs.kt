package shifa.doctor.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_pin_40
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * OpenStreetMap's tiles drawn by Compose (Android's osmdroid shows the same tiles): drag pans, the wheel zooms, a tap
 * moves the pin and reports it. The map centres on the pin whenever the pin changes.
 */
@Composable
actual fun MapPin(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
    onPick: ((latitude: Double, longitude: Double) -> Unit)?,
) {
    val pick by rememberUpdatedState(onPick)
    val tile = with(LocalDensity.current) { TILE.toPx() }
    var zoom by remember { mutableStateOf(ZOOM) }
    var center by remember { mutableStateOf(project(latitude, longitude, zoom, tile)) }
    LaunchedEffect(latitude, longitude) { center = project(latitude, longitude, zoom, tile) }
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(ShifaColors.Bg.Disabled)
            .pointerInput(tile) {
                detectDragGestures { change, drag ->
                    change.consume()
                    center -= drag
                }
            }.pointerInput(tile) {
                detectTapGestures { tap ->
                    val p = pick ?: return@detectTapGestures
                    val world = center + tap - Offset(size.width / 2f, size.height / 2f)
                    val (lat, lng) = unproject(world, zoom, tile)
                    p(lat, lng)
                }
            }.pointerInput(tile) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                        val next = (zoom + if (dy < 0) 1 else -1).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        if (next != zoom) {
                            center = if (next > zoom) center * 2f else center / 2f
                            zoom = next
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val left = center.x - width / 2
        val top = center.y - height / 2
        val count = 1 shl zoom
        for (ty in floor(top / tile).toInt()..floor((top + height) / tile).toInt()) {
            if (ty !in 0 until count) continue
            for (tx in floor(left / tile).toInt()..floor((left + width) / tile).toInt()) {
                val x = ((tx % count) + count) % count
                key(zoom, tx, ty) {
                    KamelImage(
                        { asyncPainterResource("https://tile.openstreetmap.org/$zoom/$x/$ty.png") },
                        contentDescription = null,
                        modifier =
                            Modifier
                                .offset { IntOffset((tx * tile - left).roundToInt(), (ty * tile - top).roundToInt()) }
                                // A tile is bigger than a short map: unbounded, or the box would squeeze it.
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .size(TILE),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
        val pin = project(latitude, longitude, zoom, tile)
        val pinSize = with(LocalDensity.current) { ShifaSize.S40.toPx() }
        Icon(
            painterResource(Res.drawable.ic_pin_40),
            contentDescription = null,
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            (pin.x - left - pinSize / 2).roundToInt(),
                            (pin.y - top - pinSize).roundToInt(),
                        )
                    }
                    .size(ShifaSize.S40),
            tint = ShifaColors.Icon.Brand,
        )
        // OSM's tile policy: "© OpenStreetMap contributors" on the map.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .background(ShifaColors.Bg.Surface)
                .padding(horizontal = ShifaSpacing.Inset.Xs),
        ) {
            Text("© OpenStreetMap contributors", style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
        }
    }
}

private val TILE = 256.dp // a map tile, not a layout measure
private const val ZOOM = 16
private const val MIN_ZOOM = 3
private const val MAX_ZOOM = 19

/** Web Mercator: the point's pixel in the world map at [zoom]. */
private fun project(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    tile: Float,
): Offset {
    val world = tile * 2.0.pow(zoom)
    val lat = latitude.coerceIn(-85.0511, 85.0511) * PI / 180
    val x = (longitude + 180) / 360 * world
    val y = (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * world
    return Offset(x.toFloat(), y.toFloat())
}

private fun unproject(
    point: Offset,
    zoom: Int,
    tile: Float,
): Pair<Double, Double> {
    val world = tile * 2.0.pow(zoom)
    val lng = point.x / world * 360 - 180
    val lat = atan(sinh(PI * (1 - 2 * point.y / world))) * 180 / PI
    return lat to ((lng + 540) % 360 - 180)
}
