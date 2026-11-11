package shifa.doctor.data.tracking.events

import shifa.doctor.data.chat.ConversationSort

/**
 * Group B7: chat and Shifa Intelligence (`ui/chat`, `intelligence`). Only this group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a message, prompt, reply, transcript, file name or the other party's name; kinds, counts as ranges, durations
 * and card types are fine. Bookings and moves made from a Shifa AI card reuse [CalendarEvents] with `entry =
 * "intelligence"`.
 */
object ChatEvents {
    // --- Chat (ui/chat) ---

    /** A message reached the server. [kind]: `text` / `image` / `document` / `voice`; [isRetry]: after "Tap to retry". */
    data class ChatMessageSent(
        val kind: String,
        val isRetry: Boolean,
    ) : TrackEvent("chat_message_sent", "kind" to kind, "is_retry" to isRetry)

    /**
     * A message was not sent. [kind]: as [ChatMessageSent]; [reason]: `too_large` (over 20 MB) / `file_type` (not
     * pdf, doc, docx or an image) / `too_short` (a voice note under a second) / `read_failed` (the picked file or the
     * recording couldn't be read) / a `toFailureReason()` word (upload or send failed).
     */
    data class ChatMessageFailed(
        val kind: String,
        val reason: String,
    ) : TrackEvent("chat_message_failed", "kind" to kind, "reason" to reason)

    /** A conversation started (or found) from the Chat tab's user search. [isDoctor]: with a colleague. */
    data class ChatConversationStarted(
        val isDoctor: Boolean,
    ) : TrackEvent("chat_conversation_started", "is_doctor" to isDoctor)

    /** [reason]: a `toFailureReason()` word; [entry]: `search` (the Chat tab) / `patient` ("Send message" on a patient). */
    data class ChatConversationStartFailed(
        val reason: String,
        val entry: String,
    ) : TrackEvent("chat_conversation_start_failed", "reason" to reason, "entry" to entry)

    /** The user search settled (300 ms after typing). [resultCount]: a `countBucket`; never the text. */
    data class ChatUsersSearched(
        val resultCount: String,
    ) : TrackEvent("chat_users_searched", "result_count" to resultCount)

    /** The conversation list's order picked: `newest` / `oldest` / `unread_newest` / `unread_oldest`. */
    data class ChatListSorted(
        val sort: ConversationSort,
    ) : TrackEvent("chat_list_sorted", "sort" to sort)

    // --- Shifa Intelligence (ui/intelligence) ---

    /**
     * Shifa answered. [input]: `typed` / `starter` (a welcome card) / `voice` / `card_answer` (a cancellation card's
     * yes or no); [cardType]: the card's type as the backend names it (`schedule`, `propose_appointment`, …), `other`
     * for a type the app doesn't know, null for words only; [hasScheduleChange]: Shifa itself booked, moved or
     * cancelled visits in this answer; [seconds]: from the question to the finished answer; [hasPatientContext]: the
     * chat is about one patient.
     */
    data class IntelligenceReplyReceived(
        val input: String,
        val cardType: String?,
        val hasScheduleChange: Boolean,
        val seconds: Int,
        val isRetry: Boolean,
        val hasPatientContext: Boolean,
    ) : TrackEvent(
            "intelligence_reply_received",
            "input" to input,
            "card_type" to cardType,
            "has_schedule_change" to hasScheduleChange,
            "seconds" to seconds,
            "is_retry" to isRetry,
            "has_patient_context" to hasPatientContext,
        )

    /**
     * No answer. [reason]: `empty` (neither words nor a card) / `ai_unavailable` / a `toFailureReason()` word
     * (`rate_limited` is the daily limit, `timeout` "took too long"); [input]: as [IntelligenceReplyReceived].
     */
    data class IntelligenceReplyFailed(
        val reason: String,
        val input: String,
        val isRetry: Boolean,
    ) : TrackEvent("intelligence_reply_failed", "reason" to reason, "input" to input, "is_retry" to isRetry)

    /** The answer being written was stopped (the mic, leaving the chat, another chat). [input]: as above. */
    data class IntelligenceReplyStopped(
        val input: String,
    ) : TrackEvent("intelligence_reply_stopped", "input" to input)

    /** The draft-facts card's "Document everything" went through. [factCount]: a `countBucket` of the facts saved. */
    data class IntelligenceFactsDocumented(
        val factCount: String,
    ) : TrackEvent("intelligence_facts_documented", "fact_count" to factCount)

    /** [reason]: a `toFailureReason()` word. */
    data class IntelligenceFactsDocumentFailed(
        val reason: String,
    ) : TrackEvent("intelligence_facts_document_failed", "reason" to reason)

    /** A booking or reschedule proposal's Cancel: nothing is booked. */
    data object IntelligenceProposalDismissed : TrackEvent("intelligence_proposal_dismissed")

    /** The cancellation card's "Yes, cancel" ([isConfirmed]) or "Keep them"; the backend cancels, not the app. */
    data class IntelligenceCancelAnswered(
        val isConfirmed: Boolean,
    ) : TrackEvent("intelligence_cancel_answered", "is_confirmed" to isConfirmed)

    data object IntelligenceConversationRenamed : TrackEvent("intelligence_conversation_renamed")

    data object IntelligenceConversationDeleted : TrackEvent("intelligence_conversation_deleted")

    /** The open chat went to the share sheet as a `.txt`. */
    data object IntelligenceConversationExported : TrackEvent("intelligence_conversation_exported")

    /** [reason]: `empty` (nothing to export yet) / `share_failed` (the share sheet couldn't open). */
    data class IntelligenceConversationExportFailed(
        val reason: String,
    ) : TrackEvent("intelligence_conversation_export_failed", "reason" to reason)

    // --- Shifa Intelligence voice (VoiceSession) ---

    /** A take was transcribed and goes to Shifa as a question. [mode]: `hands_free` (the mic) / `dictation`. */
    data class IntelligenceVoiceTranscribed(
        val mode: String,
    ) : TrackEvent("intelligence_voice_transcribed", "mode" to mode)

    /**
     * A take that asked nothing. [reason]: `no_speech` / `mic_unavailable` (the recorder didn't start) / `mic_denied`
     * / `pro_required` (below PRO the buttons only say so) / `background` (the app left while recording) / a
     * `toFailureReason()` word (the transcription failed: `forbidden`, `rate_limited`, `timeout`, …). [mode]: as
     * [IntelligenceVoiceTranscribed], null when no take was running (`mic_denied`, `pro_required`).
     */
    data class IntelligenceVoiceFailed(
        val reason: String,
        val mode: String?,
    ) : TrackEvent("intelligence_voice_failed", "reason" to reason, "mode" to mode)

    /** The dictation pill's ✕: the take was dropped. */
    data object IntelligenceVoiceDiscarded : TrackEvent("intelligence_voice_discarded")

    /** A take that only ended the conversation ("that's it", "rahmat", …): nothing went to Shifa. */
    data object IntelligenceVoiceSignedOff : TrackEvent("intelligence_voice_signed_off")
}
