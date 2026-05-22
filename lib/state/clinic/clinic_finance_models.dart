class DoctorEarningRow {
  final int doctorProfileId;
  final int visitCount;
  final int grossMinor;
  final int collectedMinor;
  final int outstandingMinor;

  DoctorEarningRow({
    required this.doctorProfileId,
    required this.visitCount,
    required this.grossMinor,
    required this.collectedMinor,
    required this.outstandingMinor,
  });

  factory DoctorEarningRow.fromJson(Map<String, dynamic> json) {
    return DoctorEarningRow(
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt() ?? 0,
      visitCount: (json['visitCount'] as num?)?.toInt() ?? 0,
      grossMinor: (json['grossMinor'] as num?)?.toInt() ?? 0,
      collectedMinor: (json['collectedMinor'] as num?)?.toInt() ?? 0,
      outstandingMinor: (json['outstandingMinor'] as num?)?.toInt() ?? 0,
    );
  }
}

class FinanceDashboardStats {
  final int totalRevenueMinor;
  final int outstandingMinor;
  final int overdueCount;
  final double collectionRate;
  final String currency;
  final List<DoctorEarningRow> doctorEarningsTop;

  FinanceDashboardStats({
    required this.totalRevenueMinor,
    required this.outstandingMinor,
    required this.overdueCount,
    required this.collectionRate,
    required this.currency,
    this.doctorEarningsTop = const [],
  });

  factory FinanceDashboardStats.fromJson(Map<String, dynamic> json) {
    final top = json['doctorEarningsTop'];
    return FinanceDashboardStats(
      totalRevenueMinor: (json['totalRevenueMinor'] as num?)?.toInt() ?? 0,
      outstandingMinor: (json['outstandingMinor'] as num?)?.toInt() ?? 0,
      overdueCount: (json['overdueCount'] as num?)?.toInt() ?? 0,
      collectionRate: ((json['collectionRate'] ?? 0) as num).toDouble(),
      currency: (json['currency'] ?? 'UZS') as String,
      doctorEarningsTop: top is List
          ? top
              .whereType<Map>()
              .map((e) => DoctorEarningRow.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
    );
  }
}

class FinancialRecordRow {
  final int id;
  final int clinicId;
  final int patientId;
  final int? treatmentPlanId;
  final String recordType;
  final String? recordNumber;
  final String status;
  /// Simplified UNPAID | PARTIAL | PAID | NONE for clinic UI.
  final String uiPaymentStatus;
  final int subtotalMinor;
  final int discountMinor;
  final int taxMinor;
  final int totalMinor;
  final int paidMinor;
  final int remainingMinor;
  final String currency;
  final String? issuedAt;
  final String? dueDate;
  final String? notes;
  final String createdAt;

  FinancialRecordRow({
    required this.id,
    required this.clinicId,
    required this.patientId,
    this.treatmentPlanId,
    required this.recordType,
    this.recordNumber,
    required this.status,
    this.uiPaymentStatus = '',
    required this.subtotalMinor,
    required this.discountMinor,
    required this.taxMinor,
    required this.totalMinor,
    required this.paidMinor,
    required this.remainingMinor,
    required this.currency,
    this.issuedAt,
    this.dueDate,
    this.notes,
    required this.createdAt,
  });

  factory FinancialRecordRow.fromJson(Map<String, dynamic> json) {
    return FinancialRecordRow(
      id: (json['id'] ?? 0) as int,
      clinicId: (json['clinicId'] ?? 0) as int,
      patientId: (json['patientId'] ?? 0) as int,
      treatmentPlanId: json['treatmentPlanId'] as int?,
      recordType: (json['recordType'] ?? '') as String,
      recordNumber: json['recordNumber'] as String?,
      status: (json['status'] ?? '') as String,
      uiPaymentStatus: json['uiPaymentStatus']?.toString() ?? '',
      subtotalMinor: (json['subtotalMinor'] as num?)?.toInt() ?? 0,
      discountMinor: (json['discountMinor'] as num?)?.toInt() ?? 0,
      taxMinor: (json['taxMinor'] as num?)?.toInt() ?? 0,
      totalMinor: (json['totalMinor'] as num?)?.toInt() ?? 0,
      paidMinor: (json['paidMinor'] as num?)?.toInt() ?? 0,
      remainingMinor: (json['remainingMinor'] as num?)?.toInt() ?? 0,
      currency: (json['currency'] ?? 'UZS') as String,
      issuedAt: json['issuedAt'] as String?,
      dueDate: json['dueDate'] as String?,
      notes: json['notes'] as String?,
      createdAt: (json['createdAt'] ?? '') as String,
    );
  }
}

class PaymentHistoryItem {
  final int id;
  final int treatmentPlanId;
  final int? patientId;
  final String? patientName;
  final int? doctorProfileId;
  final String? doctorName;
  final String? treatmentPlanTitle;
  final int amountMinor;
  final String currency;
  final String method;
  final String? memo;
  final int? financialRecordId;
  final String recordedAt;

  PaymentHistoryItem({
    required this.id,
    required this.treatmentPlanId,
    this.patientId,
    this.patientName,
    this.doctorProfileId,
    this.doctorName,
    this.treatmentPlanTitle,
    required this.amountMinor,
    required this.currency,
    required this.method,
    this.memo,
    this.financialRecordId,
    required this.recordedAt,
  });

