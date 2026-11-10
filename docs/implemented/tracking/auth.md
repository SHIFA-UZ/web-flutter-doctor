# Tracking B1: auth and lock

Folders `ui/splash`, `login`, `emailcode`, `register`, `auth`, `applock`; events in
`data/tracking/events/AuthEvents.kt`. Every ViewModel there takes `tracker: Tracker = Tracking` last. Events fire in
the ViewModel once the outcome is known. They never carry an e-mail address, phone number, code, invitation key,
PIN, password or name: the method, mode and kind are fixed words.

## Events

| Event | Properties | Fires in |
|---|---|---|
| `boot_gate_shown` | `gate` (`update` / `maintenance`), `is_retry` | `SplashViewModel.check`, when the launch stops at a gate (again after Try again or the store) |
| `boot_gate_failed` | `reason` (`toFailureReason()`) | `SplashViewModel.check`, when the gate could not be read (launch goes on) |
| `sign_in_succeeded` | `method` (`password` / `email_code` / `sms_code` / `password_reset`), `role` (`doctor` / `staff`) | `LoginViewModel.submit`, `CodeViewModel.verify`, `ResetPasswordViewModel.submit` after `sessions.signIn` |
| `sign_in_failed` | `method`, `reason` (`invalid_input` / `invalid_credentials` / `not_doctor_account` / `wrong_code` / `unusable_token` / `toFailureReason()`) | the same three; `invalid_input` for the on-device checks |
| `auth_code_sent` | `mode` (`sign_in` / `reset` / `sms_sign_in`), `is_resend`, `is_email_fallback` | `EmailEntryViewModel.submit` (`is_resend` false), `CodeViewModel.resend` (true). A code still fresh (cooldown) sends nothing and is not tracked |
| `auth_code_failed` | `mode`, `is_resend`, `reason` (`invalid_input` / `no_account` / `sms_unavailable` / `toFailureReason()`, e.g. `rate_limited`) | the same two |
| `invite_key_verified` | `kind` (`doctor` / `receptionist`) | `VerifyKeyViewModel.submit` |
| `invite_key_failed` | `reason` (`invalid_input` / `invalid_key` / `toFailureReason()`) | `VerifyKeyViewModel.submit` |
| `registration_form_submitted` | `is_existing_patient` | `RegisterViewModel.next`, when step 1 moves on (to 3b or step 2) |
| `registration_form_failed` | `reason` (`invalid_input` / `doctor_exists` / `toFailureReason()`) | `RegisterViewModel.next` |
| `registration_code_sent` | `kind`, `is_resend` | `RegisterViewModel.sendCode` (only when a code really went out) and `resendCode` |
| `registration_code_failed` | `kind`, `is_resend`, `reason` (`toFailureReason()`) | the same |
| `registration_completed` | `kind`, `is_existing_patient`, `has_details`, `is_details_saved` (only with details) | `RegisterViewModel.submit`, once signed in (after the step-2 PATCH) |
| `registration_failed` | `kind`, `reason` (`invalid_input` / `wrong_code` / `doctor_exists` / `ownership_required` / `unusable_token` / `toFailureReason()`) | `RegisterViewModel.register` (on-device checks), `refused`, `submit`. A receptionist's `OWNERSHIP_REQUIRED` without a code is not a failure (the code is asked next) |
| `registration_abandoned` | `kind`, `step` (`form` / `existing_patient` / `details` / `staff`) | `RegisterViewModel.back` on a clean form, `RegisterViewModel.discard` ("Discard" in the question) |
| `sign_out_cancelled` | none | `SignOutViewModel.cancel`, only while the question is open (a confirmed sign-out is `session_ended(signed_out)` from A5) |
| `app_unlocked` | `method` (`pin` / `biometric`) | `LockViewModel.submit`, `onBiometricSuccess` |
| `app_lock_pin_failed` | `source` (`lock_screen` / `settings`), `attempts_left` | `LockViewModel.submit`, `AppLockSettingsViewModel.checkCurrent` |
| `app_lock_locked_out` | `source` | the same, before `sessions.end(PinLockout)` (`session_ended(pin_lockout)` follows) |
| `app_lock_changed` | `action` (`enable` / `disable` / `change_pin`) | `AppLockSettingsViewModel.submit` / `checkCurrent` |
| `app_lock_change_failed` | `action`, `reason` (`mismatch` / `store_failed`) | the same |
| `app_lock_change_cancelled` | `action` | `AppLockSettingsViewModel.closeSheet` with a sheet open (the Set sheet after Change PIN's check counts as `change_pin`) |
| `app_lock_biometric_set` | `is_on` | `AppLockSettingsViewModel.setBiometric` |

No event uses `amountBucket` or `countBucket`.

## Screens

- `register` (B1 owns it): `TrackScreenView(TrackScreen.Register, "kind" to …, "step" to …, step = state.step)` in
  `RegisterScreen` (not in the content composable), so a step change (form → existing patient / details, and back)
  sends it again. `kind`: `doctor` / `receptionist`; `step`: `form` / `existing_patient` / `details` / `staff`.
- `splash`, `sign_in`, `email_entry`, `email_code`, `reset_password`, `verify_key`, `lock` stay `App.kt`'s (A6): no view
  sent from these folders.

## Left out, and why

- **Taps and field edits** (password visibility, picker open/close, search in the clinic and profession pickers, date
  and gender pickers): no outcome; the picker query is search text.
- **"Sign-in submitted"** as its own event: `sign_in_succeeded` + `sign_in_failed` cover every submit.
- **The session-ended banner on Sign In**: `session_ended(reason)` (A5) already says why.
- **The code entered in reset mode**: nothing is checked there; the reset's outcome is `sign_in_*` with
  `method = password_reset` (a wrong code shows there as `wrong_code`).
- **Sign-out asked / confirmed**: confirmed is `session_ended(signed_out)`; asked adds nothing beyond confirmed +
  cancelled. Where the question was opened from (More, Profile, Security, the lock's "Sign out instead") is not known
  to `SignOutViewModel`.
- **The step-2 PATCH failure** as its own event: `registration_completed.is_details_saved = false`.
- **Person properties** (`signed_up_at`, the sign-in method): left out, since identify (A5) runs from the session
  collector and the order against these events is not fixed. Add them through the coordinator if wanted.

## Needs from the coordinator

- None for screen ids. Optional: an "opened from" property for `sign_out_cancelled` would need the caller (shell,
  profile, lock) to pass a source into `SignOutViewModel`; not done, since its hosts are outside B1's folders.
