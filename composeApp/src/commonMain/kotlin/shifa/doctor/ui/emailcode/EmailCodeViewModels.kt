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
import shifa.doctor.data.api.requestId
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
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.otpResent
import shifa.doctor.generated.resources.phoneNumberRequired
import shifa.doctor.generated.resources.requestTimeout
import shifa.doctor.generated.resources.smsSignInUzbekOnly
import shifa.doctor.generated.resources.smsUnavailableUseEmail
import shifa.doctor.generated.resources.verificationFailed
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.isEmailAddress
import shifa.doctor.utils.normalizedEmail

/**
 * Sign in with an e-mail code (AUTH-03), the first two steps of a password reset (AUTH-04), or sign in with a code by
 * SMS (AUTH-10: the same two screens with a phone number; the user's call 2026-11-07).
 */
enum class EmailCodeMode { SignIn, Reset, SmsSignIn }

/** A banner on these screens: a string key (re-renders on a language switch) and whether it's an error. */
data class CodeBanner(
    val text: StringResource,
    val error: Boolean = true,
    val requestId: String? = null,
    // Placeholders in [text] ("You're now on {plan}").
    val args: List<Any> = emptyList(),
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
        val (email, error) = address(mode, s.email)
        if (error != null) return _state.update { it.copy(error = error) }
        // A code sent less than a minute ago is still valid: go on without a second e-mail.
        if (cooldown.secondsLeft(email, mode.name) > 0) {
            return _state.update { it.copy(sentTo = email) }
        }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result = send(auth, mode, email)
            result.onSuccess { hint -> cooldown.start(email, mode.name, hint) }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(loading = false, sentTo = email) },
                    onFailure = {
                            e ->
                        it.copy(loading = false, banner = CodeBanner(sendError(e, mode), requestId = e.requestId))
                    },
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
    /** SMS sign-in: the masked e-mail the code went to instead (null: by SMS to [email], the phone). */
    val emailHint: String? = null,
    /** SMS sign-in: [email] is the phone number. */
    val bySms: Boolean = false,
)

/** Flow 01/04: the 6-digit code, the 60 s resend, then sign-in (or on to the new password). */
class CodeViewModel(
    email: String,
    private val mode: EmailCodeMode,
    private val auth: AuthRepository = AuthRepository(),
    private val sessions: SessionRepository = sessionRepository,
    private val cooldown: OtpCooldown = otpCooldown,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            CodeUiState(
                email.trim(),
                emailHint = cooldown.hint(email, mode.name),
                bySms = mode == EmailCodeMode.SmsSignIn,
                resendIn = cooldown.secondsLeft(email, mode.name),
            ),
        )
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
            val token =
                if (mode == EmailCodeMode.SmsSignIn) {
                    auth.verifyLoginSmsOtp(s.email, s.code)
                } else {
                    auth.verifyEmailOtp(s.email, s.code)
                }
            val result = token.mapCatching { sessions.signIn(it).getOrThrow() }
            _state.update {
                result.fold(
                    onSuccess = { session -> it.copy(loading = false, signedIn = session) },
                    // A wrong or expired code clears the field for the next try (flow §5).
                    onFailure = {
                            e ->
                        it.copy(
                            loading = false,
                            code = "",
                            banner = CodeBanner(verifyError(e), requestId = e.requestId),
                        )
                    },
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
            result.onSuccess { hint -> cooldown.start(s.email, mode.name, hint) }
            _state.update {
                result.fold(
                    onSuccess = { hint ->
                        it.copy(
                            loading = false,
                            emailHint = hint,
                            banner = CodeBanner(Res.string.otpResent, error = false),
                        )
                    },
                    onFailure = {
                            e ->
                        it.copy(loading = false, banner = CodeBanner(sendError(e, mode), requestId = e.requestId))
                    },
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

/**
 * The address the code goes to, checked: an e-mail trimmed and lower-cased (flow 01/03 §4), or for SMS sign-in an
 * Uzbek mobile number in E.164 (the backend sends sign-in SMS to +998 only). The error when it can't be used.
 */
private fun address(
    mode: EmailCodeMode,
    input: String,
): Pair<String, StringResource?> {
    if (mode == EmailCodeMode.SmsSignIn) {
        val phone = PhoneE164.normalize(input)
        val error =
            when {
                input.isBlank() -> Res.string.phoneNumberRequired
                phone == null -> Res.string.invalidPhone
                !phone.startsWith(UZ_PREFIX) -> Res.string.smsSignInUzbekOnly
                else -> null
            }
        return (phone ?: input.trim()) to error
    }
    val email = normalizedEmail(input)
    val error =
        when {
            email.isEmpty() -> Res.string.enterEmail
            !isEmailAddress(email) -> Res.string.invalidEmail
            else -> null
        }
    return email to error
}

private const val UZ_PREFIX = "+998"

/** Sends the code; the answer is the masked e-mail it went to when an SMS sign-in code went there instead. */
private suspend fun send(
    auth: AuthRepository,
    mode: EmailCodeMode,
    address: String,
): Result<String?> =
    when (mode) {
        EmailCodeMode.SignIn -> auth.sendLoginOtp(address).map { null }
        EmailCodeMode.Reset -> auth.sendForgotPasswordOtp(address).map { null }
        EmailCodeMode.SmsSignIn -> auth.sendLoginSmsOtp(address)
    }

private fun sendError(
    e: Throwable,
    mode: EmailCodeMode,
): StringResource =
    when {
        // The SMS couldn't go out and the account has no e-mail to fall back to.
        mode == EmailCodeMode.SmsSignIn && e is ApiException && e.status == SERVICE_UNAVAILABLE ->
            Res.string.smsUnavailableUseEmail
        e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
        e is ApiException.NetworkError || e is ApiException.RateLimit -> e.toUserMessage()
        else -> e.toUserMessage(Res.string.failedToSendCode)
    }

private const val SERVICE_UNAVAILABLE = 503

private fun verifyError(e: Throwable): StringResource =
    when {
        e is ApiException.Unauthorized || e is ApiException.ValidationError -> Res.string.invalidOtp
        e is ApiException.NotFound -> Res.string.invalidOtp
        e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
        else -> e.toUserMessage(Res.string.verificationFailed)
    }
