import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';

class AdminUserStatsPanel extends StatelessWidget {
  final UserManagementStats stats;
  final VoidCallback? onFilterPatients;
  final VoidCallback? onFilterPatientsWithDevice;
  final VoidCallback? onFilterPatientsWithoutDevice;
  final VoidCallback? onFilterProfilesWithoutApp;
  final VoidCallback? onFilterDoctors;

  const AdminUserStatsPanel({
    super.key,
    required this.stats,
    this.onFilterPatients,
    this.onFilterPatientsWithDevice,
    this.onFilterPatientsWithoutDevice,
    this.onFilterProfilesWithoutApp,
    this.onFilterDoctors,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              l10n.translate('userManagementOverview'),
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
            ),
            const Spacer(),
            _RateBadge(
              label: l10n.translate('deviceActivationRate'),
              rate: stats.deviceActivationRate,
            ),
          ],
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            _StatTile(
              title: l10n.translate('totalUsers'),
              value: stats.totalUsers,
              subtitle: '${stats.totalAdmins} ${l10n.translate('admins').toLowerCase()}',
              icon: Icons.groups,
              color: Colors.blueGrey,
              onTap: null,
            ),
            _StatTile(
              title: l10n.translate('patientAppUsers'),
              value: stats.patientAppUsers,
              subtitle: '${stats.activePatientUsers} ${l10n.translate('enabled').toLowerCase()}',
              icon: Icons.person,
              color: AppColors.primaryTeal,
              onTap: onFilterPatients,
            ),
            _StatTile(
              title: l10n.translate('totalPatientProfiles'),
              value: stats.totalPatientProfiles,
              subtitle: '${stats.profilesWithAppAccount} ${l10n.translate('withAppAccount')} · ${stats.profilesWithoutAppAccount} ${l10n.translate('doctorCreatedOnly')}',
              icon: Icons.folder_shared,
              color: Colors.indigo,
              onTap: onFilterProfilesWithoutApp ?? onFilterPatients,
            ),
            _StatTile(
              title: l10n.translate('patientsWithDevice'),
              value: stats.patientsWithDevice,
              subtitle: l10n.translate('appActivatedSubtitle'),
              icon: Icons.phone_android,
              color: Colors.green,
              onTap: onFilterPatientsWithDevice,
            ),
            _StatTile(
              title: l10n.translate('patientsWithoutDevice'),
              value: stats.patientAppUsersWithoutDevice,
              subtitle: l10n.translate('profileOnlySubtitle'),
              icon: Icons.person_off_outlined,
              color: Colors.orange,
              onTap: onFilterPatientsWithoutDevice,
            ),
            _StatTile(
              title: l10n.translate('totalDoctors'),
              value: stats.totalDoctors,
              subtitle: '${stats.activeDoctors} ${l10n.translate('activeDoctors').toLowerCase()} · ${stats.doctorsWithDevice} ${l10n.translate('withDevice').toLowerCase()}',
              icon: Icons.medical_services,
              color: Colors.deepPurple,
              onTap: onFilterDoctors,
            ),
          ],
        ),
        const SizedBox(height: 12),
        _EngagementBar(stats: stats, l10n: l10n),
      ],
    );
  }
}

class _RateBadge extends StatelessWidget {
  final String label;
  final int rate;

  const _RateBadge({required this.label, required this.rate});

  @override
  Widget build(BuildContext context) {
    final color = rate >= 50 ? Colors.green : (rate >= 25 ? Colors.orange : AppColors.destructiveRed);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.devices, size: 16, color: color),
          const SizedBox(width: 6),
          Text(
            '$label: $rate%',
            style: TextStyle(fontWeight: FontWeight.w600, color: color, fontSize: 13),
          ),
        ],
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  final String title;
  final int value;
  final String subtitle;
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;

  const _StatTile({
    required this.title,
    required this.value,
    required this.subtitle,
    required this.icon,
    required this.color,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final child = Container(
      width: 210,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, size: 18, color: color),
              ),
              const Spacer(),
              if (onTap != null)
                Icon(Icons.filter_alt_outlined, size: 16, color: Colors.grey.shade500),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            value.toString(),
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                  height: 1,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            title,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  fontWeight: FontWeight.w600,
                  color: Colors.black87,
                ),
          ),
          const SizedBox(height: 2),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Colors.grey.shade600,
                  fontSize: 11,
                ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );

    if (onTap == null) return child;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: child,
      ),
    );
  }
}

class _EngagementBar extends StatelessWidget {
  final UserManagementStats stats;
  final AppLocalizations l10n;

  const _EngagementBar({required this.stats, required this.l10n});

  @override
  Widget build(BuildContext context) {
    final totalPatients = stats.patientAppUsers;
    if (totalPatients == 0) return const SizedBox.shrink();

    final withDevice = stats.patientsWithDevice;
    final withoutDevice = stats.patientAppUsersWithoutDevice;
    final neverLoggedIn = stats.patientsNeverLoggedIn;
    final deviceFraction = withDevice / totalPatients;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.translate('patientEngagementBreakdown'),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SizedBox(
              height: 10,
              child: Row(
                children: [
                  if (withDevice > 0)
                    Expanded(
                      flex: withDevice,
                      child: Container(color: Colors.green),
                    ),
                  if (withoutDevice > 0)
                    Expanded(
                      flex: withoutDevice,
                      child: Container(color: Colors.orange.shade300),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 16,
            runSpacing: 4,
            children: [
              _LegendDot(
                color: Colors.green,
                label: l10n.translate('patientsWithDevice'),
                count: withDevice,
                pct: (deviceFraction * 100).round(),
              ),
              _LegendDot(
                color: Colors.orange.shade300,
                label: l10n.translate('patientsWithoutDevice'),
                count: withoutDevice,
                pct: totalPatients > 0 ? ((withoutDevice / totalPatients) * 100).round() : 0,
              ),
              _LegendDot(
                color: Colors.grey,
                label: l10n.translate('neverLoggedIn'),
                count: neverLoggedIn,
                pct: totalPatients > 0 ? ((neverLoggedIn / totalPatients) * 100).round() : 0,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _LegendDot extends StatelessWidget {
  final Color color;
  final String label;
  final int count;
  final int pct;

  const _LegendDot({
    required this.color,
    required this.label,
    required this.count,
    required this.pct,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(
          '$label: $count ($pct%)',
          style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
        ),
      ],
    );
  }
}
