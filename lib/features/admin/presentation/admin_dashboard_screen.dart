// lib/features/admin/presentation/admin_dashboard_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

class AdminDashboardScreen extends ConsumerWidget {
  const AdminDashboardScreen({super.key});

  String _formatDelta(int value) {
    if (value > 0) return '+$value%';
    if (value < 0) return '$value%';
    return '0%';
  }

  Color _deltaColor(int value) {
    if (value > 0) return Colors.green;
    if (value < 0) return AppColors.destructiveRed;
    return Colors.grey;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final statsAsync = ref.watch(dashboardStatsProvider);

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: Text(l10n.dashboard),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
      ),
      body: statsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.error}: $e')),
        data: (stats) {
          final activeDoctorRate = stats.totalDoctors == 0
              ? 0
              : ((stats.activeDoctors / stats.totalDoctors) * 100).round();
          final doctorDelta = activeDoctorRate >= 80 ? 8 : -4;
          final patientDelta = stats.totalPatients >= 30 ? 6 : -2;
          final tokenDelta = stats.activeTokens >= 10 ? 5 : -3;
          final usersDelta = stats.totalUsers >= 50 ? 4 : -1;

          final alerts = <String>[
            if (activeDoctorRate < 70) 'Doctor activity ratio is below 70% this period.',
            if (stats.activeTokens > 25) 'High active token count. Review token hygiene.',
            if (stats.totalUsers == 0) 'No users found. Check onboarding pipeline.',
          ];

          final todoItems = <String>[
            if (stats.activeTokens > 15) 'Revoke stale invitation tokens.',
            if (activeDoctorRate < 75) 'Review recently disabled/locked doctor accounts.',
            'Review audit logs for high-risk admin actions.',
          ];

          return SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      'Overview',
                      style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    const Spacer(),
                    SegmentedButton<String>(
                      segments: const [
                        ButtonSegment(value: '7d', label: Text('7d')),
                        ButtonSegment(value: '30d', label: Text('30d')),
                        ButtonSegment(value: '90d', label: Text('90d')),
                      ],
                      selected: const {'30d'},
                      onSelectionChanged: (_) {},
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 16,
                  runSpacing: 16,
                  children: [
                    _ModernStatCard(
                      title: l10n.translate('totalDoctors'),
                      value: stats.totalDoctors.toString(),
                      delta: _formatDelta(doctorDelta),
                      deltaColor: _deltaColor(doctorDelta),
                      subtitle: '$activeDoctorRate% active',
                      icon: Icons.medical_services,
                    ),
                    _ModernStatCard(
                      title: l10n.translate('activeDoctors'),
                      value: stats.activeDoctors.toString(),
                      delta: _formatDelta(doctorDelta),
                      deltaColor: _deltaColor(doctorDelta),
                      subtitle: 'healthy workforce',
                      icon: Icons.check_circle,
                    ),
                    _ModernStatCard(
                      title: l10n.translate('totalPatients'),
                      value: stats.totalPatients.toString(),
                      delta: _formatDelta(patientDelta),
                      deltaColor: _deltaColor(patientDelta),
                      subtitle: 'growth signal',
                      icon: Icons.people,
                    ),
                    _ModernStatCard(
                      title: l10n.translate('activeTokens'),
                      value: stats.activeTokens.toString(),
                      delta: _formatDelta(tokenDelta),
                      deltaColor: _deltaColor(tokenDelta),
                      subtitle: 'invitation lifecycle',
                      icon: Icons.vpn_key,
                    ),
                    _ModernStatCard(
                      title: 'Total Users',
                      value: stats.totalUsers.toString(),
                      delta: _formatDelta(usersDelta),
                      deltaColor: _deltaColor(usersDelta),
                      subtitle: 'all roles',
                      icon: Icons.groups,
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                if (alerts.isNotEmpty) ...[
                  _SectionCard(
                    title: 'Alerts',
                    icon: Icons.warning_amber_rounded,
                    child: Column(
                      children: alerts
                          .map((a) => ListTile(
                                dense: true,
                                contentPadding: EdgeInsets.zero,
                                leading: const Icon(Icons.error_outline, color: AppColors.destructiveRed),
                                title: Text(a),
                              ))
                          .toList(),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                _SectionCard(
                  title: 'Needs Attention',
                  icon: Icons.playlist_add_check_circle_outlined,
                  child: Column(
                    children: todoItems
                        .map((t) => ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              leading: const Icon(Icons.circle, size: 10, color: Colors.black54),
                              title: Text(t),
                            ))
                        .toList(),
                  ),
                ),
                const SizedBox(height: 16),
                _SectionCard(
                  title: l10n.translate('quickActions'),
                  icon: Icons.bolt,
                  child: Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      ShifaSecondaryButton(
                        icon: Icons.add,
                        label: l10n.translate('generateToken'),
                        onPressed: () {},
                      ),
                      ShifaSecondaryButton(
                        icon: Icons.person_add,
                        label: l10n.translate('viewUsers'),
                        onPressed: () {},
                      ),
                      ShifaSecondaryButton(
                        icon: Icons.history,
                        label: l10n.translate('viewLogs'),
                        onPressed: () {},
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _ModernStatCard extends StatelessWidget {
  final String title;
  final String value;
  final String delta;
  final Color deltaColor;
  final String subtitle;
  final IconData icon;

  const _ModernStatCard({
    required this.title,
    required this.value,
    required this.delta,
    required this.deltaColor,
    required this.subtitle,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 245,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, color: AppColors.primaryTeal, size: 20),
              ),
              Text(delta, style: TextStyle(color: deltaColor, fontWeight: FontWeight.w700)),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            value,
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w700,
              color: Colors.grey.shade900,
            ),
          ),
          Text(title, style: TextStyle(color: Colors.grey.shade700)),
          const SizedBox(height: 4),
          Text(subtitle, style: TextStyle(color: Colors.grey.shade500, fontSize: 12)),
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final Widget child;

  const _SectionCard({
    required this.title,
    required this.icon,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 20, color: AppColors.primaryTeal),
              const SizedBox(width: 8),
              Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            ],
          ),
          const SizedBox(height: 8),
          child,
        ],
      ),
    );
  }
}

