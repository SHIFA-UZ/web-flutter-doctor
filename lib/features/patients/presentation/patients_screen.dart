import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:image_picker/image_picker.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart'
    show patientsProvider, patientByIdProvider;

// NEW: actions & provider for documents
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart'
    show
        uploadPatientDocumentWithClient,
        requestDocumentAccessWithClient,
        updatePatientWithClient,
        sendPatientTestSmsWithClient,
        createPatientWithClient,
        createPatientAccountWithClient,
        fetchPatientWithClient,
        fetchProphylaxisSettingsWithClient,
        upsertProphylaxisSettingsWithClient;
import 'package:shifa_doc_app_v1/features/patients/presentation/document_viewer_screen.dart';
import 'package:flutter/services.dart'; // âœ… for Clipboard
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_provider.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_forms_provider.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_wizard_sheet.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/consecutive_slot_range.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/utils/error_formatter.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/widgets/multiple_phone_fields.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/document_category.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/chat/application/open_chat_with_patient.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patient_detail_helpers.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patient_detail_overview.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patients_directory_panel.dart';

part 'patient_detail_panel.dart';

Future<Map<String, String?>?> askPatientDocumentTitleAndCategory(
  BuildContext context, {
  required String defaultTitle,
}) async {
  final titleCtrl = TextEditingController(text: defaultTitle);
  DocumentCategory? selected;
  return showModalBottomSheet<Map<String, String?>>(
    context: context,
    isScrollControlled: true,
    builder: (ctx) {
      return Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: 16 + MediaQuery.of(ctx).viewInsets.bottom,
        ),
        child: StatefulBuilder(
          builder: (ctx, setSheetState) {
            final l10n = AppLocalizations.of(ctx)!;
            return SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.uploadDocument,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: titleCtrl,
                    decoration: InputDecoration(
                      labelText: l10n.documentTitle,
                      hintText: l10n.enterDocumentTitle,
                    ),
                    autofocus: true,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    l10n.translate('documentCategoryLabel') ?? 'Document type',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    l10n.translate('documentCategoryHint') ??
                        'Pick a medical-result type to share with all doctors of this patient. Internal/private types stay visible only to you.',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<DocumentCategory>(
                    value: selected,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                    hint: Text(
                      l10n.translate('documentCategorySelect') ?? 'Select a type',
                    ),
                    items: [
                      DropdownMenuItem<DocumentCategory>(
                        enabled: false,
                        child: Text(
                          l10n.translate('documentCategoryGroupMedical') ??
                              'Medical results (visible to all doctors)',
                          style: TextStyle(
                            fontSize: 11,
                            color: Colors.grey.shade600,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      ...kMedicalResultCategories.map(
                        (c) => DropdownMenuItem<DocumentCategory>(
                          value: c,
                          child: Row(
                            children: [
                              Icon(c.icon, size: 16, color: Colors.teal),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  c.label(l10n),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      DropdownMenuItem<DocumentCategory>(
                        enabled: false,
                        child: Text(
                          l10n.translate('documentCategoryGroupPrivate') ??
                              'Private (visible only to you)',
                          style: TextStyle(
                            fontSize: 11,
                            color: Colors.grey.shade600,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      ...kPrivateCategories.map(
                        (c) => DropdownMenuItem<DocumentCategory>(
                          value: c,
                          child: Row(
                            children: [
                              Icon(c.icon, size: 16, color: Colors.grey.shade700),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  c.label(l10n),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                    onChanged: (v) => setSheetState(() => selected = v),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () => Navigator.pop(ctx),
                          child: Text(l10n.cancel),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ShifaPrimaryButton(
                          width: ButtonWidth.fill,
                          onPressed: selected == null
                              ? null
                              : () => Navigator.pop(ctx, {
                                    'title': titleCtrl.text.trim(),
                                    'category': selected!.code,
                                  }),
                          label: l10n.translate('add') ?? 'Add',
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            );
          },
        ),
      );
    },
  );
}

Future<void> patientAddDocumentPdf(
  BuildContext context,
  WidgetRef ref,
  Patient patient, {
  int? clinicWorkspaceId,
  VoidCallback? onAfterUpload,
}) async {
  final result = await FilePicker.platform.pickFiles(
    type: FileType.custom,
    allowedExtensions: const ['pdf'],
    withData: true,
  );
  if (result == null || result.files.isEmpty) return;

  final f = result.files.first;
  if (f.bytes == null || f.name.isEmpty) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          AppLocalizations.of(context)!.translate('noFileData') ??
              'No file data to upload',
        ),
      ),
    );
    return;
  }

  final defaultTitle = f.name.replaceAll(
    RegExp(r'\.pdf$', caseSensitive: false),
    '',
  );
  final form = await askPatientDocumentTitleAndCategory(
    context,
    defaultTitle: defaultTitle,
  );
  if (form == null) return;
  final title = (form['title'] ?? '').toString().trim();
  final category = form['category'];

  try {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppLocalizations.of(context)!.translate('uploading') ??
                'Uploading...',
          ),
        ),
      );
    }

    final client = ref.read(apiClientProvider);
    final newDoc = await uploadPatientDocumentWithClient(
      client: client,
      patientId: patient.id,
      fileBytes: f.bytes!,
      fileName: f.name,
      title: title.isEmpty ? f.name : title,
      category: category,
      clinicId: clinicWorkspaceId,
    );

    if (!context.mounted) return;
    ref.refresh(
      patientDocumentsProvider(
        PatientDocumentsKey(
          patientId: patient.id,
          clinicId: clinicWorkspaceId,
        ),
      ),
    );
    onAfterUpload?.call();
    if (newDoc != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${AppLocalizations.of(context)!.translate('document') ?? 'Document'} "${newDoc.title}" ${AppLocalizations.of(context)!.translate('uploaded') ?? 'uploaded'}',
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.uploadFailed),
          backgroundColor: Colors.red,
        ),
      );
    }
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          '${AppLocalizations.of(context)!.translate('uploadError') ?? 'Upload error'}: $e',
        ),
        backgroundColor: Colors.red,
      ),
    );
  }
}