  factory PaymentHistoryItem.fromJson(Map<String, dynamic> json) {
    return PaymentHistoryItem(
      id: (json['id'] ?? 0) as int,
      treatmentPlanId: (json['treatmentPlanId'] ?? 0) as int,
      patientId: (json['patientId'] as num?)?.toInt(),
      patientName: json['patientName']?.toString(),
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt(),
      doctorName: json['doctorName']?.toString(),
      treatmentPlanTitle: json['treatmentPlanTitle']?.toString(),
      amountMinor: (json['amountMinor'] ?? 0) as int,
      currency: (json['currency'] ?? 'UZS') as String,
      method: (json['method'] ?? '') as String,
      memo: json['memo'] as String?,
      financialRecordId: json['financialRecordId'] as int?,
      recordedAt: (json['recordedAt'] ?? '') as String,
    );
  }
}

class InstallmentPlanSummary {
  final int id;
  final int treatmentPlanId;
  final int totalAmountMinor;
  final String currency;
  final int numInstallments;
  final String frequency;
  final String startDate;
  final String status;
  final String? notes;
  final String createdAt;
  final List<InstallmentItemRow> items;

  InstallmentPlanSummary({
    required this.id,
    required this.treatmentPlanId,
    required this.totalAmountMinor,
    required this.currency,
    required this.numInstallments,
    required this.frequency,
    required this.startDate,
    required this.status,
    this.notes,
    required this.createdAt,
    required this.items,
  });

  factory InstallmentPlanSummary.fromJson(Map<String, dynamic> json) {
    final itemsList = (json['items'] as List<dynamic>?)
            ?.map((e) => InstallmentItemRow.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];
    return InstallmentPlanSummary(
      id: (json['id'] ?? 0) as int,
      treatmentPlanId: (json['treatmentPlanId'] ?? 0) as int,
      totalAmountMinor: (json['totalAmountMinor'] ?? 0) as int,
      currency: (json['currency'] ?? 'UZS') as String,
      numInstallments: (json['numInstallments'] ?? 0) as int,
      frequency: (json['frequency'] ?? 'MONTHLY') as String,
      startDate: (json['startDate'] ?? '') as String,
      status: (json['status'] ?? '') as String,
      notes: json['notes'] as String?,
      createdAt: (json['createdAt'] ?? '') as String,
      items: itemsList,
    );
  }
}

/// Installment row for the clinic finance Installments tab (with patient/plan).
class InstallmentItemListRow {
  final int id;
  final int sequenceNumber;
  final String dueDate;
  final int amountMinor;
  final String currency;
  final String status;
  final String? paidAt;
  final String? notes;
  final int installmentPlanId;
  final int treatmentPlanId;
  final String? treatmentPlanTitle;
  final int patientId;
  final String patientName;

  InstallmentItemListRow({
    required this.id,
    required this.sequenceNumber,
    required this.dueDate,
    required this.amountMinor,
    required this.currency,
    required this.status,
    this.paidAt,
    this.notes,
    required this.installmentPlanId,
    required this.treatmentPlanId,
    this.treatmentPlanTitle,
    required this.patientId,
    required this.patientName,
  });

  factory InstallmentItemListRow.fromJson(Map<String, dynamic> json) {
    return InstallmentItemListRow(
      id: (json['id'] as num?)?.toInt() ?? 0,
      sequenceNumber: (json['sequenceNumber'] as num?)?.toInt() ?? 0,
      dueDate: json['dueDate']?.toString() ?? '',
      amountMinor: (json['amountMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      status: json['status']?.toString() ?? '',
      paidAt: json['paidAt']?.toString(),
      notes: json['notes'] as String?,
      installmentPlanId: (json['installmentPlanId'] as num?)?.toInt() ?? 0,
      treatmentPlanId: (json['treatmentPlanId'] as num?)?.toInt() ?? 0,
      treatmentPlanTitle: json['treatmentPlanTitle'] as String?,
      patientId: (json['patientId'] as num?)?.toInt() ?? 0,
      patientName: json['patientName']?.toString() ?? '',
    );
  }
}

class InstallmentItemRow {
  final int id;
  final int sequenceNumber;
  final String dueDate;
  final int amountMinor;
  final String currency;
  final String status;
  final String? paidAt;
  final String? notes;

  InstallmentItemRow({
    required this.id,
    required this.sequenceNumber,
    required this.dueDate,
    required this.amountMinor,
    required this.currency,
    required this.status,
    this.paidAt,
    this.notes,
  });

  factory InstallmentItemRow.fromJson(Map<String, dynamic> json) {
    return InstallmentItemRow(
      id: (json['id'] ?? 0) as int,
      sequenceNumber: (json['sequenceNumber'] ?? 0) as int,
      dueDate: (json['dueDate'] ?? '') as String,
      amountMinor: (json['amountMinor'] ?? 0) as int,
      currency: (json['currency'] ?? 'UZS') as String,
      status: (json['status'] ?? '') as String,
      paidAt: json['paidAt'] as String?,
      notes: json['notes'] as String?,
    );
  }
}
