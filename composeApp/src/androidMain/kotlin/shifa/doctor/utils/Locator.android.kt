package shifa.doctor.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
actual fun rememberLocator(): suspend () -> Pair<Double, Double>? {
    val context = LocalContext.current
    // The permission answer of the request in flight.
    val answer = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            answer[0]?.complete(granted.values.any { it })
        }
    return remember(context, ask) {
        suspend {
            val allowed =
                PERMISSIONS.any {
                    ContextCompat.checkSelfPermission(
                        context,
                        it,
                    ) == PackageManager.PERMISSION_GRANTED
                } ||
                    CompletableDeferred<Boolean>().also {
                        answer[0] = it
                        ask.launch(PERMISSIONS)
                    }.await()
            if (allowed) currentLocation(context) else null
        }
    }
}

@SuppressLint("MissingPermission") // checked by the caller
private suspend fun currentLocation(context: Context): Pair<Double, Double>? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers =
        listOfNotNull(
            LocationManager.FUSED_PROVIDER.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return null
    // Every enabled provider at once, the first fix wins: fused alone asks for a balanced fix, which never turns GPS on
    // where there is no network location.
    val signals = providers.map { CancellationSignal() }
    val fix =
        try {
            withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location> { cont ->
                    providers.zip(signals).forEach { (provider, signal) ->
                        LocationManagerCompat.getCurrentLocation(
                            manager,
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context),
                        ) { if (it != null && cont.isActive) cont.resume(it) }
                    }
                }
            }
        } finally {
            signals.forEach { it.cancel() }
        } ?: providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }
    return fix?.let { it.latitude to it.longitude }
}
