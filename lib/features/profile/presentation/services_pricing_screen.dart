import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_actions.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_models.dart';

/// Allowed currencies for service price rows (doctor UI).
const List<String> _servicePriceCurrencies = ['UZS', 'USD', 'EUR', 'RUB'];

String _normalizeServiceCurrency(String? raw) {
  final c = raw?.trim().toUpperCase() ?? '';
  if (_servicePriceCurrencies.contains(c)) return c;
  return 'UZS';
}

/// Dropdown choices: fixed list plus current unknown code so existing data is not lost on edit.
List<String> _currencyDropdownCodes(String currentUppercase) {
  final set = Set<String>.from(_servicePriceCurrencies);
  if (currentUppercase.isNotEmpty && !set.contains(currentUppercase)) {
    set.add(currentUppercase);
  }
  final list = set.toList();
  list.sort();
  return list;
}

class _PriceRow {
  _PriceRow({
    required this.amount,
    required this.currencyCode,
    this.locationId,
  });

  final TextEditingController amount;
  String currencyCode;
  int? locationId;
}

class ServicesPricingScreen extends ConsumerStatefulWidget {
  const ServicesPricingScreen({super.key});

  @override
  ConsumerState<ServicesPricingScreen> createState() => _ServicesPricingScreenState();
}

class _ServicesPricingScreenState extends ConsumerState<ServicesPricingScreen> {
  bool _loading = true;
  List<Map<String, dynamic>> _services = [];
  List<Map<String, dynamic>> _groups = [];
  List<DoctorLocationDto> _locations = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final api = ref.read(apiClientProvider);
    try {
      final locs = await fetchDoctorLocations(ref);
      if (mounted) _locations = locs;
    } catch (_) {
      if (mounted) _locations = [];
    }

    final groupsRes = await api.get('/api/doctors/me/service-groups');
    if (groupsRes.statusCode == 200) {
      final list = jsonDecode(groupsRes.body) as List;
      _groups = list.cast<Map<String, dynamic>>();
    } else {
      _groups = [];
    }