Future<bool> patientAskAddAnotherPage(BuildContext context, int count) async {
  final l10n = AppLocalizations.of(context)!;
  final res = await showDialog<bool>(
    context: context,
    builder: (ctx) {
      return AlertDialog(
        title: Text(l10n.translate('addAnotherPage') ?? 'Add another page?'),
        content: Text(
          '${l10n.translate('pagesScanned') ?? 'Pages scanned'}: $count',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(l10n.translate('finish') ?? 'Finish'),
          ),
          ShifaPrimaryButton(
            onPressed: () => Navigator.pop(ctx, true),
            label: l10n.translate('addPage') ?? 'Add page',
          ),
        ],
      );
    },
  );
  return res ?? false;
}

Future<void> patientUploadPagesAsPdf(
  BuildContext context,
  WidgetRef ref,
  Patient patient,
  List<Uint8List> pages, {
  int? clinicWorkspaceId,
  VoidCallback? onAfterUpload,
}) async {
  final l10n = AppLocalizations.of(context)!;
  final defaultTitle =
      '${l10n.translate('scannedDocument') ?? 'Scanned document'} (${pages.length} ${l10n.translate('pages') ?? 'pages'})';
  final form = await askPatientDocumentTitleAndCategory(
    context,
    defaultTitle: defaultTitle,
  );
  if (form == null) return;
  final title = (form['title'] ?? '').toString().trim();
  final category = form['category'];

  try {
    final pdf = pw.Document();

    for (final bytes in pages) {
      final image = pw.MemoryImage(bytes);
      pdf.addPage(
        pw.Page(
          build: (_) =>
              pw.Center(child: pw.Image(image, fit: pw.BoxFit.contain)),
        ),
      );
    }

    final pdfBytes = await pdf.save();
    final client = ref.read(apiClientProvider);

    await uploadPatientDocumentWithClient(
      client: client,
      patientId: patient.id,
      fileBytes: pdfBytes,
      fileName: 'scanned_document.pdf',
      title: title.isEmpty ? defaultTitle : title,
      category: category,
      clinicId: clinicWorkspaceId,
    );

    ref.refresh(
      patientDocumentsProvider(
        PatientDocumentsKey(
          patientId: patient.id,
          clinicId: clinicWorkspaceId,
        ),
      ),
    );
    onAfterUpload?.call();

    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          '${l10n.translate('uploaded') ?? 'Uploaded'} ${pages.length}-${l10n.translate('pageDocument') ?? 'page document'}',
        ),
      ),
    );
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('${l10n.translate('scanFailed') ?? 'Scan failed'}: $e'),
        backgroundColor: Colors.red,
      ),
    );
  }
}

