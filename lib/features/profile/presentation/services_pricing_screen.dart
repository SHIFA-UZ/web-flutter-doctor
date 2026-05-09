import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class ServicesPricingScreen extends ConsumerStatefulWidget {
  const ServicesPricingScreen({super.key});

  @override
  ConsumerState<ServicesPricingScreen> createState() => _ServicesPricingScreenState();
}

class _ServicesPricingScreenState extends ConsumerState<ServicesPricingScreen> {
  bool _loading = true;
  List<Map<String, dynamic>> _services = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final api = ref.read(apiClientProvider);
    final res = await api.get('/api/doctors/me/services');
    if (res.statusCode == 200) {
      final parsed = jsonDecode(res.body) as List;
      _services = parsed.cast<Map<String, dynamic>>();
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _editService([Map<String, dynamic>? service]) async {
    final titleCtrl = TextEditingController(text: service?['title']?.toString() ?? '');
    final descCtrl = TextEditingController(text: service?['description']?.toString() ?? '');
    final prices = (service?['prices'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
    final amountCtrl = TextEditingController(
      text: prices.isNotEmpty ? (((prices.first['amountMinor'] as num?)?.toDouble() ?? 0) / 100).toStringAsFixed(2) : '',
    );
    final currencyCtrl = TextEditingController(text: prices.isNotEmpty ? prices.first['currency'].toString() : 'EUR');
    var isFreeConsultation = service?['isFreeConsultation'] == true;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        final dl10n = AppLocalizations.of(ctx)!;
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            return AlertDialog(
              title: Text(service == null
                  ? (dl10n.translate('newService') ?? 'New Service')
                  : (dl10n.translate('editService') ?? 'Edit Service')),
              content: SizedBox(
                width: 420,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        controller: titleCtrl,
                        decoration: InputDecoration(
                          labelText: dl10n.translate('serviceTitleLabel') ?? 'Title',
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: descCtrl,
                        minLines: 2,
                        maxLines: 4,
                        decoration: InputDecoration(
                          labelText: dl10n.translate('serviceDescriptionLabel') ??
                              'Description',
                        ),
                      ),
                      const SizedBox(height: 8),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(dl10n.translate('serviceFreeConsultation') ?? 'Free consultation (video)'),
                        subtitle: Text(
                          dl10n.translate('serviceFreeConsultationHint') ??
                              '',
                          style: Theme.of(ctx).textTheme.bodySmall,
                        ),
                        value: isFreeConsultation,
                        onChanged: (v) => setLocal(() => isFreeConsultation = v),
                      ),
                      if (!isFreeConsultation) ...[
                        const SizedBox(height: 4),
                        TextField(
                          controller: amountCtrl,
                          decoration: InputDecoration(
                            labelText: dl10n.translate('servicePriceLabel') ??
                                'Price amount (e.g. 25.00)',
                          ),
                        ),
                        const SizedBox(height: 10),
                        TextField(
                          controller: currencyCtrl,
                          decoration: InputDecoration(
                            labelText: dl10n.translate('serviceCurrencyLabel') ??
                                'Currency (EUR/UZS/USD)',
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: Text(dl10n.translate('cancel') ?? 'Cancel'),
                ),
                TextButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: Text(dl10n.translate('save') ?? 'Save'),
                ),
              ],
            );
          },
        );
      },
    );
    if (confirmed != true) return;

    final amountMinor = ((double.tryParse(amountCtrl.text.trim()) ?? 0) * 100).round();
    final body = <String, dynamic>{
      'title': titleCtrl.text.trim(),
      'description': descCtrl.text.trim(),
      'isActive': service == null ? true : (service['isActive'] != false),
      'isFreeConsultation': isFreeConsultation,
      'prices': isFreeConsultation
          ? <Map<String, dynamic>>[]
          : [
              {
                'amountMinor': amountMinor,
                'currency': currencyCtrl.text.trim().toUpperCase(),
              }
            ],
    };
    final api = ref.read(apiClientProvider);
    if (service == null) {
      await api.post('/api/doctors/me/services', body);
    } else {
      await api.patch('/api/doctors/me/services/${service['id']}', body);
    }
    await _load();
  }

  Future<void> _deleteService(int id) async {
    final api = ref.read(apiClientProvider);
    await api.delete('/api/doctors/me/services/$id');
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(
          l10n.translate('servicesPricing') ?? 'Services & Pricing',
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                ShifaPrimaryButton(
                  onPressed: () => _editService(),
                  label: l10n.translate('addService') ?? 'Add Service',
                  icon: Icons.add,
                  width: ButtonWidth.hug,
                ),
                const SizedBox(height: 16),
                ..._services.map((s) {
                  final prices = (s['prices'] as List? ?? const []);
                  final isFree = s['isFreeConsultation'] == true;
                  final priceLine = isFree
                      ? (l10n.translate('serviceFreeConsultation') ?? 'Free consultation (video)')
                      : prices.map((p) => '${((p['amountMinor'] as num?)?.toDouble() ?? 0) / 100} ${p['currency']}').join(', ');
                  return Card(
                    child: ListTile(
                      title: Text(s['title']?.toString() ?? ''),
                      subtitle: Text(
                        '${s['description'] ?? ''}\n$priceLine',
                      ),
                      isThreeLine: true,
                      trailing: Wrap(
                        spacing: 8,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.edit),
                            onPressed: () => _editService(s),
                          ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline),
                            onPressed: () => _deleteService((s['id'] as num).toInt()),
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              ],
            ),
    );
  }
}
