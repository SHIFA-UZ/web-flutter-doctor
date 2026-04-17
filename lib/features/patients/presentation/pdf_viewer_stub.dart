// Stub for non-web: PDF viewer is only implemented for web via iframe.
// On mobile we use WebView in DocumentViewerScreen instead.
import 'dart:typed_data';
import 'package:flutter/material.dart';

/// Placeholder widget when PdfViewerWeb is imported on non-web (should not be used when kIsWeb is false).
class PdfViewerWeb extends StatelessWidget {
  const PdfViewerWeb({super.key, required this.bytes});

  final Uint8List bytes;

  @override
  Widget build(BuildContext context) {
    return const Center(child: Text('PDF viewer not available on this platform'));
  }
}
