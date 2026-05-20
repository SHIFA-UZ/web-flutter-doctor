import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';

/// Picks catalog lines for [PUT /api/appointments/{id}/complete] `visitCharges`.
class VisitChargesCatalogDialog extends ConsumerStatefulWidget {
  final int clinicId;

  const VisitChargesCatalogDialog({super.key, required this.clinicId});

  @override
  ConsumerState<VisitChargesCatalogDialog> createState() => _VisitChargesCatalogDialogState();
}

class _VisitChargesCatalogDialogState extends ConsumerState<VisitChargesCatalogDialog> {
  final Map<int, int> _qty = {};
  final Map<int, bool> _pick = {};

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicCatalogProvider(widget.clinicId));
    return AlertDialog(
      title: Text(l10n.translate('visitChargesDialogTitle')),
      content: SizedBox(
        width: 420,
        height: 360,
        child: async.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('$e'),
          data: (items) {
            return ListView(
              children: items.where((c) => c.active).map((c) {
                final q = _qty[c.id] ?? 1;
                final on = _pick[c.id] ?? false;
                return CheckboxListTile(
                  value: on,
                  onChanged: (v) => setState(() {
                    _pick[c.id] = v ?? false;
                    _qty[c.id] = q;
                  }),
                  title: Text(c.title),
                  subtitle: Text('${c.defaultPriceMinor / 100} ${c.currency}'),
                  secondary: SizedBox(
                    width: 56,
                    child: TextFormField(
                      initialValue: '$q',
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Qty'),
                      onChanged: (s) => _qty[c.id] = int.tryParse(s) ?? 1,
                    ),
                  ),
                );
              }).toList(),
            );
          },
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        FilledButton(
          onPressed: () {
            final lines = <Map<String, dynamic>>[];
            for (final id in _pick.keys.where((k) => _pick[k] == true)) {
              final qty = _qty[id] ?? 1;
              if (qty < 1) continue;
              lines.add({'catalogItemId': id, 'quantity': qty});
            }
            Navigator.pop(context, lines);
          },
          child: Text(l10n.translate('visitChargesConfirm')),
        ),
      ],
    );
  }
}
