package shifa.doctor.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberBiometrics(): Biometrics {
    val activity = LocalContext.current as? FragmentActivity
    return remember(activity) { AndroidBiometrics(activity) }
}

// Weak biometrics too (most face unlocks): this gates the app's own screen, not a key in the keystore.
private const val ALLOWED = BIOMETRIC_STRONG or BIOMETRIC_WEAK

private class AndroidBiometrics(
    private val activity: FragmentActivity?,
) : Biometrics {
    override val available: Boolean
        get() =
            activity != null &&
                BiometricManager.from(activity).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(
        title: String,
        usePin: String,
    ): Boolean {
        val host = activity ?: return false
        if (!available) return false
        return suspendCancellableCoroutine { cont ->
            val prompt =
                BiometricPrompt(
                    host,
                    ContextCompat.getMainExecutor(host),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        // A cancel, "Use PIN" or too many tries; a single unrecognised finger keeps the prompt open.
                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(usePin)
                    .setAllowedAuthenticators(ALLOWED)
                    .build(),
            )
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
