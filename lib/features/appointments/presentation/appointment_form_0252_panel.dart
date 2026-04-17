// lib/features/appointments/presentation/appointment_form_0252_panel.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patient_form_screen.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';

/// Panel shown in appointment documentation when mode is "025-2".
/// Embeds the full 025-2 form (same as from Documents menu). On save: creates form, generates PDF, uploads, links to patient documents.
/// Exposes [requestSave] for "Save & Switch" and [onHasUnsavedChanges] for the unsaved-changes dialog.
class AppointmentForm0252Panel extends StatefulWidget {
  const AppointmentForm0252Panel({
    super.key,
    required this.patientId,
    required this.brand,
    this.onDocumentsChanged,
    this.onHasUnsavedChanges,
  });

  final String patientId;
  final Color brand;
  final VoidCallback? onDocumentsChanged;
  final ValueChanged<bool>? onHasUnsavedChanges;

  @override
  State<AppointmentForm0252Panel> createState() => AppointmentForm0252PanelState();
}

class AppointmentForm0252PanelState extends State<AppointmentForm0252Panel> {
  Future<bool> Function()? _saveFormRef;
  void Function(String code, String title)? _applyIcdRef;

  /// Call from parent when user chooses "Save & Switch" in the documentation mode dialog. Returns true if save succeeded.
  Future<bool> requestSave() async {
    final save = _saveFormRef;
    if (save == null) return false;
    return save();
  }

  void applyIcdSuggestion(String code, String title) {
    _applyIcdRef?.call(code, title);
  }

  @override
  Widget build(BuildContext context) {
    return _AppointmentForm0252PanelContent(
      patientId: widget.patientId,
      brand: widget.brand,
      onDocumentsChanged: widget.onDocumentsChanged,
      onHasUnsavedChanges: widget.onHasUnsavedChanges,
      registerSaveHandler: (fn) {
        // IMPORTANT: Do not call setState during build.
        // This callback is invoked from the embedded form during its initialization.
        // We only need the function reference for requestSave(); no rebuild required.
        _saveFormRef = fn;
      },
      registerIcdApplyHandler: (fn) {
        _applyIcdRef = fn;
      },
    );
  }
}

class _AppointmentForm0252PanelContent extends ConsumerWidget {
  const _AppointmentForm0252PanelContent({
    required this.patientId,
    required this.brand,
    this.onDocumentsChanged,
    this.onHasUnsavedChanges,
    this.registerSaveHandler,
    this.registerIcdApplyHandler,
  });

  final String patientId;
  final Color brand;
  final VoidCallback? onDocumentsChanged;
  final ValueChanged<bool>? onHasUnsavedChanges;
  final void Function(Future<bool> Function() saveFn)? registerSaveHandler;
  final void Function(void Function(String code, String title) applyFn)? registerIcdApplyHandler;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final patientAsync = ref.watch(patientByIdProvider(patientId));

    return patientAsync.when(
      data: (Patient? patient) {
        if (patient == null) {
          return Center(
            child: Text(
              l10n.patientIdNotAvailable,
              style: TextStyle(color: Colors.grey.shade600),
            ),
          );
        }
        return PatientFormScreen(
          patient: patient,
          templateId: '025-2',
          existingForm: null,
          isEmbedded: true,
          onSaved: onDocumentsChanged,
          onUnsavedChange: onHasUnsavedChanges,
          registerSaveHandler: registerSaveHandler,
          registerIcdApplyHandler: registerIcdApplyHandler,
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => Center(
        child: Text(
          '${l10n.error}: $err',
          style: TextStyle(color: Colors.red.shade700, fontSize: 13),
          textAlign: TextAlign.center,
        ),
      ),
    );
  }
}
