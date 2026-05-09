import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';

import 'consultation_document_upload_stub.dart'
    if (dart.library.html) 'consultation_document_upload_web.dart' as drop_reg;
import 'consultation_dropped_file.dart';

/// Tap to pick files; on web, same area accepts drag-and-drop.
class ConsultationDocumentUploadStrip extends ConsumerStatefulWidget {
  const ConsultationDocumentUploadStrip({
    super.key,
    required this.patientId,
    required this.brand,
    required this.enabled,
  });

  final String? patientId;
  final Color brand;
  final bool enabled;

  @override
  ConsumerState<ConsultationDocumentUploadStrip> createState() =>
      _ConsultationDocumentUploadStripState();
}

class _ConsultationDocumentUploadStripState
    extends ConsumerState<ConsultationDocumentUploadStrip> {
  String? _webViewType;
  bool _uploading = false;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      _webViewType = drop_reg.consultationRegisterDropView(
        onDropped: _uploadDropped,
        onBrowseTap: _pickAndUpload,
      );
    }
  }

  Future<void> _pickAndUpload() async {
    if (!widget.enabled || _uploading) return;
    final pid = widget.patientId;
    if (pid == null || pid.isEmpty) return;

    final picked = await FilePicker.platform.pickFiles(
      allowMultiple: true,
      withData: true,
    );
    if (picked == null || picked.files.isEmpty) return;

    final files = <ConsultationDroppedFile>[];
    for (final p in picked.files) {
      final bytes = p.bytes;
      if (bytes == null || bytes.isEmpty) continue;
      final name = p.name;
      if (name.isEmpty) continue;
      files.add(ConsultationDroppedFile(bytes: bytes, name: name));
    }
    if (files.isEmpty && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.consultationUploadNoBytes),
        ),
      );
      return;
    }
    await _uploadDropped(files);
  }

  Future<void> _uploadDropped(List<ConsultationDroppedFile> files) async {
    if (!widget.enabled || _uploading || files.isEmpty) return;
    final pid = widget.patientId;
    if (pid == null || pid.isEmpty) return;

    final l10n = AppLocalizations.of(context)!;
    setState(() => _uploading = true);
    final client = ref.read(apiClientProvider);
    var ok = 0;
    try {
      for (final f in files) {
        try {
          await uploadPatientDocumentWithClient(
            client: client,
            patientId: pid,
            fileBytes: f.bytes,
            fileName: f.name,
            title: f.name,
            category: 'OTHER_MEDICAL',
          );
          ok++;
        } catch (_) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('${l10n.consultationUploadFailed}: ${f.name}'),
                backgroundColor: Colors.red,
              ),
            );
          }
        }
      }
      ref.invalidate(patientDocumentsProvider(pid));
      if (mounted && ok > 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.consultationUploadSuccess(ok))),
        );
      }
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final allowInput = widget.enabled && !_uploading;
    final hint = Text(
      _uploading
          ? l10n.consultationDocumentsUploading
          : l10n.consultationDocumentsDropHint,
      style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
      textAlign: TextAlign.center,
    );

    final indicatorRow = Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (_uploading)
          SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: widget.brand,
            ),
          )
        else
          Icon(Icons.upload_file_outlined, color: widget.brand, size: 22),
        const SizedBox(width: 8),
        Flexible(child: hint),
      ],
    );

    return AbsorbPointer(
      absorbing: !allowInput,
      child: Opacity(
        opacity: widget.enabled ? 1 : 0.45,
        child: SizedBox(
          height: 56,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Stack(
              fit: StackFit.expand,
              children: [
                DecoratedBox(
                  decoration: BoxDecoration(
                    color: widget.brand.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: widget.brand.withValues(alpha: 0.35),
                    ),
                  ),
                ),
                if (kIsWeb && _webViewType != null)
                  HtmlElementView(viewType: _webViewType!),
                if (!kIsWeb)
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: allowInput ? _pickAndUpload : null,
                      child: Center(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: indicatorRow,
                        ),
                      ),
                    ),
                  ),
                if (kIsWeb && _webViewType != null)
                  IgnorePointer(
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: indicatorRow,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
