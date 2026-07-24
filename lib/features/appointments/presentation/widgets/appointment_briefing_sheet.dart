import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';

class AppointmentBriefingResult {
  const AppointmentBriefingResult({
    required this.appointmentId,
    required this.status,
    this.briefingText,
    this.documentCount = 0,
    this.generatedAt,
    this.error,
  });

  final int appointmentId;
  final String status;
  final String? briefingText;
  final int documentCount;
  final String? generatedAt;
  final String? error;

  factory AppointmentBriefingResult.fromJson(Map<String, dynamic> json) {
    return AppointmentBriefingResult(
      appointmentId: (json['appointmentId'] as num?)?.toInt() ?? 0,
      status: json['status'] as String? ?? 'NONE',
      briefingText: json['briefingText'] as String?,
      documentCount: (json['documentCount'] as num?)?.toInt() ?? 0,
      generatedAt: json['generatedAt'] as String?,
      error: json['error'] as String?,
    );
  }
}

Future<AppointmentBriefingResult> fetchAppointmentBriefing(
  ApiClient api,
  String appointmentId,
) async {
  final res = await api.get('/api/appointments/$appointmentId/briefing');
  if (res.statusCode != 200) {
    throw Exception('Failed to load briefing (${res.statusCode})');
  }
  final json = jsonDecode(res.body) as Map<String, dynamic>;
  return AppointmentBriefingResult.fromJson(json);
}

Future<AppointmentBriefingResult> retryAppointmentBriefing(
  ApiClient api,
  String appointmentId,
) async {
  final res = await api.post('/api/appointments/$appointmentId/briefing/retry', {});
  if (res.statusCode != 200) {
    throw Exception('Failed to retry briefing (${res.statusCode})');
  }
  final json = jsonDecode(res.body) as Map<String, dynamic>;
  return AppointmentBriefingResult.fromJson(json);
}

Future<void> showAppointmentBriefingSheet(
  BuildContext context,
  WidgetRef ref, {
  required String appointmentId,
  String? initialStatus,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (ctx) => _AppointmentBriefingSheet(
      appointmentId: appointmentId,
      initialStatus: initialStatus,
    ),
  );
}

class _AppointmentBriefingSheet extends ConsumerStatefulWidget {
  const _AppointmentBriefingSheet({
    required this.appointmentId,
    this.initialStatus,
  });

  final String appointmentId;
  final String? initialStatus;

  @override
  ConsumerState<_AppointmentBriefingSheet> createState() =>
      _AppointmentBriefingSheetState();
}

class _AppointmentBriefingSheetState
    extends ConsumerState<_AppointmentBriefingSheet> {
  AppointmentBriefingResult? _result;
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
      final api = ref.read(apiClientProvider);
      final result = await fetchAppointmentBriefing(api, widget.appointmentId);
      if (!mounted) return;
      setState(() {
        _result = result;
        _loading = false;
      });
      if (result.status == 'PENDING') {
        Future.delayed(const Duration(seconds: 3), () {
          if (mounted) _load();
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _retry() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = ref.read(apiClientProvider);
      final result = await retryAppointmentBriefing(api, widget.appointmentId);
      if (!mounted) return;
      setState(() {
        _result = result;
        _loading = false;
      });
      if (result.status == 'PENDING') {
        Future.delayed(const Duration(seconds: 3), () {
          if (mounted) _load();
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final height = MediaQuery.sizeOf(context).height * 0.7;
    return SafeArea(
      child: SizedBox(
        height: height,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l10n.translate('visitBriefingTitle'),
                style: AppDesignSystem.h2(context),
              ),
              const SizedBox(height: 4),
              Text(
                l10n.translate('visitBriefingSubtitle'),
                style: AppDesignSystem.body2(context),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: _loading
                    ? const Center(child: CircularProgressIndicator())
                    : _error != null
                        ? Center(child: Text(_error!))
                        : _buildBody(l10n),
              ),
              if (_result?.status == 'FAILED' || _result?.status == 'PENDING') ...[
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: _loading ? null : _retry,
                  child: Text(l10n.translate('retryBriefing')),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBody(AppLocalizations l10n) {
    final r = _result;
    if (r == null || r.status == 'NONE' || r.status == 'SKIPPED') {
      return Text(l10n.translate('visitBriefingEmpty'));
    }
    if (r.status == 'PENDING') {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.translate('visitBriefingPending')),
          const SizedBox(height: 12),
          const LinearProgressIndicator(),
        ],
      );
    }
    if (r.status == 'FAILED') {
      return Text(r.error ?? l10n.translate('visitBriefingFailed'));
    }
    return SingleChildScrollView(
      child: SelectableText(
        r.briefingText ?? '',
        style: AppDesignSystem.body1(context),
      ),
    );
  }
}
