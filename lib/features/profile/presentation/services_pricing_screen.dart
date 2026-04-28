import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
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

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(service == null ? 'New Service' : 'Edit Service'),
        content: SizedBox(
          width: 420,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: titleCtrl, decoration: const InputDecoration(labelText: 'Title')),
                const SizedBox(height: 10),
                TextField(controller: descCtrl, minLines: 2, maxLines: 4, decoration: const InputDecoration(labelText: 'Description')),
                const SizedBox(height: 10),
                TextField(controller: amountCtrl, decoration: const InputDecoration(labelText: 'Price amount (e.g. 25.00)')),
                const SizedBox(height: 10),
                TextField(controller: currencyCtrl, decoration: const InputDecoration(labelText: 'Currency (EUR/UZS/USD)')),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (confirmed != true) return;

    final amountMinor = ((double.tryParse(amountCtrl.text.trim()) ?? 0) * 100).round();
    final body = {
      'title': titleCtrl.text.trim(),
      'description': descCtrl.text.trim(),
      'isActive': true,
      'prices': [
        {
          'amountMinor': amountMinor,
          'currency': currencyCtrl.text.trim().toUpperCase(),
        }
      ]
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
    return Scaffold(
      appBar: AppBar(title: const Text('Services & Pricing')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                ShifaPrimaryButton(
                  onPressed: () => _editService(),
                  label: 'Add Service',
                  icon: Icons.add,
                  width: ButtonWidth.hug,
                ),
                const SizedBox(height: 16),
                ..._services.map((s) {
                  final prices = (s['prices'] as List? ?? const []);
                  return Card(
                    child: ListTile(
                      title: Text(s['title']?.toString() ?? ''),
                      subtitle: Text(
                        '${s['description'] ?? ''}\n${prices.map((p) => '${((p['amountMinor'] as num?)?.toDouble() ?? 0) / 100} ${p['currency']}').join(', ')}',
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
