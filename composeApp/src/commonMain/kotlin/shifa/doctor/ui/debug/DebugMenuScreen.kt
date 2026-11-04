package shifa.doctor.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.debug.DemoMode
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitch

/**
 * Debug menu (CORE-08), debug builds only, English (not translated): demo data, the development backend, and the
 * native spikes. Switching the backend signs out, because a token from one backend means nothing on the other.
 * Fake pushes come with the push tickets (PUSH-01, NOTIF-*), which define the payloads.
 */
@Composable
fun DebugMenuScreen(modifier: Modifier = Modifier) {
    var spikes by remember { mutableStateOf(spikeAutoRun) } // -spikeAutoRun (iOS check) starts there
    val demo by DemoMode.enabled.collectAsState()
    val backend by BackendChoice.current.collectAsState()
    if (spikes) {
        Column(modifier.fillMaxSize().statusBarsPadding()) {
            ShifaSubHeader("Native spikes", onBack = { spikes = false })
            SpikesScreen()
        }
    } else {
        DebugMenuScreenContent(
            demo = demo,
            backendUrl = backend.baseUrl,
            development = backend == BackendChoice.Backend.Development,
            onDemo = DemoMode::set,
            onDevelopment = { dev ->
                sessionRepository.signOut()
                BackendChoice.set(if (dev) BackendChoice.Backend.Development else BackendChoice.Backend.Production)
            },
            onSpikes = { spikes = true },
            modifier = modifier,
        )
    }
}

@Composable
fun DebugMenuScreenContent(
    demo: Boolean,
    backendUrl: String,
    development: Boolean,
    onDemo: (Boolean) -> Unit,
    onDevelopment: (Boolean) -> Unit,
    onSpikes: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
) {
    ShifaSection("Debug") {
        ShifaSettingsItem(
            "Demo data",
            { onDemo(!demo) },
            subtitle = "Answers from the app; real GETs and sign-in, no writes",
            trailing = { ShifaSwitch(demo, onDemo) },
        )
        ShifaSettingsItem(
            "Development backend",
            { onDevelopment(!development) },
            subtitle = backendUrl,
            trailing = { ShifaSwitch(development, onDevelopment) },
        )
        ShifaSettingsItem("Native spikes", onSpikes, divider = false)
    }
}
