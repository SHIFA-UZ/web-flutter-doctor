class FinanceDashboardStats {
  final int totalRevenueMinor;
  final int outstandingMinor;
  final int overdueCount;
  final double collectionRate;
  final String currency;

  FinanceDashboardStats({
    required this.totalRevenueMinor,
    required this.outstandingMinor,
    required this.overdueCount,
    required this.collectionRate,
    required this.currency,
  });

  factory FinanceDashboardStats.fromJson(Map<String, dynamic> json) {
    return FinanceDashboardStats(
      totalRevenueMinor: (json['totalRevenueMinor'] ?? 0) as int,
      outstandingMinor: (json['outstandingMinor'] ?? 0) as int,
      overdueCount: (json['overdueCount'] ?? 0) as int,
      collectionRate: ((json['collectionRate'] ?? 0) as num).toDouble(),
      currency: (json['currency'] ?? 'UZS') as String,
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
      subtotalMinor: (json['subtotalMinor'] ?? 0) as int,
      discountMinor: (json['discountMinor'] ?? 0) as int,
      taxMinor: (json['taxMinor'] ?? 0) as int,
      totalMinor: (json['totalMinor'] ?? 0) as int,
      paidMinor: (json['paidMinor'] ?? 0) as int,
      remainingMinor: (json['remainingMinor'] ?? 0) as int,
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
  final int amountMinor;
  final String currency;
  final String method;
  final String? memo;
  final int? financialRecordId;
  final String recordedAt;

  PaymentHistoryItem({
    required this.id,
    required this.treatmentPlanId,
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
