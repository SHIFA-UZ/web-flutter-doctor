package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

@Composable
actual fun rememberLocator(): suspend () -> Pair<Double, Double>? = remember { suspend { IosLocator().locate() } }

/** One Core Location fix: asks "when in use" first (Info.plist has the reason), then `requestLocation`. */
@OptIn(ExperimentalForeignApi::class)
private class IosLocator :
    NSObject(),
    CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var waiting: CancellableContinuation<Pair<Double, Double>?>? = null

    suspend fun locate(): Pair<Double, Double>? =
        withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                waiting = cont
                manager.delegate = this@IosLocator
                manager.desiredAccuracy = kCLLocationAccuracyBest
                next()
            }
        }

    private fun next() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways ->
                manager.requestLocation()
            else -> finish(null) // denied or restricted
        }
    }

    private fun finish(value: Pair<Double, Double>?) {
        waiting?.takeIf { it.isActive }?.resume(value)
        waiting = null
    }

    // Also called once when the delegate is set; only an answer to the question moves on.
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (waiting != null && manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) next()
    }

    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        (didUpdateLocations.lastOrNull() as? CLLocation)?.coordinate?.useContents { finish(latitude to longitude) }
    }

    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: NSError,
    ) = finish(null)
}
