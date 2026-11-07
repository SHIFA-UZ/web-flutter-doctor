package shifa.doctor.ui.widgets

import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Flutter's test/ai_response_text_test.dart, plus the cases its widget relies on.
class AiResponseTextTest {
    @Test
    fun `headings, bullets and leftover paragraphs`() {
        val raw =
            """
            ## Snapshot
            Adult with recurrent caries.

            ## Video consultations
            - Pain on **14**, plan restoration
            - Follow-up in **1 week**
            """.trimIndent()
        val blocks = parseBlocks(raw)
        assertThat(blocks.filter { it.kind == AiBlockKind.Heading }.map { it.text })
            .containsExactly("Snapshot", "Video consultations")
            .inOrder()
        assertThat(blocks.count { it.kind == AiBlockKind.Bullet }).isEqualTo(2)
        assertThat(blocks.single { it.kind == AiBlockKind.Paragraph }.text).isEqualTo("Adult with recurrent caries.")
    }

    @Test
    fun `marked phrases are bold, unmatched markers stay`() {
        val spans = boldSpans("Caries on **14** and **15**.")
        assertThat(spans.text).isEqualTo("Caries on 14 and 15.")
        assertThat(
            spans.spanStyles.map { spans.text.substring(it.start, it.end) },
        ).containsExactly("14", "15").inOrder()
        assertThat(spans.spanStyles.all { it.item.fontWeight == FontWeight.Bold }).isTrue()
        assertThat(boldSpans("2 ** 3").text).isEqualTo("2 ** 3")
    }

    @Test
    fun `a whole bold line is a heading, numbers and stars are bullets, blank runs make one gap`() {
        val blocks = parseBlocks("**Plan**\n1. X-ray\n* Pulp test\n\n\n\nDone.\n\n")
        assertThat(blocks.map { it.kind })
            .containsExactly(
                AiBlockKind.Heading,
                AiBlockKind.Bullet,
                AiBlockKind.Bullet,
                AiBlockKind.Spacer,
                AiBlockKind.Paragraph,
                AiBlockKind.Spacer,
            ).inOrder()
        assertThat(blocks.take(3).map { it.text }).containsExactly("Plan", "X-ray", "Pulp test").inOrder()
        // "**Reason:** follow-up" is not a heading: the bold run doesn't cover the line.
        assertThat(parseBlocks("**Reason:** follow-up").single().kind).isEqualTo(AiBlockKind.Paragraph)
        // A no-break space counts as Dart's \\s does.
        assertThat(parseBlocks("##\u00A0Plan\n-\u00A0X-ray").map { it.kind })
            .containsExactly(AiBlockKind.Heading, AiBlockKind.Bullet)
            .inOrder()
    }

    @Test
    fun `one long paragraph is split into sentences, a short or multi-line one is kept`() {
        val long =
            "The patient reports pain in the lower left molar for three days. " +
                "Cold makes it worse and it wakes her at night! Is it pulpitis? " +
                "An X-ray and a pulp test should settle it before any treatment is planned for the coming week."
        assertThat(long.length).isGreaterThan(220)
        assertThat(formatForDisplay(long).split("\n\n")).hasSize(4)
        assertThat(formatForDisplay(long.replace(". ", ".\u00A0")).split("\n\n")).hasSize(4)
        assertThat(formatForDisplay("Short. Answer. Here.")).isEqualTo("Short. Answer. Here.")
        assertThat(formatForDisplay("a\r\nb\rc  ")).isEqualTo("a\nb\nc")
        assertThat(parseBlocks(formatForDisplay("  \n "))).isEmpty()
    }
}
