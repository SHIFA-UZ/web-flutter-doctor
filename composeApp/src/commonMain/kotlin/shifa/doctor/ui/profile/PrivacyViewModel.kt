package shifa.doctor.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.account.AccountRepository
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.applock.AppLockRepository
import shifa.doctor.data.applock.appLockRepository
import shifa.doctor.data.auth.OtpCooldown
import shifa.doctor.data.auth.otpCooldown
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.ProfileEvents
import shifa.doctor.data.tracking.trackName
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.dataExportFailed
import shifa.doctor.generated.resources.dataExportOncePerDay
import shifa.doctor.generated.resources.dataExportSaved
import shifa.doctor.generated.resources.deleteAccountFailed
import shifa.doctor.generated.resources.failedToResendCode
import shifa.doctor.generated.resources.invalidOtp
import shifa.doctor.generated.resources.obligationAppointments
import shifa.doctor.generated.resources.obligationSubscription
import shifa.doctor.generated.resources.otpResent
import shifa.doctor.generated.resources.wrongOrExpiredCodeBackToCode
import shifa.doctor.ui.emailcode.CodeBanner

/** Flow 08/12 §5: the question, the e-mailed code, or what must be resolved first. */
enum class DeleteStep { Ask, Code, Blocked }

data class PrivacyUiState(
    val exporting: Boolean = false,
    val banner: CodeBanner? = null,
    val step: DeleteStep? = null,
    val maskedEmail: String? = null,
    val code: String = "",
    val codeError: StringResource? = null,
    val codeBanner: CodeBanner? = null,
    val resendIn: Int = 0,
    val busy: Boolean = false,
    val obligations: List<StringResource> = emptyList(),
)

/**
 * Privacy & data (flow 08/12, BE-02): a copy of the doctor's data (a JSON file, once a day) and deleting the account
 * (the question, then a code e-mailed to the account; refused with the reasons while appointments or a paid
 * subscription are open). A deleted account is signed out everywhere and its app-lock PIN is forgotten.
 */
