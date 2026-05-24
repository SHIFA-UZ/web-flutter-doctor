import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/clinic/pdf/treatment_plan_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/clinic/pdf/treatment_plan_pdf_service.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('generateTreatmentPlanPdf', () {
    test('builds PDF for synthetic plan EN and UZ', () async {
      final summary = TreatmentPlanSummaryDto(
        id: 42,
        clinicId: 1,
        patientId: 59,
        patientName: 'Test Patient',
        attendingDoctorName: null,
        attendingDoctors: const [
          TreatmentPlanDoctorRef(id: 12, name: 'Dr Smile'),
        ],
        title: 'Implants phase 1',
        diagnosis: 'Test diagnosis',
        status: 'ACTIVE',
        notes: 'Follow fluoride rinse.',
        planKind: 'COMPREHENSIVE',
        symptoms: ['Sensitivity', 'Pain'],
        totalMinor: 10000000,
        paidMinor: 2500000,
        owedMinor: 7500000,
        currency: 'UZS',
        planPaymentStatus: 'PARTIAL',
        createdAt: '2026-01-02T12:00:00.000Z',
        updatedAt: '2026-01-03T15:30:00.000Z',
      );

      final line = LineDetailDto(
        id: 901,
        title: 'Rentgen',
        quantity: 1,
        unitPriceMinor: 2000000,
        discountMinor: 0,
        currency: 'UZS',
        sortOrder: 0,
        status: 'PLANNED',
        linkedAppointment: null,
        lineTotalMinor: 2000000,
      );

      final detail = TreatmentPlanDetailDto(
        summary: summary,
        lines: [line],
        installmentPlans: [
          InstallmentPlanSummaryDto(
            installmentPlanId: 7,
            status: 'ACTIVE',
            totalAmountMinor: 750000000,
            currency: 'UZS',
            numInstallments: 3,
            scheduleRows: [
              InstallmentScheduleItemDto(
                installmentItemId: 101,
                sequenceNumber: 1,
                dueDate: '2026-06-01',
                amountMinor: 250000000,
                currency: 'UZS',
                status: 'PENDING',
              ),
              InstallmentScheduleItemDto(
                installmentItemId: 102,
                sequenceNumber: 2,
                dueDate: '2026-07-01',
                amountMinor: 250000000,
                currency: 'UZS',
                status: 'PENDING',
              ),
              InstallmentScheduleItemDto(
                installmentItemId: 103,
                sequenceNumber: 3,
                dueDate: '2026-08-01',
                amountMinor: 250000000,
                currency: 'UZS',
                status: 'PAID',
              ),
            ],
          ),
        ],
      );

      final genAt = DateTime.utc(2026, 5, 23, 10, 0);

      final enData = TreatmentPlanPdfData.fromDetail(
        detail: detail,
        generatedAt: genAt,
        languageCode: 'en',
        clinicDisplayName: 'Test Clinic',
      );
      final uzData = TreatmentPlanPdfData.fromDetail(
        detail: detail,
        generatedAt: genAt,
        languageCode: 'uz',
      );

      final enBytes = await generateTreatmentPlanPdf(
        data: enData,
        languageCode: 'en',
      );
      final uzBytes = await generateTreatmentPlanPdf(
        data: uzData,
        languageCode: 'uz',
      );

      expect(enBytes.length, greaterThan(800));
      expect(uzBytes.length, greaterThan(800));
    });
  });
}
