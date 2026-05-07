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
        createPatientWithClient,
        createPatientAccountWithClient;
import 'package:shifa_doc_app_v1/features/patients/presentation/document_viewer_screen.dart';
import 'package:flutter/services.dart'; // ✅ for Clipboard
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_forms_provider.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/utils/error_formatter.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class PatientsScreen extends ConsumerStatefulWidget {
  const PatientsScreen({
    Key? key,
    this.initialSelectedId,
    this.initialDocumentIdToSelect,
    this.initialDocumentTitle,
    this.initialOpenDocumentViewer = false,
  }) : super(key: key);

  /// When set (e.g. from chat header tap), this patient is selected when the screen loads.
  final String? initialSelectedId;

  /// When set (e.g. from document access notification), this document is highlighted in the list.
  final String? initialDocumentIdToSelect;

  /// Document title for opening the viewer when [initialOpenDocumentViewer] is true.
  final String? initialDocumentTitle;

  /// When true with [initialDocumentIdToSelect], open the document in PDF viewer after loading.
  final bool initialOpenDocumentViewer;

  @override
  ConsumerState<PatientsScreen> createState() => _PatientsScreenState();
}

class _PatientsScreenState extends ConsumerState<PatientsScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _query = '';
  String? _selectedId;

  @override
  void initState() {
    super.initState();

    // Optional: force a reload when the screen is constructed
    // (Your updated provider constructor may already auto-load;
    //  keeping this call guarantees the list is fresh when you open the screen.)
    ref.read(patientsProvider.notifier).loadPatients();

    final patients = ref.read(patientsProvider);
    final initialId = widget.initialSelectedId;
    if (initialId != null && patients.any((p) => p.id == initialId)) {
      _selectedId = initialId;
    } else if (patients.isNotEmpty) {
      _selectedId = patients.first.id;
    }

    _searchCtrl.addListener(() {
      setState(() => _query = _searchCtrl.text.trim());
    });
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  List<Patient> get _filtered {
    final patients = ref.watch(patientsProvider);
    if (_query.isEmpty) return patients;
    final q = _query.toLowerCase();
    return patients.where((p) => p.name.toLowerCase().contains(q)).toList();
  }

  Future<void> _handlePatientSelection(String id) async {
    setState(() => _selectedId = id);
    // Refresh document list so new uploads and access approvals from patient app are visible
    ref.refresh(patientDocumentsProvider(id));
    // Show warning if patient has chronic disease
    final patient = _filtered.firstWhere(
      (p) => p.id == id,
      orElse: () => _filtered.first,
    );
    if (patient.general.chronicDisease != null &&
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
    final patients = ref.watch(patientsProvider);
    if (patients.isEmpty) return null;
    return patients.firstWhere(
      (p) => p.id == _selectedId,
      orElse: () => patients.first,
    );
  }

  String _formatDate(DateTime d) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(d.day)}.${two(d.month)}.${d.year}';
  }

  // ---------- NEW: Pick a PDF, optional title, upload to backend, then refresh ----------
  void _showUploadOptions(BuildContext context, Patient patient) {
    showModalBottomSheet(
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
                  _addDocument(patient);
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
                  _scanMultiplePages(patient, singlePage: true);
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
                  _scanMultiplePages(patient, singlePage: false);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  // Pick a PDF, optional title, upload to backend, then refresh the doc list
  Future<void> _addDocument(Patient patient) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf'],
      withData: true, // Web needs this
    );
    if (result == null || result.files.isEmpty) return;

    final f = result.files.first;
    if (f.bytes == null || f.name.isEmpty) {
      if (!mounted) return;
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

    String tempTitle = '';
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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                AppLocalizations.of(ctx)!.uploadDocument,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                decoration: InputDecoration(
                  hintText: AppLocalizations.of(ctx)!.enterDocumentTitle,
                ),
                onChanged: (v) => tempTitle = v,
                autofocus: true,
              ),
              const SizedBox(height: 12),
              ShifaPrimaryButton(
                width: ButtonWidth.fill,
                onPressed: () => Navigator.pop(ctx),
                label: AppLocalizations.of(ctx)!.translate('add') ?? 'Add',
              ),
            ],
          ),
        );
      },
    );

    try {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)!.translate('uploading') ??
                  'Uploading...',
            ),
          ),
        );
      }

      // ✅ Read ApiClient and call the client-based action
      final client = ref.read(apiClientProvider);
      final newDoc = await uploadPatientDocumentWithClient(
        client: client,
        patientId: patient.id,
        fileBytes: f.bytes!,
        // already checked
        fileName: f.name,
        title: tempTitle.trim().isEmpty ? f.name : tempTitle.trim(),
      );

      if (!mounted) return;
      if (newDoc != null) {
        // Refresh only this patient's docs
        ref.refresh(patientDocumentsProvider(patient.id));
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
      if (!mounted) return;
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

  Future<void> _scanMultiplePages(
    Patient patient, {
    required bool singlePage,
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

      keepScanning = await _askAddAnotherPage(context, pages.length);
    }

    if (pages.isEmpty) return;

    await _uploadPagesAsPdf(patient, pages);
  }

  Future<bool> _askAddAnotherPage(BuildContext context, int count) async {
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

  Future<void> _uploadPagesAsPdf(Patient patient, List<Uint8List> pages) async {
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
        title:
            '${AppLocalizations.of(context)!.translate('scannedDocument') ?? 'Scanned document'} (${pages.length} ${AppLocalizations.of(context)!.translate('pages') ?? 'pages'})',
      );

      ref.refresh(patientDocumentsProvider(patient.id));

      if (!mounted) return;
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('uploaded') ?? 'Uploaded'} ${pages.length}-${l10n.translate('pageDocument') ?? 'page document'}',
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.translate('scanFailed') ?? 'Scan failed'}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  // --------------------------------------------------------------------------------------

  /*Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isNarrow = constraints.maxWidth < 980;

            final leftPane = Expanded(
              flex: 2,
              child: SizedBox(
                width: isNarrow ? double.infinity : null,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      AppLocalizations.of(context)!.patients,
                      style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 12),
                    _SearchField(controller: _searchCtrl),
                    const SizedBox(height: 16),
                    Expanded(
                      child: _PatientsList(
                        patients: _filtered,
                        selectedId: _selectedId,
                        onSelect: _handlePatientSelection,
                      ),
                    ),
                  ],
                ),
              ),
            );

            final rightPane = Expanded(
              flex: 3,
              child: _PatientDetailsCard(
                patient: _selected,
                brand: brand,
                onUpload: (p) => _addDocument(p),
                formatDate: _formatDate,
              ),
            );

            if (isNarrow) {
              return Column(
                children: [leftPane, const SizedBox(height: 16), rightPane],
              );
            } else {
              return Row(
                children: [leftPane, const SizedBox(width: 24), rightPane],
              );
            }
          },
        ),
      ),
    );
  }
}*/
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isNarrow = constraints.maxWidth < 980;

            final leftPane = Expanded(
              flex: 2,
              child: SizedBox(
                width: isNarrow ? double.infinity : null,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          l10n.patients,
                          style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(width: 12),
                        IconButton.filledTonal(
                          onPressed: () async {
                            try {
                              await ref
                                  .read(patientsProvider.notifier)
                                  .loadPatients();
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
                          icon: const Icon(Icons.refresh),
                          tooltip: l10n.refresh,
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    // 🔹 SEARCH + CREATE PATIENT BUTTON
                    Row(
                      children: [
                        Expanded(child: _SearchField(controller: _searchCtrl)),
                        const SizedBox(width: 12),
                        ShifaPrimaryButton(
                          onPressed: () => _openCreatePatientModal(context),
                          icon: Icons.person_add,
                          label: l10n.translate('newPatient') ?? 'New Patient',
                        ),
                      ],
                    ),

                    const SizedBox(height: 16),
                    Expanded(
                      child: _PatientsList(
                        patients: _filtered,
                        selectedId: _selectedId,
                        onSelect: _handlePatientSelection,
                      ),
                    ),
                  ],
                ),
              ),
            );

            final rightPane = Expanded(
              flex: 3,
              child: _PatientDetailsCard(
                patient: _selected,
                brand: brand,
                onUploadOptions: (p) => _showUploadOptions(context, p),
                onCreateForm: (p) => _showFormTemplateDialog(context, p),
                formatDate: _formatDate,
                selectedDocumentId: widget.initialDocumentIdToSelect,
                documentTitleForViewer: widget.initialDocumentTitle,
                openDocumentViewer: widget.initialOpenDocumentViewer,
              ),
            );

            if (isNarrow) {
              return Column(
                children: [leftPane, const SizedBox(height: 16), rightPane],
              );
            } else {
              return Row(
                children: [leftPane, const SizedBox(width: 24), rightPane],
              );
            }
          },
        ),
      ),
    );
  }

  // ---------- NEW: Create Patient Modal & Action ----------
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

  Future<void> _openCreatePatientModal(BuildContext context) async {
    final nameCtrl = TextEditingController();
    final phoneCtrl = TextEditingController();
    final emailCtrl = TextEditingController();
    final addressCtrl = TextEditingController();
    String? selectedLanguage = _patientLanguageOptions.first;
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

                  TextField(
                    controller: phoneCtrl,
                    decoration: InputDecoration(
                      labelText: '${l10n.phoneNumber} *',
                    ),
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

                  InkWell(
                    onTap: () async {
                      final picked = await showDatePicker(
                        context: ctx,
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
                      final phone = phoneCtrl.text.trim();
                      if (name.isEmpty) return;
                      if (phone.isEmpty) {
                        ScaffoldMessenger.of(ctx).showSnackBar(
                          SnackBar(
                            content: Text(
                              l10n.translate('phoneNumberRequired') ??
                                  'Phone number is required',
                            ),
                          ),
                        );
                        return;
                      }
                      Navigator.pop(ctx);
                      await _createPatientExtended(
                        name: name,
                        phone: phone,
                        email: emailCtrl.text.trim(),
                        address: addressCtrl.text.trim(),
                        birthDate: birthDate,
                        language: selectedLanguage,
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
    required String phone,
    String? email,
    String? address,
    DateTime? birthDate,
    String? language,
  }) async {
    try {
      final client = ref.read(apiClientProvider);

      final created = await createPatientWithClient(
        client: client,
        name: name,
        phone: phone,
        email: email,
        address: address,
        birthDate: birthDate,
        language: language,
        photoUrl: null,
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

  // ---------- NEW: Form Template Dialog ----------
  void _showFormTemplateDialog(BuildContext context, Patient patient) {
    showModalBottomSheet(
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
                itemBuilder: (ctx, index) {
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

/// ---------------- Left: search ----------------
class _SearchField extends StatelessWidget {
  final TextEditingController controller;
  const _SearchField({Key? key, required this.controller}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 420),
      child: TextField(
        controller: controller,
        decoration: InputDecoration(
          hintText: AppLocalizations.of(context)!.search,
          prefixIcon: const Icon(Icons.search),
          filled: true,
          fillColor: Colors.white,
          contentPadding: const EdgeInsets.symmetric(vertical: 0),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: BorderSide(color: Colors.grey.shade300),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: BorderSide(color: Colors.grey.shade300),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: BorderSide(color: brand, width: 2),
          ),
        ),
      ),
    );
  }
}

/// ---------------- Left: list ----------------
class _PatientsList extends StatelessWidget {
  final List<Patient> patients;
  final String? selectedId;
  final ValueChanged<String> onSelect;
  const _PatientsList({
    Key? key,
    required this.patients,
    required this.selectedId,
    required this.onSelect,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    if (patients.isEmpty) {
      return Center(child: Text(l10n.noPatientsFound));
    }
    return ListView.separated(
      itemCount: patients.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final p = patients[index];
        final isSelected = selectedId == p.id;
        return InkWell(
          onTap: () => onSelect(p.id),
          borderRadius: BorderRadius.circular(12),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isSelected ? brand : Colors.transparent,
                width: 2,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Row(
              children: [
                _Avatar(name: p.name, photoUrl: p.photoUrl),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    p.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (p.general.chronicDisease != null &&
                    p.general.chronicDisease!.isNotEmpty &&
                    p.general.chronicDisease != 'None')
                  Container(
                    margin: const EdgeInsets.only(right: 8),
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: Colors.red,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.flag,
                      color: Colors.white,
                      size: 16,
                    ),
                  ),
                Icon(Icons.chevron_right, color: Colors.grey.shade600),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// ---------------- Right: details ----------------
class _PatientDetailsCard extends ConsumerStatefulWidget {
  final Patient? patient;
  final Color brand;
  final void Function(Patient) onUploadOptions;
  final void Function(Patient) onCreateForm;
  final String Function(DateTime) formatDate;
  final String? selectedDocumentId;
  final String? documentTitleForViewer;
  final bool openDocumentViewer;

  const _PatientDetailsCard({
    Key? key,
    required this.patient,
    required this.brand,
    required this.onUploadOptions,
    required this.onCreateForm,
    required this.formatDate,
    this.selectedDocumentId,
    this.documentTitleForViewer,
    this.openDocumentViewer = false,
  }) : super(key: key);

  @override
  ConsumerState<_PatientDetailsCard> createState() =>
      _PatientDetailsCardState();
}

class _PatientDetailsCardState extends ConsumerState<_PatientDetailsCard> {
  final TextEditingController _documentSearchController =
      TextEditingController();
  String _documentSearchQuery = '';
  bool _documentViewerOpenedFromDeepLink = false;

  @override
  void initState() {
    super.initState();
    _documentSearchController.addListener(() {
      setState(() {
        _documentSearchQuery = _documentSearchController.text;
      });
    });
    // Fetch fresh document list when detail panel is first shown (e.g. new uploads by patient, access approvals)
    if (widget.patient != null) {
      ref.refresh(patientDocumentsProvider(widget.patient!.id));
    }
  }

  @override
  void didUpdateWidget(covariant _PatientDetailsCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    // When selected patient changes, refresh that patient's documents
    if (widget.patient != null && oldWidget.patient?.id != widget.patient?.id) {
      ref.refresh(patientDocumentsProvider(widget.patient!.id));
    }
  }

  @override
  void dispose() {
    _documentSearchController.dispose();
    super.dispose();
  }

  Future<void> _showMakeAppointmentDialog(
    BuildContext context,
    WidgetRef ref,
    Patient p,
    Color brand,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    DateTime? selectedDate;
    CalendarEntry? selectedSlot;
    bool isVideo = false;
    bool loadingSlots = false;
    List<CalendarEntry> freeSlots = [];
    bool saving = false;

    Future<void> loadSlots(DateTime day) async {
      final tz =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      if (tz == null || tz.trim().isEmpty) {
        freeSlots = [];
        return;
      }
      try {
        await ref
            .read(calendarProvider.notifier)
            .loadDay(day: day, doctorTimeZone: tz);
        final entries =
            ref.read(calendarProvider)[DateTime(
              day.year,
              day.month,
              day.day,
            )] ??
            [];
        freeSlots = entries.where((e) => e.type == EntryType.freeSlot).toList();
        // Use doctor's timezone to check if day is in the past
        final todayInDoctorZone = getTodayInTimezone(tz);
        final slotDay = DateTime(day.year, day.month, day.day);
        if (slotDay.isBefore(todayInDoctorZone)) freeSlots = [];
      } catch (_) {
        freeSlots = [];
      }
    }

    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            title: Text(
              l10n.translate('makeAppointment') ?? 'Make appointment',
            ),
            content: SingleChildScrollView(
              child: SizedBox(
                width: double.maxFinite,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1) Date
                    ListTile(
                      title: Text(
                        l10n.translate('selectDate') ?? 'Select Date',
                      ),
                      subtitle: Text(
                        selectedDate != null
                            ? '${selectedDate!.day}.${selectedDate!.month}.${selectedDate!.year}'
                            : l10n.translate('notSelected') ?? 'Not selected',
                      ),
                      trailing: const Icon(Icons.calendar_today),
                      onTap: () async {
                        // Use doctor's timezone to prevent booking past days in doctor's calendar
                        final doctorTz =
                            ref
                                    .read(profileAllProvider)
                                    .valueOrNull
                                    ?.profile['timeZone']
                                as String?;
                        final todayInDoctorZone = getTodayInTimezone(doctorTz);
                        final picked = await showDatePicker(
                          context: context,
                          initialDate: selectedDate ?? todayInDoctorZone,
                          firstDate: todayInDoctorZone,
                          lastDate: DateTime(2030),
                        );
                        if (picked != null) {
                          setDialogState(() {
                            selectedDate = DateTime(
                              picked.year,
                              picked.month,
                              picked.day,
                            );
                            selectedSlot = null;
                            freeSlots = [];
                          });
                          setDialogState(() => loadingSlots = true);
                          await loadSlots(selectedDate!);
                          if (context.mounted)
                            setDialogState(() => loadingSlots = false);
                        }
                      },
                    ),
                    const Divider(height: 1),
                    // 2) Slots
                    const SizedBox(height: 8),
                    Text(
                      l10n.translate('availableSlots') ?? 'Available Slots',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    if (selectedDate == null)
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          l10n.translate('pleaseSelectDateFirst') ??
                              'Please select a date first',
                          style: TextStyle(color: Colors.grey.shade600),
                        ),
                      )
                    else if (loadingSlots)
                      const Padding(
                        padding: EdgeInsets.all(16.0),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (freeSlots.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          l10n.translate('noSlotsAvailable') ??
                              'No slots available',
                          style: TextStyle(color: Colors.grey.shade600),
                        ),
                      )
                    else
                      ConstrainedBox(
                        constraints: const BoxConstraints(maxHeight: 220),
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: freeSlots.length,
                          itemBuilder: (context, index) {
                            final slot = freeSlots[index];
                            final two = (int n) => n.toString().padLeft(2, '0');
                            final timeStr =
                                '${two(slot.start.hour)}:${two(slot.start.minute)} - ${two(slot.end.hour)}:${two(slot.end.minute)}';
                            final isSelected =
                                selectedSlot != null &&
                                selectedSlot!.start == slot.start &&
                                selectedSlot!.end == slot.end;
                            return ListTile(
                              title: Text(timeStr),
                              selected: isSelected,
                              onTap: () =>
                                  setDialogState(() => selectedSlot = slot),
                              trailing: isSelected
                                  ? const Icon(Icons.check, color: Colors.teal)
                                  : null,
                            );
                          },
                        ),
                      ),
                    const SizedBox(height: 12),
                    const Divider(height: 1),
                    // 3) Appointment type
                    Text(
                      l10n.translate('appointmentType') ?? 'Appointment type',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        ChoiceChip(
                          label: Text(
                            l10n.translate('clinicAddress') ?? 'Clinic',
                          ),
                          selected: !isVideo,
                          onSelected: (v) =>
                              setDialogState(() => isVideo = false),
                          selectedColor: brand.withOpacity(0.3),
                        ),
                        const SizedBox(width: 8),
                        ChoiceChip(
                          label: Text(l10n.videoCall),
                          selected: isVideo,
                          onSelected: (v) =>
                              setDialogState(() => isVideo = true),
                          selectedColor: brand.withOpacity(0.3),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: saving ? null : () => Navigator.of(ctx).pop(),
                child: Text(l10n.cancel),
              ),
              ShifaPrimaryButton(
                isLoading: saving,
                onPressed:
                    saving || selectedDate == null || selectedSlot == null
                    ? null
                    : () async {
                        setDialogState(() => saving = true);
                        final doctorTimeZone =
                            ref
                                    .read(profileAllProvider)
                                    .valueOrNull
                                    ?.profile['timeZone']
                                as String?;
                        if (doctorTimeZone == null ||
                            doctorTimeZone.trim().isEmpty) {
                          setDialogState(() => saving = false);
                          if (ctx.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.translate('failedToAssign') ??
                                      'Profile timezone not available.',
                                ),
                              ),
                            );
                          }
                          return;
                        }
                        try {
                          final patientId = int.parse(p.id);
                          await ref
                              .read(calendarProvider.notifier)
                              .bookFreeSlotRemote(
                                day: selectedDate!,
                                slot: selectedSlot!,
                                patientId: patientId,
                                doctorTimeZone: doctorTimeZone,
                                location: isVideo
                                    ? 'Video Consultation'
                                    : 'Clinic Address',
                                reason: 'Check Up',
                                isVideo: isVideo,
                              );
                          invalidateAppointmentRelatedProviders(ref);
                          await refreshCalendarDay(
                            ref,
                            selectedDate!,
                            doctorTimeZone!,
                          );
                          if (ctx.mounted) {
                            Navigator.of(ctx).pop();
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.translate('patientAssigned') ??
                                      'Patient assigned',
                                ),
                              ),
                            );
                          }
                        } catch (e) {
                          if (ctx.mounted) {
                            setDialogState(() => saving = false);
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  '${l10n.translate('failedToAssign') ?? 'Failed to assign'}: $e',
                                ),
                              ),
                            );
                          }
                        }
                      },
                label: l10n.translate('confirm') ?? 'Confirm',
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final patient = widget.patient;
    final brand = widget.brand;

    if (patient == null) {
      return const SizedBox();
    }
    final p = patient;

    // Backend-backed document list for this patient
    final docsAsync = ref.watch(patientDocumentsProvider(p.id));
    final formsAsync = ref.watch(patientFormsProvider(p.id));

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFF8F8F8),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 14,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        children: [
          // Header: avatar, name, chronic badge, three-dots menu
          Row(
            children: [
              _Avatar(size: 44, name: p.name, photoUrl: p.photoUrl),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  p.name,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (p.general.chronicDisease != null &&
                  p.general.chronicDisease!.isNotEmpty &&
                  p.general.chronicDisease != 'None')
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: const BoxDecoration(
                    color: Colors.red,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.flag, color: Colors.white, size: 20),
                ),
              PopupMenuButton<String>(
                icon: Icon(Icons.more_vert, color: brand, size: 24),
                padding: EdgeInsets.zero,
                onSelected: (value) {
                  if (value == 'make_appointment') {
                    _showMakeAppointmentDialog(context, ref, p, brand);
                  } else if (value == 'briefing') {
                    ref
                        .read(patientBriefingProvider.notifier)
                        .generate(p.id, p.name);
                  }
                },
                itemBuilder: (context) => [
                  PopupMenuItem(
                    value: 'make_appointment',
                    child: Row(
                      children: [
                        Icon(Icons.calendar_today, size: 18, color: brand),
                        const SizedBox(width: 8),
                        Text(
                          AppLocalizations.of(
                                context,
                              )!.translate('makeAppointment') ??
                              'Make appointment',
                        ),
                      ],
                    ),
                  ),
                  PopupMenuItem(
                    value: 'briefing',
                    child: Row(
                      children: [
                        Icon(Icons.summarize, size: 18, color: brand),
                        const SizedBox(width: 8),
                        Text(AppLocalizations.of(context)!.generateBriefing),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),

          // General card (collapsible)
          _CollapsibleCard(
            title: AppLocalizations.of(context)!.generalInformation,
            icon: Icons.person_outline,
            brand: brand,
            child: _GeneralInfo(
              general: p.general,
              patientId: p.id,
              onUpdate: () {
                // Refresh patient list after update
                ref.read(patientsProvider.notifier).loadPatients();
                // Also invalidate patientByIdProvider to update any screens using it (e.g., home screen)
                ref.invalidate(patientByIdProvider(p.id));
              },
            ),
          ),
          const SizedBox(height: 12),

          // 🔐 Patient App Access (collapsible)
          _CollapsibleCard(
            title: AppLocalizations.of(context)!.patientAppAccess,
            icon: Icons.vpn_key_outlined,
            brand: brand,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (p.hasAccount)
                  Row(
                    children: [
                      const Icon(
                        Icons.check_circle,
                        color: Colors.green,
                        size: 16,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          p.username != null && p.username!.isNotEmpty
                              ? '${AppLocalizations.of(context)!.accountAlreadyAvailable} (${p.username})'
                              : AppLocalizations.of(
                                  context,
                                )!.accountAlreadyAvailable,
                          style: const TextStyle(
                            fontSize: 13,
                            color: Colors.green,
                          ),
                        ),
                      ),
                    ],
                  )
                else
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        AppLocalizations.of(context)!.noAccountYet,
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade600,
                        ),
                      ),
                      const SizedBox(height: 8),
                      ShifaPrimaryButton(
                        width: ButtonWidth.fill,
                        onPressed: () => _createAccount(context, ref, p),
                        icon: Icons.person_add_alt_1,
                        label: AppLocalizations.of(context)!.createPatientAccount,
                      ),
                    ],
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Document History with search
          Row(
            children: [
              Expanded(
                child: Text(
                  AppLocalizations.of(context)!.translate('documentHistory') ??
                      'Document History',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: Colors.grey.shade800,
                  ),
                ),
              ),
              // Refresh document list (e.g. after patient uploads or approves access)
              IconButton(
                icon: const Icon(Icons.refresh, size: 20),
                onPressed: () {
                  ref.refresh(patientDocumentsProvider(p.id));
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(
                          AppLocalizations.of(
                                context,
                              )!.translate('refreshing') ??
                              'Refreshing...',
                        ),
                        duration: const Duration(seconds: 1),
                      ),
                    );
                  }
                },
                tooltip:
                    AppLocalizations.of(context)!.translate('refresh') ??
                    'Refresh list',
              ),
              // Three-dots menu for actions
              PopupMenuButton<String>(
                icon: Icon(Icons.more_vert, color: brand),
                onSelected: (value) {
                  switch (value) {
                    case 'upload':
                      widget.onUploadOptions(p);
                      break;
                    case 'form':
                      widget.onCreateForm(p);
                      break;
                    case 'task':
                      ShellScope.pushNamed(
                        context,
                        AppRoutes.createTask,
                        arguments: {'patientId': int.parse(p.id)},
                      );
                      break;
                  }
                },
                itemBuilder: (context) => [
                  PopupMenuItem(
                    value: 'upload',
                    child: Row(
                      children: [
                        Icon(Icons.upload, size: 18, color: brand),
                        const SizedBox(width: 8),
                        Text(AppLocalizations.of(context)!.uploadDocument),
                      ],
                    ),
                  ),
                  PopupMenuItem(
                    value: 'form',
                    child: Row(
                      children: [
                        Icon(Icons.description, size: 18, color: brand),
                        const SizedBox(width: 8),
                        Text(
                          AppLocalizations.of(
                                context,
                              )!.translate('createForm') ??
                              'Create Form',
                        ),
                      ],
                    ),
                  ),
                  PopupMenuItem(
                    value: 'task',
                    child: Row(
                      children: [
                        Icon(Icons.task, size: 18, color: brand),
                        const SizedBox(width: 8),
                        Text(AppLocalizations.of(context)!.createTask),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Document search field
          TextField(
            controller: _documentSearchController,
            decoration: InputDecoration(
              hintText: AppLocalizations.of(context)!.search,
              prefixIcon: const Icon(Icons.search, size: 18),
              filled: true,
              fillColor: Colors.white,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 8,
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(color: Colors.grey.shade300),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(color: Colors.grey.shade300),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide(color: brand, width: 2),
              ),
            ),
          ),
          const SizedBox(height: 8),

          Expanded(
            child: docsAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, st) {
                final l10n = AppLocalizations.of(context)!;
                final safeMessage = sanitizeErrorMessage(e, l10n);
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.error_outline,
                          color: Colors.red.shade400,
                          size: 28,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '${l10n.error}: $safeMessage',
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 8),
                        TextButton.icon(
                          onPressed: () => ref.refresh(patientDocumentsProvider(p.id)),
                          icon: const Icon(Icons.refresh, size: 16),
                          label: Text(l10n.retry),
                        ),
                      ],
                    ),
                  ),
                );
              },
              data: (docs) {
                final l10n = AppLocalizations.of(context)!;
                // Filter documents by search query
                final filteredDocs = _documentSearchQuery.isEmpty
                    ? docs
                    : docs
                          .where(
                            (doc) => doc.title.toLowerCase().contains(
                              _documentSearchQuery.toLowerCase(),
                            ),
                          )
                          .toList();
                // Deep link: open document in PDF viewer once when openDocumentViewer is true and doc is in list
                if (widget.openDocumentViewer &&
                    widget.selectedDocumentId != null &&
                    widget.patient != null &&
                    !_documentViewerOpenedFromDeepLink &&
                    docs.any(
                      (d) => d.id.toString() == widget.selectedDocumentId,
                    )) {
                  _documentViewerOpenedFromDeepLink = true;
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (!mounted) return;
                    ShellScope.push(
                      context,
                      MaterialPageRoute<void>(
                        builder: (_) => DocumentViewerScreen(
                          patientId: widget.patient!.id,
                          documentId: widget.selectedDocumentId!,
                          title: widget.documentTitleForViewer ?? 'Document',
                        ),
                      ),
                    );
                  });
                }
                // Get forms to check which documents are forms
                return formsAsync.when(
                  loading: () => filteredDocs.isEmpty
                      ? Center(child: Text(l10n.noDocuments))
                      : _buildDocumentList(
                          context,
                          ref,
                          filteredDocs,
                          [],
                          brand,
                          widget.formatDate,
                          p,
                        ),
                  error: (_, __) => filteredDocs.isEmpty
                      ? Center(child: Text(l10n.noDocuments))
                      : _buildDocumentList(
                          context,
                          ref,
                          filteredDocs,
                          [],
                          brand,
                          widget.formatDate,
                          p,
                        ),
                  data: (forms) => filteredDocs.isEmpty
                      ? Center(child: Text(l10n.noDocuments))
                      : _buildDocumentList(
                          context,
                          ref,
                          filteredDocs,
                          forms,
                          brand,
                          widget.formatDate,
                          p,
                        ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDocumentList(
    BuildContext context,
    WidgetRef ref,
    List<PatientDocument> docs,
    List<PatientForm> forms,
    Color brand,
    String Function(DateTime) formatDate,
    Patient patient,
  ) {
    // Find form linked to a document by documentId
    PatientForm? findFormForDocument(PatientDocument doc) {
      if (forms.isEmpty) return null;

      // Match by documentId (most reliable)
      try {
        return forms.firstWhere(
          (f) => f.documentId != null && f.documentId == doc.id,
        );
      } catch (e) {
        // Fallback: try matching by title pattern if documentId not available
        if (doc.title.startsWith('Form ')) {
          final titleMatch = RegExp(r'Form (\w+)').firstMatch(doc.title);
          if (titleMatch != null) {
            final templateId = titleMatch.group(1);
            if (templateId != null) {
              try {
                return forms.firstWhere(
                  (f) =>
                      f.templateId == templateId &&
                      f.date.year == doc.date.year &&
                      f.date.month == doc.date.month &&
                      f.date.day == doc.date.day,
                );
              } catch (e) {
                // Last resort: just match template
                try {
                  return forms.firstWhere((f) => f.templateId == templateId);
                } catch (e) {
                  return null;
                }
              }
            }
          }
        }
        return null;
      }
    }

    return ListView.separated(
      itemCount: docs.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final d = docs[index];
        final urlToOpen = d.canView ? (d.url ?? '') : '';
        final linkedForm = findFormForDocument(d);
        final isForm = linkedForm != null;
        final l10n = AppLocalizations.of(context)!;
        final isLocked = !d.canView;
        // Show localized form title for 025-2 (e.g. "025-2 raqamli tibbiy hujjat" in Uzbek)
        final isForm0252 =
            (isForm && linkedForm?.templateId == '025-2') ||
            d.title.startsWith('Form 025-2') ||
            RegExp(r'Form 025-2\s*\([\d-]+\)').hasMatch(d.title);
        final displayTitle = isForm0252
            ? '${l10n.form0252MedicalDocument} (${formatDate(d.date)})'
            : d.title;
        final isSelected =
            widget.selectedDocumentId != null &&
            d.id.toString() == widget.selectedDocumentId;

        final card = _CardBox(
          child: Row(
            children: [
              // Doc info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (isForm)
                          Icon(Icons.description, size: 16, color: brand),
                        if (isForm) const SizedBox(width: 4),
                        if (isLocked)
                          Icon(
                            Icons.lock_outline,
                            size: 16,
                            color: Colors.grey.shade600,
                          ),
                        if (isLocked) const SizedBox(width: 4),
                        Expanded(
                          child: Text(
                            displayTitle,
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      formatDate(d.date),
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                      ),
                    ),
                    if (isLocked && d.creatorLabel.isNotEmpty) ...[
                      const SizedBox(height: 2),
                      Builder(
                        builder: (context) {
                          final locale = Localizations.localeOf(
                            context,
                          ).languageCode.toLowerCase();
                          final creator = d.creatorLabel == 'Unknown'
                              ? (l10n.translate('anotherUser') ??
                                    'Another user')
                              : d.creatorLabel;
                          final byline = locale == 'uz'
                              ? '$creator tomonidan yuklangan'
                              : '${l10n.uploadedBy} $creator';
                          return Text(
                            byline,
                            style: TextStyle(
                              fontSize: 11,
                              color: Colors.grey.shade600,
                            ),
                          );
                        },
                      ),
                    ],
                  ],
                ),
              ),
              // Edit button for forms (only when not locked)
              if (isForm && linkedForm != null && !isLocked)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: IconButton.filledTonal(
                    style: ButtonStyle(
                      backgroundColor: MaterialStatePropertyAll(
                        brand.withOpacity(0.15),
                      ),
                      foregroundColor: MaterialStatePropertyAll(brand),
                    ),
                    onPressed: () {
                      ShellScope.pushNamed(
                        context,
                        AppRoutes.patientForm,
                        arguments: {
                          'patient': patient,
                          'templateId': linkedForm!.templateId,
                          'existingForm': linkedForm!,
                        },
                      ).then((_) {
                        ref.refresh(patientFormsProvider(patient.id));
                        ref.refresh(patientDocumentsProvider(patient.id));
                      });
                    },
                    icon: const Icon(Icons.edit, size: 20),
                    tooltip: l10n.edit,
                  ),
                ),
              // Three-dot menu for locked docs (Request access)
              if (isLocked)
                PopupMenuButton<String>(
                  icon: Icon(Icons.more_vert, color: Colors.grey.shade700),
                  onSelected: (value) async {
                    if (value == 'request_access') {
                      try {
                        final client = ref.read(apiClientProvider);
                        await requestDocumentAccessWithClient(
                          client: client,
                          patientId: patient.id,
                          documentId: d.id,
                        );
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(l10n.requestAccessSent)),
                          );
                          ref.refresh(patientDocumentsProvider(patient.id));
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('${l10n.error}: $e'),
                              backgroundColor: Colors.red,
                            ),
                          );
                        }
                      }
                    }
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: 'request_access',
                      child: Text(l10n.requestAccess),
                    ),
                  ],
                ),
              // Open in viewer: prefer authenticated download (works after access granted)
              IconButton.filledTonal(
                style: ButtonStyle(
                  backgroundColor: MaterialStatePropertyAll(
                    brand.withOpacity(0.15),
                  ),
                  foregroundColor: MaterialStatePropertyAll(brand),
                ),
                onPressed: urlToOpen.isNotEmpty
                    ? () => _openDocument(
                        context,
                        ref: ref,
                        patientId: patient.id,
                        documentId: d.id,
                        title: d.title,
                        l10n: l10n,
                      )
                    : null,
                icon: const Icon(Icons.picture_as_pdf, size: 20),
                tooltip: l10n.openDocument,
              ),
            ],
          ),
        );
        if (isSelected) {
          return Container(
            decoration: BoxDecoration(
              border: Border.all(color: brand, width: 2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: card,
          );
        }
        return card;
      },
    );
  }
}