class PrivacyViewModel(
    private val account: AccountRepository = AccountRepository(),
    private val sessions: SessionRepository = sessionRepository,
    private val appLock: AppLockRepository = appLockRepository,
    private val cooldown: OtpCooldown = otpCooldown,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    /** [save] writes the file where the platform keeps downloads; its answer is the saved name. */
    fun export(save: (name: String, bytes: ByteArray) -> Result<String>) {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true, banner = null) }
        viewModelScope.launch {
            val banner =
                account.exportData().fold(
                    onSuccess = { bytes ->
                        save(FILE_NAME, bytes).fold(
                            { name ->
                                tracker.track(ProfileEvents.DataExportSaved)
                                CodeBanner(Res.string.dataExportSaved, error = false, args = listOf(name))
                            },
                            {
                                tracker.track(ProfileEvents.DataExportFailed("save_failed"))
                                CodeBanner(Res.string.dataExportFailed)
                            },
                        )
                    },
                    onFailure = { e ->
                        tracker.track(
                            ProfileEvents.DataExportFailed(
                                if (e is ApiException.RateLimit) "daily_limit" else e.toFailureReason(),
                            ),
                        )
                        val text =
                            if (e is ApiException.RateLimit) {
                                Res.string.dataExportOncePerDay
                            } else {
                                e.toUserMessage(Res.string.dataExportFailed)
                            }
                        CodeBanner(text, requestId = e.requestId)
                    },
                )
            _state.update { it.copy(exporting = false, banner = banner) }
        }
    }

    fun askDelete() = _state.update { it.copy(step = DeleteStep.Ask, banner = null) }

    /** Cancel, Back or a swipe on any of the sheets: nothing has happened yet. */
    fun closeDelete() {
        val s = _state.value
        if (s.busy) return
        s.step?.let { tracker.track(ProfileEvents.AccountDeletionCancelled(it.trackName())) }
        _state.update { it.copy(step = null, code = "", codeError = null) }
    }

    /** "Continue" on the question: the code goes out, or the reasons come back. */
    fun continueDelete() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            account.requestDeletion().fold(
                onSuccess = { sent ->
                    tracker.track(ProfileEvents.AccountDeletionCodeSent)
                    cooldown.start(COOLDOWN_KEY, PURPOSE)
                    _state.update {
                        it.copy(
                            busy = false,
                            step = DeleteStep.Code,
                            maskedEmail = sent.maskedEmail,
                            code = "",
                            codeError = null,
                        )
                    }
                    tick()
                },
                onFailure = { e -> refused(e, "request") },
            )
        }
    }

    /** Digits only, at most 6; the sixth confirms. */
    fun onCode(value: String) {
        val code = value.filter(Char::isDigit).take(CODE_LENGTH)
        _state.update { it.copy(code = code, codeError = null) }
        if (code.length == CODE_LENGTH) confirm()
    }

    fun confirm() {
        val s = _state.value
        if (s.busy) return
        if (s.code.length != CODE_LENGTH) return _state.update { it.copy(codeError = Res.string.invalidOtp) }
        _state.update { it.copy(busy = true, codeBanner = null) }
        viewModelScope.launch {
            account.confirmDeletion(s.code).fold(
                onSuccess = {
                    // Before the session ends: the reset that follows drops the identity.
                    tracker.track(ProfileEvents.AccountDeleted)
                    _state.update { it.copy(busy = false, step = null) }
                    // The PIN of a deleted account must not stay on the device; the shell leaves on session end.
                    appLock.forget()
                    sessions.accountDeleted()
                },
                onFailure = { e -> refused(e, "confirm") },
            )
        }
    }

    fun resend() {
        val s = _state.value
        if (s.busy || cooldown.secondsLeft(COOLDOWN_KEY, PURPOSE) > 0) return
        _state.update { it.copy(busy = true, codeBanner = null) }
        viewModelScope.launch {
            val sent = account.requestDeletion()
            if (sent.isSuccess) cooldown.start(COOLDOWN_KEY, PURPOSE)
            _state.update {
                it.copy(
                    busy = false,
                    codeBanner =
                        sent.fold(
                            { CodeBanner(Res.string.otpResent, error = false) },
                            { e -> CodeBanner(Res.string.failedToResendCode, requestId = e.requestId) },
                        ),
                )
            }
            tick()
        }
    }

    private fun refused(
        e: Throwable,
        step: String,
    ) {
        val code = (e as? ApiException)?.code
        tracker.track(
            when (code) {
                "HAS_ACTIVE_OBLIGATIONS" ->
                    ProfileEvents.AccountDeletionBlocked(
                        (e as ApiException).details.filter { it in OBLIGATIONS }.map { it.lowercase() },
                    )
                "EMAIL_OTP_INVALID", "EMAIL_OTP_EXPIRED" -> ProfileEvents.AccountDeletionFailed(step, "wrong_code")
                else -> ProfileEvents.AccountDeletionFailed(step, e.toFailureReason())
            },
        )
        when (code) {
            "HAS_ACTIVE_OBLIGATIONS" ->
                _state.update {
                    it.copy(
                        busy = false,
                        step = DeleteStep.Blocked,
                        obligations = obligationTexts((e as ApiException).details),
                    )
                }
            "EMAIL_OTP_INVALID", "EMAIL_OTP_EXPIRED" ->
                _state.update { it.copy(busy = false, code = "", codeError = Res.string.wrongOrExpiredCodeBackToCode) }
            else ->
                _state.update {
                    it.copy(
                        busy = false,
                        step = null,
                        banner = CodeBanner(e.toUserMessage(Res.string.deleteAccountFailed), requestId = e.requestId),
                    )
                }
        }
    }

    // "Resend code (59 s)", once a second while the code sheet is open.
    private fun tick() {
        viewModelScope.launch {
            while (_state.value.step == DeleteStep.Code) {
                val left = cooldown.secondsLeft(COOLDOWN_KEY, PURPOSE)
                _state.update { it.copy(resendIn = left) }
                if (left == 0) break
                delay(SECOND_MS)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "shifa-doctor-data.json"
        const val CODE_LENGTH = 6
        const val SECOND_MS = 1_000L
        const val PURPOSE = "ACCOUNT_DELETION"

        // One signed-in doctor: the cooldown is the account's, whatever its address.
        const val COOLDOWN_KEY = "me"

        // The obligation codes sent with `account_deletion_blocked`; anything else is left out.
        val OBLIGATIONS = setOf("UPCOMING_APPOINTMENTS", "ACTIVE_SUBSCRIPTION")

        /** The backend's `details` codes as sentences; an unknown code is left out, never shown raw. */
        fun obligationTexts(details: List<String>): List<StringResource> =
            details.mapNotNull {
                when (it) {
                    "UPCOMING_APPOINTMENTS" -> Res.string.obligationAppointments
                    "ACTIVE_SUBSCRIPTION" -> Res.string.obligationSubscription
                    else -> null
                }
            }
    }
}