Future<void> patientScanMultiplePages(
  BuildContext context,
  WidgetRef ref,
  Patient patient, {
  required bool singlePage,
  int? clinicWorkspaceId,
  VoidCallback? onAfterUpload,
}) async {
  final picker = ImagePicker();
  final List<Uint8List> pages = [];

  bool keepScanning = true;

  while (keepScanning) {
    final image = await picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 85,
    );

    if (image == null) break;

    pages.add(await image.readAsBytes());

    if (singlePage) break;

    keepScanning = await patientAskAddAnotherPage(context, pages.length);
  }

  if (pages.isEmpty) return;

  await patientUploadPagesAsPdf(
    context,
    ref,
    patient,
    pages,
    clinicWorkspaceId: clinicWorkspaceId,
    onAfterUpload: onAfterUpload,
  );
}

/// Shared document upload entry (PDF / camera / multi-scan) for [PatientDetailPanel] in any host screen.
Future<void> showPatientDocumentUploadOptions(
  BuildContext context,
  WidgetRef ref,
  Patient patient, {
  int? clinicWorkspaceId,
  VoidCallback? onAfterUpload,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    builder: (ctx) {
      return SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.picture_as_pdf),
              title: Text(
                AppLocalizations.of(ctx)!.translate('uploadPdf') ??
                    'Upload PDF',
              ),
              onTap: () {
                Navigator.pop(ctx);
                patientAddDocumentPdf(
                  context,
                  ref,
                  patient,
                  clinicWorkspaceId: clinicWorkspaceId,
                  onAfterUpload: onAfterUpload,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: Text(
                AppLocalizations.of(ctx)!.translate('takePhoto') ??
                    'Take Photo',
              ),
              onTap: () {
                Navigator.pop(ctx);
                patientScanMultiplePages(
                  context,
                  ref,
                  patient,
                  singlePage: true,
                  clinicWorkspaceId: clinicWorkspaceId,
                  onAfterUpload: onAfterUpload,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.document_scanner),
              title: Text(
                AppLocalizations.of(ctx)!.translate('scanMultiPage') ??
                    'Scan (multi-page)',
              ),
              onTap: () {
                Navigator.pop(ctx);
                patientScanMultiplePages(
                  context,
                  ref,
                  patient,
                  singlePage: false,
                  clinicWorkspaceId: clinicWorkspaceId,
                  onAfterUpload: onAfterUpload,
                );
              },
            ),
          ],
        ),
      );
    },
  );
}

void showPatientFormTemplateSheet(BuildContext context, Patient patient) {
  showModalBottomSheet<void>(
    context: context,
    builder: (ctx) {
      return SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                AppLocalizations.of(context)!.selectFormTemplate,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            ListView.builder(
              shrinkWrap: true,
              itemCount: formTemplates.length,
              itemBuilder: (sheetCtx, index) {
                final template = formTemplates[index];
                return ListTile(
                  leading: const Icon(Icons.description),
                  title: Text(template.name),
                  onTap: () {
                    Navigator.pop(ctx);
                    ShellScope.pushNamed(
                      context,
                      AppRoutes.patientForm,
                      arguments: {
                        'patient': patient,
                        'templateId': template.id,
                        'existingForm': null,
                      },
                    );
                  },
                );
              },
            ),
          ],
        ),
      );
    },
  );
}

class PatientsScreen extends ConsumerStatefulWidget {
  const PatientsScreen({
    Key? key,
    this.initialSelectedId,
    this.initialDocumentIdToSelect,
    this.initialDocumentTitle,
    this.initialOpenDocumentViewer = false,
    this.clinicWorkspaceId,
  }) : super(key: key);

  /// When set (e.g. from chat header tap), this patient is selected when the screen loads.
  final String? initialSelectedId;

  /// When set (e.g. from document access notification), this document is highlighted in the list.
  final String? initialDocumentIdToSelect;

  /// Document title for opening the viewer when [initialOpenDocumentViewer] is true.
  final String? initialDocumentTitle;

  /// When true with [initialDocumentIdToSelect], open the document in PDF viewer after loading.
  final bool initialOpenDocumentViewer;

