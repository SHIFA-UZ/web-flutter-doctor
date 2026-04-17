// Stub for non-web: DailyVideoEmbedWeb is only used on web.
// On mobile we use CallClient from daily_flutter instead.
import 'package:flutter/material.dart';

/// Placeholder when DailyVideoEmbedWeb is imported on non-web.
/// Should not be used when kIsWeb is false.
class DailyVideoEmbedWeb extends StatelessWidget {
  const DailyVideoEmbedWeb({
    super.key,
    required this.roomUrl,
    required this.token,
  });

  final String roomUrl;
  final String token;

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Text('Video embed not available on this platform'),
    );
  }
}
