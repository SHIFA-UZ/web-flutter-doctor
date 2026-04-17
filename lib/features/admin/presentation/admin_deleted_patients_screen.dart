import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';

import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart' as pdf_downloader;

class AdminDeletedPatientsScreen extends ConsumerStatefulWidget {
  const AdminDeletedPatientsScreen({super.key});

  @override
  ConsumerState<AdminDeletedPatientsScreen> createState() => _AdminDeletedPatientsScreenState();
}

class _AdminDeletedPatientsScreenState extends ConsumerState<AdminDeletedPatientsScreen> {
  final _phoneCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _userIdCtrl = TextEditingController();

  bool _searchLoading = false;
  bool _exportLoading = false;
  bool _pdfLoading = false;
  List<DeletedPatientMatch> _matches = [];
  Map<String, dynamic>? _exportData;
  DeletedPatientMatch? _selectedMatch;

  @override
  void dispose() {
    _phoneCtrl.dispose();
    _emailCtrl.dispose();
    _userIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _onSearch() async {
    setState(() {
      _searchLoading = true;
      _exportData = null;
      _selectedMatch = null;
      _matches = [];
    });

    final phone = _phoneCtrl.text.trim();
    final email = _emailCtrl.text.trim();
    final userIdRaw = _userIdCtrl.text.trim();

    int? userId;
    if (userIdRaw.isNotEmpty) {
      userId = int.tryParse(userIdRaw);
      if (userId == null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Invalid userId')),
        );
        setState(() => _searchLoading = false);
        return;
      }
    }

    if (phone.isEmpty && email.isEmpty && userId == null) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Provide phone, email, or userId')),
      );
      setState(() => _searchLoading = false);
      return;
    }

    try {
      final actions = ref.read(adminActionsProvider);
      final results = await actions.searchDeletedPatients(
        phone: phone.isEmpty ? null : phone,
        email: email.isEmpty ? null : email,
        userId: userId,
      );

      if (!mounted) return;
      setState(() => _matches = results);
      if (results.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No deleted patient match found')),
        );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Search failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _searchLoading = false);
    }
  }

  Future<void> _onExport(DeletedPatientMatch match) async {
    setState(() {
      _exportLoading = true;
      _exportData = null;
      _selectedMatch = match;
    });

    try {
      final actions = ref.read(adminActionsProvider);
      final data = await actions.exportDeletedPatient(match.patientProfileId);
      if (!mounted) return;
      setState(() => _exportData = data);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Export failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _exportLoading = false);
    }
  }

  Future<void> _onDownloadPdf(DeletedPatientMatch match) async {
    if (!kIsWeb) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('PDF download is supported on web only.')),
      );
      return;
    }

    setState(() {
      _pdfLoading = true;
    });

    try {
      final actions = ref.read(adminActionsProvider);
      final pdfBytes = await actions.exportDeletedPatientPdfBytes(match.patientProfileId);
      if (!mounted) return;
      await pdf_downloader.downloadPdfBytes(
        pdfBytes,
        filename: 'deleted_patient_${match.patientProfileId}_export.pdf',
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('PDF download failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _pdfLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final prettyJson = _exportData == null ? null : const JsonEncoder.withIndent('  ').convert(_exportData);

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: const Text('Deleted Patients (Legal Export)'),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Find deleted patient record (GDPR/legal export)',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _phoneCtrl,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                        labelText: 'Phone (original)',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _emailCtrl,
                      keyboardType: TextInputType.emailAddress,
                      decoration: const InputDecoration(
                        labelText: 'Email (original)',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _userIdCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'User ID (optional)',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    ShifaPrimaryButton(
                      label: _searchLoading ? 'Searching...' : 'Search deleted patient',
                      onPressed: _searchLoading ? null : _onSearch,
                      icon: Icons.search,
                      width: ButtonWidth.fill,
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 16),

            if (_matches.isNotEmpty) ...[
              const Text(
                'Matches',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              ListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _matches.length,
                itemBuilder: (context, i) {
                  final m = _matches[i];
                  return Card(
                    child: ListTile(
                      title: Text('PatientProfile: ${m.patientProfileId}'),
                      subtitle: Text('Matched by ${m.matchedBy} • Deleted at: ${m.deletedAt ?? "n/a"}'),
                      trailing: SizedBox(
                        width: 220,
                        child: Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          alignment: WrapAlignment.end,
                          children: [
                            ShifaActionButton(
                              label: 'Export',
                              onPressed: _exportLoading ? null : () => _onExport(m),
                              isLoading: _exportLoading && _selectedMatch?.patientProfileId == m.patientProfileId,
                            ),
                            ShifaActionButton(
                              label: 'Download PDF',
                              onPressed: _pdfLoading ? null : () => _onDownloadPdf(m),
                              icon: Icons.picture_as_pdf,
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ],

            if (_exportData != null) ...[
              const SizedBox(height: 16),
              const Text(
                'Export JSON',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: SelectableText(
                    prettyJson ?? '',
                    style: const TextStyle(fontFamily: 'monospace'),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

