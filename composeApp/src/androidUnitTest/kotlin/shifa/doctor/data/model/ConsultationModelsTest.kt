package shifa.doctor.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Ported from Flutter's `consultation_notes_api_test.dart` and `scribe_documentation_compose_test.dart`, plus edges. */
class ConsultationModelsTest {
    @Test
    fun `manual notes join for the appointment notes field, Shifa AI's left out`() {
        val notes =
            listOf(
                ConsultationNote(
                    1,
                    "Scribe summary",
                    source = ConsultationNote.AI_DRAFT,
                    createdAt = "2026-10-19T10:00:00Z",
                ),
                ConsultationNote(2, "Patient reports mild pain.", createdAt = "2026-10-19T10:05:00Z"),
                ConsultationNote(3, "Added ibuprofen.", createdAt = "2026-10-19T10:06:00Z"),
                ConsultationNote(4, "  "),
            )
        assertThat(joinedManualNotes(notes)).isEqualTo("Patient reports mild pain.\n\nAdded ibuprofen.")
    }

    @Test
    fun `the documentation text puts the scribe before the doctor's notes`() {
        val scribe = ConsultationNote(1, "AI protocol and transcript", source = ConsultationNote.AI_DRAFT)
        val doctor = ConsultationNote(2, "Follow up next week")
        val text = composeVisitDocumentationText("From Shifa AI", "Doctor notes", listOf(doctor, scribe))
        assertThat(text.indexOf("AI protocol")).isLessThan(text.indexOf("Follow up next week"))
        assertThat(text).contains("From Shifa AI")
        assertThat(text).contains("Doctor notes")
        // Drafts join the scribe's block, extra notes the doctor's; an empty block is left out.
        assertThat(
            composeVisitDocumentationText(
                "AI",
                "Doctor",
                emptyList(),
                listOf(DraftNote("d", body = "Draft SOAP")),
                " Typed ",
            ),
        ).isEqualTo("AI\n\nDraft SOAP\n\nDoctor\n\nTyped")
        assertThat(
            composeVisitDocumentationText("AI", "Doctor", listOf(doctor)),
        ).isEqualTo("Doctor\n\nFollow up next week")
        assertThat(composeVisitDocumentationText("AI", "Doctor", emptyList())).isEmpty()
    }

    @Test
    fun `the transcript is cut from the shown text, and an empty body shows the SOAP parts`() {
        assertThat(
            stripScribeTranscript("SUBJECTIVE:\nPain\n\nTRANSCRIPT:\nlong dialogue"),
        ).isEqualTo("SUBJECTIVE:\nPain")
        assertThat(stripScribeTranscript("no transcript here ")).isEqualTo("no transcript here")
        assertThat(stripScribeTranscript("transcript: only")).isEmpty()
        assertThat(ConsultationNote(1, " ", "S", null, "P").displayText).isEqualTo("S\n\nP")
    }
}
