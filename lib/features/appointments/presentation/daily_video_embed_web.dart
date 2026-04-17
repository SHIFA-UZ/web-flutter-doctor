// Web-only: embed Daily.co Prebuilt video call in an iframe using HtmlElementView.
// Keeps the video call inside the app instead of opening a new tab.
// ignore: avoid_web_libraries_in_flutter
import 'dart:html' as html;
// ignore: avoid_web_libraries_in_flutter
import 'dart:ui_web' as ui;

import 'package:flutter/material.dart';

/// Embeds Daily.co Prebuilt video call in the Flutter web app.
/// Uses iframe with room URL and token (format: roomUrl?t=token).
class DailyVideoEmbedWeb extends StatefulWidget {
  const DailyVideoEmbedWeb({
    super.key,
    required this.roomUrl,
    required this.token,
  });

  final String roomUrl;
  final String token;

  @override
  State<DailyVideoEmbedWeb> createState() => _DailyVideoEmbedWebState();
}

class _DailyVideoEmbedWebState extends State<DailyVideoEmbedWeb> {
  static int _viewCounter = 0;
  late final String _viewType;

  @override
  void initState() {
    super.initState();
    _viewType = 'shifa-daily-video-${_viewCounter++}';
    _registerView();
  }

  void _registerView() {
    final iframeUrl = '${widget.roomUrl}?t=${widget.token}';
    ui.platformViewRegistry.registerViewFactory(
      _viewType,
      (int viewId) {
        final iframe = html.IFrameElement()
          ..src = iframeUrl
          ..style.border = 'none'
          ..style.width = '100%'
          ..style.height = '100%'
          ..allow = 'camera; microphone; display-capture'
          ..allowFullscreen = true;
        return iframe;
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return HtmlElementView(viewType: _viewType);
  }
}
