// lib/features/admin/presentation/admin_doctor_activity_screen.dart
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_doctor_activity_detail_panel.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart' as dl;
import 'package:shifa_doc_app_v1/state/admin/admin_provider_params.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

enum _PresetRange { last7, last30, last90, custom, all }

class AdminDoctorActivityScreen extends ConsumerStatefulWidget {
  const AdminDoctorActivityScreen({super.key});

  @override
  ConsumerState<AdminDoctorActivityScreen> createState() => _AdminDoctorActivityScreenState();
}

class _AdminDoctorActivityScreenState extends ConsumerState<AdminDoctorActivityScreen> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  final _searchCtrl = TextEditingController();
  Timer? _debounce;
  int _page = 0;
  static const _pageSize = 25;

  _PresetRange _preset = _PresetRange.last30;
  DateTime? _customFromUtc;
  DateTime? _customToUtc;

  String _sort = 'appointments';
  bool _sortDesc = true;
  int? _selectedDoctorId;

  @override
  void initState() {
    super.initState();
    _searchCtrl.addListener(_onSearchChanged);
    final n = _todayUtc();
    _customFromUtc = n.subtract(const Duration(days: 29));
    _customToUtc = n;
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSearchChanged() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      if (!mounted) return;
      setState(() => _page = 0);
    });
  }

  DateTime _todayUtc() {
    final now = DateTime.now().toUtc();
    return DateTime.utc(now.year, now.month, now.day);
  }

  String _iso(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  ({String? from, String? to}) _rangeIso() {
    final today = _todayUtc();
    switch (_preset) {
      case _PresetRange.last7:
        final from = today.subtract(const Duration(days: 6));
        return (from: _iso(from), to: _iso(today));
      case _PresetRange.last30:
        final from = today.subtract(const Duration(days: 29));
        return (from: _iso(from), to: _iso(today));
      case _PresetRange.last90:
        final from = today.subtract(const Duration(days: 89));
        return (from: _iso(from), to: _iso(today));
      case _PresetRange.custom:
        final f = _customFromUtc;
        final t = _customToUtc;
        if (f == null || t == null) return (from: null, to: null);
        final lo = f.isBefore(t) ? f : t;
        final hi = f.isBefore(t) ? t : f;
        return (from: _iso(lo), to: _iso(hi));
      case _PresetRange.all:
        return (from: null, to: null);
    }
  }

  DoctorActivityParams _params() {
    final r = _rangeIso();
    final q = _searchCtrl.text.trim();
    return DoctorActivityParams(
      fromIso: r.from,
      toIso: r.to,
      search: q.isEmpty ? null : q,
      sort: _sort,
      dir: _sortDesc ? 'desc' : 'asc',
      page: _page,
      size: _pageSize,
    );
  }

  void _toggleSort(String field) {
    setState(() {
      if (_sort == field) {
        _sortDesc = !_sortDesc;
      } else {
        _sort = field;
        _sortDesc = field == 'name' ? false : true;
      }
      _page = 0;
    });
  }

  DataColumn _sortCol(String title, String field) {
    final active = _sort == field;
    return DataColumn(
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(title, style: const TextStyle(fontSize: 13)),
          if (active) Icon(_sortDesc ? Icons.arrow_downward : Icons.arrow_upward, size: 14),
        ],
      ),
      onSort: (_, __) => _toggleSort(field),
    );
  }

  Future<void> _pickCustom() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime.utc(2020),
      lastDate: DateTime.utc(now.year + 2, 12, 31),
      initialDateRange: DateTimeRange(
        start: _customFromUtc ?? now.subtract(const Duration(days: 29)),
        end: _customToUtc ?? now,
      ),
    );
    if (picked == null || !mounted) return;
    setState(() {
      _preset = _PresetRange.custom;
      _customFromUtc = DateTime.utc(picked.start.year, picked.start.month, picked.start.day);
      _customToUtc = DateTime.utc(picked.end.year, picked.end.month, picked.end.day);
      _page = 0;
    });
  }

  Future<void> _exportCsv(List<AdminDoctorActivityRow> rows) async {
    const header =
        'doctorId,doctorName,clinicName,appointmentsBooked,appointmentsCompleted,cancelPct,videoAppts,activePatients,patientsCreated,documents,treatmentPlans,remoteTasks,consultNotes,forms,aiRequests,aiDrafts,lastActive';
    final sb = StringBuffer(header);
    for (final r in rows) {
      sb.writeln();
      String csvCell(String? s) {
        if (s == null || s.isEmpty) return '';
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
          return '"${s.replaceAll('"', '""')}"';
        }
        return s;
      }
      sb.write([
        r.doctorId,
        csvCell(r.doctorName),
        csvCell(r.clinicName),
        r.appointmentsBooked,
        r.appointmentsCompleted,
        (r.cancellationRate * 100).toStringAsFixed(1),
        r.videoAppointments,
        r.activePatients,
        r.patientsCreated,
        r.documentsUploaded,
        r.treatmentPlans,
        r.remoteTasks,
        r.consultationNotes,
        r.patientForms,
        r.aiRequests,
        r.aiDraftNotes,
        csvCell(r.lastActiveAt),
      ].join(','));
    }
    try {
      await dl.downloadBytes(
        utf8.encode(sb.toString()),
        filename: 'doctor_activity_${DateTime.now().millisecondsSinceEpoch}.csv',
        mimeType: 'text/csv;charset=utf-8',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('CSV download started')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Export failed (web only): $e')));
    }
  }

  Widget _presetChip(String label, _PresetRange preset) {
    final sel = _preset == preset;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        label: Text(label),
        selected: sel,
        onSelected: (_) => setState(() {
          _preset = preset;
          _page = 0;
        }),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final p = _params();
    final snap = ref.watch(adminDoctorActivityProvider(p));

    return Scaffold(
      key: _scaffoldKey,
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black87,
        title: const Text('Doctor activity'),
      ),
      endDrawer: _selectedDoctorId == null
          ? null
          : Drawer(
              width: 440,
              child: AdminDoctorActivityDetailPanel(
                doctorId: _selectedDoctorId!,
                fromIso: _rangeIso().from,
                toIso: _rangeIso().to,
                onClose: () {
                  Navigator.of(context).maybePop();
                  setState(() => _selectedDoctorId = null);
                },
              ),
            ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Material(
            color: Colors.white,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                runSpacing: 8,
                children: [
                  _presetChip('7 days', _PresetRange.last7),
                  _presetChip('30 days', _PresetRange.last30),
                  _presetChip('90 days', _PresetRange.last90),
                  _presetChip('All time', _PresetRange.all),
                  FilterChip(
                    label: const Text('Custom'),
                    selected: _preset == _PresetRange.custom,
                    onSelected: (_) => _pickCustom(),
                  ),
                  SizedBox(
                    width: 260,
                    child: TextField(
                      controller: _searchCtrl,
                      decoration: const InputDecoration(
                        hintText: 'Search doctor / clinic',
                        prefixIcon: Icon(Icons.search, size: 20),
                        isDense: true,
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                  ShifaSecondaryButton(label: 'Refresh', onPressed: () => ref.invalidate(adminDoctorActivityProvider(p))),
                  ShifaSecondaryButton(
                    label: 'Export CSV',
                    onPressed: () => snap.whenData((d) => _exportCsv(d['content'] as List<AdminDoctorActivityRow>)),
                  ),
                ],
              ),
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: snap.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(child: Text('${l10n.error}: $e')),
                data: (data) {
                  final rows = data['content'] as List<AdminDoctorActivityRow>;
                  final totalPages = ((data['totalPages'] as num?)?.toInt() ?? 1).clamp(1, 100000);

                  final table = SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SingleChildScrollView(
                      child: DataTable(
                        headingRowColor: WidgetStateProperty.all(Colors.grey.shade100),
                        columns: [
                          _sortCol('Doctor', 'name'),
                          const DataColumn(label: Text('Clinic')),
                          _sortCol('Booked', 'appointments'),
                          _sortCol('Done', 'completed'),
                          const DataColumn(label: Text('Cncl %')),
                          const DataColumn(label: Text('Act pt')),
                          _sortCol('Pt new', 'patientscreated'),
                          const DataColumn(label: Text('Docs')),
                          const DataColumn(label: Text('Plans')),
                          const DataColumn(label: Text('Tasks')),
                          const DataColumn(label: Text('Notes')),
                          const DataColumn(label: Text('Forms')),
                          _sortCol('AI req', 'airequests'),
                          const DataColumn(label: Text('Drafts')),
                          const DataColumn(label: Text('Video')),
                          _sortCol('Last act', 'lastactive'),
                        ],
                        rows: rows.map((r) {
                          final la = r.lastActiveAt == null ? '—' : (r.lastActiveAt!.length >= 16 ? r.lastActiveAt!.substring(0, 16) : r.lastActiveAt!);
                          return DataRow(
                            selected: _selectedDoctorId == r.doctorId,
                            cells: [
                              DataCell(Text(r.doctorName)),
                              DataCell(Text(r.clinicName ?? '—')),
                              DataCell(Text('${r.appointmentsBooked}')),
                              DataCell(Text('${r.appointmentsCompleted}')),
                              DataCell(Text('${(r.cancellationRate * 100).toStringAsFixed(1)}%')),
                              DataCell(Text('${r.activePatients}')),
                              DataCell(Text('${r.patientsCreated}')),
                              DataCell(Text('${r.documentsUploaded}')),
                              DataCell(Text('${r.treatmentPlans}')),
                              DataCell(Text('${r.remoteTasks}')),
                              DataCell(Text('${r.consultationNotes}')),
                              DataCell(Text('${r.patientForms}')),
                              DataCell(Text('${r.aiRequests}')),
                              DataCell(Text('${r.aiDraftNotes}')),
                              DataCell(Text('${r.videoAppointments}')),
                              DataCell(Text(la)),
                            ],
                            onSelectChanged: (_) {
                              setState(() => _selectedDoctorId = r.doctorId);
                              WidgetsBinding.instance.addPostFrameCallback((__) {
                                _scaffoldKey.currentState?.openEndDrawer();
                              });
                            },
                          );
                        }).toList(),
                      ),
                    ),
                  );

                  return Column(
                    children: [
                      Expanded(child: rows.isEmpty ? const Center(child: Text('No results')) : table),
                      if (totalPages > 1)
                        Padding(
                          padding: const EdgeInsets.only(top: 12),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.chevron_left),
                                onPressed: _page > 0 ? () => setState(() => _page--) : null,
                              ),
                              Text('${_page + 1} / $totalPages'),
                              IconButton(
                                icon: const Icon(Icons.chevron_right),
                                onPressed: _page < totalPages - 1 ? () => setState(() => _page++) : null,
                              ),
                            ],
                          ),
                        ),
                    ],
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}
