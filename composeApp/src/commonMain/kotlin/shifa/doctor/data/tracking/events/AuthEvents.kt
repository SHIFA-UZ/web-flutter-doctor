package shifa.doctor.data.tracking.events

import shifa.doctor.data.session.Session
import shifa.doctor.data.tracking.TrackingRole

/**
 * Group B1: splash, sign-in, e-mail and SMS codes, password reset, registration, sign-out, app lock (`ui/splash`,
 * `login`, `emailcode`, `register`, `auth`, `applock`). Only this group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never the e-mail address, phone number, code, invitation key or PIN; the sign-in method is a fixed word.
 */
object AuthEvents {
    /** The role a new session signed in with (sign-in only issues doctor and staff sessions). */
    fun role(session: Session): TrackingRole = if (session.isClinicStaff) TrackingRole.Staff else TrackingRole.Doctor

    // ---- Splash ----

    /** The launch stopped at a gate. [gate]: `update` / `maintenance`. [isRetry]: after "Try again" or the store. */
    data class BootGateShown(
        val gate: String,
        val isRetry: Boolean,
    ) : TrackEvent("boot_gate_shown", "gate" to gate, "is_retry" to isRetry)

    /** The gate couldn't be read; launch went on without it. [reason]: a `toFailureReason()` word. */
    data class BootGateFailed(
        val reason: String,
    ) : TrackEvent("boot_gate_failed", "reason" to reason)

    // ---- Sign-in (password, e-mail code, SMS code, password reset) ----

    /**
     * A session was opened. [method]: `password` / `email_code` / `sms_code` / `password_reset`; [role]: `doctor` /
     * `staff`. (Registration has its own `registration_completed`.)
     */
    data class SignInSucceeded(
        val method: String,
        val role: TrackingRole,
    ) : TrackEvent("sign_in_succeeded", "method" to method, "role" to role)

    /**
     * [method] as in [SignInSucceeded]. [reason]: `invalid_input` (checked on the device) / `invalid_credentials` /
     * `not_doctor_account` / `wrong_code` (a wrong or expired code) / `unusable_token` / a `toFailureReason()` word.
     */
    data class SignInFailed(
        val method: String,
        val reason: String,
    ) : TrackEvent("sign_in_failed", "method" to method, "reason" to reason)

    /**
     * A sign-in or reset code went out. [mode]: `sign_in` / `reset` / `sms_sign_in` (as the `email_code` screen's
     * mode). [isResend]: from the code screen. [isEmailFallback]: an SMS sign-in code went to the e-mail instead.
     */
    data class AuthCodeSent(
        val mode: String,
        val isResend: Boolean,
        val isEmailFallback: Boolean,
    ) : TrackEvent("auth_code_sent", "mode" to mode, "is_resend" to isResend, "is_email_fallback" to isEmailFallback)

    /**
     * [mode], [isResend] as in [AuthCodeSent]. [reason]: `invalid_input` / `no_account` / `sms_unavailable` / a
     * `toFailureReason()` word (`rate_limited`, `network`, …).
     */
    data class AuthCodeFailed(
        val mode: String,
        val isResend: Boolean,
        val reason: String,
    ) : TrackEvent("auth_code_failed", "mode" to mode, "is_resend" to isResend, "reason" to reason)

    // ---- Registration (invitation key, form, code, account) ----

    /** The invitation key was accepted. [kind]: `doctor` / `receptionist`. */
    data class InviteKeyVerified(
        val kind: String,
    ) : TrackEvent("invite_key_verified", "kind" to kind)

    /** [reason]: `invalid_input` (empty) / `invalid_key` / a `toFailureReason()` word. */
    data class InviteKeyFailed(
        val reason: String,
    ) : TrackEvent("invite_key_failed", "reason" to reason)

    /**
     * Step 1 (the doctor's form) passed its checks and moved on. [isExistingPatient]: the address or phone has a
     * patient account, so the confirmation view opened instead of step 2.
     */
    data class RegistrationFormSubmitted(
        val isExistingPatient: Boolean,
    ) : TrackEvent("registration_form_submitted", "is_existing_patient" to isExistingPatient)

