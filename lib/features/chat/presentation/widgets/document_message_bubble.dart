// lib/features/chat/presentation/widgets/document_message_bubble.dart
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

class DocumentMessageBubble extends StatelessWidget {
  final ChatMessage message;
  final Color brandColor;
  final bool isMine;

  const DocumentMessageBubble({
    Key? key,
    required this.message,
    required this.brandColor,
    required this.isMine,
  }) : super(key: key);

  String _formatFileSize(int? bytes) {
    if (bytes == null) return '';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

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

  IconData _getFileIcon(String? fileName) {
    if (fileName == null) return Icons.insert_drive_file;
    final ext = fileName.toLowerCase().split('.').last;
    switch (ext) {
      case 'pdf':
        return Icons.picture_as_pdf;
      case 'doc':
      case 'docx':
        return Icons.description;
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
        return Icons.image;
      default:
        return Icons.insert_drive_file;
    }
  }

  Future<void> _openDocument(BuildContext context, String url) async {
    try {
      if (await canLaunchUrlString(url)) {
        await launchUrlString(url, mode: LaunchMode.externalApplication);
      } else {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.cannotOpenDocumentUrl),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (e) {
      debugPrint('Error opening document: $e');
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.somethingWentWrong),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bubbleColor = isMine ? brandColor : Colors.grey.shade200;
    final textColor = isMine ? Colors.white : Colors.black87;
    final l10n = AppLocalizations.of(context)!;
    final fileName = message.content.fileName ?? l10n.document;
    final fileSize = message.content.fileSize;
    final fileUrl = message.content.fileUrl;

    final senderLabel = message.senderRole == SenderRole.doctor
        ? AppLocalizations.of(context)!.doctor
        : AppLocalizations.of(context)!.patient;

    return Container(
      constraints: const BoxConstraints(maxWidth: 320),
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
          // Document card
          GestureDetector(
            onTap: fileUrl != null ? () => _openDocument(context, fileUrl) : null,
            child: Container(
              decoration: BoxDecoration(
                color: bubbleColor,
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(16),
                  topRight: const Radius.circular(16),
                  bottomLeft: Radius.circular(isMine ? 16 : 4),
                  bottomRight: Radius.circular(isMine ? 4 : 16),
                ),
              ),
              padding: const EdgeInsets.all(12),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // File icon
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: isMine ? Colors.white24 : Colors.grey.shade300,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(
                      _getFileIcon(fileName),
                      color: isMine ? Colors.white : Colors.grey.shade700,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 12),
                  // File info
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          fileName,
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: textColor,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (fileSize != null) ...[
                          const SizedBox(height: 2),
                          Text(
                            _formatFileSize(fileSize),
                            style: TextStyle(
                              fontSize: 12,
                              color: isMine ? Colors.white70 : Colors.grey.shade600,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  // Timestamp and status
                  Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _formatTime(message.sentAt.toLocal()),
                        style: TextStyle(
                          fontSize: 11,
                          color: isMine ? Colors.white70 : Colors.grey.shade600,
                        ),
                      ),
                      if (isMine) ...[
                        const SizedBox(height: 2),
                        _buildStatusIcon(message.status),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
