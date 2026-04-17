import 'package:flutter/material.dart';

/// Readable renderer for streamed AI plain-text responses.
///
/// - Preserves explicit line breaks from the backend stream.
/// - Adds visual spacing between paragraphs.
/// - Renders basic bullet/numbered list lines with indentation.
/// - Applies display-only fallback sentence breaks when content is one long block.
class AiResponseText extends StatelessWidget {
  const AiResponseText({
    super.key,
    required this.text,
    this.style,
    this.maxWidth = 680,
  });

  final String text;
  final TextStyle? style;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final baseStyle = style ??
        const TextStyle(
          fontSize: 13,
          height: 1.5,
          color: Colors.black87,
        );

    final displayText = _formatForDisplay(text);
    if (displayText.trim().isEmpty) {
      return const SizedBox.shrink();
    }

    final lines = displayText.split('\n');

    return Align(
      alignment: Alignment.topLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: _buildLineWidgets(lines, baseStyle),
        ),
      ),
    );
  }

  List<Widget> _buildLineWidgets(List<String> lines, TextStyle baseStyle) {
    final widgets = <Widget>[];
    for (final line in lines) {
      final trimmed = line.trimRight();
      if (trimmed.trim().isEmpty) {
        widgets.add(const SizedBox(height: 10));
        continue;
      }

      final bulletMatch = RegExp(r'^\s*([-*•]|\d+\.)\s+(.*)$').firstMatch(trimmed);
      if (bulletMatch != null) {
        widgets.add(
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 1),
                  child: Text(
                    bulletMatch.group(1) ?? '•',
                    style: baseStyle,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    bulletMatch.group(2) ?? '',
                    softWrap: true,
                    style: baseStyle,
                  ),
                ),
              ],
            ),
          ),
        );
      } else {
        widgets.add(
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              trimmed,
              softWrap: true,
              style: baseStyle,
            ),
          ),
        );
      }
    }
    return widgets;
  }

  String _formatForDisplay(String value) {
    var text = value.replaceAll('\r\n', '\n').replaceAll('\r', '\n').trimRight();
    if (!text.contains('\n') && text.length > 220) {
      final sentences = text.split(RegExp(r'(?<=[.!?])\s+'));
      if (sentences.length >= 3) {
        text = sentences.join('\n\n');
      }
    }
    return text;
  }
}
