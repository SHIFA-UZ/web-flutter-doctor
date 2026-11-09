package shifa.doctor.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.data.push.Push
import shifa.doctor.data.push.pushRegistration
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem

/**
 * PUSH-01…03 checks (debug menu, signed in): this device's token, a real test push from the backend to every device of
 * the doctor, and the newest inbox row of each type opened as if its push had been tapped (the row is marked read).
 */
@Composable
internal fun DebugPushSection() {
    val scope = rememberCoroutineScope()
    val device by Push.device.collectAsState()
    var sent by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<AppNotification>?>(null) }
    ShifaSection("Push") {
        ShifaSettingsItem(
            "This device",
            {},
            // The token's end only: enough to tell devices apart.
            subtitle = device?.let { "${it.platform} · …${it.token.takeLast(8)}" } ?: "No FCM token yet",
        )
        ShifaSettingsItem(
            "Send a test push",
            {
                sent = "Sending…"
                scope.launch {
                    sent =
                        pushRegistration.sendTestPush().fold(
                            { "Sent: it rings on every device signed in to this doctor" },
                            { "Failed: ${it::class.simpleName}" },
                        )
                }
            },
            subtitle = sent ?: "POST /doctors/me/test-notification",
        )
        ShifaSettingsItem(
            "Tap a push from the inbox",
            { scope.launch { rows = NotificationsRepository().page(0).getOrNull()?.items?.distinctBy { it.type } } },
            subtitle = "The newest notification of each type",
            divider = !rows.isNullOrEmpty(),
        )
        rows?.forEachIndexed { i, n ->
            ShifaSettingsItem(n.type, {
                Push.onTap(n.pushData())
            }, subtitle = "Opens as a tapped push", divider = i < rows!!.lastIndex)
        }
    }
}

// The `data` the backend sends with this row's push (backend `docs/api/push-notifications.md`).
private fun AppNotification.pushData(): Map<String, String> =
    buildMap {
        put("notificationId", id.toString())
        put("type", type)
        put("title", title)
        put("message", message)
        put("createdAt", createdAt.toString())
        appointmentId?.let { put("appointmentId", "$it") }
        appointmentStartAt?.let { put("appointmentStartAt", "$it") }
        patientId?.let { put("patientId", "$it") }
        patientName?.let { put("patientName", it) }
        documentId?.let { put("documentId", "$it") }
        documentTitle?.let { put("documentTitle", it) }
        documentAccessRequestId?.let { put("documentAccessRequestId", "$it") }
        taskId?.let { put("taskId", "$it") }
        conversationId?.let { put("conversationId", "$it") }
        carePartnershipId?.let { put("carePartnershipId", "$it") }
    }
