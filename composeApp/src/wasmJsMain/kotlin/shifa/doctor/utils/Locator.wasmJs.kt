package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberLocator(): suspend () -> Pair<Double, Double>? =
    remember {
        suspend {
            suspendCancellableCoroutine { cont ->
                currentPosition(
                    { lat, lng -> if (cont.isActive) cont.resume(lat to lng) },
                    { if (cont.isActive) cont.resume(null) },
                )
            }
        }
    }

// The browser asks the doctor first; refused, unsupported or no fix in time is [fail].
private fun currentPosition(
    ok: (Double, Double) -> Unit,
    fail: () -> Unit,
): Unit =
    js(
        """
        navigator.geolocation
            ? navigator.geolocation.getCurrentPosition(
                function (p) { ok(p.coords.latitude, p.coords.longitude); },
                function () { fail(); },
                { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 })
            : fail()
        """,
    )