  /// When set (e.g. opened from clinic workspace roster), loads patient/documents/prophylaxis with clinic scope.
  final int? clinicWorkspaceId;

  @override
  ConsumerState<PatientsScreen> createState() => _PatientsScreenState();
}

class _PatientsScreenState extends ConsumerState<PatientsScreen> {
  String? _selectedId;
  Patient? _overlayPatient;
  final Set<String> _favoriteIds = {};

  @override
  void initState() {
    super.initState();

    ref.read(patientsProvider.notifier).loadPatients();

    final patients = ref.read(patientsProvider);
    final initialId = widget.initialSelectedId;
    final clinicWs = widget.clinicWorkspaceId;
    if (initialId != null && patients.any((p) => p.id == initialId)) {
      _selectedId = initialId;
    } else if (initialId != null && clinicWs != null) {
      _selectedId = initialId;
      _loadClinicOverlayPatient(initialId, clinicWs);
    }
    // First patient auto-select runs in build via ref.listen (desktop only).

  }

  Future<void> _loadClinicOverlayPatient(String patientId, int clinicId) async {
    try {
      final client = ref.read(apiClientProvider);
      final p = await fetchPatientWithClient(
        client: client,
        patientId: patientId,
        clinicId: clinicId,
      );
      if (mounted) {
        setState(() => _overlayPatient = p);
      }
    } catch (_) {
      // Leave overlay null; detail panel may stay empty until list refresh includes patient.
    }
  }

  PatientDocumentsKey _docKey(String patientId) => PatientDocumentsKey(
        patientId: patientId,
        clinicId: widget.clinicWorkspaceId,
      );

  @override
  void dispose() {
    super.dispose();
  }

  List<Patient> get _sidebarPatients {
    final base = ref.watch(patientsProvider);
    final o = _overlayPatient;
    if (o == null || base.any((p) => p.id == o.id)) return base;
    return [o, ...base];
  }

  Future<void> _handlePatientSelection(String id) async {
    setState(() => _selectedId = id);
    // Refresh document list so new uploads and access approvals from patient app are visible
    ref.refresh(patientDocumentsProvider(_docKey(id)));
    // Show warning if patient has chronic disease
    Patient? patient;
    for (final p in _sidebarPatients) {
      if (p.id == id) {
        patient = p;
        break;
      }
    }
    patient ??= _overlayPatient?.id == id ? _overlayPatient : null;
    if (patient != null &&
        patient.general.chronicDisease != null &&
        patient.general.chronicDisease!.isNotEmpty &&
        patient.general.chronicDisease != 'None') {
      await showChronicDiseaseWarning(
        context,
        patient.name,
        patient.general.chronicDisease!,
      );
    }
  }

  Patient? get _selected {
    final id = _selectedId;
    if (id == null) return null;
    if (_overlayPatient?.id == id) return _overlayPatient;
    final patients = ref.watch(patientsProvider);
    for (final p in patients) {
      if (p.id == id) return p;
    }
    return _overlayPatient?.id == id ? _overlayPatient : null;
  }

