// lib/state/admin/admin_providers.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api/api_providers.dart';
import 'admin_actions.dart';
import '../../features/admin/domain/admin_models.dart';
import 'admin_provider_params.dart';

final adminActionsProvider = Provider<AdminActions>((ref) {
  final apiClient = ref.watch(adminApiClientProvider);
  return AdminActions(apiClient: apiClient);
});

final dashboardStatsProvider = FutureProvider<DashboardStats>((ref) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.getDashboardStats();
});

final adminTokensProvider = FutureProvider.family<Map<String, dynamic>, TokensProviderParams>((ref, params) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.listTokens(
    consumed: params.consumed,
    purpose: params.purpose,
    page: params.page,
    size: params.size,
  );
});

final adminUsersProvider = FutureProvider.family<Map<String, dynamic>, UsersProviderParams>((ref, params) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.listUsers(
    role: params.role,
    enabled: params.enabled,
    search: params.search,
    page: params.page,
    size: params.size,
  );
});

final adminClinicsProvider = FutureProvider.family<Map<String, dynamic>, ClinicsListParams>((ref, params) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.listClinics(page: params.page, size: params.size);
});

final adminClinicDetailProvider = FutureProvider.family<AdminClinicDetail, int>((ref, clinicId) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.getClinic(clinicId);
});

final auditLogsProvider = FutureProvider.family<Map<String, dynamic>, AuditLogsProviderParams>((ref, params) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.getAuditLogs(
    adminUserId: params.adminUserId,
    entityType: params.entityType,
    entityId: params.entityId,
    actionType: params.actionType,
    page: params.page,
    size: params.size,
  );
});

final activityLogsProvider = FutureProvider.family<Map<String, dynamic>, ActivityLogsProviderParams>((ref, params) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.getActivityLogs(
    userId: params.userId,
    activityType: params.activityType,
    page: params.page,
    size: params.size,
  );
});

final systemConfigProvider = FutureProvider<Map<String, String>>((ref) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.getSystemConfig();
});

final failedStripeWebhooksProvider = FutureProvider<List<FailedWebhookEvent>>((ref) async {
  final actions = ref.watch(adminActionsProvider);
  return actions.listFailedStripeWebhooks();
});
