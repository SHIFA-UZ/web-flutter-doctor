// lib/features/patients/presentation/document_viewer_screen.dart
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:webview_flutter/webview_flutter.dart';

// On web, webview_flutter does not work; use iframe-based PDF viewer instead.
import 'package:shifa_doc_app_v1/features/patients/presentation/pdf_viewer_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/patients/presentation/pdf_viewer_web.dart' as pdf_viewer;

/// In-app document viewer: opens PDF or image in a browser-like window (no download, no external app).
class DocumentViewerScreen extends ConsumerStatefulWidget {
  const DocumentViewerScreen({
    super.key,
    required this.patientId,
    required this.documentId,
    required this.title,
    this.clinicWorkspaceId,
  });

  final String patientId;
  final String documentId;
  final String title;
  final int? clinicWorkspaceId;

  @override
  ConsumerState<DocumentViewerScreen> createState() => _DocumentViewerScreenState();
}

class _DocumentViewerScreenState extends ConsumerState<DocumentViewerScreen> {
  Uint8List? _bytes;
  String? _filename;
  String? _contentType;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final client = ref.read(apiClientProvider);
    final result = await fetchDocumentDownloadWithClient(
      client: client,
      patientId: widget.patientId,
      documentId: widget.documentId,
      clinicId: widget.clinicWorkspaceId,
    );
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (result != null && result.bytes.isNotEmpty) {
        _bytes = result.bytes;
        _filename = result.filename;
        _contentType = result.contentType;
      } else {
        // Resolved against AppLocalizations in build() since context isn't
        // safe to use synchronously here.
        _error = '';
      }
    });
  }

  /// True when the underlying bytes are an image we can render with
  /// `Image.memory`. We trust the byte signature first (most reliable),
  /// then the response Content-Type, and finally the filename extension.
  /// This avoids falling back to the PDF embed when Content-Disposition is
  /// stripped by CORS or encoded via RFC 5987.
  static bool _isImage({
    required Uint8List bytes,
    String? filename,
    String? contentType,
  }) {
    final mime = detectMimeFromBytes(bytes);
    if (mime != null) return mime.startsWith('image/');
    final ct = contentType?.toLowerCase().split(';').first.trim();
    if (ct != null && ct.startsWith('image/')) return true;
    if (filename == null) return false;
    final ext = filename.toLowerCase().split('.').last;
    return ext == 'jpg' || ext == 'jpeg' || ext == 'png' || ext == 'gif' || ext == 'webp';
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return Scaffold(
        appBar: AppBar(
          leading: appBarBackLeading(context),
          automaticallyImplyLeading: false,
          title: Text(widget.title),
        ),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    if (_error != null) {
      final l10n = AppLocalizations.of(context)!;
      final message = _error!.isNotEmpty
          ? _error!
          : (l10n.translate('couldNotLoadDocument') ?? 'Could not load document');
      return Scaffold(
        appBar: AppBar(
          leading: appBarBackLeading(context),
          automaticallyImplyLeading: false,
          title: Text(widget.title),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.error_outline, size: 48, color: Colors.grey.shade600),
                const SizedBox(height: 16),
                Text(message, textAlign: TextAlign.center),
              ],
            ),
          ),
        ),
      );
    }
    final bytes = _bytes!;
    final isImage = _isImage(
      bytes: bytes,
      filename: _filename,
      contentType: _contentType,
    );

    return Scaffold(
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        title: Text(widget.title),
      ),
      body: isImage
          ? _buildImageView(bytes)
          : kIsWeb
              ? pdf_viewer.PdfViewerWeb(bytes: bytes)
              : _buildPdfView(bytes),
    );
  }

  Widget _buildImageView(Uint8List bytes) {
    return InteractiveViewer(
      minScale: 0.5,
      maxScale: 4,
      child: Center(
        child: Image.memory(
          bytes,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => const Icon(Icons.broken_image, size: 48),
        ),
      ),
    );
  }

  Widget _buildPdfView(Uint8List bytes) {
    final base64 = base64Encode(bytes);
    final html = '''
<!DOCTYPE html>
<html>
<head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;height:100vh;">
<embed type="application/pdf" src="data:application/pdf;base64,$base64" width="100%" height="100%" />
</body>
</html>''';
    final uri = Uri.dataFromString(
      html,
      mimeType: 'text/html',
      encoding: utf8,
    );
    final controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadRequest(uri);
    return WebViewWidget(controller: controller);
  }
}
