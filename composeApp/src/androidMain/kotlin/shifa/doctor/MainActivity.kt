package shifa.doctor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import shifa.doctor.data.push.Push
import shifa.doctor.utils.PushNotifications

// A FragmentActivity: BiometricPrompt needs one (app lock, LOCK-01).
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        // Light bars always: the app has no dark theme.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        // PUSH-03: a push tapped while the app was closed or in the background (not again on a recreation).
        if (savedInstanceState == null) openTappedPush(intent)
        setContent { App() }
    }

    // PUSH-03: a push tapped while the app was open (singleTop: this activity gets the intent).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTappedPush(intent)
    }

    private fun openTappedPush(intent: Intent?) {
        PushNotifications.tapped(intent)?.let {
            Push.onTap(it)
            intent?.replaceExtras(Bundle()) // handled once
        }
    }
}
