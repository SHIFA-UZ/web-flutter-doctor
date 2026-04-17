/// Notification item returned by GET /api/notifications (doctor or patient).
class DoctorNotificationModel {
  final int id;
  final String title;
  final String message;
  final String type;
  final int? appointmentId;
  final int? patientId;
  final int? documentId;
  final int? documentAccessRequestId;
  final int? taskId;
  /// When type is DOCUMENT_ACCESS_REQUEST: "pending" | "approved" | "rejected"
  final String? documentAccessRequestStatus;
  /// For document-access notifications: patient name, document title, requesting doctor name (for localization).
  final String? patientName;
  final String? documentTitle;
  final String? requestingDoctorName;
  final DateTime createdAt;
  final DateTime? readAt;

  const DoctorNotificationModel({
    required this.id,
    required this.title,
    required this.message,
    required this.type,
    this.appointmentId,
    this.patientId,
    this.documentId,
    this.documentAccessRequestId,
    this.taskId,
    this.documentAccessRequestStatus,
    this.patientName,
    this.documentTitle,
    this.requestingDoctorName,
    required this.createdAt,
    this.readAt,
  });

  bool get isRead => readAt != null;

  bool get isDocumentAccessRequest =>
      type == 'DOCUMENT_ACCESS_REQUEST' && documentAccessRequestId != null;

  bool get isDocumentAccessApproved => type == 'DOCUMENT_ACCESS_APPROVED';
  bool get isDocumentAccessRejected => type == 'DOCUMENT_ACCESS_REJECTED';

  factory DoctorNotificationModel.fromJson(Map<String, dynamic> json) {
    return DoctorNotificationModel(
      id: json['id'] as int,
      title: json['title'] as String,
      message: json['message'] as String,
      type: json['type'] as String,
      appointmentId: json['appointmentId'] as int?,
      patientId: json['patientId'] as int?,
      documentId: json['documentId'] as int?,
      documentAccessRequestId: json['documentAccessRequestId'] as int?,
      taskId: json['taskId'] as int?,
      documentAccessRequestStatus: json['documentAccessRequestStatus'] as String?,
      patientName: json['patientName'] as String?,
      documentTitle: json['documentTitle'] as String?,
      requestingDoctorName: json['requestingDoctorName'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      readAt: json['readAt'] != null
          ? DateTime.parse(json['readAt'] as String)
          : null,
    );
  }
}
