import 'package:flutter/material.dart';

/// Readable renderer for AI briefing / streamed responses.
///
/// Supports a small markdown subset used by patient briefing:
/// `## Headings`, `-` / `•` bullets, and `**bold**`.
class AiResponseText extends StatelessWidget {
  const AiResponseText({
    super.key,
    required this.text,
    this.style,
    this.maxWidth = 680,
    this.headingColor,
  });

  final String text;
  final TextStyle? style;
  final double maxWidth;
  final Color? headingColor;

  @override
  Widget build(BuildContext context) {
    final baseStyle = style ??
        const TextStyle(
          fontSize: 13,
          height: 1.5,
          color: Colors.black87,
        );
    final brand = headingColor ?? Theme.of(context).colorScheme.primary;

    final displayText = formatForDisplay(text);
    if (displayText.trim().isEmpty) {
      return const SizedBox.shrink();
    }

    final blocks = parseBlocks(displayText);

    return SelectionArea(
      child: Align(
        alignment: Alignment.topLeft,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (final block in blocks) _buildBlock(block, baseStyle, brand),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBlock(AiTextBlock block, TextStyle baseStyle, Color brand) {
    switch (block.kind) {
      case AiTextBlockKind.spacer:
        return const SizedBox(height: 10);
      case AiTextBlockKind.heading:
        return Padding(
          padding: const EdgeInsets.only(top: 12, bottom: 8),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.only(left: 8),
            decoration: BoxDecoration(
              border: Border(
                left: BorderSide(color: brand, width: 3),
              ),
            ),
            child: SelectableText(
              block.text,
              style: baseStyle.copyWith(
                fontWeight: FontWeight.w700,
                fontSize: (baseStyle.fontSize ?? 13) + 1.5,
                color: brand,
                height: 1.3,
              ),
            ),
          ),
        );
      case AiTextBlockKind.bullet:
        return Padding(
          padding: const EdgeInsets.only(bottom: 6, left: 2),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                  '•',
                  style: baseStyle.copyWith(fontWeight: FontWeight.w700),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: SelectableText.rich(
                  TextSpan(children: inlineSpans(block.text, baseStyle)),
                ),
              ),
            ],
          ),
        );
      case AiTextBlockKind.paragraph:
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: SelectableText.rich(
            TextSpan(children: inlineSpans(block.text, baseStyle)),
          ),
        );
    }
  }

  /// Inserts paragraph breaks for a single long block with no newlines.
  static String formatForDisplay(String value) {
    var text = value.replaceAll('\r\n', '\n').replaceAll('\r', '\n').trimRight();
    if (!text.contains('\n') && text.length > 220) {
      final sentences = text.split(RegExp(r'(?<=[.!?])\s+'));
      if (sentences.length >= 3) {
        text = sentences.join('\n\n');
      }
    }
    return text;
  }

  static List<AiTextBlock> parseBlocks(String text) {
    final blocks = <AiTextBlock>[];
    for (final rawLine in text.split('\n')) {
      final trimmed = rawLine.trim();
      if (trimmed.isEmpty) {
        if (blocks.isEmpty || blocks.last.kind == AiTextBlockKind.spacer) {
          continue;
        }
        blocks.add(const AiTextBlock(kind: AiTextBlockKind.spacer, text: ''));
        continue;
      }

      final headingMatch = RegExp(r'^#{1,3}\s+(.+)$').firstMatch(trimmed);
      if (headingMatch != null) {
        blocks.add(
          AiTextBlock(
            kind: AiTextBlockKind.heading,
            text: headingMatch.group(1)!.trim(),
          ),
        );
        continue;
      }

      final wrappedHeading = RegExp(r'^\*\*([^*]+)\*\*$').firstMatch(trimmed);
      if (wrappedHeading != null) {
        blocks.add(
          AiTextBlock(
            kind: AiTextBlockKind.heading,
            text: wrappedHeading.group(1)!.trim(),
          ),
        );
        continue;
      }

      final bulletMatch = RegExp(r'^([-*•]|\d+\.)\s+(.*)$').firstMatch(trimmed);
      if (bulletMatch != null) {
        blocks.add(
          AiTextBlock(
            kind: AiTextBlockKind.bullet,
            text: bulletMatch.group(2) ?? '',
          ),
        );
        continue;
      }

      blocks.add(AiTextBlock(kind: AiTextBlockKind.paragraph, text: trimmed));
    }
    return blocks;
  }

  static List<InlineSpan> inlineSpans(String text, TextStyle baseStyle) {
    final spans = <InlineSpan>[];
    final regex = RegExp(r'\*\*(.+?)\*\*');
    var cursor = 0;
    for (final match in regex.allMatches(text)) {
      if (match.start > cursor) {
        spans.add(TextSpan(text: text.substring(cursor, match.start), style: baseStyle));
      }
      spans.add(
        TextSpan(
          text: match.group(1),
          style: baseStyle.copyWith(fontWeight: FontWeight.w700),
        ),
      );
      cursor = match.end;
    }
    if (cursor < text.length) {
      spans.add(TextSpan(text: text.substring(cursor), style: baseStyle));
    }
    if (spans.isEmpty) {
      spans.add(TextSpan(text: text, style: baseStyle));
    }
    return spans;
  }
}

enum AiTextBlockKind { heading, bullet, paragraph, spacer }

class AiTextBlock {
  const AiTextBlock({required this.kind, required this.text});

  final AiTextBlockKind kind;
  final String text;
}
