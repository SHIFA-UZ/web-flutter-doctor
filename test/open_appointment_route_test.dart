import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/open_appointment_route.dart';

void main() {
  Map<String, dynamic> payload({
    String route = 'inPerson',
    String status = 'IN_PROGRESS',
    String location = 'Clinic',
    Map<String, dynamic>? overrides,
  }) {
    final appointment = <String, dynamic>{
      'appointmentId': 10,
      'patientId': 3,
      'patientName': 'Franco',
      'location': location,
      'status': status,
      'day': '2026-10-16',
      'startLocal': '09:00',
      'endLocal': '09:30',
      'startAt': '2026-10-16T09:00:00Z',
      'route': route,
      'reason': 'Checkup',
    };
    if (overrides != null) appointment.addAll(overrides);
    return {'route': route, 'appointment': appointment};
  }

  setUp(OpenAppointmentAutoOpen.resetForTest);

  test('in-person and video routes keep the required appointment fields', () {
    final inPerson = planOpenAppointmentRoute(payload());
    expect(inPerson!.routeName, AppRoutes.inPerson);
    expect(inPerson.appointment.id, '10');
    expect(inPerson.appointment.location, 'Clinic');
    expect(inPerson.appointment.status, AppointmentStatus.inProgress);
    expect(inPerson.appointment.day, DateTime.parse('2026-10-16'));

    final video = planOpenAppointmentRoute(
      payload(route: 'video', location: 'Video Consultation'),
    );
    expect(video!.routeName, AppRoutes.videoCall);
    expect(video.appointment.isVideo, isTrue);
  });

  test('no-show cancelled and incomplete payloads do not navigate', () {
    expect(planOpenAppointmentRoute(payload(status: 'NO_SHOW')), isNull);
    expect(planOpenAppointmentRoute(payload(status: 'CANCELLED')), isNull);
    expect(
      planOpenAppointmentRoute(payload(overrides: {'startLocal': null})),
      isNull,
    );
    expect(planOpenAppointmentRoute({'spokenHint': 'missing appointment'}), isNull);
  });

  test('the same visit is auto-opened once', () {
    final plan = planOpenAppointmentRoute(payload())!;
    expect(OpenAppointmentAutoOpen.claim(plan.autoOpenKey), isTrue);
    expect(OpenAppointmentAutoOpen.claim(plan.autoOpenKey), isFalse);
  });
}
