package shifa.doctor.data.push

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.session.Session

/** `POST /doctors/me/fcm-tokens` (BE-05). */
@Serializable
data class PushTokenRequest(
    val token: String,
    val platform: String,
)

/**
 * PUSH-01 (BE-05): this device's FCM token on the signed-in doctor's account, so every device the doctor uses rings and
 * signing out on one leaves the others ringing. Registered once per session and token (again when FCM refreshes it; the
 * backend moves a token another account had to this one). At sign-out it is removed with the ending session's token, in
 * the background, so signing out never waits for the network. Clinic staff have no doctor account to ring (the route is
 * doctors-only).
 */
class PushRegistration(
    private val client: () -> HttpClient = { httpClient },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()

    // The session token and the device token last registered with it.
    private var registered: Pair<String, String>? = null

    // The last sign-out's removal: a registration waits for it, so signing straight back in can't lose the token.
    private var cleanup: Job? = null

    suspend fun register(
        session: Session?,
        device: Push.Device?,
    ): Result<Unit> {
        cleanup?.join()
        return mutex.withLock {
            if (session == null || session.isClinicStaff || device == null) return@withLock Result.success(Unit)
            val key = session.token to device.token
            if (registered == key) return@withLock Result.success(Unit)
            apiCall<Unit> {
                client().post(ApiConfig.Endpoints.DOCTOR_PUSH_TOKENS) {
                    contentType(ContentType.Application.Json)
                    setBody(PushTokenRequest(device.token, device.platform))
                }
            }.onSuccess { registered = key }
        }
    }

    /** Debug menu: the backend sends a test push to every device of the signed-in doctor. */
    suspend fun sendTestPush(): Result<Unit> = apiCall<Unit> { client().post(ApiConfig.Endpoints.DOCTOR_TEST_PUSH) }

    /**
     * The doctor signed out of the session that had [sessionToken]: this device stops ringing for that account. Best
     * effort (an unknown token is a no-op on the backend); an expired session can't do it, and the token moves on with
     * the next sign-in.
     */
    fun signedOut(
        sessionToken: String,
        device: String? = Push.device.value?.token,
    ) {
        registered = null
        if (device == null) return
        val previous = cleanup
        // In order: after an earlier removal and after a registration still on its way (it holds the lock).
        cleanup =
            scope.launch {
                previous?.join()
                mutex.withLock {
                    apiCall<Unit> {
                        client().delete(ApiConfig.Endpoints.doctorPushToken(device)) {
                            // The session is already gone from the client: this request carries the one it ended.
                            header(HttpHeaders.Authorization, "Bearer $sessionToken")
                        }
                    }
                }
            }
    }
}

val pushRegistration by lazy { PushRegistration() }
