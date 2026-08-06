// lib/features/clinic/presentation/clinic_invitations_tab.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';

/// Receptionist invitations for the clinic (active invites from backend).
class ClinicInvitationsTab extends ConsumerWidget {
  const ClinicInvitationsTab({super.key, required this.clinicId});

  final int clinicId;

  Future<void> _openInviteDialog(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context)!;
    final emailCtrl = TextEditingController();

    Future<void> send() async {
      final raw = emailCtrl.text.trim().toLowerCase();
      if (raw.isEmpty || !raw.contains('@')) return;
      try {
        await createClinicReceptionistInvitation(
          ref,
          clinicId: clinicId,
          email: raw,
        );
        if (context.mounted) {
          Navigator.of(context).pop();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(l10n.translate('clinicInviteInviteSent'))),
          );
        }
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(e.toString())),
          );
        }
      }
    }

    await showDialog<void>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: Text(l10n.translate('clinicInviteDialogTitle')),
          content: TextField(
            controller: emailCtrl,
            autofocus: true,
            decoration: InputDecoration(
              labelText: l10n.translate('clinicInviteEmailLabel'),
            ),
            keyboardType: TextInputType.emailAddress,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: send,
              child: Text(l10n.translate('clinicInviteSend')),
            ),
          ],
        );
      },
    );

    emailCtrl.dispose();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final invitations = ref.watch(clinicInvitationsProvider(clinicId));

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(clinicInvitationsProvider(clinicId));
      },
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: invitations.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            children: [
              Center(child: Text('${l10n.error}: $e')),
            ],
          ),
          data: (rows) {
            return CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: ShifaPrimaryButton(
                      label: l10n.translate('clinicInviteCreateTitle'),
                      icon: Icons.person_add_alt_1_outlined,
                      width: ButtonWidth.fill,
                      onPressed: () => _openInviteDialog(context, ref),
                    ),
                  ),
                ),
                if (rows.isEmpty)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Text(l10n.translate('clinicInviteEmpty')),
                    ),
                  )
                else
                  SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (ctx, idx) {
                        final row = rows[idx];
                        return Padding(
                          padding: EdgeInsets.only(
                            bottom: idx == rows.length - 1 ? 0 : 12,
                          ),
                          child: Card(
                            child: ListTile(
                              title: Text(
                                (row.emailSentTo != null &&
                                        row.emailSentTo!.isNotEmpty)
                                    ? row.emailSentTo!
                                    : '${l10n.translate('clinicInviteEmailLabel')} —',
                              ),
                              subtitle: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if ((row.expiresAt ?? '').isNotEmpty)
                                    Text(
                                      '${l10n.translate('clinicInviteExpires')}: ${row.expiresAt}',
                                    ),
                                  Row(
                                    children: [
                                      Icon(
                                        row.consumed
                                            ? Icons.check_circle
                                            : Icons.hourglass_bottom,
                                        size: 16,
                                        color: row.pending
                                            ? AppColors.primaryTeal
                                            : Colors.grey,
                                      ),
                                      const SizedBox(width: 8),
                                      Text(
                                        row.consumed
                                            ? l10n.translate(
                                                'clinicInviteConsumed',
                                              )
                                            : l10n.translate(
                                                'clinicInvitePending',
                                              ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                              trailing: IconButton(
                                tooltip: l10n.translate(
                                  'clinicInviteRevokeTooltip',
                                ),
                                onPressed: !row.pending
                                    ? null
                                    : () async {
                                        await revokeClinicInvitation(
                                          ref,
                                          clinicId: clinicId,
                                          invitationId: row.id,
                                        );
                                      },
                                icon: const Icon(Icons.cancel_outlined),
                              ),
                            ),
                          ),
                        );
                      },
                      childCount: rows.length,
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
