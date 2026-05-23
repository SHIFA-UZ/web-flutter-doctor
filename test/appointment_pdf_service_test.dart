import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('generateAppointmentPdf', () {
    test('builds PDF for general appointment (UZ + EN)', () async {
      final base = AppointmentPdfData(
        appointmentId: '322',
        clinicName: 'Test Clinic',
        patientName: 'Test Patient',
        patientId: '59',
        doctorName: 'Dr. Smile',
        specialization: 'Dentistry',
        licenseNumber: 'LIC-001',
        appointmentType: 'Shaxsan',
        duration: '30 min',
        dateStr: '2026-05-23',
        timeStr: '13:20',
        appointmentDate: DateTime.utc(2026, 5, 23, 13, 20),
        notes:
            'Subjective:\nChief complaint...\n\nObjective:\nExam normal.\n\nPlan:\nFollow up.',
      );

      final uz = await generateAppointmentPdf(data: base, languageCode: 'uz');
      final en = await generateAppointmentPdf(data: base, languageCode: 'en');

      expect(uz.length, greaterThan(500));
      expect(en.length, greaterThan(500));
    });

    test('builds PDF with structured dental billing', () async {
      final dental = AppointmentPdfData(
        appointmentId: '322',
        clinicName: 'Dental Office',
        patientName: 'Test Patient',
        patientId: '59',
        doctorName: 'Dr. Smile',
        specialization: 'Dentistry',
        appointmentType: 'Shaxsan',
        duration: '30 min',
        dateStr: '2026-05-23',
        timeStr: '13:20',
        appointmentDate: DateTime.utc(2026, 5, 23, 13, 20),
        notes: 'Post-op rinse instructions given.',
        isDentalDocumentation: true,
        dentalBilling: AppointmentPdfDentalBilling(
          header: 'STOMATOLOGIK QABUL — tishlar boyicha',
          lines: [
            AppointmentPdfDentalLine(
              tooth: 'UL 1',
              serviceTitle: 'Rentgen',
              amountMinor: 2000000,
              currency: 'UZS',
            ),
            AppointmentPdfDentalLine(
              tooth: 'UL 1',
              serviceTitle: 'Tish olish',
              amountMinor: 23000000,
              currency: 'UZS',
            ),
          ],
          subtotalMinor: 25000000,
          discountPercent: 1.0,
          totalMinor: 24750000,
          currency: 'UZS',
        ),
      );

      final bytes = await generateAppointmentPdf(data: dental, languageCode: 'uz');
      expect(bytes.length, greaterThan(800));
    });
  });
}
