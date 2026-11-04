package shifa.doctor.utils

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// The Custom Tabs protocol is this one extra (androidx.browser only wraps it). With no Custom Tabs browser
// the default browser opens it; with no browser at all nothing happens.
@Composable
actual fun rememberInAppBrowser(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
            runCatching { context.startActivity(intent) }
        }
    }
}
