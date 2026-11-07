package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * Face ID / Touch ID / fingerprint for the app lock (LOCK-01): `BiometricPrompt` on Android, `LAContext` on iOS.
 * [authenticate] is true only for a recognised face or finger; a cancel, a failure or an OS lockout are false and the
 * PIN pad stays (flow 01/12 §6).
 */
interface Biometrics {
    /** Hardware present and a face or finger enrolled. */
    val available: Boolean

    suspend fun authenticate(
        title: String,
        usePin: String,
    ): Boolean
}

@Composable
expect fun rememberBiometrics(): Biometrics
