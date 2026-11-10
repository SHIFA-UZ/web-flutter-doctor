# Tracking: profile, settings, billing, legal, privacy (B9)

Folder `ui/profile`, events in `data/tracking/events/ProfileEvents.kt`, tests in the existing ViewModel tests under
`androidUnitTest/.../ui/profile/` (one per outcome pair, with `RecordingTracker`). Every ViewModel takes
`tracker: Tracker = Tracking` last, except `TimezoneViewModel`, where it comes before `deviceZone` (its test passes
that as a trailing lambda).

## Events

| Event | Properties | Fired from |
|---|---|---|
| `profile_section_saved` | `section` (`personal_info` / `contact` / `extended` / `invoicing`), `changed_fields` (field names only) | `PersonalInfoViewModel.save`, `ContactViewModel.save`, `ExtendedProfileViewModel.save`, `BillingViewModel.save` |
| `profile_section_save_failed` | `section` (also `time_zone`, `tags`, `certificates`), `reason` | the same saves; `TimezoneViewModel.save`; the tag and certificate list writes |
| `profile_edit_discarded` | `section` | `discard()` of Personal info, Contact, Extended, Billing |
| `profile_file_uploaded` | `kind` (`photo` / `certificate_image` / `certificate_pdf`) | `PersonalInfoViewModel.upload`, `ExtendedProfileViewModel.upload` |
| `profile_file_upload_failed` | `kind`, `reason` (`too_large` or a failure word) | the same |
| `profile_camera_denied` | `section` | `cameraDenied()` of Personal info and Extended |
| `profile_tags_saved` | `tag_count` (`countBucket`) | `ExtendedProfileViewModel.writeTags` (a tag added or removed) |
| `profile_certificate_deleted` | — | `ExtendedProfileViewModel.deleteCertificate` |
| `practice_time_zone_changed` | `from_zone` (null for a first zone), `to_zone` (IANA ids) | `TimezoneViewModel.save` |
| `practice_time_zone_change_cancelled` | — | `TimezoneViewModel.cancelConfirm` |
| `profile_service_saved` | `is_new`, `is_free`, `has_location_rate`, **`price_bucket`** | `ServicesViewModel.save` |
| `profile_service_save_failed` | `is_new`, `reason` | `ServicesViewModel.save` |
| `profile_service_deleted` / `profile_service_delete_failed` | — / `reason` | `ServicesViewModel.delete` (already gone counts as deleted) |
| `profile_service_groups_saved` / `profile_service_groups_save_failed` | `group_count` (`countBucket`) / `reason` | `ServicesViewModel.saveGroups` |
| `subscription_checkout_opened` | `plan` (backend code, `PRO`), `from_tier`, **`price_bucket`** | `BillingViewModel.upgrade`, once the checkout URL is in hand |
| `subscription_checkout_returned` | `is_tier_changed`, `tier` | `BillingViewModel.resumed` after a checkout |
| `stripe_connect_opened` | — | `BillingViewModel.connect` |
| `stripe_connect_returned` | `has_account` | `BillingViewModel.resumed` after onboarding |
| `billing_link_failed` | `kind` (`checkout` / `connect`), `reason` (`not_opened` when the browser refused the window) | `BillingViewModel.goOut`, `urlOpened(false)` |
| `password_changed` / `password_change_failed` | — / `reason` (`wrong_current_password` or a failure word) | `SecurityViewModel.change` |
| `settings_saved` | `changed` (`country` / `start_tab` / `two_fa` / `encrypted_docs`), `country` and `start_tab` when changed | `SettingsViewModel.change` (a save that only carried the language sends nothing) |
| `settings_save_failed` | `reason` | `SettingsViewModel.change` |
| `settings_language_changed` | `from`, `to` (`english` / `uzbek` / `uzbek_cyrillic` / `russian`) | `LanguageScreen` callback (no ViewModel) |
| `usage_data_enabled` | — | `SettingsViewModel.setUsageData(true)` |
| `legal_link_opened` | `link` (`terms` / `privacy` / `licences`) | `AboutScreen` callback (no ViewModel) |
| `contract_opened` / `contract_pdf_failed` | — / `reason` | `AboutViewModel.openContract` / `loadPdf` |
| `diagnostics_copied` | — | `AboutScreen` callback |
| `data_export_saved` / `data_export_failed` | — / `reason` (`daily_limit` / `save_failed` / a failure word) | `PrivacyViewModel.export` |
| `account_deletion_code_sent` | — | `PrivacyViewModel.continueDelete` |
| `account_deletion_blocked` | `obligations` (`upcoming_appointments` / `active_subscription`; unknown codes left out) | `PrivacyViewModel.refused` |
| `account_deletion_failed` | `step` (`request` / `confirm`), `reason` (`wrong_code` or a failure word) | `PrivacyViewModel.refused` |
| `account_deleted` | — | `PrivacyViewModel.confirm`, before the session ends (the reset follows) |
| `account_deletion_cancelled` | `step` (`ask` / `code` / `blocked`) | `PrivacyViewModel.closeDelete` |

**Money (`amountBucket`):** `profile_service_saved.price_bucket` (the first price row; null when free) and
`subscription_checkout_opened.price_bucket` (the plan's price; non-UZS plans are `foreign`).

## Opt-out switch (D3)

Settings has a "Share usage data" switch in its own section, on by default, for doctors and clinic staff. The
ViewModel reads `Tracking.optedOut` and writes `Tracking.setOptOut(out)` through two injected lambdas, so its test
needs no platform storage. Only turning it on is tracked (`usage_data_enabled`); turning it off sends nothing.

**Wording to be confirmed by the owner** (strings `usageData`, `usageDataHint`, in en / uz / ru):
"Share usage data" — "Helps us improve the app by sharing how it is used, without names, messages or medical data."
Not in the design: built from the existing settings row, switch and helper-text styles. Screenshot
`composeApp/screenshots/Settings_doctor.png` re-recorded.

## Screens

None sent from here: every profile screen id (`profile`, `profile_*`, `language`) belongs to the shell (A6).

## Left out, and why

- Profile root: "profile viewed (completeness)" and the fix-it chips: the shell sends the view; a completeness range
  would need a new shared bucket. Load failures are covered by `api_error`.
- Map pin set on Contact: it rides on `profile_section_saved` as `map_pin` (nothing is saved until Save).
- Data-export / deletion "asked" taps: the outcomes cover the funnel (`code_sent`, `cancelled` with its step).
- The sign-in screen's language pill is B1's; `settings_language_changed` fires wherever `LanguageScreen` is shown.
- Tier-gate impressions belong to the gated features and the shell.
- The web's notification-permission prompt in Settings: a platform dialog, not a ViewModel outcome.

## Needs from the coordinator

- None.
