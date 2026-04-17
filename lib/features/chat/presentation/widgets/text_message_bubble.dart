// lib/features/chat/presentation/widgets/text_message_bubble.dart
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:url_launcher/url_launcher_string.dart';

/// Matches http(s) URLs and plain www. URLs for linkification.
final _urlRegex = RegExp(
  r'(https?:\/\/(?:www\.)?[^\s<>\]\)]+|www\.[^\s<>\]\)]+)',
  caseSensitive: false,
);

/// Builds selectable, linkified spans: plain text is copiable, URLs are clickable.
List<InlineSpan> _buildSelectableLinkSpans(
  String text,
  TextStyle baseStyle, {
  required Color linkColor,
  BuildContext? context,
}) {
  final spans = <InlineSpan>[];
  int lastEnd = 0;
  for (final match in _urlRegex.allMatches(text)) {
    if (match.start > lastEnd) {
      spans.add(TextSpan(
        text: text.substring(lastEnd, match.start),
        style: baseStyle,
      ));
    }
    final url = match.group(0)!;
    final href = url.startsWith('www.') ? 'https://$url' : url;
    spans.add(TextSpan(
      text: url,
      style: baseStyle.copyWith(
        color: linkColor,
        decoration: TextDecoration.underline,
      ),
      recognizer: TapGestureRecognizer()
        ..onTap = () {
          launchUrlString(href, mode: LaunchMode.externalApplication).catchError((_) {
            final ctx = context;
            if (ctx != null && ctx.mounted) {
              ScaffoldMessenger.of(ctx).showSnackBar(
                SnackBar(
                  content: Text(
                    AppLocalizations.of(ctx)!.translate('cannotOpenLink') ?? 'Cannot open link',
                  ),
                  backgroundColor: Colors.red,
                ),
              );
            }
            return false;
          });
        },
    ));
    lastEnd = match.end;
  }
  if (lastEnd < text.length) {
    spans.add(TextSpan(
      text: text.substring(lastEnd),
      style: baseStyle,
    ));
  }
  return spans.isEmpty ? [TextSpan(text: text, style: baseStyle)] : spans;
}

class TextMessageBubble extends StatelessWidget {
  final ChatMessage message;
  final Color brandColor;
  final bool isMine;

  const TextMessageBubble({
    Key? key,
    required this.message,
    required this.brandColor,
    required this.isMine,
  }) : super(key: key);

  String _formatTime(DateTime dateTime) {
    return '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}';
  }

  Widget _buildStatusIcon(MessageStatus status) {
    switch (status) {
      case MessageStatus.sending:
        return SizedBox(
          width: 12,
          height: 12,
          child: CircularProgressIndicator(
            strokeWidth: 1.5,
            valueColor: AlwaysStoppedAnimation<Color>(Colors.white70),
          ),
        );
      case MessageStatus.sent:
        return Icon(Icons.check, size: 14, color: Colors.white70);
      case MessageStatus.delivered:
        return Icon(Icons.done_all, size: 14, color: Colors.white70);
      case MessageStatus.read:
        return Icon(Icons.done_all, size: 14, color: Colors.blue.shade300);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bubbleColor = isMine ? brandColor : Colors.grey.shade200;
    final textColor = isMine ? Colors.white : Colors.black87;
    final senderLabel = message.senderRole == SenderRole.doctor
        ? AppLocalizations.of(context)!.doctor
        : AppLocalizations.of(context)!.patient;

    return Container(
      constraints: const BoxConstraints(maxWidth: 520),
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Column(
        crossAxisAlignment: isMine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          // Sender label (only show if not mine)
          if (!isMine)
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 2),
              child: Text(
                senderLabel,
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey.shade600,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          // Message bubble
          Container(
            decoration: BoxDecoration(
              color: bubbleColor,
              borderRadius: BorderRadius.only(
                topLeft: const Radius.circular(16),
                topRight: const Radius.circular(16),
                bottomLeft: Radius.circular(isMine ? 16 : 4),
                bottomRight: Radius.circular(isMine ? 4 : 16),
              ),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (message.content.text != null && message.content.text!.isNotEmpty)
                  SelectableText.rich(
                    TextSpan(
                      children: _buildSelectableLinkSpans(
                        message.content.text!,
                        TextStyle(
                          fontSize: 15,
                          color: textColor,
                          height: 1.4,
                        ),
                        linkColor: isMine ? Colors.white : brandColor,
                        context: context,
                      ),
                    ),
                  ),
                const SizedBox(height: 4),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SelectableText(
                      _formatTime(message.sentAt.toLocal()),
                      style: TextStyle(
                        fontSize: 11,
                        color: isMine ? Colors.white70 : Colors.grey.shade600,
                      ),
                    ),
                    if (isMine) ...[
                      const SizedBox(width: 4),
                      _buildStatusIcon(message.status),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
