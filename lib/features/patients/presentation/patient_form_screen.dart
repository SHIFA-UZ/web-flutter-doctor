import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_forms_provider.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

/// Uzbek-only labels for Form 025-2 PDF (no English in output).
abstract class _Form0252PdfUz {
  static const String fullName = "To'liq ism";
  static const String patientId = 'Bemor ID';
  static const String date = 'Sana';
  static const String ageLabel = "Yoshi";
  static const String gender = 'Jinsi';
  static const String address = 'Manzil';
  static const String job = 'Kasbi';
  static const String diagnosis = 'Tashxis';
  static const String doctor = 'Shifokor';

  static const String sectionComplaints = 'SHIKOYATI';
  static const String sectionObjectiveExam = 'OBYEKTIV TEKSHIRUV';
  static const String sectionPastAndComorbid = "BOSHDAN O'TKAZGAN VA YO'LDOSH KASALLIKLAR";
  static const String sectionOcclusion = "TISH JIPSLANISHI (PRIPKUS)";
  static const String sectionDiseaseDevelopment = "KASALLIK RIVOJLANISHI";
  static const String sectionOralCavity = "OG'IZ BO'SHLIG'I HOLATI";
  static const String sectionXrayLab = "RENTGEN VA LABORATOR TEKSHIRUV MA'LUMOTLARI";
  static const String sectionDentalChart = 'TISH DIAGRAMMASI';
  static const String sectionTreatment = 'DAVOLANISH';
  static const String sectionTreatmentResult = "DAVOLANISH NATIJASI (EPIKRIZ)";
  static const String sectionRecommendations = "KO'RSATMALAR";
  static const String sectionReturnVisits = "QAYTA TASHRIFLAR";
}

class _IcdSearchItem {
  final String code;
  final String title;
  final String? subtitle;

  const _IcdSearchItem({required this.code, required this.title, this.subtitle});
}

class PatientFormScreen extends ConsumerStatefulWidget {
  final Patient patient;
  final String templateId;
  final PatientForm? existingForm; // For editing
  /// When true, form is embedded (e.g. in appointment documentation panel). No AppBar; on save call [onSaved] instead of popping.
  final bool isEmbedded;
  /// Called after successful save when [isEmbedded] is true.
  final VoidCallback? onSaved;
  /// Called when form content changes (for unsaved-changes tracking in parent).
  final ValueChanged<bool>? onUnsavedChange;
  /// Optional: parent can register a callback to trigger save (e.g. "Save & Switch").
  final void Function(Future<bool> Function() saveFn)? registerSaveHandler;
  /// Optional: parent can register a callback to apply ICD suggestion (code+title).
  final void Function(void Function(String code, String title) applyFn)? registerIcdApplyHandler;

  const PatientFormScreen({
    Key? key,
    required this.patient,
    required this.templateId,
    this.existingForm,
    this.isEmbedded = false,
    this.onSaved,
    this.onUnsavedChange,
    this.registerSaveHandler,
    this.registerIcdApplyHandler,
  }) : super(key: key);

  @override
  ConsumerState<PatientFormScreen> createState() => _PatientFormScreenState();
}

