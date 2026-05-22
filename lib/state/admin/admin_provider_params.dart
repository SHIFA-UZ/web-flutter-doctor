// lib/state/admin/admin_provider_params.dart
// Parameter classes for FutureProvider.family to ensure proper equality comparison

class TokensProviderParams {
  final bool? consumed;
  final String? purpose;
  final int page;
  final int size;

  TokensProviderParams({
    this.consumed,
    this.purpose,
    this.page = 0,
    this.size = 20,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TokensProviderParams &&
          runtimeType == other.runtimeType &&
          consumed == other.consumed &&
          purpose == other.purpose &&
          page == other.page &&
          size == other.size;

  @override
  int get hashCode => consumed.hashCode ^ purpose.hashCode ^ page.hashCode ^ size.hashCode;
}

class UsersProviderParams {
  final String? role;
  final bool? enabled;
  final String? search;
  final int page;
  final int size;

  UsersProviderParams({
    this.role,
    this.enabled,
    this.search,
    this.page = 0,
    this.size = 20,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is UsersProviderParams &&
          runtimeType == other.runtimeType &&
          role == other.role &&
          enabled == other.enabled &&
          search == other.search &&
          page == other.page &&
          size == other.size;

  @override
  int get hashCode => role.hashCode ^ enabled.hashCode ^ search.hashCode ^ page.hashCode ^ size.hashCode;
}

class AuditLogsProviderParams {
  final int? adminUserId;
  final String? entityType;
  final int? entityId;
  final String? actionType;
  final int page;
  final int size;

  AuditLogsProviderParams({
    this.adminUserId,
    this.entityType,
    this.entityId,
    this.actionType,
    this.page = 0,
    this.size = 50,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AuditLogsProviderParams &&
          runtimeType == other.runtimeType &&
          adminUserId == other.adminUserId &&
          entityType == other.entityType &&
          entityId == other.entityId &&
          actionType == other.actionType &&
          page == other.page &&
          size == other.size;

  @override
  int get hashCode =>
      adminUserId.hashCode ^
      entityType.hashCode ^
      entityId.hashCode ^
      actionType.hashCode ^
      page.hashCode ^
      size.hashCode;
}

class ActivityLogsProviderParams {
  final int? userId;
  final String? activityType;
  final int page;
  final int size;

  ActivityLogsProviderParams({
    this.userId,
    this.activityType,
    this.page = 0,
    this.size = 50,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ActivityLogsProviderParams &&
          runtimeType == other.runtimeType &&
          userId == other.userId &&
          activityType == other.activityType &&
          page == other.page &&
          size == other.size;

  @override
  int get hashCode => userId.hashCode ^ activityType.hashCode ^ page.hashCode ^ size.hashCode;
}

class ClinicsListParams {
  final int page;
  final int size;

  ClinicsListParams({this.page = 0, this.size = 50});

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ClinicsListParams &&
          runtimeType == other.runtimeType &&
          page == other.page &&
          size == other.size;

  @override
  int get hashCode => page.hashCode ^ size.hashCode;
}

class DoctorActivityParams {
  final String? fromIso;
  final String? toIso;
  final String? search;
  final String sort;
  final String dir;
  final int page;
  final int size;

  DoctorActivityParams({
    this.fromIso,
    this.toIso,
    this.search,
    this.sort = 'appointments',
    this.dir = 'desc',
    this.page = 0,
    this.size = 25,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DoctorActivityParams &&
          runtimeType == other.runtimeType &&
          fromIso == other.fromIso &&
          toIso == other.toIso &&
          search == other.search &&
          sort == other.sort &&
          dir == other.dir &&
          page == other.page &&
          size == other.size;

  @override
  int get hashCode =>
      fromIso.hashCode ^
      toIso.hashCode ^
      search.hashCode ^
      sort.hashCode ^
      dir.hashCode ^
      page.hashCode ^
      size.hashCode;
}

class DoctorActivityDetailParams {
  final int doctorId;
  final String? fromIso;
  final String? toIso;

  DoctorActivityDetailParams({
    required this.doctorId,
    this.fromIso,
    this.toIso,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DoctorActivityDetailParams &&
          runtimeType == other.runtimeType &&
          doctorId == other.doctorId &&
          fromIso == other.fromIso &&
          toIso == other.toIso;

  @override
  int get hashCode => doctorId.hashCode ^ fromIso.hashCode ^ toIso.hashCode;
}
