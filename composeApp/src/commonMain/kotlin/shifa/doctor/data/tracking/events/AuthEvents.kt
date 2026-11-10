package shifa.doctor.data.tracking.events

/**
 * Group B1: splash, sign-in, e-mail and SMS codes, password reset, registration, sign-out, app lock (`ui/splash`,
 * `login`, `emailcode`, `register`, `auth`, `applock`). Only this group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never the e-mail address, phone number, code, invitation key or PIN; the sign-in method is a fixed word.
 */
object AuthEvents
