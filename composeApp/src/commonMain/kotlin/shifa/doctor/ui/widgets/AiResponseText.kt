package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.DART_WS

internal enum class AiBlockKind { Heading, Bullet, Paragraph, Spacer }

internal data class AiBlock(
    val kind: AiBlockKind,
    val text: String = "",
)

/**
 * An AI answer as the doctor reads it (AI-02, Flutter's `AiResponseText`): the small markdown subset the backend
 * writes, `#`–`###` and whole-line `**…**` headings (bold, brand, a brand bar on the left), `-` / `*` / `•` / `1.`
 * bullets, `**bold**` inside lines, and one gap per run of blank lines. A single 220+ character paragraph is split into
 * sentences. Selectable for copying. Text-only by design: the boards show answers as plain 14/22 text.
 */
@Composable
fun AiResponseText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ShifaTextStyles.aiText(),
    color: Color = ShifaColors.Text.Primary,
) {
    val blocks = remember(text) { parseBlocks(formatForDisplay(text)) }
    if (blocks.isEmpty()) return
    SelectionContainer(modifier) {
        Column {
            blocks.forEach { b ->
                when (b.kind) {
                    AiBlockKind.Spacer -> Box(Modifier.height(ShifaSize.S10))
                    AiBlockKind.Heading ->
                        Row(Modifier.padding(top = ShifaSize.S12, bottom = ShifaSize.S8).height(IntrinsicSize.Min)) {
                            Box(Modifier.width(ShifaSize.S3).fillMaxHeight().background(ShifaColors.Border.Brand))
                            Text(
                                b.text,
                                Modifier.padding(start = ShifaSize.S8),
                                style = ShifaTextStyles.panelTitle(),
                                color = ShifaColors.Text.BrandStrong,
                            )
                        }
                    AiBlockKind.Bullet ->
                        Row(
                            Modifier.padding(start = ShifaSize.S2, bottom = ShifaSize.S6),
                            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                        ) {
                            Text("•", style = style.copy(fontWeight = FontWeight.Bold), color = color)
                            Text(boldSpans(b.text), Modifier.weight(1f), style = style, color = color)
                        }
                    AiBlockKind.Paragraph ->
                        Text(boldSpans(b.text), Modifier.padding(bottom = ShifaSize.S8), style = style, color = color)
                }
            }
        }
    }
}

/** Line endings normalised; one long single-line answer (> 220 chars, 3+ sentences) becomes a paragraph per sentence. */
internal fun formatForDisplay(value: String): String {
    val text = value.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
    if (text.contains('\n') || text.length <= 220) return text
    val sentences = text.split(SENTENCE_END)
    return if (sentences.size >= 3) sentences.joinToString("\n\n") else text
}

internal fun parseBlocks(text: String): List<AiBlock> {
    val blocks = mutableListOf<AiBlock>()
    for (raw in text.split('\n')) {
        val line = raw.trim()
        if (line.isEmpty()) {
            if (blocks.isNotEmpty() && blocks.last().kind != AiBlockKind.Spacer) blocks += AiBlock(AiBlockKind.Spacer)
            continue
        }
        val heading = HEADING.matchEntire(line) ?: WRAPPED_HEADING.matchEntire(line)
        val bullet = BULLET.matchEntire(line)
        blocks +=
            when {
                heading != null -> AiBlock(AiBlockKind.Heading, heading.groupValues[1].trim())
                bullet != null -> AiBlock(AiBlockKind.Bullet, bullet.groupValues[2])
                else -> AiBlock(AiBlockKind.Paragraph, line)
            }
    }
    return blocks
}

/** `**…**` runs in bold (non-greedy, as Flutter); unmatched markers stay as typed. */
internal fun boldSpans(text: String): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        BOLD.findAll(text).forEach { m ->
            append(text.substring(cursor, m.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            cursor = m.range.last + 1
        }
        append(text.substring(cursor))
    }

private val SENTENCE_END = Regex("(?<=[.!?])$DART_WS+")
private val HEADING = Regex("#{1,3}$DART_WS+(.+)")
private val WRAPPED_HEADING = Regex("\\*\\*([^*]+)\\*\\*")
private val BULLET = Regex("([-*•]|\\d+\\.)$DART_WS+(.*)")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