/// Open document in the in-app viewer (browser-like window). No download, no external app.
void _openDocument(
  BuildContext context, {
  required WidgetRef ref,
  required String patientId,
  required String documentId,
  required String title,
  required AppLocalizations l10n,
}) {
  ShellScope.push(
    context,
    MaterialPageRoute<void>(
      builder: (_) => DocumentViewerScreen(
        patientId: patientId,
        documentId: documentId,
        title: title,
      ),
    ),
  ).then((_) {
    // Refresh document list when returning (e.g. after patient approved access in another tab)
    ref.refresh(patientDocumentsProvider(patientId));
  });
}

/// ---------------- Bits & pieces ----------------
class _Avatar extends StatelessWidget {
  final String? photoUrl;
  final String name;
  final double size;
  const _Avatar({Key? key, required this.name, this.photoUrl, this.size = 24})
    : super(key: key);

  String get initials {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      return parts.first.isNotEmpty
          ? parts.first.substring(0, 1).toUpperCase()
          : '?';
    }
    final first = parts.first.isNotEmpty ? parts.first.substring(0, 1) : '?';
    final last = parts.last.isNotEmpty ? parts.last.substring(0, 1) : '?';
    return (first + last).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final bg = Colors.grey.shade300;
    final hasUrl = photoUrl != null && photoUrl!.isNotEmpty;
    return CircleAvatar(
      radius: size,
      backgroundColor: bg,
      backgroundImage: hasUrl ? NetworkImage(photoUrl!) : null,
      child: hasUrl
          ? null
          : Text(
              initials,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: Colors.grey.shade700,
                fontSize: size * 0.8,
              ),
            ),
    );
  }
}

