package shifa.doctor.data.tracking.events

import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.subscription.DoctorTier

/**
 * Group B9: profile, settings, subscription and billing, security, legal, privacy (`ui/profile`). Only this group's
 * job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never profile values: the names of the changed fields only. Owns the usage-data switch (`Tracking.setOptOut`, D3).
 */
object ProfileEvents {
    /**
     * A profile form saved. [section]: `personal_info` / `contact` / `extended` / `invoicing`. [changedFields]: the
     * names of the changed fields (`first_name`, `last_name`, `dob`, `gender`, `profession`; `phone`, `email`,
     * `address`, `map_pin`; `biography`, `telegram`, `instagram`; `billing_name`, `billing_email`, `iban`, `tax_id`),
     * never their values.
     */
    data class SectionSaved(
        val section: String,
        val changedFields: List<String>,
    ) : TrackEvent("profile_section_saved", "section" to section, "changed_fields" to changedFields)

    /**
     * A profile write that did not land. [section]: as [SectionSaved], or `time_zone` / `tags` / `certificates`.
     * [reason]: a `toFailureReason()` word.
     */
    data class SectionSaveFailed(
        val section: String,
        val reason: String,
    ) : TrackEvent("profile_section_save_failed", "section" to section, "reason" to reason)

    /** Edits thrown away at the discard question. [section]: as [SectionSaved]. */
    data class EditDiscarded(
        val section: String,
    ) : TrackEvent("profile_edit_discarded", "section" to section)

    /** [kind]: `photo` / `certificate_image` / `certificate_pdf`. */
    data class FileUploaded(
        val kind: String,
    ) : TrackEvent("profile_file_uploaded", "kind" to kind)

    /** [kind]: as [FileUploaded]; [reason]: `too_large` or a `toFailureReason()` word. */
    data class FileUploadFailed(
        val kind: String,
        val reason: String,
    ) : TrackEvent("profile_file_upload_failed", "kind" to kind, "reason" to reason)

    /** The camera permission was refused while picking. [section]: `personal_info` / `extended`. */
    data class CameraDenied(
        val section: String,
    ) : TrackEvent("profile_camera_denied", "section" to section)

    /** The service tags saved (a tag added or removed). [tagCount]: `countBucket` of the list. */
    data class TagsSaved(
        val tagCount: String,
    ) : TrackEvent("profile_tags_saved", "tag_count" to tagCount)

    data object CertificateDeleted : TrackEvent("profile_certificate_deleted")

    /** The practice time zone set (IANA ids, not personal). [fromZone] is null for a first zone. */
    data class TimeZoneChanged(
        val fromZone: String?,
        val toZone: String,
    ) : TrackEvent("practice_time_zone_changed", "from_zone" to fromZone, "to_zone" to toZone)

    /** "Change practice timezone?" answered with Cancel. */
    data object TimeZoneChangeCancelled : TrackEvent("practice_time_zone_change_cancelled")

    /**
     * A service of the doctor's own saved. [priceBucket]: `amountBucket` of its first price (null when free).
     * [hasLocationRate]: a price is limited to one location.
     */
    data class ServiceSaved(
        val isNew: Boolean,
        val isFree: Boolean,
        val hasLocationRate: Boolean,
        val priceBucket: String?,
    ) : TrackEvent(
            "profile_service_saved",
            "is_new" to isNew,
            "is_free" to isFree,
            "has_location_rate" to hasLocationRate,
            "price_bucket" to priceBucket,
        )

    /** [reason]: a `toFailureReason()` word. */
    data class ServiceSaveFailed(
        val isNew: Boolean,
        val reason: String,
    ) : TrackEvent("profile_service_save_failed", "is_new" to isNew, "reason" to reason)

    data object ServiceDeleted : TrackEvent("profile_service_deleted")

    data class ServiceDeleteFailed(
        val reason: String,
    ) : TrackEvent("profile_service_delete_failed", "reason" to reason)

    /** [groupCount]: `countBucket` of the groups after the save. */
    data class ServiceGroupsSaved(
        val groupCount: String,
    ) : TrackEvent("profile_service_groups_saved", "group_count" to groupCount)

    data class ServiceGroupsSaveFailed(
        val reason: String,
    ) : TrackEvent("profile_service_groups_save_failed", "reason" to reason)

