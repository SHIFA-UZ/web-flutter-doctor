package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// No face or finger on the web: the app lock keeps its PIN pad.
@Composable
actual fun rememberBiometrics(): Biometrics =
    remember {
        object : Biometrics {
            override val available = false

            override suspend fun authenticate(
                title: String,
                usePin: String,
            ) = false
        }
    }
