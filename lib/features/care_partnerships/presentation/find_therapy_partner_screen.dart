import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/features/care_partnerships/data/care_partnership_repository.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';

final carePartnershipRepositoryProvider = Provider<CarePartnershipRepository>(
  (ref) => CarePartnershipRepository(ref.watch(apiClientProvider)),
);

class FindTherapyPartnerScreen extends ConsumerStatefulWidget {
  const FindTherapyPartnerScreen({
    super.key,
    required this.patientId,
    required this.patientName,
  });

  final int patientId;
  final String patientName;

  @override
  ConsumerState<FindTherapyPartnerScreen> createState() =>
      _FindTherapyPartnerScreenState();
}

class _FindTherapyPartnerScreenState
    extends ConsumerState<FindTherapyPartnerScreen> {
  final _searchCtrl = TextEditingController();
  final _professionCtrl = TextEditingController();
  final _messageCtrl = TextEditingController();
  List<CarePartnerHit> _hits = const [];
  bool _loading = false;
  String? _error;
  CarePartnerHit? _selected;

  @override
  void initState() {
    super.initState();
    _search();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _professionCtrl.dispose();
    _messageCtrl.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ref.read(carePartnershipRepositoryProvider);
      final hits = await repo.searchPartners(
        country: 'Uzbekistan',
        profession: _professionCtrl.text.trim().isEmpty
            ? null
            : _professionCtrl.text.trim(),
        q: _searchCtrl.text.trim().isEmpty ? null : _searchCtrl.text.trim(),
      );
      if (!mounted) return;
      setState(() {
        _hits = hits;
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

  Future<void> _invite() async {
    final partner = _selected;
    if (partner == null) return;
    final l10n = AppLocalizations.of(context)!;
    try {
      final repo = ref.read(carePartnershipRepositoryProvider);
      final created = await repo.invite(
        patientId: widget.patientId,
        partnerDoctorId: partner.id,
        specialtyRequested: _professionCtrl.text.trim().isEmpty
            ? partner.profession
            : _professionCtrl.text.trim(),
        message: _messageCtrl.text.trim().isEmpty ? null : _messageCtrl.text.trim(),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('partnerInviteSent'))),
      );
      if (!mounted) return;
      ShellScope.pushReplacementNamed(
        context,
        AppRoutes.carePartnershipDetail,
        arguments: created.id,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        leading: const AppPageBackButton(),
        title: Text(l10n.translate('findTherapyPartner')),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              l10n.translate('findPartnerForPatient')
                  .replaceAll('{name}', widget.patientName),
              style: AppDesignSystem.body1(context),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _searchCtrl,
              decoration: InputDecoration(
                labelText: l10n.search,
                prefixIcon: const Icon(Icons.search),
              ),
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _professionCtrl,
              decoration: InputDecoration(
                labelText: l10n.translate('specialtyFilter'),
              ),
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton(
                onPressed: _loading ? null : _search,
                child: Text(l10n.search),
              ),
            ),
            const SizedBox(height: 12),
            if (_loading) const LinearProgressIndicator(),
            if (_error != null) Text(_error!, style: const TextStyle(color: Colors.red)),
            Expanded(
              child: ListView.separated(
                itemCount: _hits.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, i) {
                  final hit = _hits[i];
                  final selected = _selected?.id == hit.id;
                  return ListTile(
                    selected: selected,
                    title: Text(hit.displayName),
                    subtitle: Text(
                      [
                        hit.profession,
                        hit.clinic,
                        hit.locationCity ?? hit.locationCountry,
                      ].where((e) => e != null && e.isNotEmpty).join(' · '),
                    ),
                    trailing: selected
                        ? const Icon(Icons.check_circle)
                        : const Icon(Icons.chevron_right),
                    onTap: () => setState(() => _selected = hit),
                  );
                },
              ),
            ),
            if (_selected != null) ...[
              TextField(
                controller: _messageCtrl,
                decoration: InputDecoration(
                  labelText: l10n.translate('partnerInviteMessage'),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: _invite,
                child: Text(l10n.translate('sendPartnerInvite')),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
