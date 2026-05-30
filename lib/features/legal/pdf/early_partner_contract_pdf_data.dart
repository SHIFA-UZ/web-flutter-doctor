// lib/features/legal/pdf/early_partner_contract_pdf_data.dart

/// Fillable partner fields and contract metadata for the early-user partnership PDF.
class EarlyPartnerContractPdfData {
  final String contractNumber;
  final DateTime effectiveDate;
  final int termMonths;

  final String? partnerFullName;
  final String? partnerClinic;
  final bool roleDoctor;
  final bool rolePatient;
  final bool roleBoth;
  final String? partnerPhone;
  final String? partnerEmail;

  final String supplierLegalName;
  final String supplierAddress;
  final String shifaSignatoryName;
  final String shifaSignatoryTitle;
  final DateTime? shifaSignedDate;

  const EarlyPartnerContractPdfData({
    this.contractNumber = 'SHIFA-0461',
    required this.effectiveDate,
    this.termMonths = 6,
    this.partnerFullName,
    this.partnerClinic,
    this.roleDoctor = false,
    this.rolePatient = false,
    this.roleBoth = false,
    this.partnerPhone,
    this.partnerEmail,
    this.supplierLegalName =
        "Qobilov Bekzodbek Olimjon o'g'li YaTT",
    this.supplierAddress = "Istiqbol 30, Andijon, O'zbekiston",
    this.shifaSignatoryName = 'Bekzodbek Qobilov Olimjon O\'g\'li',
    this.shifaSignatoryTitle = 'CEO, Founder',
    this.shifaSignedDate,
  });

  /// Blank template for printing and handwriting partner details.
  factory EarlyPartnerContractPdfData.blankTemplate({
    String contractNumber = 'SHIFA-0461',
    DateTime? effectiveDate,
    DateTime? shifaSignedDate,
  }) {
    final effective = effectiveDate ?? DateTime(2026, 6, 1);
    return EarlyPartnerContractPdfData(
      contractNumber: contractNumber,
      effectiveDate: effective,
      shifaSignedDate: shifaSignedDate ?? DateTime(2026, 5, 25),
    );
  }
}
