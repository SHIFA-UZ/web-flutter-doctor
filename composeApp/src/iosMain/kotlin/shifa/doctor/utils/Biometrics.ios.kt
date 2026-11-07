package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometrics(): Biometrics = remember { IosBiometrics() }

// Face ID needs NSFaceIDUsageDescription in Info.plist (kept from the Flutter app).
@OptIn(ExperimentalForeignApi::class)
private class IosBiometrics : Biometrics {
    override val available: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    override suspend fun authenticate(
        title: String,
        usePin: String,
    ): Boolean {
        if (!available) return false
        val context = LAContext().apply { localizedFallbackTitle = usePin }
        return suspendCancellableCoroutine { cont ->
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, title) { ok, _ ->
                if (cont.isActive) cont.resume(ok)
            }
            cont.invokeOnCancellation { context.invalidate() }
        }
    }
}