class _GeneralInfo extends ConsumerStatefulWidget {
  final PatientGeneral general;
  final String patientId;
  final VoidCallback onUpdate;
  const _GeneralInfo({
    Key? key,
    required this.general,
    required this.patientId,
    required this.onUpdate,
  }) : super(key: key);

  @override
  ConsumerState<_GeneralInfo> createState() => _GeneralInfoState();
}

class _GeneralInfoState extends ConsumerState<_GeneralInfo> {
  static const List<String> chronicDiseases = [
    'None',
    'Diabetes (Type 1)',
    'Diabetes (Type 2)',
    'HIV/AIDS',
    'Hypertension',
    'Heart Disease',
    'Chronic Kidney Disease',
    'Chronic Liver Disease',
    'Asthma',
    'COPD',
    'Cancer',
    'Epilepsy',
    'Multiple Sclerosis',
    'Parkinson\'s Disease',
    'Rheumatoid Arthritis',
    'Lupus',
    'Crohn\'s Disease',
    'Ulcerative Colitis',
    'Hemophilia',
    'Sickle Cell Disease',
    'Thalassemia',
    'Other',
  ];

  String? _selectedChronicDisease;
  bool _isUpdating = false;

  @override
  void initState() {
    super.initState();
    _selectedChronicDisease = widget.general.chronicDisease ?? 'None';
  }

