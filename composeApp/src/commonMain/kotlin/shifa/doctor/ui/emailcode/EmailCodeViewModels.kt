package shifa.doctor.ui.emailcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.OtpCooldown
import shifa.doctor.data.auth.otpCooldown
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.enterEmail
import shifa.doctor.generated.resources.failedToSendCode
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidOtp
import shifa.doctor.generated.resources.otpResent
import shifa.doctor.generated.resources.requestTimeout
import shifa.doctor.generated.resources.verificationFailed

/** Sign in with an e-mail code (AUTH-03), or the first two steps of a password reset (AUTH-04). */
enum class EmailCodeMode { SignIn, Reset }

/** A banner on these screens: a string key (re-renders on a language switch) and whether it's an error. */
data class CodeBanner(
    val text: StringResource,
    val error: Boolean = true,
)

data class EmailEntryUiState(
    val email: String = "",
    val error: StringResource? = null,
    val loading: Boolean = false,
    val banner: CodeBanner? = null,
    /** Set when the code is on its way: the code screen opens for this address. */
    val sentTo: String? = null,
)

/** Flow 01/03: the address, checked here, then a code is sent (or the last one is still fresh). */
class EmailEntryViewModel(
    private val mode: EmailCodeMode,
    initialEmail: String = "",
    private val auth: AuthRepository = AuthRepository(),
    private val cooldown: OtpCooldown = otpCooldown,
) : ViewModel() {
    // Back from the code screen keeps the address (flow 01/03 §2).
    private val _state = MutableStateFlow(EmailEntryUiState(initialEmail))
    val state: StateFlow<EmailEntryUiState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null, sentTo = null) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val email = normalized(s.email) // flow §4: trimmed and lower-cased before checking and sending
        val error =
            when {
                email.isEmpty() -> Res.string.enterEmail
                !EMAIL.matches(email) -> Res.string.invalidEmail
                else -> null
            }
        if (error != null) return _state.update { it.copy(error = error) }
        // A code sent less than a minute ago is still valid: go on without a second e-mail.
        if (cooldown.secondsLeft(email, mode.name) > 0) return _state.update { it.copy(sentTo = email) }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result = send(auth, mode, email)
            if (result.isSuccess) cooldown.start(email, mode.name)
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(loading = false, sentTo = email) },
                    onFailure = { e -> it.copy(loading = false, banner = CodeBanner(sendError(e))) },
                )
            }
        }
    }

    /** Back from the code screen: stay here, ready to send again. */
    fun consumeSent() = _state.update { it.copy(sentTo = null) }
}

data class CodeUiState(
    val email: String,
    val code: String = "",
    val error: StringResource? = null,
    val loading: Boolean = false,
    val resendIn: Int = 0,
    val banner: CodeBanner? = null,
    val signedIn: Session? = null,
    /** Reset mode: the code to carry to the new-password screen (no request here, flow 01/04 §5 step 5). */
    val resetCode: String? = null,
)

/** Flow 01/04: the 6-digit code, the 60 s resend, then sign-in (or on to the new password). */
class CodeViewModel(
    email: String,
    private val mode: EmailCodeMode,
    private val auth: AuthRepository = AuthRepository(),
    private val sessions: SessionRepository = sessionRepository,
    private val cooldown: OtpCooldown = otpCooldown,
) : ViewModel() {
    private val _state = MutableStateFlow(CodeUiState(email.trim(), resendIn = cooldown.secondsLeft(email, mode.name)))
    val state: StateFlow<CodeUiState> = _state.asStateFlow()

    init {
        tick()
    }

    /** Digits only, at most 6; the sixth submits (flow §5 step 2 target). */
    fun onCode(value: String) {
        val code = value.filter(Char::isDigit).take(CODE_LENGTH)
        _state.update { it.copy(code = code, error = null, resetCode = null) }
        if (code.length == CODE_LENGTH) verify()
    }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun verify() {
        val s = _state.value
        if (s.loading) return
        if (s.code.length != CODE_LENGTH) return _state.update { it.copy(error = Res.string.invalidOtp) }
        if (mode == EmailCodeMode.Reset) return _state.update { it.copy(resetCode = s.code) }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result = auth.verifyEmailOtp(s.email, s.code).mapCatching { sessions.signIn(it).getOrThrow() }
            _state.update {
                result.fold(
                    onSuccess = { session -> it.copy(loading = false, signedIn = session) },
                    // A wrong or expired code clears the field for the next try (flow §5).
                    onFailure = { e -> it.copy(loading = false, code = "", banner = CodeBanner(verifyError(e))) },
                )
            }
        }
    }

    fun resend() {
        val s = _state.value
        if (s.loading || cooldown.secondsLeft(s.email, mode.name) > 0) return
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result = send(auth, mode, s.email)
            if (result.isSuccess) cooldown.start(s.email, mode.name)
            _state.update {
                result.fold(
                    onSuccess = {
                            _ ->
                        it.copy(loading = false, banner = CodeBanner(Res.string.otpResent, error = false))
                    },
                    onFailure = { e -> it.copy(loading = false, banner = CodeBanner(sendError(e))) },
                )
            }
            tick()
        }
    }

    // The countdown on "Resend code (59 s)", once a second until it reaches 0.
    private fun tick() {
        viewModelScope.launch {
            while (true) {
                val left = cooldown.secondsLeft(_state.value.email, mode.name)
                _state.update { it.copy(resendIn = left) }
                if (left == 0) break
                delay(SECOND_MS)
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
        const val SECOND_MS = 1_000L
    }
}

private fun normalized(email: String) = email.trim().lowercase()

private val EMAIL = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")

private suspend fun send(
    auth: AuthRepository,
    mode: EmailCodeMode,
    email: String,
): Result<Unit> =
    when (mode) {
        EmailCodeMode.SignIn -> auth.sendLoginOtp(email)
        EmailCodeMode.Reset -> auth.sendForgotPasswordOtp(email)
    }

private fun sendError(e: Throwable): StringResource =
    when {
        e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
        e is ApiException.NetworkError || e is ApiException.RateLimit -> e.toUserMessage()
        else -> e.toUserMessage(Res.string.failedToSendCode)
    }

private fun verifyError(e: Throwable): StringResource =
    when {
        e is ApiException.Unauthorized || e is ApiException.ValidationError -> Res.string.invalidOtp
        e is ApiException.NotFound -> Res.string.invalidOtp
        e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
        else -> e.toUserMessage(Res.string.verificationFailed)
    }