class _PatientFormScreenState extends ConsumerState<PatientFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _addressCtrl;
  late TextEditingController _jobCtrl;
  late TextEditingController _diagnosisCtrl;
  late TextEditingController _complaintsCtrl;
  late TextEditingController _otherIllnessesCtrl;
  late TextEditingController _moreDetailsCtrl;
  late TextEditingController _visualCheckupCtrl;
  late TextEditingController _occlusionCtrl;
  late TextEditingController _oralCavityCtrl;
  late TextEditingController _xrayLabCtrl;
  late TextEditingController _treatmentCtrl;
  late TextEditingController _treatmentResultCtrl;
  late TextEditingController _recommendationsCtrl;

  bool _isSaving = false;
  bool _warningShown = false;

  // ---- ICD-10 structured diagnosis (optional) ----
  String? _diagnosisCode;
  String? _diagnosisDisplay;
  String? _diagnosisSystem;

  final _icdSearchCtrl = TextEditingController();
  final _icdSearchFocus = FocusNode();
  Timer? _icdDebounce;
  bool _icdSearching = false;
  List<_IcdSearchItem> _icdResults = const [];

  Future<void> _checkChronicDiseaseWarning() async {
    if (_warningShown) return;
    
    final chronicDisease = widget.patient.general.chronicDisease;
    if (chronicDisease != null &&
        chronicDisease.isNotEmpty &&
        chronicDisease != 'None' &&
        mounted) {
      _warningShown = true;
      await showChronicDiseaseWarning(
        context,
        widget.patient.name,
        chronicDisease,
      );
    }
  }

  // Auto-filled values
  late String _patientId;
  late DateTime _date;
  late String _fullName;
  String? _gender;
  int? _age;

  // Dental chart (template 025-2)
  late Map<String, String> _dentalChart;
  late List<PatientFormFollowup> _followups;

  static const List<String> _dentalCodes = [
    '', // empty / not recorded
    'O',
    'R',
    'C',
    'P',
    'Pt',
    'П',
    'A',
    'I',
    'II',
    'III',
    'K',
    'И',
  ];

  @override
  void initState() {
    super.initState();
    _patientId = widget.patient.id;
    _date = widget.existingForm?.date ?? DateTime.now();
    _fullName = widget.patient.name;
    
    // Check for chronic disease warning after first frame
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkChronicDiseaseWarning();
    });
    
    // Calculate age from birth date
    if (widget.patient.general.birthDate != null) {
      final birthDate = widget.patient.general.birthDate!;
      _age = DateTime.now().year - birthDate.year;
      if (DateTime.now().month < birthDate.month ||
          (DateTime.now().month == birthDate.month &&
              DateTime.now().day < birthDate.day)) {
        _age = _age! - 1;
      }
    }

    // Initialize controllers with existing form data or empty
    _addressCtrl = TextEditingController(
        text: widget.existingForm?.address ?? widget.patient.general.address ?? '');
    _jobCtrl = TextEditingController(text: widget.existingForm?.job ?? '');
    _diagnosisCtrl = TextEditingController(text: widget.existingForm?.diagnosis ?? '');

    _diagnosisCode = widget.existingForm?.diagnosisCode;
    _diagnosisDisplay = widget.existingForm?.diagnosisDisplay;
    _diagnosisSystem = widget.existingForm?.diagnosisSystem;
    _complaintsCtrl = TextEditingController(text: widget.existingForm?.complaints ?? '');
    _otherIllnessesCtrl =
        TextEditingController(text: widget.existingForm?.otherIllnesses ?? '');
    _moreDetailsCtrl = TextEditingController(text: widget.existingForm?.moreDetails ?? '');
    _visualCheckupCtrl =
        TextEditingController(text: widget.existingForm?.visualCheckup ?? '');

    _dentalChart = Map<String, String>.from(widget.existingForm?.dentalChart ?? const {});
    _followups = List<PatientFormFollowup>.from(widget.existingForm?.followups ?? const []);

    // Pre-fill dental diagram from latest 025-2 for this patient when creating NEW form (continuity).
    if (widget.existingForm == null && widget.templateId == '025-2') {
      WidgetsBinding.instance.addPostFrameCallback((_) => _prefillDentalChartFromLatest0252());
    }

    _occlusionCtrl = TextEditingController(text: widget.existingForm?.occlusion ?? '');
    _oralCavityCtrl = TextEditingController(text: widget.existingForm?.oralCavityCondition ?? '');
    _xrayLabCtrl = TextEditingController(text: widget.existingForm?.xrayLabData ?? '');
    _treatmentCtrl = TextEditingController(text: widget.existingForm?.treatment ?? '');
    _treatmentResultCtrl = TextEditingController(text: widget.existingForm?.treatmentResult ?? '');
    _recommendationsCtrl = TextEditingController(text: widget.existingForm?.recommendations ?? '');
    if (widget.registerSaveHandler != null) {
      widget.registerSaveHandler!.call(() => _saveForm());
    }
    if (widget.registerIcdApplyHandler != null) {
      widget.registerIcdApplyHandler!.call((code, title) {
        if (!mounted) return;
        setState(() {
          _diagnosisCode = code;
          _diagnosisDisplay = title;
          _diagnosisSystem = "ICD10";
          // Autofill search field for editability/visibility (doctor can adjust).
          _icdSearchCtrl.text = '$code $title';
          _icdResults = const [];
        });
        widget.onUnsavedChange?.call(true);
      });
    }
    _addUnsavedListeners();
  }

  Future<void> _searchIcd(String q) async {
    final query = q.trim();
    if (query.isEmpty) {
      setState(() {
        _icdResults = const [];
        _icdSearching = false;
      });
      return;
    }

    setState(() => _icdSearching = true);
    try {
      final api = ref.read(apiClientProvider);
      final lang = Localizations.localeOf(context).languageCode;
      final res = await api.get(
        '/api/icd10/search',
        params: {'q': query, 'limit': '20', 'lang': lang},
      );
      if (!mounted) return;
      if (res.statusCode == 200) {
        final list = jsonDecode(utf8.decode(res.bodyBytes)) as List<dynamic>;
        final items = list
            .whereType<Map>()
            .map((m) => _IcdSearchItem(
                  code: (m['code'] ?? '').toString(),
                  title: (m['title'] ?? '').toString(),
                  subtitle: (m['subtitle'] ?? '').toString().trim().isEmpty
                      ? null
                      : (m['subtitle'] ?? '').toString(),
                ))
            .where((i) => i.code.isNotEmpty && i.title.isNotEmpty)
            .toList(growable: false);
        setState(() {
          _icdResults = items;
          _icdSearching = false;
        });
      } else {
        setState(() {
          _icdResults = const [];
          _icdSearching = false;
        });
      }
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _icdResults = const [];
        _icdSearching = false;
      });
    }
  }

  void _selectIcd(_IcdSearchItem item) {
    setState(() {
      _diagnosisCode = item.code;
      _diagnosisDisplay = item.title;
      _diagnosisSystem = "ICD10";
      _icdResults = const [];
      _icdSearchCtrl.clear();
    });
    widget.onUnsavedChange?.call(true);
    _icdSearchFocus.unfocus();
  }

  void _clearIcd() {
    setState(() {
      _diagnosisCode = null;
      _diagnosisDisplay = null;
      _diagnosisSystem = null;
    });
    widget.onUnsavedChange?.call(true);
  }

  void _addUnsavedListeners() {
    void notify() => widget.onUnsavedChange?.call(true);
    _addressCtrl.addListener(notify);
    _jobCtrl.addListener(notify);
    _diagnosisCtrl.addListener(notify);
    _complaintsCtrl.addListener(notify);
    _otherIllnessesCtrl.addListener(notify);
    _moreDetailsCtrl.addListener(notify);
    _visualCheckupCtrl.addListener(notify);
    _occlusionCtrl.addListener(notify);
    _oralCavityCtrl.addListener(notify);
    _xrayLabCtrl.addListener(notify);
    _treatmentCtrl.addListener(notify);
    _treatmentResultCtrl.addListener(notify);
    _recommendationsCtrl.addListener(notify);
  }

  /// Continuity: when creating a new 025-2 form, pre-fill ONLY the teeth diagram from the most recent
  /// 025-2 form created by the same doctor for this patient. No other fields are copied. If none exists, leave diagram empty.
  Future<void> _prefillDentalChartFromLatest0252() async {
    try {
      final profile = await ref.read(profileAllProvider.future);
      final p = profile.profile;
      final currentDoctorName = '${p['lastName'] ?? ''} ${p['firstName'] ?? ''}'.trim();

      ref.invalidate(patientFormsProvider(_patientId));
      final forms = await ref.read(patientFormsProvider(_patientId).future);

      final list0252BySameDoctor = forms
          .where((f) =>
              f.templateId == '025-2' &&
              currentDoctorName.isNotEmpty &&
              (f.doctorName ?? '').trim() == currentDoctorName)
          .toList();
      // Backend orders by date desc only; same-day forms need ordering by id desc (latest created = highest id).
      list0252BySameDoctor.sort((a, b) {
        final idA = int.tryParse(a.id ?? '0') ?? 0;
        final idB = int.tryParse(b.id ?? '0') ?? 0;
        return idB.compareTo(idA);
      });
      final latest0252 = list0252BySameDoctor.isNotEmpty ? list0252BySameDoctor.first : null;

      if (latest0252 != null && latest0252.dentalChart.isNotEmpty && mounted) {
        setState(() {
          _dentalChart = Map<String, String>.from(latest0252.dentalChart);
        });
      }
    } catch (_) {
      // Ignore: leave diagram empty
    }
  }

  @override
  void dispose() {
    _icdDebounce?.cancel();
    _icdSearchCtrl.dispose();
    _icdSearchFocus.dispose();
    _addressCtrl.dispose();
    _jobCtrl.dispose();
    _diagnosisCtrl.dispose();
    _complaintsCtrl.dispose();
    _otherIllnessesCtrl.dispose();
    _moreDetailsCtrl.dispose();
    _visualCheckupCtrl.dispose();
    _occlusionCtrl.dispose();
    _oralCavityCtrl.dispose();
    _xrayLabCtrl.dispose();
    _treatmentCtrl.dispose();
    _treatmentResultCtrl.dispose();
    _recommendationsCtrl.dispose();
    super.dispose();
  }

  /// Returns true if save succeeded, false otherwise. When [widget.isEmbedded], does not pop; calls [widget.onSaved] on success.
  Future<bool> _saveForm() async {
    if (!_formKey.currentState!.validate()) return false;

    setState(() => _isSaving = true);

    try {
      final client = ref.read(apiClientProvider);
      
      // Build form data
      final form = PatientForm(
        id: widget.existingForm?.id,
        patientId: _patientId,
        templateId: widget.templateId,
        date: _date,
        fullName: _fullName,
        gender: _gender,
        address: _addressCtrl.text.trim().isEmpty ? null : _addressCtrl.text.trim(),
        age: _age,
        job: _jobCtrl.text.trim().isEmpty ? null : _jobCtrl.text.trim(),
        diagnosis: _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
        diagnosisCode: _diagnosisCode,
        diagnosisDisplay: _diagnosisDisplay,
        diagnosisSystem: _diagnosisSystem,
        complaints: _complaintsCtrl.text.trim().isEmpty ? null : _complaintsCtrl.text.trim(),
        otherIllnesses:
            _otherIllnessesCtrl.text.trim().isEmpty ? null : _otherIllnessesCtrl.text.trim(),
        moreDetails: _moreDetailsCtrl.text.trim().isEmpty ? null : _moreDetailsCtrl.text.trim(),
        visualCheckup:
            _visualCheckupCtrl.text.trim().isEmpty ? null : _visualCheckupCtrl.text.trim(),
        occlusion: _occlusionCtrl.text.trim().isEmpty ? null : _occlusionCtrl.text.trim(),
        oralCavityCondition:
            _oralCavityCtrl.text.trim().isEmpty ? null : _oralCavityCtrl.text.trim(),
        xrayLabData: _xrayLabCtrl.text.trim().isEmpty ? null : _xrayLabCtrl.text.trim(),
        treatment: _treatmentCtrl.text.trim().isEmpty ? null : _treatmentCtrl.text.trim(),
        treatmentResult:
            _treatmentResultCtrl.text.trim().isEmpty ? null : _treatmentResultCtrl.text.trim(),
        recommendations:
            _recommendationsCtrl.text.trim().isEmpty ? null : _recommendationsCtrl.text.trim(),
        dentalChart: widget.templateId == '025-2' ? _dentalChart : const {},
        followups: widget.templateId == '025-2' ? _followups : const [],
      );

      // Save form data to backend (create or update)
      PatientForm savedForm;
      if (widget.existingForm?.id != null) {
        // Update existing form
        savedForm = await updatePatientFormWithClient(
          client: client,
          patientId: _patientId,
          formId: widget.existingForm!.id!,
          form: form,
        );
      } else {
        // Create new form
        savedForm = await createPatientFormWithClient(
          client: client,
          patientId: _patientId,
          form: form,
        );
      }

      // Generate PDF using in-memory _dentalChart so entered values are always shown (backend response may omit or alter dentalChart).
      final l10n = AppLocalizations.of(context)!;
      final formForPdf = savedForm.copyWith(dentalChart: _dentalChart);
      final pdfBytes = await _generatePdf(formForPdf, l10n);

      // Upload or update PDF document
      final fileName = 'form_${widget.templateId}_${_date.toIso8601String().split('T')[0]}.pdf';
      final title = 'Form ${widget.templateId} (${_date.toIso8601String().split('T')[0]})';

      PatientDocument? document;
      
      // Check if form already has a linked document (editing existing form)
      if (widget.existingForm?.documentId != null) {
        // Update existing document
        document = await updatePatientDocumentWithClient(
          client: client,
          patientId: _patientId,
          documentId: widget.existingForm!.documentId!,
          fileBytes: pdfBytes,
          fileName: fileName,
        );
      } else {
        // Create new document. Forms (e.g. 025-2) stay doctor-private regardless
        // of category, but tag the upload so the document list shows the type.
        document = await uploadPatientDocumentWithClient(
          client: client,
          patientId: _patientId,
          fileBytes: pdfBytes,
          fileName: fileName,
          title: title,
          category: 'FORM_025_2',
        );

        // Link document to form if form was just created
        if (savedForm.id != null && document != null) {
          final linkedForm = await linkDocumentToFormWithClient(
            client: client,
            patientId: _patientId,
            formId: savedForm.id!,
            documentId: document.id,
          );
          // Update savedForm with the linked documentId
          savedForm = linkedForm;
        }
      }

      // Refresh documents and forms so appointment notes "From last 025-2" updates
      ref.invalidate(patientDocumentsProvider(_patientId));
      ref.invalidate(patientFormsProvider(_patientId));

      if (!mounted) return false;
      if (widget.isEmbedded) {
        widget.onUnsavedChange?.call(false);
        widget.onSaved?.call();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.formSavedSuccessfully)),
        );
        return true;
      }
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.formSavedSuccessfully)),
      );
      return true;
    } catch (e) {
      if (!mounted) return false;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${AppLocalizations.of(context)!.errorSavingForm}: $e'),
          backgroundColor: Colors.red,
        ),
      );
      return false;
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
      }
    }
  }

  Future<Uint8List> _generatePdf(PatientForm form, AppLocalizations l10n) async {
    final pdf = pw.Document();
    
    // Try to load a font that supports Cyrillic characters
    pw.Font? cyrillicFont;
    try {
      final fontData = await rootBundle.load('assets/fonts/DejaVuSans.ttf');
      cyrillicFont = pw.Font.ttf(fontData);
    } catch (e) {
      // Font file not found - will use default font (may not support Cyrillic)
      // To fix: Download DejaVu Sans from https://dejavu-fonts.github.io/
      // and place it in assets/fonts/DejaVuSans.ttf
      // See CYRILLIC_FONT_SETUP.md for instructions
      cyrillicFont = null;
    }

    // Form 025-2: landscape A4, Uzbek-only labels, bordered metadata, standardized sections.
    if (form.templateId == '025-2') {
      pdf.addPage(
        pw.MultiPage(
          pageFormat: PdfPageFormat.a4.landscape,
          margin: const pw.EdgeInsets.all(40),
          build: (pw.Context context) => _build0252PortraitContent(form, l10n, cyrillicFont),
        ),
      );
      return pdf.save();
    }

    // Non-025-2: portrait MultiPage (unchanged).
    pdf.addPage(
      pw.MultiPage(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(40),
        build: (pw.Context context) => [
          pw.Text(
            'Form ${form.templateId}',
            style: pw.TextStyle(
              fontSize: 24,
              fontWeight: pw.FontWeight.bold,
              font: cyrillicFont,
            ),
          ),
          pw.SizedBox(height: 20),
          _buildPdfRow('${l10n.patientId}:', form.patientId, font: cyrillicFont),
          _buildPdfRow('${l10n.date}:', _formatDate(form.date), font: cyrillicFont),
          _buildPdfRow('${l10n.fullName}:', form.fullName, font: cyrillicFont),
          if (form.gender != null) _buildPdfRow('${l10n.gender}:', form.gender!, font: cyrillicFont),
          if (form.address != null) _buildPdfRow('${l10n.address}:', form.address!, font: cyrillicFont),
          if (form.age != null) _buildPdfRow('${l10n.age}:', form.age.toString(), font: cyrillicFont),
          if (form.job != null) _buildPdfRow('${l10n.job}:', form.job!, font: cyrillicFont),
          if (form.diagnosis != null) _buildPdfRow('${l10n.diagnosis}:', form.diagnosis!, font: cyrillicFont),
          if (form.complaints != null) _buildPdfRow('${l10n.complaints}:', form.complaints!, font: cyrillicFont),
          if (form.otherIllnesses != null)
            _buildPdfRow('${l10n.otherIllnessesAndComplications}:', form.otherIllnesses!, font: cyrillicFont),
          if (form.moreDetails != null) _buildPdfRow('${l10n.moreDetailsOnAbove}:', form.moreDetails!, font: cyrillicFont),
          if (form.visualCheckup != null) _buildPdfRow('${l10n.visualCheckup}:', form.visualCheckup!, font: cyrillicFont),
        ],
      ),
    );

    return pdf.save();
  }

  // ---------- Form 025-2 portrait A4 (clean layout) ----------

  List<pw.Widget> _build0252PortraitContent(PatientForm form, AppLocalizations l10n, pw.Font? font) {
    final list = <pw.Widget>[
      _build0252HeaderPortraitPdf(form, font),
      pw.SizedBox(height: 12),
      _build0252MetadataBlock(form, font),
      pw.SizedBox(height: 14),
      _build0252SectionHeader(_Form0252PdfUz.sectionComplaints, font),
      if (form.complaints != null && form.complaints!.isNotEmpty)
        pw.Paragraph(text: form.complaints!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionObjectiveExam, font),
      if (form.visualCheckup != null && form.visualCheckup!.isNotEmpty)
        pw.Paragraph(text: form.visualCheckup!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.diagnosis, font),
      // ICD-10 structured diagnosis (assistive) must always appear under "Tashxis" when selected.
      if (form.diagnosisCode != null &&
          form.diagnosisDisplay != null &&
          form.diagnosisCode!.trim().isNotEmpty &&
          form.diagnosisDisplay!.trim().isNotEmpty)
        pw.Paragraph(
          text: '${form.diagnosisCode!.trim()} — ${form.diagnosisDisplay!.trim()}',
          style: pw.TextStyle(fontSize: 11, font: font),
          margin: pw.EdgeInsets.zero,
        ),
      // Free-text diagnosis remains the primary editable field (backward compatible).
      if (form.diagnosis != null && form.diagnosis!.isNotEmpty)
        pw.Paragraph(
          text: form.diagnosis!,
          style: pw.TextStyle(fontSize: 11, font: font),
          margin: pw.EdgeInsets.zero,
        ),
      _build0252SectionHeader(_Form0252PdfUz.sectionPastAndComorbid, font),
      if (form.otherIllnesses != null && form.otherIllnesses!.isNotEmpty)
        pw.Paragraph(text: form.otherIllnesses!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionOcclusion, font),
      if (form.occlusion != null && form.occlusion!.isNotEmpty)
        pw.Paragraph(text: form.occlusion!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionDiseaseDevelopment, font),
      if (form.moreDetails != null && form.moreDetails!.isNotEmpty)
        pw.Paragraph(text: form.moreDetails!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionOralCavity, font),
      if (form.oralCavityCondition != null && form.oralCavityCondition!.isNotEmpty)
        pw.Paragraph(text: form.oralCavityCondition!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionXrayLab, font),
      if (form.xrayLabData != null && form.xrayLabData!.isNotEmpty)
        pw.Paragraph(text: form.xrayLabData!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionDentalChart, font),
      pw.SizedBox(height: 6),
      _buildDentalChartPortraitPdf(form.dentalChart, font),
      pw.SizedBox(height: 14),
      _build0252SectionHeader(_Form0252PdfUz.sectionTreatment, font),
      if (form.treatment != null && form.treatment!.isNotEmpty)
        pw.Paragraph(text: form.treatment!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionTreatmentResult, font),
      if (form.treatmentResult != null && form.treatmentResult!.isNotEmpty)
        pw.Paragraph(text: form.treatmentResult!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
      _build0252SectionHeader(_Form0252PdfUz.sectionRecommendations, font),
      if (form.recommendations != null && form.recommendations!.isNotEmpty)
        pw.Paragraph(text: form.recommendations!, style: pw.TextStyle(fontSize: 11, font: font), margin: pw.EdgeInsets.zero),
    ];
    if (form.followups.isNotEmpty) {
      list.addAll([
        _build0252SectionHeader(_Form0252PdfUz.sectionReturnVisits, font),
        pw.SizedBox(height: 6),
        _buildFollowupsPdf(form.followups, l10n, font),
      ]);
    }
    return list;
  }

  pw.Widget _build0252HeaderPortraitPdf(PatientForm form, pw.Font? font) {
    final clinicName = form.doctorClinic ?? '';
    final formNumber = form.formNumber?.toString() ?? '';
    final dateStr = _formatDate(form.date);
    final titleStyle = pw.TextStyle(fontSize: 17, fontWeight: pw.FontWeight.bold, font: font);
    final smallStyle = pw.TextStyle(fontSize: 9, font: font, color: PdfColors.grey700);

    return pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.stretch,
      children: [
        pw.Row(
          mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Expanded(
              child: pw.Column(
                crossAxisAlignment: pw.CrossAxisAlignment.start,
                children: [
                  pw.Text(clinicName, style: pw.TextStyle(fontSize: 11, fontWeight: pw.FontWeight.bold, font: font)),
                  pw.SizedBox(height: 2),
                  pw.Text(
                    "2017-yil 25-dekabrdagi Nr. 777-sonli buyruq bilan tasdiqlangan 025-2 raqamli tibbiy hujjat shakli",
                    style: smallStyle,
                  ),
                ],
              ),
            ),
            pw.Expanded(
              child: pw.Column(
                mainAxisAlignment: pw.MainAxisAlignment.center,
                children: [
                  pw.Text('STOMATOLOGIK BEMORNING TIBBIY KARTASI', style: titleStyle, textAlign: pw.TextAlign.center),
                ],
              ),
            ),
            pw.Expanded(
              child: pw.Column(
                crossAxisAlignment: pw.CrossAxisAlignment.end,
                children: [
                  pw.Text('Nr. $formNumber', style: pw.TextStyle(fontSize: 10, font: font)),
                  pw.Text('Sana: $dateStr', style: pw.TextStyle(fontSize: 10, font: font)),
                  pw.Text('Bemor ID: ${form.patientId}', style: pw.TextStyle(fontSize: 10, font: font)),
                ],
              ),
            ),
          ],
        ),
        pw.SizedBox(height: 8),
        pw.Container(height: 1, color: PdfColors.grey400),
      ],
    );
  }

  pw.Widget _build0252SectionHeader(String text, pw.Font? font) {
    return pw.Container(
      margin: const pw.EdgeInsets.only(top: 14),
      width: double.infinity,
      padding: const pw.EdgeInsets.symmetric(vertical: 6, horizontal: 8),
      color: PdfColors.grey300,
      child: pw.Text(
        text.toUpperCase(),
        style: pw.TextStyle(fontSize: 14, fontWeight: pw.FontWeight.bold, font: font),
      ),
    );
  }

  pw.Widget _build0252MetadataBlock(PatientForm form, pw.Font? font) {
    final rows = <pw.Widget>[
      _buildPdfRow(_Form0252PdfUz.fullName, form.fullName, font: font),
      _buildPdfRow(_Form0252PdfUz.patientId, form.patientId, font: font),
      _buildPdfRow(_Form0252PdfUz.date, _formatDate(form.date), font: font),
      if (form.age != null)
        _buildPdfRow(_Form0252PdfUz.ageLabel, '${form.age} yosh', font: font),
      if (form.gender != null) _buildPdfRow(_Form0252PdfUz.gender, form.gender!, font: font),
      if (form.address != null) _buildPdfRow(_Form0252PdfUz.address, form.address!, font: font),
      if (form.job != null) _buildPdfRow(_Form0252PdfUz.job, form.job!, font: font),
      if (form.doctorName != null) _buildPdfRow(_Form0252PdfUz.doctor, form.doctorName!, font: font),
    ];
    return pw.Container(
      padding: const pw.EdgeInsets.all(12),
      decoration: pw.BoxDecoration(
        border: pw.Border.all(color: PdfColors.grey600, width: 1),
      ),
      child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.stretch, children: rows),
    );
  }

  /// Parses UI dental chart (TOP_*_*, BOTTOM_*_*) into rows of 16 values each. Each value gets its own cell in the PDF.
  /// Returns (topRows, bottomRows). Each row is List<String> of 16 cells (indices 0–7 = right 8→1, 8–15 = left 1→8).
  static (List<List<String>> top, List<List<String>> bottom) _dentalChartRowsForPdf(Map<String, String> chart) {
    const cellsPerRow = 16;
    final topByRow = <int, List<String>>{};
    final bottomByRow = <int, List<String>>{};
    for (final entry in chart.entries) {
      final key = entry.key;
      final value = entry.value;
      if (key.startsWith('TOP_')) {
        final parts = key.split('_');
        if (parts.length >= 3) {
          final rowIndex = int.tryParse(parts[1]) ?? 0;
          final cellIndex = int.tryParse(parts[2]) ?? 0;
          if (cellIndex >= 0 && cellIndex < cellsPerRow) {
            topByRow.putIfAbsent(rowIndex, () => List.filled(cellsPerRow, ''));
            topByRow[rowIndex]![cellIndex] = value;
          }
        }
      } else if (key.startsWith('BOTTOM_')) {
        final parts = key.split('_');
        if (parts.length >= 3) {
          final rowIndex = int.tryParse(parts[1]) ?? 0;
          final cellIndex = int.tryParse(parts[2]) ?? 0;
          if (cellIndex >= 0 && cellIndex < cellsPerRow) {
            bottomByRow.putIfAbsent(rowIndex, () => List.filled(cellsPerRow, ''));
            bottomByRow[rowIndex]![cellIndex] = value;
          }
        }
      }
    }
    final topRows = topByRow.keys.toList()..sort();
    final bottomRows = bottomByRow.keys.toList()..sort();
    return (
      topRows.map((i) => List<String>.from(topByRow[i]!)).toList(),
      bottomRows.map((i) => List<String>.from(bottomByRow[i]!)).toList(),
    );
  }

  /// Converts UR/UL/LR/LL single-value chart to one row per jaw for PDF (legacy format).
  static Map<String, String> _dentalChartForPdf(Map<String, String> chart) {
    if (chart.isEmpty) return chart;
    if (chart.keys.any((k) => k.startsWith('TOP_') || k.startsWith('BOTTOM_'))) return chart;
    return chart;
  }

  /// Dental chart: single number row 8 7 6 5 4 3 2 1 | 1 2 3 4 5 6 7 8.
  /// Multiple rows of cells above = upper jaw (one row per UI row); multiple below = lower jaw. Each value in its own cell.
  pw.Widget _buildDentalChartPortraitPdf(Map<String, String> chart, pw.Font? font) {
    const boxSize = 26.0;
    final cellStyle = pw.TextStyle(fontSize: 9, font: font);

    pw.Widget cell(String? code) {
      final text = (code != null && code.isNotEmpty) ? code : '';
      return pw.Container(
        width: boxSize,
        height: boxSize,
        alignment: pw.Alignment.center,
        decoration: pw.BoxDecoration(
          border: pw.Border.all(color: PdfColors.grey600, width: 0.5),
        ),
        child: pw.Text(text, style: cellStyle, textAlign: pw.TextAlign.center, maxLines: 2),
      );
    }

    pw.Widget numberCell(String n) {
      return pw.Container(
        width: boxSize,
        height: 22,
        alignment: pw.Alignment.center,
        child: pw.Text(n, style: pw.TextStyle(fontSize: 10, fontWeight: pw.FontWeight.bold, font: font)),
      );
    }

    const rightNums = ['8', '7', '6', '5', '4', '3', '2', '1'];
    const leftNums = ['1', '2', '3', '4', '5', '6', '7', '8'];

    pw.Widget rowOfCellsFromList(List<String> values) {
      final list = List<String>.filled(16, '');
      for (var i = 0; i < values.length && i < 16; i++) list[i] = values[i];
      return pw.Row(
        mainAxisSize: pw.MainAxisSize.min,
        mainAxisAlignment: pw.MainAxisAlignment.center,
        children: [
          ...List.generate(8, (i) => cell(list[i])),
          pw.SizedBox(width: 4),
          pw.Container(width: 2, height: boxSize, color: PdfColors.grey400),
          pw.SizedBox(width: 4),
          ...List.generate(8, (i) => cell(list[8 + i])),
        ],
      );
    }

    pw.Widget rowOfCellsFromChart(Map<String, String> pdfChart, String rightQuad, String leftQuad) {
      return pw.Row(
        mainAxisSize: pw.MainAxisSize.min,
        mainAxisAlignment: pw.MainAxisAlignment.center,
        children: [
          ...rightNums.map((n) => cell(pdfChart['$rightQuad$n'])),
          pw.SizedBox(width: 4),
          pw.Container(width: 2, height: boxSize, color: PdfColors.grey400),
          pw.SizedBox(width: 4),
          ...leftNums.map((n) => cell(pdfChart['$leftQuad$n'])),
        ],
      );
    }

    pw.Widget numberRow() {
      return pw.Row(
        mainAxisSize: pw.MainAxisSize.min,
        mainAxisAlignment: pw.MainAxisAlignment.center,
        children: [
          ...rightNums.map((n) => numberCell(n)),
          pw.SizedBox(width: 4),
          pw.SizedBox(width: 2),
          pw.SizedBox(width: 4),
          ...leftNums.map((n) => numberCell(n)),
        ],
      );
    }

    final hasUiKeys = chart.keys.any((k) => k.startsWith('TOP_') || k.startsWith('BOTTOM_'));
    if (hasUiKeys) {
      final (topRows, bottomRows) = _dentalChartRowsForPdf(chart);
      return pw.Column(
        mainAxisSize: pw.MainAxisSize.min,
        children: [
          for (final row in topRows) ...[
            rowOfCellsFromList(row),
            pw.SizedBox(height: 2),
          ],
          numberRow(),
          pw.SizedBox(height: 2),
          for (final row in bottomRows) ...[
            rowOfCellsFromList(row),
            pw.SizedBox(height: 2),
          ],
        ],
      );
    }

    final pdfChart = _dentalChartForPdf(chart);
    return pw.Column(
      mainAxisSize: pw.MainAxisSize.min,
      children: [
        rowOfCellsFromChart(pdfChart, 'UR', 'UL'),
        pw.SizedBox(height: 2),
        numberRow(),
        pw.SizedBox(height: 2),
        rowOfCellsFromChart(pdfChart, 'LR', 'LL'),
      ],
    );
  }

  pw.Widget _buildFollowupsPdf(List<PatientFormFollowup> followups, AppLocalizations l10n, pw.Font? font) {
    pw.Widget cell(String text, {bool header = false}) {
      return pw.Container(
        padding: const pw.EdgeInsets.all(4),
        decoration: pw.BoxDecoration(
          border: pw.Border.all(color: PdfColors.grey600, width: 0.5),
          color: header ? PdfColors.grey200 : PdfColors.white,
        ),
        child: pw.Text(
          text,
          style: pw.TextStyle(
            fontSize: 9,
            fontWeight: header ? pw.FontWeight.bold : pw.FontWeight.normal,
            font: font,
          ),
        ),
      );
    }
    final rows = <pw.TableRow>[
      pw.TableRow(
        children: [
          cell(l10n.date, header: true),
          cell(l10n.clinicalFindingsConclusion, header: true),
          cell(l10n.doctorsSurname, header: true),
        ],
      ),
      ...followups.map((f) {
        final d =
            '${f.date.year.toString().padLeft(4, '0')}-${f.date.month.toString().padLeft(2, '0')}-${f.date.day.toString().padLeft(2, '0')}';
        return pw.TableRow(
          children: [
            cell(d),
            cell(f.clinicalFindings),
            cell(f.doctorName ?? ''),
          ],
        );
      }),
    ];
    return pw.Table(columnWidths: const {0: pw.FixedColumnWidth(70)}, children: rows);
  }

  pw.Widget _buildPdfRow(String label, String value, {pw.Font? font}) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 12),
      child: pw.Row(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.SizedBox(
            width: 200,
            child: pw.Text(
              label,
              style: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                font: font,
              ),
            ),
          ),
          pw.Expanded(
            child: pw.Text(
              value,
              style: pw.TextStyle(font: font),
            ),
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime date) {
    return '${date.day.toString().padLeft(2, '0')}.${date.month.toString().padLeft(2, '0')}.${date.year}';
  }

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: widget.isEmbedded
          ? null
          : AppBar(
              title: Text(widget.templateId == '025-2' ? l10n.form0252MedicalDocument : 'Form ${widget.templateId}'),
              actions: [
                if (widget.existingForm != null)
                  IconButton(
                    icon: const Icon(Icons.print),
                    onPressed: () async {
                      // Print: build form from current state so PDF shows all fields including dental chart
                      final form = PatientForm(
                  id: widget.existingForm!.id,
                  patientId: _patientId,
                  templateId: widget.templateId,
                  date: _date,
                  fullName: _fullName,
                  gender: _gender,
                  address: _addressCtrl.text.trim().isEmpty ? null : _addressCtrl.text.trim(),
                  age: _age,
                  job: _jobCtrl.text.trim().isEmpty ? null : _jobCtrl.text.trim(),
                  diagnosis: _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
                  diagnosisCode: _diagnosisCode,
                  diagnosisDisplay: _diagnosisDisplay,
                  diagnosisSystem: _diagnosisSystem,
                  complaints: _complaintsCtrl.text.trim().isEmpty ? null : _complaintsCtrl.text.trim(),
                  otherIllnesses:
                      _otherIllnessesCtrl.text.trim().isEmpty ? null : _otherIllnessesCtrl.text.trim(),
                  moreDetails: _moreDetailsCtrl.text.trim().isEmpty ? null : _moreDetailsCtrl.text.trim(),
                  visualCheckup:
                      _visualCheckupCtrl.text.trim().isEmpty ? null : _visualCheckupCtrl.text.trim(),
                  occlusion: _occlusionCtrl.text.trim().isEmpty ? null : _occlusionCtrl.text.trim(),
                  oralCavityCondition:
                      _oralCavityCtrl.text.trim().isEmpty ? null : _oralCavityCtrl.text.trim(),
                  xrayLabData: _xrayLabCtrl.text.trim().isEmpty ? null : _xrayLabCtrl.text.trim(),
                  treatment: _treatmentCtrl.text.trim().isEmpty ? null : _treatmentCtrl.text.trim(),
                  treatmentResult:
                      _treatmentResultCtrl.text.trim().isEmpty ? null : _treatmentResultCtrl.text.trim(),
                  recommendations:
                      _recommendationsCtrl.text.trim().isEmpty ? null : _recommendationsCtrl.text.trim(),
                  doctorName: widget.existingForm!.doctorName,
                  doctorClinic: widget.existingForm!.doctorClinic,
                  formNumber: widget.existingForm!.formNumber,
                  dentalChart: widget.templateId == '025-2' ? _dentalChart : const {},
                  followups: widget.templateId == '025-2' ? _followups : const [],
                      );
                      final l10n = AppLocalizations.of(context)!;
                      await _generatePdf(form, l10n);
                      if (mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(AppLocalizations.of(context)!.pdfGeneratedPrintingNotImplemented)),
                        );
                      }
                    },
                  ),
              ],
            ),
      body: Form(
        key: _formKey,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Auto-filled fields (read-only)
              _buildReadOnlyField(l10n.patientId, _patientId),
              _buildReadOnlyField(l10n.date, _formatDate(_date)),
              _buildReadOnlyField(l10n.fullName, _fullName),
              if (_gender != null) _buildReadOnlyField(l10n.gender, _gender!),
              if (_age != null) _buildReadOnlyField(l10n.age, _age.toString()),

              const SizedBox(height: 24),

              // Editable fields
              TextFormField(
                controller: _addressCtrl,
                decoration: InputDecoration(
                  labelText: l10n.address,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _jobCtrl,
                decoration: InputDecoration(
                  labelText: l10n.job,
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _diagnosisCtrl,
                decoration: InputDecoration(
                  labelText: l10n.diagnosis,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 3,
              ),
              const SizedBox(height: 16),
              // ---- ICD-10 picker (optional; free-text diagnosis remains primary) ----
              if (_diagnosisCode != null && _diagnosisDisplay != null) ...[
                Align(
                  alignment: Alignment.centerLeft,
                  child: InputChip(
                    label: Text('${_diagnosisCode!} — ${_diagnosisDisplay!}'),
                    onDeleted: _clearIcd,
                  ),
                ),
                const SizedBox(height: 12),
              ],
              TextFormField(
                controller: _icdSearchCtrl,
                focusNode: _icdSearchFocus,
                decoration: InputDecoration(
                  labelText: 'ICD-10',
                  hintText: l10n.translate('icd10SearchHint'),
                  border: const OutlineInputBorder(),
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _icdSearching
                      ? const Padding(
                          padding: EdgeInsets.all(12),
                          child: SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        )
                      : (_icdSearchCtrl.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.close),
                              onPressed: () => setState(() {
                                _icdSearchCtrl.clear();
                                _icdResults = const [];
                              }),
                            )
                          : null),
                ),
                onChanged: (v) {
                  _icdDebounce?.cancel();
                  _icdDebounce = Timer(const Duration(milliseconds: 250), () {
                    if (mounted) _searchIcd(v);
                  });
                },
              ),
              if (_icdResults.isNotEmpty) ...[
                const SizedBox(height: 8),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 240),
                  child: Material(
                    elevation: 2,
                    borderRadius: BorderRadius.circular(8),
                    child: ListView.separated(
                      shrinkWrap: true,
                      itemCount: _icdResults.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, i) {
                        final item = _icdResults[i];
                        return ListTile(
                          dense: true,
                          title: Text(
                            '${item.code} — ${item.title}',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: item.subtitle == null
                              ? null
                              : Text(
                                  item.subtitle!,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                          onTap: () => _selectIcd(item),
                        );
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 16),
              ] else
                const SizedBox(height: 16),
              TextFormField(
                controller: _complaintsCtrl,
                decoration: InputDecoration(
                  labelText: l10n.complaints,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 3,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _otherIllnessesCtrl,
                decoration: InputDecoration(
                  labelText: l10n.otherIllnessesAndComplications,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 3,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _moreDetailsCtrl,
                decoration: InputDecoration(
                  labelText: l10n.moreDetailsOnAbove,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 4,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _visualCheckupCtrl,
                decoration: InputDecoration(
                  labelText: l10n.visualCheckup,
                  border: const OutlineInputBorder(),
                ),
                maxLines: 3,
              ),

              if (widget.templateId == '025-2') ...[
                const SizedBox(height: 24),
                Text(
                  l10n.dentalChart,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: brand,
                  ),
                ),
                const SizedBox(height: 10),
                _DentalChartGrid(
                  value: _dentalChart,
                  codes: _dentalCodes,
                  onChanged: (next) {
                    setState(() => _dentalChart = next);
                    widget.onUnsavedChange?.call(true);
                  },
                ),
                const SizedBox(height: 10),
                Text(
                  l10n.translate('dentalLegend'),
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
                ),

                const SizedBox(height: 24),
                TextFormField(
                  controller: _occlusionCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.occlusionBiteType,
                    border: const OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _oralCavityCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.oralCavityCondition,
                    border: const OutlineInputBorder(),
                  ),
                  maxLines: 3,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _xrayLabCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.xrayLabExaminationData,
                    border: const OutlineInputBorder(),
                  ),
                  maxLines: 3,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _treatmentCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.treatment,
                    border: const OutlineInputBorder(),
                  ),
                  maxLines: 3,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _treatmentResultCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.treatmentResultProgress,
                    border: const OutlineInputBorder(),
                  ),
                  maxLines: 3,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _recommendationsCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.recommendationsInstructions,
                    border: const OutlineInputBorder(),
                  ),
                  maxLines: 3,
                ),
                const SizedBox(height: 16),
                _buildReadOnlyField(
                  l10n.doctor,
                  widget.existingForm?.doctorName ?? l10n.willBeSetAutomaticallyOnSave,
                ),

                const SizedBox(height: 24),
                Text(
                  l10n.returnVisits,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: brand,
                  ),
                ),
                const SizedBox(height: 10),
                _FollowupsTable(
                  followups: _followups,
                  onChanged: (next) {
                    setState(() => _followups = next);
                    widget.onUnsavedChange?.call(true);
                  },
                ),
              ],

              const SizedBox(height: 32),
              ShifaPrimaryButton(
                width: ButtonWidth.fill,
                onPressed: _isSaving ? null : _saveForm,
                isLoading: _isSaving,
                label: l10n.saveForm,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildReadOnlyField(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade600,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 4),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.grey.shade100,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.grey.shade300),
            ),
            child: Text(
              value,
              style: const TextStyle(fontSize: 16),
            ),
          ),
        ],
      ),
    );
  }
}