    /** Step 1 stopped. [reason]: `invalid_input` / `doctor_exists` / a `toFailureReason()` word. */
    data class RegistrationFormFailed(
        val reason: String,
    ) : TrackEvent("registration_form_failed", "reason" to reason)

    /** The registration code went out. [kind]: `doctor` / `receptionist`. [isResend]: from the code sheet. */
    data class RegistrationCodeSent(
        val kind: String,
        val isResend: Boolean,
    ) : TrackEvent("registration_code_sent", "kind" to kind, "is_resend" to isResend)

    /** [kind], [isResend] as in [RegistrationCodeSent]; [reason]: a `toFailureReason()` word. */
    data class RegistrationCodeFailed(
        val kind: String,
        val isResend: Boolean,
        val reason: String,
    ) : TrackEvent("registration_code_failed", "kind" to kind, "is_resend" to isResend, "reason" to reason)

    /**
     * The account exists and is signed in. [kind]: `doctor` / `receptionist`. [isExistingPatient]: a patient account
     * was confirmed (3b). [hasDetails]: step 2 had something filled in; [isDetailsSaved]: its PATCH went through
     * (null without details).
     */
    data class RegistrationCompleted(
        val kind: String,
        val isExistingPatient: Boolean,
        val hasDetails: Boolean,
        val isDetailsSaved: Boolean?,
    ) : TrackEvent(
            "registration_completed",
            "kind" to kind,
            "is_existing_patient" to isExistingPatient,
            "has_details" to hasDetails,
            "is_details_saved" to isDetailsSaved,
        )

    /**
     * `register` refused or its session could not open. [kind] as above. [reason]: `invalid_input` / `wrong_code` /
     * `doctor_exists` / `ownership_required` (the current password is asked) / `unusable_token` (the account exists
     * but its token couldn't be used) / a `toFailureReason()` word.
     */
    data class RegistrationFailed(
        val kind: String,
        val reason: String,
    ) : TrackEvent("registration_failed", "kind" to kind, "reason" to reason)

    /** Left before an account existed. [kind] as above; [step]: `form` / `existing_patient` / `details` / `staff`. */
    data class RegistrationAbandoned(
        val kind: String,
        val step: String,
    ) : TrackEvent("registration_abandoned", "kind" to kind, "step" to step)

    // ---- Sign-out ----

    /** The sign-out question was closed without signing out (a confirmed one is `session_ended(signed_out)`). */
    data object SignOutCancelled : TrackEvent("sign_out_cancelled")

    // ---- App lock ----

    /** [method]: `pin` / `biometric`. */
    data class AppUnlocked(
        val method: String,
    ) : TrackEvent("app_unlocked", "method" to method)

    /**
     * A wrong PIN. [source]: `lock_screen` / `settings` (the PIN asked before turning the lock off or changing it).
     * [attemptsLeft]: tries before the session ends.
     */
    data class AppLockPinFailed(
        val source: String,
        val attemptsLeft: Int,
    ) : TrackEvent("app_lock_pin_failed", "source" to source, "attempts_left" to attemptsLeft)

    /** The last wrong PIN ended the session (`session_ended(pin_lockout)` follows). [source] as above. */
    data class AppLockLockedOut(
        val source: String,
    ) : TrackEvent("app_lock_locked_out", "source" to source)

    /** [action]: `enable` / `disable` / `change_pin`. */
    data class AppLockChanged(
        val action: String,
    ) : TrackEvent("app_lock_changed", "action" to action)

    /** [action] as in [AppLockChanged]; [reason]: `mismatch` (the repeat differed) / `store_failed`. */
    data class AppLockChangeFailed(
        val action: String,
        val reason: String,
    ) : TrackEvent("app_lock_change_failed", "action" to action, "reason" to reason)

    /** The PIN sheet was closed before it finished. [action] as in [AppLockChanged]. */
    data class AppLockChangeCancelled(
        val action: String,
    ) : TrackEvent("app_lock_change_cancelled", "action" to action)

    /** Biometric unlock turned on or off in settings. */
    data class AppLockBiometricSet(
        val isOn: Boolean,
    ) : TrackEvent("app_lock_biometric_set", "is_on" to isOn)
}