    /**
     * The Stripe checkout page for a plan was handed to the browser. [plan]: the plan code as the backend spells it
     * (`PRO`, `PREMIUM`); [fromTier]: the plan held then; [priceBucket]: `amountBucket` of the plan's price.
     */
    data class CheckoutOpened(
        val plan: String,
        val fromTier: DoctorTier?,
        val priceBucket: String?,
    ) : TrackEvent(
            "subscription_checkout_opened",
            "plan" to plan,
            "from_tier" to fromTier,
            "price_bucket" to priceBucket,
        )

    /** Back from the checkout: [isTierChanged] once the plan read differs from the one held before; [tier]: now. */
    data class CheckoutReturned(
        val isTierChanged: Boolean,
        val tier: DoctorTier?,
    ) : TrackEvent("subscription_checkout_returned", "is_tier_changed" to isTierChanged, "tier" to tier)

    data object StripeConnectOpened : TrackEvent("stripe_connect_opened")

    /** Back from the Stripe Connect onboarding. */
    data class StripeConnectReturned(
        val hasAccount: Boolean,
    ) : TrackEvent("stripe_connect_returned", "has_account" to hasAccount)

    /**
     * A checkout or onboarding page that could not be opened. [kind]: `checkout` / `connect`; [reason]: `not_opened`
     * (the browser refused the window) or a `toFailureReason()` word.
     */
    data class BillingLinkFailed(
        val kind: String,
        val reason: String,
    ) : TrackEvent("billing_link_failed", "kind" to kind, "reason" to reason)

    data object PasswordChanged : TrackEvent("password_changed")

    /** [reason]: `wrong_current_password` or a `toFailureReason()` word. */
    data class PasswordChangeFailed(
        val reason: String,
    ) : TrackEvent("password_change_failed", "reason" to reason)

    /**
     * The settings block saved. [changed]: `country` / `start_tab` / `two_fa` / `encrypted_docs`; [country] and
     * [startTab] (a `ShellTab` key) are sent only when they changed.
     */
    data class SettingsSaved(
        val changed: List<String>,
        val country: String?,
        val startTab: String?,
    ) : TrackEvent("settings_saved", "changed" to changed, "country" to country, "start_tab" to startTab)

    data class SettingsSaveFailed(
        val reason: String,
    ) : TrackEvent("settings_save_failed", "reason" to reason)

    /** The language picked on the Language screen (Settings or More). */
    data class LanguageChanged(
        val from: AppLanguage,
        val to: AppLanguage,
    ) : TrackEvent("settings_language_changed", "from" to from, "to" to to)

    /** The usage-data switch turned back on (D3). Turning it off is never sent: tracking is off from then on. */
    data object UsageDataEnabled : TrackEvent("usage_data_enabled")

    /** [link]: `terms` / `privacy` / `licences`. */
    data class LegalLinkOpened(
        val link: String,
    ) : TrackEvent("legal_link_opened", "link" to link)

    data object ContractOpened : TrackEvent("contract_opened")

    data class ContractPdfFailed(
        val reason: String,
    ) : TrackEvent("contract_pdf_failed", "reason" to reason)

    data object DiagnosticsCopied : TrackEvent("diagnostics_copied")

    data object DataExportSaved : TrackEvent("data_export_saved")

    /** [reason]: `daily_limit` / `save_failed` (the file could not be written) / a `toFailureReason()` word. */
    data class DataExportFailed(
        val reason: String,
    ) : TrackEvent("data_export_failed", "reason" to reason)

    /** The deletion code e-mailed (Continue on the question). */
    data object AccountDeletionCodeSent : TrackEvent("account_deletion_code_sent")

    /** Refused while obligations are open. [obligations]: `upcoming_appointments` / `active_subscription`. */
    data class AccountDeletionBlocked(
        val obligations: List<String>,
    ) : TrackEvent("account_deletion_blocked", "obligations" to obligations)

    /** [step]: `request` / `confirm`; [reason]: `wrong_code` or a `toFailureReason()` word. */
    data class AccountDeletionFailed(
        val step: String,
        val reason: String,
    ) : TrackEvent("account_deletion_failed", "step" to step, "reason" to reason)

    /** Sent before the session ends (the reset follows). */
    data object AccountDeleted : TrackEvent("account_deleted")

    /** The deletion sheets closed before the end. [step]: `ask` / `code` / `blocked`. */
    data class AccountDeletionCancelled(
        val step: String,
    ) : TrackEvent("account_deletion_cancelled", "step" to step)
}