class _DentalChartGrid extends StatefulWidget {
  final Map<String, String> value;
  final List<String> codes;
  final ValueChanged<Map<String, String>> onChanged;

  const _DentalChartGrid({
    required this.value,
    required this.codes,
    required this.onChanged,
  });

  @override
  State<_DentalChartGrid> createState() => _DentalChartGridState();
}

class _DentalChartGridState extends State<_DentalChartGrid> {
  // Additional input cells above and below the tooth map
  // Each row contains 16 cells (8 teeth per quadrant, 2 quadrants per jaw)
  final List<List<String>> _topInputRows = [];
  final List<List<String>> _bottomInputRows = [];
  
  static const int _cellsPerRow = 16; // 8 UR + 8 UL for upper, 8 LR + 8 LL for lower

  @override
  void initState() {
    super.initState();
    _loadInputs();
  }

  @override
  void didUpdateWidget(covariant _DentalChartGrid oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value == widget.value) return;
    // Only reload from value when it has *more* rows than we have (e.g. pre-fill from previous form).
    // If we have more rows locally (e.g. user just added an empty row), don't wipe - empty rows aren't persisted.
    final topRowIndices = widget.value.keys
        .where((k) => k.startsWith('TOP_'))
        .map((k) {
          final parts = k.split('_');
          return parts.length >= 2 ? int.tryParse(parts[1]) ?? -1 : -1;
        })
        .where((i) => i >= 0)
        .toSet();
    final bottomRowIndices = widget.value.keys
        .where((k) => k.startsWith('BOTTOM_'))
        .map((k) {
          final parts = k.split('_');
          return parts.length >= 2 ? int.tryParse(parts[1]) ?? -1 : -1;
        })
        .where((i) => i >= 0)
        .toSet();
    final valueTopCount = topRowIndices.isEmpty ? 0 : (topRowIndices.reduce((a, b) => a > b ? a : b) + 1);
    final valueBottomCount = bottomRowIndices.isEmpty ? 0 : (bottomRowIndices.reduce((a, b) => a > b ? a : b) + 1);
    if (valueTopCount > _topInputRows.length || valueBottomCount > _bottomInputRows.length) {
      _topInputRows.clear();
      _bottomInputRows.clear();
      _loadInputs();
    }
  }

  void _loadInputs() {
    // Load top rows (sort by row index so order is consistent)
    final topEntries = widget.value.entries
        .where((e) => e.key.startsWith('TOP_'))
        .toList();
    if (topEntries.isNotEmpty) {
      final rowMap = <int, List<String>>{};
      for (final entry in topEntries) {
        final parts = entry.key.split('_');
        if (parts.length >= 3) {
          final rowIndex = int.tryParse(parts[1]) ?? 0;
          final cellIndex = int.tryParse(parts[2]) ?? 0;
          if (!rowMap.containsKey(rowIndex)) {
            rowMap[rowIndex] = List.filled(_cellsPerRow, '');
          }
          if (cellIndex < _cellsPerRow) {
            rowMap[rowIndex]![cellIndex] = entry.value;
          }
        }
      }
      final sortedTop = rowMap.keys.toList()..sort();
      for (final i in sortedTop) {
        _topInputRows.add(List<String>.from(rowMap[i]!));
      }
    }

    // Load bottom rows
    final bottomEntries = widget.value.entries
        .where((e) => e.key.startsWith('BOTTOM_'))
        .toList();
    if (bottomEntries.isNotEmpty) {
      final rowMap = <int, List<String>>{};
      for (final entry in bottomEntries) {
        final parts = entry.key.split('_');
        if (parts.length >= 3) {
          final rowIndex = int.tryParse(parts[1]) ?? 0;
          final cellIndex = int.tryParse(parts[2]) ?? 0;
          if (!rowMap.containsKey(rowIndex)) {
            rowMap[rowIndex] = List.filled(_cellsPerRow, '');
          }
          if (cellIndex < _cellsPerRow) {
            rowMap[rowIndex]![cellIndex] = entry.value;
          }
        }
      }
      final sortedBottom = rowMap.keys.toList()..sort();
      for (final i in sortedBottom) {
        _bottomInputRows.add(List<String>.from(rowMap[i]!));
      }
    }
  }

  void _addTopRow() {
    setState(() {
      _topInputRows.insert(0, List.filled(_cellsPerRow, ''));
    });
    _saveInputs();
  }

  void _addBottomRow() {
    setState(() {
      _bottomInputRows.add(List.filled(_cellsPerRow, ''));
    });
    _saveInputs();
  }

  void _updateTopInput(int rowIndex, int cellIndex, String? newValue) {
    setState(() {
      if (rowIndex < _topInputRows.length && cellIndex < _topInputRows[rowIndex].length) {
        _topInputRows[rowIndex][cellIndex] = newValue ?? '';
      }
    });
    _saveInputs();
  }

  void _updateBottomInput(int rowIndex, int cellIndex, String? newValue) {
    setState(() {
      if (rowIndex < _bottomInputRows.length && cellIndex < _bottomInputRows[rowIndex].length) {
        _bottomInputRows[rowIndex][cellIndex] = newValue ?? '';
      }
    });
    _saveInputs();
  }

  void _removeTopRow(int rowIndex) {
    setState(() {
      _topInputRows.removeAt(rowIndex);
    });
    _saveInputs();
  }

  void _removeBottomRow(int rowIndex) {
    setState(() {
      _bottomInputRows.removeAt(rowIndex);
    });
    _saveInputs();
  }

  void _saveInputs() {
    final updated = Map<String, String>.from(widget.value);
    // Clear old top/bottom entries
    updated.removeWhere((key, value) => key.startsWith('TOP_') || key.startsWith('BOTTOM_'));
    // Add new entries
    for (int rowIndex = 0; rowIndex < _topInputRows.length; rowIndex++) {
      for (int cellIndex = 0; cellIndex < _topInputRows[rowIndex].length; cellIndex++) {
        final value = _topInputRows[rowIndex][cellIndex];
        if (value.isNotEmpty) {
          updated['TOP_${rowIndex}_$cellIndex'] = value;
        }
      }
    }
    for (int rowIndex = 0; rowIndex < _bottomInputRows.length; rowIndex++) {
      for (int cellIndex = 0; cellIndex < _bottomInputRows[rowIndex].length; cellIndex++) {
        final value = _bottomInputRows[rowIndex][cellIndex];
        if (value.isNotEmpty) {
          updated['BOTTOM_${rowIndex}_$cellIndex'] = value;
        }
      }
    }
    widget.onChanged(updated);
  }

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    const codeLabel = <String, String>{
      '': '-',
      'O': 'O',
      'R': 'R',
      'C': 'C',
      'P': 'P',
      'Pt': 'Pt',
      'П': 'П',
      'A': 'A',
      'I': 'I',
      'II': 'II',
      'III': 'III',
      'K': 'K',
      'И': 'И',
    };

    String labelFor(String v) => codeLabel[v] ?? v;

    // Tooth notation: UR1-UR8, UL1-UL8, LL1-LL8, LR1-LR8
    // Upper Right: UR8 to UR1 (right to left from patient's perspective)
    // Upper Left: UL1 to UL8 (left to right from patient's perspective)
    // Lower Right: LR8 to LR1 (right to left from patient's perspective)
    // Lower Left: LL1 to LL8 (left to right from patient's perspective)

    Widget buildTooth(String quadrant, int toothNum, {bool isUpper = true}) {
      final key = '$quadrant$toothNum';
      final current = widget.value[key] ?? '';
      final toothLabel = '$quadrant$toothNum';

      // Determine tooth shape based on type
      bool isIncisor = toothNum == 1 || toothNum == 2;
      bool isCanine = toothNum == 3;
      bool isPremolar = toothNum == 4 || toothNum == 5;
      bool isMolar = toothNum >= 6;

      return Container(
          width: 45,
          height: isUpper ? 60 : 60,
          margin: const EdgeInsets.all(2),
          decoration: BoxDecoration(
            color: current.isNotEmpty 
                ? brand.withOpacity(0.15) 
                : Colors.grey.shade50,
            border: Border.all(
              color: current.isNotEmpty ? brand : Colors.grey.shade400,
              width: current.isNotEmpty ? 2 : 1.5,
            ),
            borderRadius: BorderRadius.only(
              topLeft: isUpper ? const Radius.circular(20) : const Radius.circular(4),
              topRight: isUpper ? const Radius.circular(20) : const Radius.circular(4),
              bottomLeft: isUpper ? const Radius.circular(4) : const Radius.circular(20),
              bottomRight: isUpper ? const Radius.circular(4) : const Radius.circular(20),
            ),
          ),
          child: Stack(
            children: [
              // Tooth visual representation
              Positioned.fill(
                child: CustomPaint(
                  painter: _ToothPainter(
                    isIncisor: isIncisor,
                    isCanine: isCanine,
                    isPremolar: isPremolar,
                    isMolar: isMolar,
                    isUpper: isUpper,
                    hasCondition: current.isNotEmpty,
                    color: brand,
                  ),
                ),
              ),
              // Label and value
              Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    toothLabel,
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.bold,
                      color: current.isNotEmpty ? brand : Colors.grey.shade600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  if (current.isNotEmpty)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                      decoration: BoxDecoration(
                        color: brand,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        labelFor(current),
                        style: const TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: Colors.white,
                        ),
                      ),
                    ),
                ],
              ),
            ],
          ),
        );
    }

    Widget buildInputCell({
      required String? value,
      required VoidCallback onAdd,
      required Function(String?) onChanged,
      required VoidCallback? onRemove,
      required bool showRemove,
    }) {
      return Container(
        width: 45, // Match tooth width exactly
        height: 40,
        margin: const EdgeInsets.all(2), // Match tooth margin
        decoration: BoxDecoration(
          border: Border.all(color: Colors.grey.shade300),
          borderRadius: BorderRadius.circular(8),
        ),
        child: DropdownButtonHideUnderline(
          child: DropdownButton<String>(
            value: widget.codes.contains(value ?? '') ? (value ?? '') : '',
            isDense: true,
            isExpanded: true,
            icon: const Icon(Icons.arrow_drop_down, size: 14),
            items: widget.codes
                .map(
                  (c) => DropdownMenuItem<String>(
                    value: c,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Text(
                        labelFor(c),
                        style: const TextStyle(fontSize: 10),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                )
                .toList(),
            onChanged: onChanged,
          ),
        ),
      );
    }

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Container(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // Title
            Builder(
              builder: (context) {
                final l10n = AppLocalizations.of(context)!;
                return Text(
                  l10n.toothMap.toUpperCase(),
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: brand,
                  ),
                );
              },
            ),
            const SizedBox(height: 8),
            // Column-based layout: Each tooth has its own vertical column
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Section labels row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // UR8 to UR1 section label
                      SizedBox(
                        width: 49 * 8, // 8 teeth * width
                        child: Text(
                          'I',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 16),
                      // UL1 to UL8 section label
                      SizedBox(
                        width: 49 * 8, // 8 teeth * width
                        child: Text(
                          'II',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  // Top input rows (stacked above teeth)
                  ..._topInputRows.asMap().entries.map((rowEntry) {
                    final rowIndex = rowEntry.key;
                    final row = rowEntry.value;
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          // UR8 to UR1 cells
                          ...([8, 7, 6, 5, 4, 3, 2, 1].asMap().entries.map((entry) {
                            final urIndex = entry.key; // 0-7
                            return buildInputCell(
                              value: row[urIndex],
                              onAdd: _addTopRow,
                              onChanged: (v) => _updateTopInput(rowIndex, urIndex, v),
                              onRemove: null,
                              showRemove: false,
                            );
                          })),
                          const SizedBox(width: 16),
                          // UL1 to UL8 cells
                          ...([1, 2, 3, 4, 5, 6, 7, 8].asMap().entries.map((entry) {
                            final ulIndex = entry.key + 8; // 8-15
                            return buildInputCell(
                              value: row[ulIndex],
                              onAdd: _addTopRow,
                              onChanged: (v) => _updateTopInput(rowIndex, ulIndex, v),
                              onRemove: null,
                              showRemove: false,
                            );
                          })),
                        ],
                      ),
                    );
                  }),
                  // Add top row button
                  if (_topInputRows.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          SizedBox(
                            width: 49 * 8 + 16 + 49 * 8,
                            child: Center(
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  // Remove buttons for each row
                                  ..._topInputRows.asMap().entries.map((rowEntry) {
                                    final rowIndex = rowEntry.key;
                                    return IconButton(
                                      icon: Icon(Icons.close, color: Colors.red.shade400, size: 20),
                                      onPressed: () => _removeTopRow(rowIndex),
                                      tooltip: AppLocalizations.of(context)!.removeRow,
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                    );
                                  }),
                                  IconButton(
                                    icon: Icon(Icons.add_circle, color: brand, size: 28),
                                    onPressed: _addTopRow,
                                    tooltip: AppLocalizations.of(context)!.addRow,
                                    padding: EdgeInsets.zero,
                                    constraints: const BoxConstraints(),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    )
                  else
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          SizedBox(
                            width: 49 * 8 + 16 + 49 * 8,
                            child: Center(
                              child: IconButton(
                                icon: Icon(Icons.add_circle, color: brand, size: 28),
                                onPressed: _addTopRow,
                                tooltip: AppLocalizations.of(context)!.addRow,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(height: 8),
                  // Upper teeth row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // UR8 to UR1
                      ...([8, 7, 6, 5, 4, 3, 2, 1].map((num) => buildTooth('UR', num, isUpper: true))),
                      const SizedBox(width: 16),
                      // UL1 to UL8
                      ...([1, 2, 3, 4, 5, 6, 7, 8].map((num) => buildTooth('UL', num, isUpper: true))),
                    ],
                  ),
                  const SizedBox(height: 4),
                  // Tooth type labels
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // UR8 to UR1 labels
                      ...([8, 7, 6, 5, 4, 3, 2, 1].map((num) {
                        String label = '';
                        if (num == 8) label = 'wisdom';
                        else if (num >= 6) label = 'molars';
                        else if (num >= 4) label = 'pre-molars';
                        else if (num == 3) label = 'Canine';
                        else label = 'incisors';
                        return SizedBox(
                          width: 49,
                          child: Text(
                            label,
                            style: TextStyle(
                              fontSize: 8,
                              color: Colors.grey.shade600,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        );
                      })),
                      const SizedBox(width: 16),
                      // UL1 to UL8 labels
                      ...([1, 2, 3, 4, 5, 6, 7, 8].map((num) {
                        String label = '';
                        if (num == 1 || num == 2) label = 'incisors';
                        else if (num == 3) label = 'Canine';
                        else if (num == 4 || num == 5) label = 'pre-molars';
                        else if (num >= 6) label = 'molars';
                        if (num == 8) label = 'wisdom';
                        return SizedBox(
                          width: 49,
                          child: Text(
                            label,
                            style: TextStyle(
                              fontSize: 8,
                              color: Colors.grey.shade600,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        );
                      })),
                    ],
                  ),
                  const SizedBox(height: 8),
                  // Lower teeth row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // LR8 to LR1
                      ...([8, 7, 6, 5, 4, 3, 2, 1].map((num) => buildTooth('LR', num, isUpper: false))),
                      const SizedBox(width: 16),
                      // LL1 to LL8
                      ...([1, 2, 3, 4, 5, 6, 7, 8].map((num) => buildTooth('LL', num, isUpper: false))),
                    ],
                  ),
                  const SizedBox(height: 4),
                  // Lower section labels
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                        width: 49 * 8,
                        child: Text(
                          'IV',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 16),
                      SizedBox(
                        width: 49 * 8,
                        child: Text(
                          'III',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  // Bottom input rows (stacked below teeth)
                  ..._bottomInputRows.asMap().entries.map((rowEntry) {
                    final rowIndex = rowEntry.key;
                    final row = rowEntry.value;
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          // LR8 to LR1 cells
                          ...([8, 7, 6, 5, 4, 3, 2, 1].asMap().entries.map((entry) {
                            final lrIndex = entry.key; // 0-7
                            return buildInputCell(
                              value: row[lrIndex],
                              onAdd: _addBottomRow,
                              onChanged: (v) => _updateBottomInput(rowIndex, lrIndex, v),
                              onRemove: null,
                              showRemove: false,
                            );
                          })),
                          const SizedBox(width: 16),
                          // LL1 to LL8 cells
                          ...([1, 2, 3, 4, 5, 6, 7, 8].asMap().entries.map((entry) {
                            final llIndex = entry.key + 8; // 8-15
                            return buildInputCell(
                              value: row[llIndex],
                              onAdd: _addBottomRow,
                              onChanged: (v) => _updateBottomInput(rowIndex, llIndex, v),
                              onRemove: null,
                              showRemove: false,
                            );
                          })),
                        ],
                      ),
                    );
                  }),
                  // Add bottom row button
                  if (_bottomInputRows.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          SizedBox(
                            width: 49 * 8 + 16 + 49 * 8,
                            child: Center(
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  // Remove buttons for each row
                                  ..._bottomInputRows.asMap().entries.map((rowEntry) {
                                    final rowIndex = rowEntry.key;
                                    return IconButton(
                                      icon: Icon(Icons.close, color: Colors.red.shade400, size: 20),
                                      onPressed: () => _removeBottomRow(rowIndex),
                                      tooltip: AppLocalizations.of(context)!.removeRow,
                                      padding: EdgeInsets.zero,
                                      constraints: const BoxConstraints(),
                                    );
                                  }),
                                  IconButton(
                                    icon: Icon(Icons.add_circle, color: brand, size: 28),
                                    onPressed: _addBottomRow,
                                    tooltip: AppLocalizations.of(context)!.addRow,
                                    padding: EdgeInsets.zero,
                                    constraints: const BoxConstraints(),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    )
                  else
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          SizedBox(
                            width: 49 * 8 + 16 + 49 * 8,
                            child: Center(
                              child: IconButton(
                                icon: Icon(Icons.add_circle, color: brand, size: 28),
                                onPressed: _addBottomRow,
                                tooltip: AppLocalizations.of(context)!.addRow,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

}

class _ToothPainter extends CustomPainter {
  final bool isIncisor;
  final bool isCanine;
  final bool isPremolar;
  final bool isMolar;
  final bool isUpper;
  final bool hasCondition;
  final Color color;

  _ToothPainter({
    required this.isIncisor,
    required this.isCanine,
    required this.isPremolar,
    required this.isMolar,
    required this.isUpper,
    required this.hasCondition,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = hasCondition ? color.withOpacity(0.2) : Colors.grey.shade200
      ..style = PaintingStyle.fill;

    final borderPaint = Paint()
      ..color = hasCondition ? color : Colors.grey.shade300
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;

    final path = Path();
    
    // Draw tooth shape based on type
    if (isIncisor) {
      // Rectangular shape for incisors
      path.addRRect(RRect.fromRectAndRadius(
        Rect.fromLTWH(size.width * 0.2, size.height * 0.1, size.width * 0.6, size.height * 0.8),
        const Radius.circular(4),
      ));
    } else if (isCanine) {
      // Pointed shape for canines
      path.moveTo(size.width * 0.5, size.height * 0.1);
      path.lineTo(size.width * 0.3, size.height * 0.5);
      path.lineTo(size.width * 0.2, size.height * 0.9);
      path.lineTo(size.width * 0.8, size.height * 0.9);
      path.lineTo(size.width * 0.7, size.height * 0.5);
      path.close();
    } else if (isPremolar) {
      // Rounded rectangular for premolars
      path.addRRect(RRect.fromRectAndRadius(
        Rect.fromLTWH(size.width * 0.15, size.height * 0.1, size.width * 0.7, size.height * 0.8),
        const Radius.circular(6),
      ));
    } else {
      // Wider, more rounded for molars
      path.addRRect(RRect.fromRectAndRadius(
        Rect.fromLTWH(size.width * 0.1, size.height * 0.1, size.width * 0.8, size.height * 0.8),
        const Radius.circular(8),
      ));
    }

    canvas.drawPath(path, paint);
    canvas.drawPath(path, borderPaint);

    // Draw root lines
    if (isUpper) {
      // Roots at bottom for upper teeth
      final rootPaint = Paint()
        ..color = Colors.grey.shade400
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1;
      
      if (isMolar) {
        // Multiple roots for molars
        canvas.drawLine(
          Offset(size.width * 0.3, size.height * 0.9),
          Offset(size.width * 0.3, size.height),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.5, size.height * 0.9),
          Offset(size.width * 0.5, size.height),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.7, size.height * 0.9),
          Offset(size.width * 0.7, size.height),
          rootPaint,
        );
      } else {
        // Single or double root for others
        canvas.drawLine(
          Offset(size.width * 0.4, size.height * 0.9),
          Offset(size.width * 0.4, size.height),
          rootPaint,
        );
        if (isPremolar) {
          canvas.drawLine(
            Offset(size.width * 0.6, size.height * 0.9),
            Offset(size.width * 0.6, size.height),
            rootPaint,
          );
        }
      }
    } else {
      // Roots at top for lower teeth
      final rootPaint = Paint()
        ..color = Colors.grey.shade400
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1;
      
      if (isMolar) {
        // Multiple roots for molars
        canvas.drawLine(
          Offset(size.width * 0.3, size.height * 0.1),
          Offset(size.width * 0.3, 0),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.5, size.height * 0.1),
          Offset(size.width * 0.5, 0),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.7, size.height * 0.1),
          Offset(size.width * 0.7, 0),
          rootPaint,
        );
      } else {
        // Single or double root for others
        canvas.drawLine(
          Offset(size.width * 0.4, size.height * 0.1),
          Offset(size.width * 0.4, 0),
          rootPaint,
        );
        if (isPremolar) {
          canvas.drawLine(
            Offset(size.width * 0.6, size.height * 0.1),
            Offset(size.width * 0.6, 0),
            rootPaint,
          );
        }
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}

class _FollowupsTable extends StatelessWidget {
  final List<PatientFormFollowup> followups;
  final ValueChanged<List<PatientFormFollowup>> onChanged;

  const _FollowupsTable({
    required this.followups,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    final l10n = AppLocalizations.of(context)!;

    Future<void> addRow() async {
      final next = List<PatientFormFollowup>.from(followups)
        ..add(
          PatientFormFollowup(
            date: DateTime.now(),
            clinicalFindings: '',
            doctorName: null,
          ),
        );
      onChanged(next);
    }

    Future<void> editRow(int index) async {
      final f = followups[index];
      final ctrl = TextEditingController(text: f.clinicalFindings);
      DateTime date = f.date;

      final res = await showDialog<bool>(
        context: context,
        builder: (ctx) {
          final l10n = AppLocalizations.of(ctx)!;
          return AlertDialog(
            title: Text(l10n.returnVisits),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Text('${l10n.date}:'),
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: () async {
                        final picked = await showDatePicker(
                          context: ctx,
                          firstDate: DateTime(2000),
                          lastDate: DateTime(2100),
                          initialDate: date,
                        );
                        if (picked != null) {
                          date = picked;
                          (ctx as Element).markNeedsBuild();
                        }
                      },
                      child: Text(
                        '${date.year.toString().padLeft(4, '0')}-'
                        '${date.month.toString().padLeft(2, '0')}-'
                        '${date.day.toString().padLeft(2, '0')}',
                      ),
                    ),
                  ],
                ),
                TextField(
                  controller: ctrl,
                  decoration: InputDecoration(
                    labelText: l10n.clinicalFindingsConclusion,
                  ),
                  maxLines: 4,
                ),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(l10n.cancel)),
              ShifaPrimaryButton(onPressed: () => Navigator.pop(ctx, true), label: l10n.save),
            ],
          );
        },
      );

      if (res != true) return;
      final next = List<PatientFormFollowup>.from(followups);
      next[index] = PatientFormFollowup(
        date: date,
        clinicalFindings: ctrl.text.trim(),
        doctorName: f.doctorName,
      );
      onChanged(next);
    }

    void removeRow(int index) {
      final next = List<PatientFormFollowup>.from(followups)..removeAt(index);
      onChanged(next);
    }

    Widget header(String text) => Expanded(
          child: Text(text, style: const TextStyle(fontWeight: FontWeight.w700)),
        );

    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: brand.withOpacity(0.06),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: Colors.grey.shade300),
          ),
          child: Row(
            children: [
              header(l10n.date),
              header(l10n.clinicalFindingsConclusion),
              header(l10n.doctorsSurname),
              const SizedBox(width: 40),
            ],
          ),
        ),
        const SizedBox(height: 8),
        if (followups.isEmpty)
          Align(
            alignment: Alignment.centerLeft,
            child: Text(l10n.noReturnVisitsAddedYet),
          )
        else
          ...followups.asMap().entries.map((e) {
            final idx = e.key;
            final f = e.value;
            final dateStr =
                '${f.date.day.toString().padLeft(2, '0')}.${f.date.month.toString().padLeft(2, '0')}.${f.date.year}';
            return Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.grey.shade300),
              ),
              child: Row(
                children: [
                  Expanded(child: Text(dateStr)),
                  Expanded(
                    child: Text(
                      f.clinicalFindings.isEmpty ? '(tap to edit)' : f.clinicalFindings,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  Expanded(child: Text(f.doctorName ?? '(auto)')),
                  IconButton(
                    onPressed: () => editRow(idx),
                    icon: const Icon(Icons.edit, size: 18),
                    tooltip: AppLocalizations.of(context)!.editRow,
                  ),
                  IconButton(
                    onPressed: () => removeRow(idx),
                    icon: const Icon(Icons.delete_outline, size: 18),
                    tooltip: AppLocalizations.of(context)!.removeRow,
                  ),
                ],
              ),
            );
          }),
        const SizedBox(height: 10),
        Align(
          alignment: Alignment.centerLeft,
          child: ShifaSecondaryButton(
            onPressed: addRow,
            icon: Icons.add,
            label: l10n.addReturnVisit,
          ),
        ),
      ],
    );
  }
}
