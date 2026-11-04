package shifa.doctor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import shifa.doctor.ui.theme.ShifaTheme

/** Root of the app. Placeholder until AUTH-01; the locale and screen switching arrive with CORE-02…06 and AUTH-01. */
@Composable
fun App() {
    ShifaTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Shifa Doctor", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
