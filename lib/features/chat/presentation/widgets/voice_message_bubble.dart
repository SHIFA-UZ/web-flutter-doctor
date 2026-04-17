// lib/features/chat/presentation/widgets/voice_message_bubble.dart
import 'package:flutter/material.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

class VoiceMessageBubble extends StatefulWidget {
  final ChatMessage message;
  final Color brandColor;
  final bool isMine;

  const VoiceMessageBubble({
    Key? key,
    required this.message,
    required this.brandColor,
    required this.isMine,
  }) : super(key: key);

  @override
  State<VoiceMessageBubble> createState() => _VoiceMessageBubbleState();
}

class _VoiceMessageBubbleState extends State<VoiceMessageBubble> {
  final AudioPlayer _audioPlayer = AudioPlayer();
  bool _isPlaying = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;

  @override
  void initState() {
    super.initState();
    _audioPlayer.onPlayerStateChanged.listen((state) {
      setState(() {
        _isPlaying = state == PlayerState.playing;
      });
    });
    _audioPlayer.onDurationChanged.listen((duration) {
      setState(() {
        _duration = duration;
      });
    });
    _audioPlayer.onPositionChanged.listen((position) {
      setState(() {
        _position = position;
      });
    });
    _audioPlayer.onPlayerComplete.listen((_) {
      setState(() {
        _isPlaying = false;
        _position = Duration.zero;
      });
    });
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
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

  Future<void> _togglePlayback() async {
    final audioUrl = widget.message.content.fileUrl;
    if (audioUrl == null) return;

    if (_isPlaying) {
      await _audioPlayer.pause();
    } else {
      if (_position == Duration.zero) {
        await _audioPlayer.play(UrlSource(audioUrl));
      } else {
        await _audioPlayer.resume();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final bubbleColor = widget.isMine ? widget.brandColor : Colors.grey.shade200;
    final duration = widget.message.content.duration != null
        ? Duration(seconds: widget.message.content.duration!)
        : _duration;

    final senderLabel = widget.message.senderRole == SenderRole.doctor
        ? AppLocalizations.of(context)!.doctor
        : AppLocalizations.of(context)!.patient;

    return Container(
      constraints: const BoxConstraints(maxWidth: 280),
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Column(
        crossAxisAlignment: widget.isMine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          // Sender label (only show if not mine)
          if (!widget.isMine)
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
          // Voice message bubble
          Container(
            decoration: BoxDecoration(
              color: bubbleColor,
              borderRadius: BorderRadius.only(
                topLeft: const Radius.circular(16),
                topRight: const Radius.circular(16),
                bottomLeft: Radius.circular(widget.isMine ? 16 : 4),
                bottomRight: Radius.circular(widget.isMine ? 4 : 16),
              ),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Play/Pause button
                IconButton(
                  icon: Icon(
                    _isPlaying ? Icons.pause : Icons.play_arrow,
                    color: widget.isMine ? Colors.white : Colors.black87,
                  ),
                  onPressed: _togglePlayback,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
                const SizedBox(width: 8),
                // Progress bar
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(2),
                        child: LinearProgressIndicator(
                          value: duration.inSeconds > 0
                              ? _position.inSeconds / duration.inSeconds
                              : 0,
                          backgroundColor: widget.isMine
                              ? Colors.white24
                              : Colors.grey.shade300,
                          valueColor: AlwaysStoppedAnimation<Color>(
                            widget.isMine ? Colors.white : widget.brandColor,
                          ),
                          minHeight: 3,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            _formatDuration(_position),
                            style: TextStyle(
                              fontSize: 12,
                              color: widget.isMine ? Colors.white70 : Colors.grey.shade600,
                            ),
                          ),
                          Text(
                            _formatDuration(duration),
                            style: TextStyle(
                              fontSize: 12,
                              color: widget.isMine ? Colors.white70 : Colors.grey.shade600,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                // Timestamp and status
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      _formatTime(widget.message.sentAt.toLocal()),
                      style: TextStyle(
                        fontSize: 11,
                        color: widget.isMine ? Colors.white70 : Colors.grey.shade600,
                      ),
                    ),
                    if (widget.isMine) ...[
                      const SizedBox(height: 2),
                      _buildStatusIcon(widget.message.status),
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
