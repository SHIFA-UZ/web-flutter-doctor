// Web-only: display PDF in an iframe using HtmlElementView (webview_flutter does not support web).
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
// ignore: avoid_web_libraries_in_flutter
import 'dart:html' as html;
// ignore: avoid_web_libraries_in_flutter
import 'dart:ui_web' as ui;

class PdfViewerWeb extends StatefulWidget {
  const PdfViewerWeb({super.key, required this.bytes});

  final Uint8List bytes;

  @override
  State<PdfViewerWeb> createState() => _PdfViewerWebState();
}

class _PdfViewerWebState extends State<PdfViewerWeb> {
  static int _viewCounter = 0;
  late final String _viewType;

  @override
  void initState() {
    super.initState();
    _viewType = 'shifa-pdf-${_viewCounter++}';
    _registerView();
  }

  void _registerView() {
    final base64 = base64Encode(widget.bytes);
    final dataUrl = 'data:application/pdf;base64,$base64';
    if (ui.platformViewRegistry.registerViewFactory(
      _viewType,
      (int viewId) {
        final iframe = html.IFrameElement()
          ..src = dataUrl
          ..style.border = 'none'
          ..style.display = 'block'
          ..style.width = '100%'
          ..style.height = '100%'
          ..style.minHeight = '100%'
          ..allowFullscreen = true;
        return iframe;
      },
    )) {
      // Registration succeeded
    }
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox.expand(
      child: HtmlElementView(viewType: _viewType),
    );
  }
}