    final res = await api.get('/api/doctors/me/services');
    if (res.statusCode == 200) {
      final parsed = jsonDecode(res.body) as List;
      _services = parsed.cast<Map<String, dynamic>>();
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _manageGroups() async {
    await showDialog<void>(
      context: context,
      builder: (ctx) => _ServiceGroupsEditorDialog(
        api: ref.read(apiClientProvider),
        l10n: AppLocalizations.of(context)!,
      ),
    );
    await _load();
  }

  Future<void> _editService([Map<String, dynamic>? service]) async {
    final dl10n = AppLocalizations.of(context)!;
    if (service != null && service['clinicManaged'] == true) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(dl10n.translate('serviceManagedByClinic'))),
        );
      }
      return;
    }
    final titleCtrl = TextEditingController(text: service?['title']?.toString());
    final descCtrl = TextEditingController(text: service?['description']?.toString());
    var isFreeConsultation = service?['isFreeConsultation'] == true;
    int? groupId = (service?['groupId'] as num?)?.toInt();

    final priceRows = <_PriceRow>[];
    final existingPrices = (service?['prices'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
    if (existingPrices.isEmpty) {
      priceRows.add(
        _PriceRow(
          amount: TextEditingController(),
          currencyCode: 'UZS',
          locationId: null,
        ),
      );
    } else {
      for (final p in existingPrices) {
        priceRows.add(
          _PriceRow(
            amount: TextEditingController(
              text: (((p['amountMinor'] as num?)?.toDouble() ?? 0) / 100).toStringAsFixed(2),
            ),
            currencyCode: _normalizeServiceCurrency(p['currency']?.toString()),
            locationId: (p['locationId'] as num?)?.toInt(),
          ),
        );
      }
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            return AlertDialog(
              title: Text(service == null
                  ? (dl10n.translate('newService'))
                  : (dl10n.translate('editService'))),
              content: SizedBox(
                width: 460,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        controller: titleCtrl,
                        decoration: InputDecoration(
                          labelText: dl10n.translate('serviceTitleLabel'),
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: descCtrl,
                        minLines: 2,
                        maxLines: 4,
                        decoration: InputDecoration(
                          labelText: dl10n.translate('serviceDescriptionLabel'),
                        ),
                      ),
                      const SizedBox(height: 10),
                      DropdownButtonFormField<int?>(
                        value: groupId,
                        decoration: InputDecoration(
                          labelText: dl10n.translate('serviceGroupLabel'),
                        ),
                        items: [
                          DropdownMenuItem<int?>(
                            value: null,
                            child: Text(dl10n.translate('serviceGroupNone')),
                          ),
                          ..._groups.map(
                            (g) => DropdownMenuItem<int?>(
                              value: (g['id'] as num).toInt(),
                              child: Text(g['name']?.toString() ?? ''),
                            ),
                          ),
                        ],
                        onChanged: (v) => setLocal(() => groupId = v),
                      ),
                      const SizedBox(height: 8),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(dl10n.translate('serviceFreeConsultation')),
                        subtitle: Text(
                          dl10n.translate('serviceFreeConsultationHint'),
                          style: Theme.of(ctx).textTheme.bodySmall,
                        ),
                        value: isFreeConsultation,
                        onChanged: (v) => setLocal(() => isFreeConsultation = v),
                      ),
                      if (!isFreeConsultation) ...[
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            dl10n.translate('servicePricesSection'),
                            style: Theme.of(ctx).textTheme.bodySmall,
                          ),
                        ),
                        const SizedBox(height: 8),
                        ...priceRows.asMap().entries.map((e) {
                          final i = e.key;
                          final row = e.value;
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                border: Border.all(color: Theme.of(ctx).dividerColor),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.all(8),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.stretch,
                                  children: [
                                    Row(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Expanded(
                                          child: TextField(
                                            controller: row.amount,
                                            keyboardType: const TextInputType.numberWithOptions(
                                              decimal: true,
                                            ),
                                            decoration: InputDecoration(
                                              labelText: dl10n.translate('servicePriceLabel'),
                                              isDense: true,
                                            ),
                                          ),
                                        ),
                                        const SizedBox(width: 8),
                                        SizedBox(
                                          width: 104,
                                          child: DropdownButtonFormField<String>(
                                            value: row.currencyCode,
                                            decoration: InputDecoration(
                                              labelText: dl10n.translate('serviceCurrencyLabel'),
                                              isDense: true,
                                            ),
                                            items: _currencyDropdownCodes(row.currencyCode)
                                                .map(
                                                  (code) => DropdownMenuItem(
                                                    value: code,
                                                    child: Text(code),
                                                  ),
                                                )
                                                .toList(),
                                            onChanged: (v) {
                                              if (v != null) {
                                                setLocal(() => row.currencyCode = v);
                                              }
                                            },
                                          ),
                                        ),
                                        if (priceRows.length > 1)
                                          IconButton(
                                            icon: const Icon(Icons.close),
                                            onPressed: () {
                                              row.amount.dispose();
                                              setLocal(() => priceRows.removeAt(i));
                                            },
                                          ),
                                      ],
                                    ),
                                    const SizedBox(height: 6),
                                    DropdownButtonFormField<int?>(
                                      value: row.locationId,
                                      decoration: InputDecoration(
                                        labelText: dl10n.translate('priceScopeLabel'),
                                        isDense: true,
                                      ),
                                      items: [
                                        DropdownMenuItem<int?>(
                                          value: null,
                                          child: Text(
                                            dl10n.translate('priceScopeAllLocations'),
                                          ),
                                        ),
                                        ..._locations.where((l) => l.id != null).map(
                                              (l) => DropdownMenuItem<int?>(
                                                value: l.id,
                                                child: Text(
                                                  l.label +
                                                      (l.locationCity != null && l.locationCity!.isNotEmpty
                                                          ? ' — ${l.locationCity}'
                                                          : ''),
                                                  overflow: TextOverflow.ellipsis,
                                                ),
                                              ),
                                            ),
                                      ],
                                      onChanged: (v) => setLocal(() => row.locationId = v),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          );
                        }),
                        TextButton.icon(
                          onPressed: () {
                            setLocal(() {
                              priceRows.add(
                                _PriceRow(
                                  amount: TextEditingController(),
                                  currencyCode: 'UZS',
                                  locationId: null,
                                ),
                              );
                            });
                          },
                          icon: const Icon(Icons.add),
                          label: Text(dl10n.translate('addPriceRow')),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () {
                    for (final r in priceRows) {
                      r.amount.dispose();
                    }
                    Navigator.pop(ctx, false);
                  },
                  child: Text(dl10n.translate('cancel')),
                ),
                TextButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: Text(dl10n.translate('save')),
                ),
              ],
            );
          },
        );
      },
    );

    if (confirmed != true) {
      for (final r in priceRows) {
        r.amount.dispose();
      }
      return;
    }

    List<Map<String, dynamic>> builtPrices = [];
    if (!isFreeConsultation) {
      final seen = <String>{};
      for (final r in priceRows) {
        final amountMinor = ((double.tryParse(r.amount.text.trim()) ?? 0) * 100).round();
        final cur = r.currencyCode.trim().toUpperCase();
        if (amountMinor <= 0 || cur.isEmpty) continue;
        final key = '${cur}_${r.locationId ?? 'global'}';
        if (seen.contains(key)) {
          for (final x in priceRows) {
            x.amount.dispose();
          }
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Duplicate currency and location combination')),
            );
          }
          return;
        }
        seen.add(key);
        builtPrices.add({
          'amountMinor': amountMinor,
          'currency': cur,
          if (r.locationId != null) 'locationId': r.locationId,
        });
      }
      if (builtPrices.isEmpty) {
        for (final r in priceRows) {
          r.amount.dispose();
        }
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Add at least one valid price')),
          );
        }
        return;
      }
    }

    for (final r in priceRows) {
      r.amount.dispose();
    }

    final body = <String, dynamic>{
      'title': titleCtrl.text.trim(),
      'description': descCtrl.text.trim(),
      'isActive': service == null ? true : (service['isActive'] != false),
      'isFreeConsultation': isFreeConsultation,
      'groupId': groupId,
      'prices': isFreeConsultation ? <Map<String, dynamic>>[] : builtPrices,
    };

    final api = ref.read(apiClientProvider);
    if (service == null) {
      await api.post('/api/doctors/me/services', body);
    } else {
      await api.patch('/api/doctors/me/services/${service['id']}', body);
    }
    await _load();
  }

  Future<void> _deleteService(Map<String, dynamic> s) async {
    final l10n = AppLocalizations.of(context)!;
    if (s['clinicManaged'] == true) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.translate('serviceManagedByClinic'))),
        );
      }
      return;
    }
    final api = ref.read(apiClientProvider);
    await api.delete('/api/doctors/me/services/${(s['id'] as num).toInt()}');
    await _load();
  }

  DoctorLocationDto? _locationById(int id) {
    for (final l in _locations) {
      if (l.id == id) return l;
    }
    return null;
  }

  String _locationLabelForSummary(int? locationId) {
    final l10n = AppLocalizations.of(context)!;
    if (locationId == null) {
      return l10n.translate('priceScopeAllLocations');
    }
    final loc = _locationById(locationId);
    if (loc != null) {
      final city = loc.locationCity;
      if (city != null && city.isNotEmpty) {
        return '${loc.label} — $city';
      }
      return loc.label;
    }
    return '${l10n.translate('location')} #$locationId';
  }

  String _priceSummary(Map<String, dynamic> s) {
    final l10n = AppLocalizations.of(context)!;
    if (s['isFreeConsultation'] == true) {
      return l10n.translate('serviceFreeConsultation');
    }
    final prices = (s['prices'] as List? ?? const []);
    if (prices.isEmpty) return '—';
    return prices.map((p) {
      final locId = (p['locationId'] as num?)?.toInt();
      final locHint = _locationLabelForSummary(locId);
      return '${((p['amountMinor'] as num?)?.toDouble() ?? 0) / 100} ${p['currency']} ($locHint)';
    }).join('\n');
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        title: Text(
          l10n.translate('servicesPricing'),
        ),
        actions: [
          IconButton(
            tooltip: l10n.translate('serviceGroupsTitle'),
            onPressed: _loading ? null : _manageGroups,
            icon: const Icon(Icons.folder_outlined),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                ShifaPrimaryButton(
                  onPressed: () => _editService(),
                  label: l10n.translate('addService'),
                  icon: Icons.add,
                  width: ButtonWidth.hug,
                ),
                const SizedBox(height: 16),
                ..._services.map((s) {
                  final groupLine = (s['groupName'] != null &&
                          s['groupName'].toString().trim().isNotEmpty)
                      ? '${s['groupName']}\n'
                      : '';
                  final managed = s['clinicManaged'] == true;
                  final managedLine = managed ? '${l10n.translate('serviceManagedByClinicShort')}\n' : '';
                  return Card(
                    child: ListTile(
                      title: Text(s['title']?.toString() ?? ''),
                      subtitle: Text(
                        '$managedLine$groupLine${s['description'] ?? ''}\n${_priceSummary(s)}',
                      ),
                      isThreeLine: true,
                      trailing: managed
                          ? null
                          : Wrap(
                        spacing: 8,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.edit),
                            onPressed: () => _editService(s),
                          ),
                          IconButton(
                            icon: const Icon(Icons.delete_outline),
                            onPressed: () => _deleteService(s),
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

class _ServiceGroupsEditorDialog extends StatefulWidget {
  const _ServiceGroupsEditorDialog({
    required this.api,
    required this.l10n,
  });

  final ApiClient api;
  final AppLocalizations l10n;

  @override
  State<_ServiceGroupsEditorDialog> createState() => _ServiceGroupsEditorDialogState();
}

class _ServiceGroupsEditorDialogState extends State<_ServiceGroupsEditorDialog> {
  bool _loading = true;
  List<Map<String, dynamic>> _items = [];

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    setState(() => _loading = true);
    final res = await widget.api.get('/api/doctors/me/service-groups');
    if (res.statusCode == 200) {
      final list = jsonDecode(res.body) as List;
      _items = list.cast<Map<String, dynamic>>();
    } else {
      _items = [];
    }
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = widget.l10n;
    return AlertDialog(
      title: Text(l10n.translate('serviceGroupsTitle')),
      content: SizedBox(
        width: 420,
        height: 360,
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    l10n.translate('serviceGroupsHint'),
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 8),
                  Expanded(
                    child: ListView.builder(
                      itemCount: _items.length,
                      itemBuilder: (context, index) {
                        final g = _items[index];
                        final id = (g['id'] as num).toInt();
                        return ListTile(
                          title: Text(g['name']?.toString() ?? ''),
                          subtitle:
                              Text('${l10n.translate('sortOrder')}: ${g['sortOrder']}'),
                          trailing: Wrap(
                            children: [
                              IconButton(
                                icon: const Icon(Icons.edit_outlined),
                                onPressed: () async {
                                  final nameCtrl =
                                      TextEditingController(text: g['name']?.toString());
                                  final sortCtrl = TextEditingController(
                                    text: '${g['sortOrder'] ?? 0}',
                                  );
                                  final ok = await showDialog<bool>(
                                    context: context,
                                    builder: (c2) => AlertDialog(
                                      title: Text(l10n.translate('editGroup')),
                                      content: Column(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          TextField(
                                            controller: nameCtrl,
                                            decoration: InputDecoration(
                                              labelText: l10n.translate('groupName'),
                                            ),
                                          ),
                                          TextField(
                                            controller: sortCtrl,
                                            keyboardType: TextInputType.number,
                                            decoration: InputDecoration(
                                              labelText: l10n.translate('sortOrder'),
                                            ),
                                          ),
                                        ],
                                      ),
                                      actions: [
                                        TextButton(
                                          onPressed: () => Navigator.pop(c2, false),
                                          child: Text(l10n.translate('cancel')),
                                        ),
                                        TextButton(
                                          onPressed: () => Navigator.pop(c2, true),
                                          child: Text(l10n.translate('save')),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (ok != true) return;
                                  await widget.api.patch(
                                    '/api/doctors/me/service-groups/$id',
                                    {
                                      'name': nameCtrl.text.trim(),
                                      'sortOrder': int.tryParse(sortCtrl.text.trim()) ?? 0,
                                    },
                                  );
                                  await _reload();
                                },
                              ),
                              IconButton(
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () async {
                                  await widget.api.delete('/api/doctors/me/service-groups/$id');
                                  await _reload();
                                },
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.translate('close')),
        ),
        TextButton(
          onPressed: () async {
            final nameCtrl = TextEditingController();
            final sortCtrl = TextEditingController(text: '0');
            final ok = await showDialog<bool>(
              context: context,
              builder: (c2) => AlertDialog(
                title: Text(l10n.translate('newGroup')),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: nameCtrl,
                      decoration: InputDecoration(
                        labelText: l10n.translate('groupName'),
                      ),
                    ),
                    TextField(
                      controller: sortCtrl,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: l10n.translate('sortOrder'),
                      ),
                    ),
                  ],
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(c2, false),
                    child: Text(l10n.translate('cancel')),
                  ),
                  TextButton(
                    onPressed: () => Navigator.pop(c2, true),
                    child: Text(l10n.translate('save')),
                  ),
                ],
              ),
            );
            if (ok != true) return;
            await widget.api.post(
              '/api/doctors/me/service-groups',
              {
                'name': nameCtrl.text.trim(),
                'sortOrder': int.tryParse(sortCtrl.text.trim()) ?? 0,
              },
            );
            await _reload();
          },
          child: Text(l10n.translate('addGroup')),
        ),
      ],
    );
  }
}
