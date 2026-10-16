import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdfx/pdfx.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_chart_codec.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_fdi_chart.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/pdf_viewer_stub.dart'
    if (dart.library.html)
        'package:shifa_doc_app_v1/features/patients/presentation/pdf_viewer_web.dart'
    as pdf_viewer;
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';

/// Inline PDF/image viewer for Intelligence chat (no separate route).
class IntelligenceInlineDocumentViewer extends ConsumerStatefulWidget {
  const IntelligenceInlineDocumentViewer({
    super.key,
    required this.patientId,
    required this.documentId,
    required this.title,
    this.height = 360,
  });

  final String patientId;
  final String documentId;
  final String title;
  final double height;

  @override
  ConsumerState<IntelligenceInlineDocumentViewer> createState() =>
      _IntelligenceInlineDocumentViewerState();
}

class _IntelligenceInlineDocumentViewerState
    extends ConsumerState<IntelligenceInlineDocumentViewer> {
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
  void didUpdateWidget(covariant IntelligenceInlineDocumentViewer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.documentId != widget.documentId ||
        oldWidget.patientId != widget.patientId) {
      _pdfController?.dispose();
      _pdfController = null;
      setState(() {
        _loading = true;
        _error = null;
        _bytes = null;
      });
      _load();
    }
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
    final l10n = AppLocalizations.of(context)!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          widget.title,
          style: AppDesignSystem.body2(context).copyWith(
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: Container(
            height: widget.height,
            color: Colors.grey.shade100,
            alignment: Alignment.center,
            child: _buildBody(l10n),
          ),
        ),
      ],
    );
  }

  Widget _buildBody(AppLocalizations l10n) {
    if (_loading) {
      return const CircularProgressIndicator(strokeWidth: 2);
    }
    if (_error != null || _bytes == null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              (_error != null && _error!.isNotEmpty)
                  ? _error!
                  : l10n.translate('couldNotLoadDocument'),
              textAlign: TextAlign.center,
              style: AppDesignSystem.caption(context),
            ),
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
      );
    }
    final bytes = _bytes!;
    final isImage = _isImage(
      bytes: bytes,
      filename: _filename,
      contentType: _contentType,
    );
    if (isImage) {
      return InteractiveViewer(
        minScale: 0.5,
        maxScale: 4,
        child: Image.memory(
          bytes,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) =>
              const Icon(Icons.broken_image, size: 40),
        ),
      );
    }
    if (kIsWeb) {
      return pdf_viewer.PdfViewerWeb(bytes: bytes);
    }
    final controller = _pdfController;
    if (controller == null) {
      return const CircularProgressIndicator(strokeWidth: 2);
    }
    return PdfViewPinch(controller: controller);
  }
}

/// Read-only dental FDI chart embedded in Intelligence chat.
class IntelligenceInlineDentalChart extends StatefulWidget {
  const IntelligenceInlineDentalChart({
    super.key,
    required this.brand,
    required this.payload,
  });

  final Color brand;
  final Map<String, dynamic> payload;

  @override
  State<IntelligenceInlineDentalChart> createState() =>
      _IntelligenceInlineDentalChartState();
}

class _IntelligenceInlineDentalChartState
    extends State<IntelligenceInlineDentalChart> {
  late DentalDentition _dentition;
  String? _selectedTooth;
  List<Map<String, dynamic>> _selectedLines = const [];

  @override
  void initState() {
    super.initState();
    _dentition = _parseDentition(widget.payload['dentition']?.toString());
  }

  @override
  void didUpdateWidget(covariant IntelligenceInlineDentalChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.payload != widget.payload) {
      _dentition = _parseDentition(widget.payload['dentition']?.toString());
      _selectedTooth = null;
      _selectedLines = const [];
    }
  }

  static DentalDentition _parseDentition(String? raw) {
    final t = raw?.trim().toLowerCase();
    return t == 'primary' ? DentalDentition.primary : DentalDentition.permanent;
  }

  Map<String, int> get _counts {
    final raw = widget.payload['toothServiceCounts'];
    if (raw is! Map) return const {};
    final out = <String, int>{};
    raw.forEach((k, v) {
      if (v is num) out[k.toString()] = v.toInt();
    });
    return out;
  }

  Map<String, List<Map<String, dynamic>>> get _teeth {
    final doc = widget.payload['documentation'];
    if (doc is! Map) return const {};
    final teeth = doc['teeth'];
    if (teeth is! Map) return const {};
    final out = <String, List<Map<String, dynamic>>>{};
    teeth.forEach((k, v) {
      if (v is! List) return;
      out[k.toString()] = [
        for (final item in v)
          if (item is Map) Map<String, dynamic>.from(item),
      ];
    });
    return out;
  }

  void _onToothTap(String fdi) {
    final lines = _teeth[fdi] ??
        _teeth[DentalChartCodec.normalizeToothKey(fdi)] ??
        const <Map<String, dynamic>>[];
    setState(() {
      _selectedTooth = fdi;
      _selectedLines = lines;
    });
  }

  @override
  Widget build(BuildContext context) {
    final patientName = widget.payload['patientName']?.toString() ?? 'Patient';
    final dateLabel = widget.payload['date']?.toString() ?? '';
    final notes = widget.payload['notes']?.toString();
    final l10n = AppLocalizations.of(context)!;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          [
            patientName,
            if (dateLabel.isNotEmpty) dateLabel,
          ].join(' · '),
          style: AppDesignSystem.body2(context).copyWith(
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: DentalFdiChart(
            brand: widget.brand,
            dentition: _dentition,
            showDentitionToggle: true,
            onDentitionChanged: (d) => setState(() => _dentition = d),
            toothServiceCounts: _counts,
            onToothTap: _onToothTap,
            showTitle: false,
          ),
        ),
        if (_selectedTooth != null) ...[
          const SizedBox(height: 10),
          Text(
            '${l10n.translate('dentalToothServices')} $_selectedTooth',
            style: AppDesignSystem.caption(context).copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
          if (_selectedLines.isEmpty)
            Text(
              l10n.translate('intelNoToothServices'),
              style: AppDesignSystem.caption(context).copyWith(
                color: AppDesignSystem.textSecondary,
              ),
            )
          else
            ..._selectedLines.take(6).map((line) {
              final name = line['name']?.toString() ??
                  line['serviceName']?.toString() ??
                  line['label']?.toString() ??
                  'Service';
              return Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text('• $name', style: AppDesignSystem.caption(context)),
              );
            }),
        ],
        if (notes != null && notes.trim().isNotEmpty) ...[
          const SizedBox(height: 10),
          Text(
            notes.trim(),
            style: AppDesignSystem.caption(context).copyWith(
              color: AppDesignSystem.textSecondary,
            ),
          ),
        ],
      ],
    );
  }
}
