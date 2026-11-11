package shifa.doctor.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.AuthEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountDeleted
import shifa.doctor.generated.resources.accountDisabledBanner
import shifa.doctor.generated.resources.enterEmailOrPhone
import shifa.doctor.generated.resources.invalidCredentials
import shifa.doctor.generated.resources.minimum6Characters
import shifa.doctor.generated.resources.notDoctorAccount
import shifa.doctor.generated.resources.pinLockoutBanner
import shifa.doctor.generated.resources.requestTimeout
import shifa.doctor.generated.resources.sessionExpiredBanner
import shifa.doctor.generated.resources.signedOutInactivity
import shifa.doctor.generated.resources.somethingWentWrong
import shifa.doctor.generated.resources.youveSignedOut

/** What the banner says and how it looks; strings are kept as keys so a language switch re-renders them (flow §8). */
data class LoginBanner(
    val text: StringResource,
    val error: Boolean,
    val requestId: String? = null,
    // A calm note, not a warning ("You've signed out"): the design's teal banner.
    val neutral: Boolean = false,
)

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val identifierError: StringResource? = null,
    val passwordError: StringResource? = null,
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val banner: LoginBanner? = null,
    val signedIn: Session? = null,
)

/** AUTH-02 (flow 01/02, 01 §2.1). */
class LoginViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val sessions: SessionRepository = sessionRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    // Why the last session ended by itself, shown once (flow 01/14).
    private val _state =
        MutableStateFlow(LoginUiState(banner = sessions.ended.replayCache.lastOrNull()?.let(::reasonBanner)))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onIdentifier(value: String) = _state.update { it.copy(identifier = value, identifierError = null) }

    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        // Flow §9: identifier not empty after trim; password 6+ characters after trim. The password is still sent
        // exactly as typed (Flutter trimmed what it sent, so a password with spaces around it never worked).
        val idError = Res.string.enterEmailOrPhone.takeIf { s.identifier.isBlank() }
        val pwError = Res.string.minimum6Characters.takeIf { s.password.trim().length < MIN_PASSWORD }
        if (idError != null || pwError != null) {
            tracker.track(AuthEvents.SignInFailed(METHOD, "invalid_input"))
            _state.update { it.copy(identifierError = idError, passwordError = pwError) }
            return
        }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result = auth.login(s.identifier, s.password).mapCatching { sessions.signIn(it).getOrThrow() }
            result.fold(
                { tracker.track(AuthEvents.SignInSucceeded(METHOD, AuthEvents.role(it))) },
                { tracker.track(AuthEvents.SignInFailed(METHOD, failureReason(it))) },
            )
            _state.update {
                result.fold(
                    onSuccess = { session -> it.copy(loading = false, signedIn = session) },
                    onFailure = {
                            e ->
                        it.copy(
                            loading = false,
                            banner = LoginBanner(errorText(e), error = true, requestId = e.requestId),
                        )
                    },
                )
            }
        }
    }

    // A wrong password is a 401 (or 400) on this public path: say so instead of the backend's text.
    private fun errorText(e: Throwable): StringResource =
        when {
            e is ApiException.Unauthorized || e is ApiException.ValidationError -> Res.string.invalidCredentials
            // app=doctor refused the account (a 403 that is not a disabled account).
            e is ApiException.Forbidden && e.code != "ACCOUNT_DISABLED" -> Res.string.notDoctorAccount
            e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
            e is IllegalArgumentException -> Res.string.somethingWentWrong // a token the app can't use (wrong role)
            else -> e.toUserMessage(Res.string.somethingWentWrong)
        }

    // The same cases as [errorText], as fixed words.
    private fun failureReason(e: Throwable): String =
        when {
            e is ApiException.Unauthorized || e is ApiException.ValidationError -> "invalid_credentials"
            e is ApiException.Forbidden && e.code != "ACCOUNT_DISABLED" -> "not_doctor_account"
            e is IllegalArgumentException -> "unusable_token"
            else -> e.toFailureReason()
        }

    private fun reasonBanner(reason: SessionEndReason) =
        LoginBanner(
            when (reason) {
                SessionEndReason.AccountDisabled -> Res.string.accountDisabledBanner
                SessionEndReason.SignedOut -> Res.string.youveSignedOut
                SessionEndReason.PinLockout -> Res.string.pinLockoutBanner
                SessionEndReason.Inactivity -> Res.string.signedOutInactivity
                SessionEndReason.AccountDeleted -> Res.string.accountDeleted
                else -> Res.string.sessionExpiredBanner
            },
            error = reason == SessionEndReason.AccountDisabled || reason == SessionEndReason.PinLockout,
            neutral = reason == SessionEndReason.SignedOut || reason == SessionEndReason.AccountDeleted,
        )

    private companion object {
        const val MIN_PASSWORD = 6
        const val METHOD = "password"
    }
}
