// TEMPORARY DEBUG SCREEN - Remove after fixing timezone issue
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:timezone/timezone.dart' as tz;
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';

class TimezoneDebugScreen extends ConsumerWidget {
  const TimezoneDebugScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(profileAllProvider);
    final appointmentsAsync = ref.watch(todayAppointmentsProvider);

    return Scaffold(
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        title: const Text('Timezone Debug'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('PROFILE TIMEZONE:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            const SizedBox(height: 8),
            profileAsync.when(
              data: (profile) {
                final tz = profile.profile['timeZone'];
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Timezone from profile: ${tz ?? "NULL"}'),
                    Text('Type: ${tz.runtimeType}'),
                    Text('Is empty: ${tz?.toString().isEmpty ?? true}'),
                    if (tz != null) Text('Full profile: ${profile.profile}'),
                  ],
                );
              },
              loading: () => const Text('Loading profile...'),
              error: (e, st) => Text('Error: $e'),
            ),
            const Divider(height: 32),
            const Text('CURRENT TIME:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            const SizedBox(height: 8),
            Builder(builder: (context) {
              final doctorTz = profileAsync.valueOrNull?.profile['timeZone'] as String?;
              final nowUtc = DateTime.now().toUtc();
              final nowLocal = DateTime.now();
              final nowDoctor = getNowInTimezone(doctorTz);

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Device Local: ${nowLocal.hour}:${nowLocal.minute}:${nowLocal.second}'),
                  Text('UTC: ${nowUtc.hour}:${nowUtc.minute}:${nowUtc.second}'),
                  Text('Doctor Timezone ($doctorTz): ${nowDoctor.hour}:${nowDoctor.minute}:${nowDoctor.second}'),
                ],
              );
            }),
            const Divider(height: 32),
            const Text('APPOINTMENTS:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            const SizedBox(height: 8),
            appointmentsAsync.when(
              data: (appointments) {
                if (appointments.isEmpty) {
                  return const Text('No appointments today');
                }
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: appointments.take(3).map((appt) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          border: Border.all(color: Colors.grey),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Patient: ${appt.patientName}', style: const TextStyle(fontWeight: FontWeight.bold)),
                            Text('Appointment ID: ${appt.id}'),
                            Text('Start Time (TimeOfDay): ${appt.start.hour}:${appt.start.minute}'),
                            Text('Formatted: ${appt.start.format(context)}'),
                          ],
                        ),
                      ),
                    );
                  }).toList(),
                );
              },
              loading: () => const CircularProgressIndicator(),
              error: (e, st) => Text('Error: $e'),
            ),
            const Divider(height: 32),
            const Text('TEST CONVERSION:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            const SizedBox(height: 8),
            Builder(builder: (context) {
              final doctorTz = profileAsync.valueOrNull?.profile['timeZone'] as String?;
              const testUtc = '2024-03-15T08:00:00Z';
              final converted = CalendarEntry.utcIsoToTimeOfDayInZone(testUtc, doctorTz);

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Input UTC: $testUtc (08:00 UTC)'),
                  Text('Doctor timezone: $doctorTz'),
                  Text('Converted TimeOfDay: ${converted.hour}:${converted.minute}'),
                  Text('Expected: Should be 08:00 if UTC, or offset by timezone'),
                ],
              );
            }),
          ],
        ),
      ),
    );
  }
}
