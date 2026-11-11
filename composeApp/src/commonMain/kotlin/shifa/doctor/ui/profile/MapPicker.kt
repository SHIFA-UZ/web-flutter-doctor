package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.confirm
import shifa.doctor.generated.resources.currentLocation
import shifa.doctor.generated.resources.ic_locate_22
import shifa.doctor.generated.resources.locationUnavailable
import shifa.doctor.generated.resources.selectLocationOnMap
import shifa.doctor.generated.resources.selectedLocation
import shifa.doctor.generated.resources.tapMapToMovePin
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.MapPin
import shifa.doctor.utils.rememberLocator
import shifa.doctor.utils.tr

/**
 * The map picker while it is open (flow 08/04), shared by the contact address (PROF-07) and the locations (LOC-2):
 * where the pin is, where it was when the map opened, and the "Discard changes?" question after it moved.
 */
data class PinEdit(
    val pin: Pair<Double, Double>,
    val start: Pair<Double, Double> = pin,
    val asking: Boolean = false,
) {
    fun moved(
        latitude: Double,
        longitude: Double,
    ) = copy(pin = latitude to longitude)

    /** Back or ×: a moved pin asks first; Back on the question closes the question; otherwise the map closes (null). */
    fun closing(): PinEdit? =
        when {
            asking -> copy(asking = false)
            pin != start -> copy(asking = true)
            else -> null
        }

    companion object {
        /** On the saved pin, or Tashkent's centre (flow 08/04 §3). */
        fun at(
            latitude: Double?,
            longitude: Double?,
        ) = PinEdit((latitude ?: TASHKENT.first) to (longitude ?: TASHKENT.second))
    }
}

/** The map and, after the pin moved, the Discard question over it. "Use my location" moves the pin to the device. */
@Composable
internal fun MapPickerHost(
    edit: PinEdit,
    onMove: (Double, Double) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) {
    val locate = rememberLocator()
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    MapPickerContent(
        edit.pin,
        onMove = { latitude, longitude ->
            failed = false
            onMove(latitude, longitude)
        },
        onConfirm = onConfirm,
        onClose = onClose,
        onLocate = {
            if (!locating) {
                scope.launch {
                    locating = true
                    val here = locate()
                    locating = false
                    failed = here == null
                    here?.let { onMove(it.first, it.second) }
                }
            }
        },
        locating = locating,
        locateFailed = failed,
    )
    if (edit.asking) DiscardChangesSheet(onDiscard, onKeep)
}

/** Six decimals (flow 08/03 §4). */
internal fun coordinate(value: Double): String {
    val micro = kotlin.math.round(value * 1_000_000).toLong()
    val sign = if (micro < 0) "-" else ""
    val abs = kotlin.math.abs(micro)
    return "$sign${abs / 1_000_000}.${(abs % 1_000_000).toString().padStart(6, '0')}"
}

internal val TASHKENT = 41.2995 to 69.2401

internal fun coordinates(
    latitude: Double?,
    longitude: Double?,
) = if (latitude != null && longitude != null) "${coordinate(latitude)}, ${coordinate(longitude)}" else null

/**
 * Flow 08/04: the map with one pin (a tap moves it), "Use my location" over its corner (not in the design), and a card
 * with the coordinates and Confirm.
 */
@Composable
internal fun MapPickerContent(
    pin: Pair<Double, Double>,
    onMove: (Double, Double) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onLocate: () -> Unit = {},
    locating: Boolean = false,
    locateFailed: Boolean = false,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(
        tr(Res.string.selectLocationOnMap),
        Modifier.statusBarsPadding(),
        onBack = onClose,
        onCheck = onConfirm,
        checkDescription = Res.string.confirm,
    )
    Box(Modifier.weight(1f)) {
        MapPin(pin.first, pin.second, Modifier.fillMaxSize(), onPick = onMove)
        ShifaIconButton(
            Res.drawable.ic_locate_22,
            tr(Res.string.currentLocation),
            onLocate,
            Modifier.align(Alignment.TopEnd).padding(ShifaSpacing.Inset.Md),
            variant = ShifaIconButtonVariant.Surface,
            enabled = !locating,
        )
        ShifaSection(
            tr(Res.string.selectedLocation),
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                Text(
                    coordinates(pin.first, pin.second).orEmpty(),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    tr(if (locateFailed) Res.string.locationUnavailable else Res.string.tapMapToMovePin),
                    style = ShifaTextStyles.metaSmall(),
                    color = if (locateFailed) ShifaColors.Text.Error else ShifaColors.Text.Secondary,
                )
                ShifaPrimaryButton(tr(Res.string.confirm), onConfirm, Modifier.fillMaxWidth())
            }
        }
    }
}
