import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_wizard_sheet.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/create_patient_sheet.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

void openCalendarForNewAppointment(WidgetRef ref) {
  ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar);
}

void openCalendarForVideoConsultation(WidgetRef ref) {
  ref.read(calendarQuickBookIntentProvider.notifier).state =
      const CalendarQuickBookIntent(preferVideoConsultation: true);
  ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar);
}

Future<void> openCreatePatientForm(BuildContext context, WidgetRef ref) async {
  await showCreatePatientSheet(context, ref);
}

void openPatientsForDocumentUpload(WidgetRef ref) {
  ref.read(shellProvider.notifier).setTab(DoctorShellTab.patients);
}

Future<void> openCreateTreatmentPlanForm(
  BuildContext context,
  WidgetRef ref,
) async {
  final l10n = AppLocalizations.of(context)!;

  if (!ref.read(hasClinicWorkspaceProvider)) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          l10n.translate('clinicTreatmentPlansSelectPatient') ??
              'Set up a clinic workspace to create treatment plans.',
        ),
      ),
    );
    ref.read(shellProvider.notifier).setTab(DoctorShellTab.clinic);
    return;
  }

  var clinicId = ref.read(selectedClinicIdProvider);
  clinicId ??= ref.read(selectedClinicProvider)?.clinicId;
  if (clinicId == null) {
    final clinics = await ref.read(myClinicsProvider.future);
    if (clinics.isEmpty) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('clinicTreatmentPlansSelectPatient') ??
                  'Set up a clinic workspace to create treatment plans.',
            ),
          ),
        );
      }
      ref.read(shellProvider.notifier).setTab(DoctorShellTab.clinic);
      return;
    }
    clinicId = clinics.first.clinicId;
  }

  if (!context.mounted) return;
  await TreatmentPlanWizardSheet.show(
    context,
    ref,
    clinicId: clinicId,
  );
}

Future<void> openCreateRemoteCareTaskForm(
  BuildContext context,
  WidgetRef ref,
) async {
  final l10n = AppLocalizations.of(context)!;

  if (!ref.read(doctorFeatureProvider(DoctorFeature.remoteCareTasks))) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(l10n.error)),
    );
    return;
  }

  await ShellScope.pushNamed(context, AppRoutes.createTask);
}
