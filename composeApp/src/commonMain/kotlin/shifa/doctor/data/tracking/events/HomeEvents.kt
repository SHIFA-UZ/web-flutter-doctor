package shifa.doctor.data.tracking.events

import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.tasks.ScheduleMode

/**
 * Group B2: Home, the notifications inbox, Reports and Tasks (`ui/home`, `notifications`, `analytics`, `tasks`).
 * Only this group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never search text, a task's title or instructions, or a notification's text; a notification's type is fine.
 */
object HomeEvents {
    // Home

    /** Home's Video Call chip found a free slot (the booking it opens is B3's). */
    data object HomeVideoSlotFound : TrackEvent("home_video_slot_found")

    /** [reason]: `no_slots` (none in the next 30 days) / a `toFailureReason()` word. */
    data class HomeVideoSlotSearchFailed(
        val reason: String,
    ) : TrackEvent("home_video_slot_search_failed", "reason" to reason)

    /**
     * A patient opened from Home's search page. [results]: how many matched, as a `countBucket`; [searched]: whether
     * anything was typed (the list shows every patient before that). Never the text.
     */
    data class HomeSearchResultOpened(
        val results: String,
        val searched: Boolean,
    ) : TrackEvent("home_search_result_opened", "results" to results, "searched" to searched)

    /** An active task tapped in Home's attention centre. */
    data object HomeAttentionTaskOpened : TrackEvent("home_attention_task_opened")

    // Notifications (a tapped push is the shell's `push_opened`, never this)

    /**
     * An inbox row, or one on Home, tapped and its target found. [type]: the row's backend type as spelled there
     * (`APPOINTMENT_BOOKED`), `other` when it isn't a known backend type, nothing else from it; [target]: `appointment` / `patient` / `document` / `chat` /
     * `partnership` / `task` / `inbox`, or `none` when nothing opened (the appointment is gone, its read failed, or
     * the row leads nowhere); [source]: `inbox` / `home_attention` / `home_activity`.
     */
    data class NotificationOpened(
        val type: String,
        val target: String,
        val source: String,
    ) : TrackEvent("notification_opened", "type" to type, "target" to target, "source" to source)

    data class NotificationsFilterSelected(
        val filter: NotificationFilter,
    ) : TrackEvent("notifications_filter_selected", "filter" to filter)

    /** [unread]: the unread rows shown before, as a `countBucket`. */
    data class NotificationsMarkedAllRead(
        val unread: String,
    ) : TrackEvent("notifications_marked_all_read", "unread" to unread)

    data class NotificationsMarkAllFailed(
        val reason: String,
    ) : TrackEvent("notifications_mark_all_failed", "reason" to reason)

    /** NOTIF-04: a colleague's request to see a patient's documents, approved or declined. */
    data class AccessRequestDecided(
        val approved: Boolean,
    ) : TrackEvent("access_request_decided", "approved" to approved)

    data class AccessRequestDecideFailed(
        val approved: Boolean,
        val reason: String,
    ) : TrackEvent("access_request_decide_failed", "approved" to approved, "reason" to reason)

    // Reports (the practice analytics card on Home and the Reports page). No money is sent.

    /**
     * The doctor chose a period. [source]: `home` / `reports`; [days]: its length; [endsToday]: a "last N days"
     * period rather than one in the past.
     */
    data class ReportRangeChanged(
        val source: String,
        val days: Int,
        val endsToday: Boolean,
    ) : TrackEvent("report_range_changed", "source" to source, "days" to days, "ends_today" to endsToday)

    /** The CSV reached the share sheet. [days]: the exported period's length. */
    data class ReportExported(
        val source: String,
        val days: Int,
    ) : TrackEvent("report_exported", "source" to source, "days" to days)

    /** [reason]: `incomplete` (a block could not be read) / `share_unavailable` (no share sheet). */
    data class ReportExportFailed(
        val source: String,
        val reason: String,
    ) : TrackEvent("report_export_failed", "source" to source, "reason" to reason)

    // Tasks (remote care). Never a task's title, description, labels or the patient.

    /** A task created for a patient. [slots]: check-ins a day (1–15); [fromTemplate]: opened from a template. */
    data class TaskCreated(
        val category: TaskCategory,
        val scheduleMode: ScheduleMode,
        val slots: Int,
        val fromTemplate: Boolean,
    ) : TrackEvent(
            "task_created",
            "category" to category,
            "schedule_mode" to scheduleMode,
            "slots" to slots,
            "from_template" to fromTemplate,
        )

    /**
     * [reason]: `invalid` (with [problems], the form's failed checks as `TaskFormProblem` track names: `no_patient`,
     * `no_name`, `no_slots`, `no_end_date`, `end_before_start`, `bad_duration`) / a `toFailureReason()` word.
     */
    data class TaskCreateFailed(
        val reason: String,
        val problems: List<String>? = null,
    ) : TrackEvent("task_create_failed", "reason" to reason, "problems" to problems)

    data object TaskCancelled : TrackEvent("task_cancelled")

    data class TaskCancelFailed(
        val reason: String,
    ) : TrackEvent("task_cancel_failed", "reason" to reason)
}