  String _formatDate(DateTime d) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(d.day)}.${two(d.month)}.${d.year}';
  }

  // ---------- NEW: Pick a PDF, optional title, upload to backend, then refresh ----------
  void _showUploadOptions(BuildContext context, Patient patient) {
    showPatientDocumentUploadOptions(
      context,
      ref,
      patient,
      clinicWorkspaceId: widget.clinicWorkspaceId,
    );
  }

  // --------------------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final isMobile = Responsive.isMobile(context);

    ref.listen<List<Patient>>(patientsProvider, (prev, next) {
      if (!mounted || isMobile) return;
      if (_selectedId != null || widget.initialSelectedId != null) return;
      if (next.isNotEmpty) {
        setState(() => _selectedId = next.first.id);
      }
    });

    return Scaffold(
      backgroundColor: AppColors.scaffoldBackground,
      body: Padding(
        padding: Responsive.screenPadding(context),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isWide =
                constraints.maxWidth >= Responsive.tabletBreakpoint;

            if (isMobile && _selectedId != null) {
              return _buildMobilePatientDetail(context, l10n, brand);
            }

            final directoryPatients = _sidebarPatients;

            if (!isWide) {
              return PatientsDirectoryPanel(
                patients: directoryPatients,
                selectedId: _selectedId,
                favoriteIds: _favoriteIds,
                onSelect: _handlePatientSelection,
                onCreatePatient: () => _openCreatePatientModal(context),
                onRefresh: () async {
                  try {
                    await ref.read(patientsProvider.notifier).loadPatients();
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            l10n.translate('listRefreshed') ??
                                'Patient list refreshed',
                          ),
                          duration: const Duration(seconds: 2),
                        ),
                      );
                    }
                  } catch (e) {
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('${l10n.error}: $e'),
                          backgroundColor: Colors.red,
                        ),
                      );
                    }
                  }
                },
              );
            }

            return Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(
                  flex: 7,
                  child: PatientsDirectoryPanel(
                    patients: directoryPatients,
                    selectedId: _selectedId,
                    favoriteIds: _favoriteIds,
                    onSelect: _handlePatientSelection,
                    onCreatePatient: () => _openCreatePatientModal(context),
                    onRefresh: () async {
                      try {
                        await ref.read(patientsProvider.notifier).loadPatients();
                        if (mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                l10n.translate('listRefreshed') ??
                                    'Patient list refreshed',
                              ),
                              duration: const Duration(seconds: 2),
                            ),
                          );
                        }
                      } catch (e) {
                        if (mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('${l10n.error}: $e'),
                              backgroundColor: Colors.red,
                            ),
                          );
                        }
                      }
                    },
                  ),
                ),
                const SizedBox(width: AppDesignSystem.sectionGap),
                Expanded(
                  flex: 13,
                  child: PatientDetailPanel(
                    patient: _selected,
                    brand: brand,
                    clinicWorkspaceId: widget.clinicWorkspaceId,
                    isFavorite: _selectedId != null &&
                        _favoriteIds.contains(_selectedId),
                    onToggleFavorite: _selectedId == null
                        ? null
                        : () => _toggleFavorite(_selectedId!),
                    onUploadOptions: (p) => _showUploadOptions(context, p),
                    onCreateForm: (p) =>
                        showPatientFormTemplateSheet(context, p),
                    formatDate: _formatDate,
                    selectedDocumentId: widget.initialDocumentIdToSelect,
                    documentTitleForViewer: widget.initialDocumentTitle,
                    openDocumentViewer: widget.initialOpenDocumentViewer,
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  void _toggleFavorite(String patientId) {
    setState(() {
      if (_favoriteIds.contains(patientId)) {
        _favoriteIds.remove(patientId);
      } else {
        _favoriteIds.add(patientId);
      }
    });
  }

  Widget _buildMobilePatientDetail(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
  ) {
    final patient = _selected;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: () => setState(() => _selectedId = null),
            icon: const Icon(Icons.arrow_back),
            label: Text(l10n.patients),
          ),
        ),
        Expanded(
          child: patient == null
              ? const Center(child: CircularProgressIndicator())
              : PatientDetailPanel(
                  patient: patient,
                  brand: brand,
                  clinicWorkspaceId: widget.clinicWorkspaceId,
                  isFavorite: _favoriteIds.contains(patient.id),
                  onToggleFavorite: () => _toggleFavorite(patient.id),
                  onUploadOptions: (p) => _showUploadOptions(context, p),
                  onCreateForm: (p) =>
                      showPatientFormTemplateSheet(context, p),
                  formatDate: _formatDate,
                  selectedDocumentId: widget.initialDocumentIdToSelect,
                  documentTitleForViewer: widget.initialDocumentTitle,
                  openDocumentViewer: widget.initialOpenDocumentViewer,
                ),
        ),
      ],
    );
  }

  static const List<String> _patientLanguageOptions = [
    'english',
    'uzbek',
    'russian',
    'german',
    'karakalpak',
    'kazakh',
    'kyrgyz',
    'tajik',
    'turkmen',
    'arabic',
  ];

  static const List<String> _patientGenderOptions = ['', 'Male', 'Female', 'Other'];
  static const List<String> _patientBloodGroups = [
    '',
    'A+',
    'A-',
    'B+',
    'B-',
    'AB+',
    'AB-',
    'O+',
    'O-',
  ];

  Future<void> _openCreatePatientModal(BuildContext context) async {
    final nameCtrl = TextEditingController();
    final phoneFieldsKey = GlobalKey<MultiplePhoneFieldsState>();
    final emailCtrl = TextEditingController();
    final addressCtrl = TextEditingController();
    final allergiesCtrl = TextEditingController();
    String? selectedLanguage = _patientLanguageOptions.first;
    String? selectedGender = '';
    String? selectedBloodGroup = '';
    DateTime? birthDate;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(
            left: 16,
            right: 16,
            top: 16,
            bottom: 16 + MediaQuery.of(ctx).viewInsets.bottom,
          ),
          child: StatefulBuilder(
            builder: (ctx, setModalState) {
              final l10n = AppLocalizations.of(ctx)!;
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    l10n.translate('createNewPatient') ?? 'Create New Patient',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 16),

                  TextField(
                    controller: nameCtrl,
                    decoration: InputDecoration(
                      labelText:
                          '${l10n.translate('fullName') ?? 'Full Name'} *',
                    ),
                  ),
                  const SizedBox(height: 12),

                  MultiplePhoneFields(
                    key: phoneFieldsKey,
                    labelText: '${l10n.phoneNumber} (${l10n.optional})',
                  ),
                  const SizedBox(height: 12),

                  TextField(
                    controller: emailCtrl,
                    decoration: InputDecoration(labelText: l10n.email),
                  ),
                  const SizedBox(height: 12),

                  TextField(
                    controller: addressCtrl,
                    decoration: InputDecoration(labelText: l10n.address),
                  ),
                  const SizedBox(height: 12),

                  DropdownButtonFormField<String>(
                    value: selectedLanguage,
                    decoration: InputDecoration(
                      labelText: l10n.language,
                      border: const OutlineInputBorder(),
                    ),
                    items: _patientLanguageOptions
                        .map(
                          (String lang) => DropdownMenuItem<String>(
                            value: lang,
                            child: Text(
                              lang[0].toUpperCase() + lang.substring(1),
                            ),
                          ),
                        )
                        .toList(),
                    onChanged: (String? value) {
                      if (value != null) {
                        setModalState(() => selectedLanguage = value);
                      }
                    },
                  ),
                  const SizedBox(height: 12),

                  DropdownButtonFormField<String>(
                    value: selectedGender,
                    decoration: InputDecoration(
                      labelText: l10n.gender,
                      border: const OutlineInputBorder(),
                    ),
                    items: _patientGenderOptions
                        .map(
                          (g) => DropdownMenuItem<String>(
                            value: g,
                            child: Text(g.isEmpty ? '—' : g),
                          ),
                        )
                        .toList(),
                    onChanged: (value) {
                      setModalState(() => selectedGender = value);
                    },
                  ),
                  const SizedBox(height: 12),

                  DropdownButtonFormField<String>(
                    value: selectedBloodGroup,
                    decoration: InputDecoration(
                      labelText: l10n.translate('bloodGroup') ?? 'Blood Group',
                      border: const OutlineInputBorder(),
                    ),
                    items: _patientBloodGroups
                        .map(
                          (g) => DropdownMenuItem<String>(
                            value: g,
                            child: Text(g.isEmpty ? '—' : g),
                          ),
                        )
                        .toList(),
                    onChanged: (value) {
                      setModalState(() => selectedBloodGroup = value);
                    },
                  ),
                  const SizedBox(height: 12),

                  TextField(
                    controller: allergiesCtrl,
                    decoration: InputDecoration(
                      labelText: l10n.translate('allergies') ?? 'Allergies',
                    ),
                  ),
                  const SizedBox(height: 12),

                  InkWell(
                    onTap: () async {
                      final picked = await showDatePicker(
                        context: ctx,
                        locale: localeForMaterialIntl(Localizations.localeOf(ctx)),
                        firstDate: DateTime(1900),
                        lastDate: DateTime.now(),
                        initialDate: DateTime(1990),
                      );
                      if (picked != null) {
                        setModalState(() => birthDate = picked);
                      }
                    },
                    child: InputDecorator(
                      decoration: InputDecoration(labelText: l10n.birthDate),
                      child: Text(
                        birthDate == null
                            ? l10n.selectDate
                            : '${birthDate!.day}.${birthDate!.month}.${birthDate!.year}',
                      ),
                    ),
                  ),

                  const SizedBox(height: 20),

                  ShifaPrimaryButton(
                    width: ButtonWidth.fill,
                    onPressed: () async {
                      final name = nameCtrl.text.trim();
                      final phones =
                          phoneFieldsKey.currentState?.values ?? const [];
                      if (name.isEmpty) return;
                      Navigator.pop(ctx);
                      await _createPatientExtended(
                        name: name,
                        phones: phones,
                        email: emailCtrl.text.trim(),
                        address: addressCtrl.text.trim(),
                        birthDate: birthDate,
                        language: selectedLanguage,
                        gender: selectedGender,
                        bloodGroup: selectedBloodGroup,
                        allergies: allergiesCtrl.text.trim(),
                      );
                    },
                    label: l10n.translate('createPatient') ?? 'Create Patient',
                  ),
                ],
              );
            },
          ),
        );
      },
    );
  }

  Future<void> _createPatientExtended({
    required String name,
    List<String>? phones,
    String? email,
    String? address,
    DateTime? birthDate,
    String? language,
    String? gender,
    String? bloodGroup,
    String? allergies,
  }) async {
    try {
      final client = ref.read(apiClientProvider);

      final created = await createPatientWithClient(
        client: client,
        name: name,
        phones: phones,
        email: email,
        address: address,
        birthDate: birthDate,
        language: language,
        photoUrl: null,
        gender: gender,
        bloodGroup: bloodGroup,
        allergies: allergies,
      );

      // Reload patients list
      await ref.read(patientsProvider.notifier).loadPatients();

      // Auto-select newly created patient
      setState(() => _selectedId = created.id);

      if (!mounted) return;
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.translate('patientCreated') ?? 'Patient created'),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      final l10n = AppLocalizations.of(context)!;
      final msg = e.toString().replaceFirst('Exception: ', '');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            msg.contains('already exists')
                ? msg
                : '${l10n.translate('createFailed') ?? 'Create failed'}: $msg',
          ),
          backgroundColor: Colors.red,
        ),
      );
    }
  }
}

