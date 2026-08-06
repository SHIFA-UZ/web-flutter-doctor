import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/features/care_partnerships/data/care_partnership_repository.dart';
import 'package:shifa_doc_app_v1/features/care_partnerships/presentation/find_therapy_partner_screen.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

class CarePartnershipsListScreen extends ConsumerStatefulWidget {
  const CarePartnershipsListScreen({super.key});

  @override
  ConsumerState<CarePartnershipsListScreen> createState() =>
      _CarePartnershipsListScreenState();
}

class _CarePartnershipsListScreenState
    extends ConsumerState<CarePartnershipsListScreen> {
  List<CarePartnership> _items = const [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items =
          await ref.read(carePartnershipRepositoryProvider).listMine();
      if (!mounted) return;
      setState(() {
        _items = items;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  int? _myDoctorId() {
    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final raw =
        profile?['id'] ?? profile?['doctorId'] ?? profile?['doctorProfileId'];
    if (raw is num) return raw.toInt();
    return int.tryParse(raw?.toString() ?? '');
  }

  bool _iAmPartner(CarePartnership p) {
    if (p.viewerRole != null) return p.iAmPartner;
    final myId = _myDoctorId();
    return myId != null && myId == p.partnerDoctorId;
  }

  bool _iAmInitiator(CarePartnership p) {
    if (p.viewerRole != null) return p.iAmInitiator;
    final myId = _myDoctorId();
    return myId != null && myId == p.initiatingDoctorId;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        leading: const AppPageBackButton(),
        title: Text(l10n.translate('carePartnerships')),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: _items.isEmpty
                      ? ListView(
                          children: [
                            const SizedBox(height: 80),
                            Center(
                              child: Text(
                                l10n.translate('noCarePartnerships'),
                                style: AppDesignSystem.body1(context),
                              ),
                            ),
                          ],
                        )
                      : ListView.separated(
                          itemCount: _items.length,
                          separatorBuilder: (_, __) => const Divider(height: 1),
                          itemBuilder: (context, i) {
                            final p = _items[i];
                            final roleLabel = _iAmPartner(p)
                                ? l10n.translate('partnershipRoleInvitee')
                                : l10n.translate('partnershipRoleSender');
                            return ListTile(
                              title: Text(
                                p.patientName ?? 'Patient #${p.patientId}',
                              ),
                              subtitle: Text(
                                '${p.status} · $roleLabel · ${p.initiatingDoctorName} ↔ ${p.partnerDoctorName}',
                              ),
                              onTap: () => ShellScope.pushNamed(
                                context,
                                AppRoutes.carePartnershipDetail,
                                arguments: p.id,
                              ),
                            );
                          },
                        ),
                ),
    );
  }
}

class CarePartnershipDetailScreen extends ConsumerStatefulWidget {
  const CarePartnershipDetailScreen({super.key, required this.partnershipId});

  final int partnershipId;

  @override
  ConsumerState<CarePartnershipDetailScreen> createState() =>
      _CarePartnershipDetailScreenState();
}

class _CarePartnershipDetailScreenState
    extends ConsumerState<CarePartnershipDetailScreen> {
  CarePartnership? _partnership;
  List<CarePartnershipProgress> _progress = const [];
  final _progressCtrl = TextEditingController();
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _progressCtrl.dispose();
    super.dispose();
  }

  int? _myDoctorId() {
    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final raw =
        profile?['id'] ?? profile?['doctorId'] ?? profile?['doctorProfileId'];
    if (raw is num) return raw.toInt();
    return int.tryParse(raw?.toString() ?? '');
  }

  bool _iAmPartner(CarePartnership p) {
    if (p.viewerRole != null) return p.iAmPartner;
    final myId = _myDoctorId();
    return myId != null && myId == p.partnerDoctorId;
  }

  bool _iAmInitiator(CarePartnership p) {
    if (p.viewerRole != null) return p.iAmInitiator;
    final myId = _myDoctorId();
    return myId != null && myId == p.initiatingDoctorId;
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ref.read(carePartnershipRepositoryProvider);
      final p = await repo.getById(widget.partnershipId);
      final progress = await repo.listProgress(widget.partnershipId);
      if (!mounted) return;
      setState(() {
        _partnership = p;
        _progress = progress;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _act(Future<CarePartnership> Function() action) async {
    try {
      await action();
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> _postProgress() async {
    final text = _progressCtrl.text.trim();
    if (text.isEmpty) return;
    try {
      await ref
          .read(carePartnershipRepositoryProvider)
          .addProgress(widget.partnershipId, text);
      _progressCtrl.clear();
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final p = _partnership;
    final iAmPartner = p != null && _iAmPartner(p);
    final iAmInitiator = p != null && _iAmInitiator(p);

    return Scaffold(
      appBar: AppBar(
        leading: const AppPageBackButton(),
        title: Text(l10n.translate('carePartnershipDetail')),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : p == null
                  ? const SizedBox.shrink()
                  : Column(
                      children: [
                        Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                p.patientName ?? 'Patient #${p.patientId}',
                                style: AppDesignSystem.h2(context),
                              ),
                              const SizedBox(height: 4),
                              Text('${l10n.translate('status')}: ${p.status}'),
                              Text(
                                '${p.initiatingDoctorName} ↔ ${p.partnerDoctorName}',
                              ),
                              if ((p.message ?? '').isNotEmpty)
                                Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Text(p.message!),
                                ),
                              if (p.status == 'PENDING' && iAmInitiator) ...[
                                const SizedBox(height: 12),
                                Text(
                                  l10n
                                      .translate('inviteSentWaiting')
                                      .replaceAll(
                                        '{name}',
                                        p.partnerDoctorName,
                                      ),
                                  style: AppDesignSystem.body1(context),
                                ),
                              ],
                              if (p.status == 'PENDING' && iAmPartner) ...[
                                const SizedBox(height: 12),
                                Text(
                                  l10n
                                      .translate('inviteReceivedFrom')
                                      .replaceAll(
                                        '{name}',
                                        p.initiatingDoctorName,
                                      ),
                                  style: AppDesignSystem.body1(context),
                                ),
                              ],
                              const SizedBox(height: 12),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  if (p.status == 'PENDING' && iAmPartner) ...[
                                    FilledButton(
                                      onPressed: () => _act(
                                        () => ref
                                            .read(
                                              carePartnershipRepositoryProvider,
                                            )
                                            .accept(p.id),
                                      ),
                                      child: Text(
                                        l10n.translate('acceptInvite'),
                                      ),
                                    ),
                                    OutlinedButton(
                                      onPressed: () => _act(
                                        () => ref
                                            .read(
                                              carePartnershipRepositoryProvider,
                                            )
                                            .decline(p.id),
                                      ),
                                      child: Text(
                                        l10n.translate('declineInvite'),
                                      ),
                                    ),
                                  ],
                                  if (p.status == 'PENDING' && iAmInitiator)
                                    OutlinedButton(
                                      onPressed: () => _act(
                                        () => ref
                                            .read(
                                              carePartnershipRepositoryProvider,
                                            )
                                            .cancel(p.id),
                                      ),
                                      child: Text(
                                        l10n.translate('cancelPartnerInvite'),
                                      ),
                                    ),
                                  if (p.status == 'ACTIVE' &&
                                      (iAmPartner || iAmInitiator))
                                    OutlinedButton(
                                      onPressed: () => _act(
                                        () => ref
                                            .read(
                                              carePartnershipRepositoryProvider,
                                            )
                                            .complete(p.id),
                                      ),
                                      child: Text(
                                        l10n.translate('completePartnership'),
                                      ),
                                    ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        const Divider(height: 1),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                          child: Align(
                            alignment: Alignment.centerLeft,
                            child: Text(
                              l10n.translate('partnershipProgress'),
                              style: AppDesignSystem.h2(context),
                            ),
                          ),
                        ),
                        Expanded(
                          child: ListView.builder(
                            padding: const EdgeInsets.symmetric(horizontal: 16),
                            itemCount: _progress.length,
                            itemBuilder: (context, i) {
                              final u = _progress[i];
                              return Card(
                                child: ListTile(
                                  title: Text(u.authorDoctorName),
                                  subtitle: Text(u.body),
                                  trailing: Text(
                                    u.createdAt.length > 16
                                        ? u.createdAt.substring(0, 16)
                                        : u.createdAt,
                                    style: AppDesignSystem.caption(context),
                                  ),
                                ),
                              );
                            },
                          ),
                        ),
                        if (p.status == 'ACTIVE' &&
                            (iAmPartner || iAmInitiator))
                          SafeArea(
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: TextField(
                                      controller: _progressCtrl,
                                      decoration: InputDecoration(
                                        hintText: l10n
                                            .translate('progressUpdateHint'),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  IconButton.filled(
                                    onPressed: _postProgress,
                                    icon: const Icon(Icons.send),
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
    );
  }
}