  @override
  void didUpdateWidget(_GeneralInfo oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Reset state when patient changes
    if (oldWidget.patientId != widget.patientId ||
        oldWidget.general.chronicDisease != widget.general.chronicDisease) {
      setState(() {
        _selectedChronicDisease = widget.general.chronicDisease ?? 'None';
      });
    }
  }

  Future<void> _updateChronicDisease(String? value) async {
    if (_isUpdating) return;
    setState(() {
      _isUpdating = true;
      _selectedChronicDisease = value;
    });

    try {
      final client = ref.read(apiClientProvider);
      // Send empty string to clear, or the actual value to set
      final chronicDiseaseValue = value == 'None' || value == null ? '' : value;
      final updatedPatient = await updatePatientWithClient(
        client: client,
        patientId: widget.patientId,
        chronicDisease:
            chronicDiseaseValue, // Always send the value (empty string to clear)
      );

      // Update local state immediately based on the response
      if (mounted) {
        setState(() {
          _selectedChronicDisease =
              updatedPatient.general.chronicDisease ?? 'None';
        });
      }

      // Refresh all patient data
      widget.onUpdate();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.chronicDiseaseUpdated),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)!.failedToUpdate}: $e',
            ),
            backgroundColor: Colors.red,
          ),
        );
        // Revert on error
        setState(() {
          _selectedChronicDisease = widget.general.chronicDisease ?? 'None';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _isUpdating = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    String two(int n) => n.toString().padLeft(2, '0');
    String? dob;
    if (widget.general.birthDate != null) {
      final d = widget.general.birthDate!;
      dob = '${two(d.day)}.${two(d.month)}.${d.year}';
    }
    final rows = <MapEntry<String, String>>[
      MapEntry(l10n.patientId, widget.patientId),
      if (dob != null) MapEntry(l10n.birthDate, dob),
      if (widget.general.phone != null)
        MapEntry(l10n.phoneNumber, widget.general.phone!),
      if (widget.general.email != null)
        MapEntry(l10n.email, widget.general.email!),
      if (widget.general.formattedLocation != null)
        MapEntry(l10n.address, widget.general.formattedLocation!),
      if (widget.general.language != null)
        MapEntry(l10n.language, widget.general.language!),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.generalInformation,
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: Colors.grey.shade800,
          ),
        ),
        const SizedBox(height: 8),
        ...rows.map(
          (e) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Row(
              children: [
                SizedBox(
                  width: 110,
                  child: Text(
                    e.key,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                ),
                Expanded(
                  child: Text(e.value, style: const TextStyle(fontSize: 12)),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        // Chronic Disease Dropdown
        Row(
          children: [
            SizedBox(
              width: 110,
              child: Text(
                AppLocalizations.of(context)!.chronicDisease,
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ),
            Expanded(
              child: DropdownButtonFormField<String>(
                value: _selectedChronicDisease,
                isExpanded: true,
                decoration: InputDecoration(
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                ),
                items: chronicDiseases.map((disease) {
                  return DropdownMenuItem<String>(
                    value: disease,
                    child: Text(
                      l10n.translateChronicDisease(disease),
                      style: TextStyle(
                        fontSize: 12,
                        color: disease == 'None'
                            ? Colors.grey
                            : disease == 'HIV/AIDS' || disease == 'Cancer'
                            ? Colors.red
                            : Colors.black87,
                      ),
                    ),
                  );
                }).toList(),
                onChanged: _isUpdating
                    ? null
                    : (value) {
                        if (value != null) {
                          _updateChronicDisease(value);
                        }
                      },
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _CardBox extends StatelessWidget {
  final Widget child;
  const _CardBox({Key? key, required this.child}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: child,
    );
  }
}

class _CollapsibleCard extends StatefulWidget {
  final String title;
  final IconData icon;
  final Color brand;
  final Widget child;

  const _CollapsibleCard({
    Key? key,
    required this.title,
    required this.icon,
    required this.brand,
    required this.child,
  }) : super(key: key);

  @override
  State<_CollapsibleCard> createState() => _CollapsibleCardState();
}

class _CollapsibleCardState extends State<_CollapsibleCard> {
  bool _isExpanded = true;

  @override
  Widget build(BuildContext context) {
    return _CardBox(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => setState(() => _isExpanded = !_isExpanded),
            child: Row(
              children: [
                Icon(widget.icon, size: 16, color: widget.brand),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    widget.title,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                Icon(
                  _isExpanded ? Icons.expand_less : Icons.expand_more,
                  color: Colors.grey.shade600,
                ),
              ],
            ),
          ),
          if (_isExpanded) ...[const SizedBox(height: 12), widget.child],
        ],
      ),
    );
  }
}