Future<void> _createAccount(
  BuildContext context,
  WidgetRef ref,
  Patient p,
) async {
  try {
    final client = ref.read(apiClientProvider);
    final result = await createPatientAccountWithClient(
      client: client,
      patientId: p.id,
    );

    if (context.mounted) {
      _showAccountCreatedModal(
        context,
        result['username'],
        result['oneTimePassword'],
      );
      // Refresh patient list to update "hasAccount" flag
      ref.read(patientsProvider.notifier).loadPatients();
    }
  } catch (e) {
    if (context.mounted) {
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.errorCreatingAccount}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }
}

void _showAccountCreatedModal(
  BuildContext context,
  String username,
  String password,
) {
  final l10n = AppLocalizations.of(context)!;
  showDialog(
    context: context,
    barrierDismissible: false, // Force user to read/close
    builder: (ctx) => AlertDialog(
      title: Row(
        children: [
          const Icon(Icons.verified_user, color: Colors.green),
          const SizedBox(width: 8),
          Text(l10n.accountCreated),
        ],
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.shareCredentialsWithPatient),
          const SizedBox(height: 20),
          _CredentialRow(label: l10n.username, value: username),
          const SizedBox(height: 12),
          _CredentialRow(
            label: l10n.oneTimePassword,
            value: password,
            isSecret: true,
          ),
          const SizedBox(height: 20),
          Text(
            l10n.forSecurityPasswordShownOnce,
            style: const TextStyle(
              color: Colors.red,
              fontWeight: FontWeight.bold,
              fontSize: 12,
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: Text(l10n.close),
        ),
      ],
    ),
  );
}

class _CredentialRow extends StatelessWidget {
  final String label;
  final String value;
  final bool isSecret;

  const _CredentialRow({
    required this.label,
    required this.value,
    this.isSecret = false,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
        ),
        const SizedBox(height: 4),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: Colors.grey.shade100,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: Colors.grey.shade300),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  value,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontFamily: 'monospace',
                  ),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.copy, size: 18),
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: value));
                  final l10n = AppLocalizations.of(context)!;
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('$label ${l10n.copiedToClipboard}'),
                      duration: const Duration(seconds: 1),
                    ),
                  );
                },
                tooltip: AppLocalizations.of(context)!.copy,
              ),
            ],
          ),
        ),
      ],
    );
  }
}
