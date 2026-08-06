// lib/features/patients/presentation/document_viewer_screen.dart
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdfx/pdfx.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';

// On web, use iframe-based PDF viewer (pdfx is native-oriented).
import 'package:shifa_doc_app_v1/features/patients/presentation/pdf_viewer_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/patients/presentation/pdf_viewer_web.dart' as pdf_viewer;

/// In-app document viewer: opens PDF or image inside the app (no download, no external app).
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
  ConsumerState<DocumentViewerScreen> createState() =>
      _DocumentViewerScreenState();
}

class _DocumentViewerScreenState extends ConsumerState<DocumentViewerScreen> {
  Uint8List? _bytes;
  String? _filename;
  String? _contentType;
  String? _error;
  bool _loading = true;
  PdfControllerPinch? _pdfController;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _pdfController?.dispose();
    super.dispose();
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

    if (result == null || result.bytes.isEmpty) {
      setState(() {
        _loading = false;
        _error = '';
      });
      return;
    }

    final bytes = result.bytes;
    final isImage = _isImage(
      bytes: bytes,
      filename: result.filename,
      contentType: result.contentType,
    );

    _pdfController?.dispose();
    _pdfController = null;
    if (!isImage && !kIsWeb) {
      _pdfController = PdfControllerPinch(
        document: PdfDocument.openData(bytes),
        initialPage: 1,
      );
    }

    setState(() {
      _loading = false;
      _bytes = bytes;
      _filename = result.filename;
      _contentType = result.contentType;
      _error = null;
    });
  }

  /// True when the underlying bytes are an image we can render with
  /// `Image.memory`. We trust the byte signature first (most reliable),
  /// then the response Content-Type, and finally the filename extension.
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
    return ext == 'jpg' ||
        ext == 'jpeg' ||
        ext == 'png' ||
        ext == 'gif' ||
        ext == 'webp';
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
          : (l10n.translate('couldNotLoadDocument'));
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
                const SizedBox(height: 16),
                TextButton(
                  onPressed: () {
                    setState(() {
                      _loading = true;
                      _error = null;
                    });
                    _load();
                  },
                  child: Text(l10n.retry),
                ),
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
              : _buildNativePdfView(),
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
          errorBuilder: (_, __, ___) =>
              const Icon(Icons.broken_image, size: 48),
        ),
      ),
    );
  }

  Widget _buildNativePdfView() {
    final controller = _pdfController;
    if (controller == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return PdfViewPinch(controller: controller);
  }
}
