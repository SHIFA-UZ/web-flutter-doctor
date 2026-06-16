import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/text_cleaner.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_provider.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

/// Collapsible chatbot-like panel from bottom-right showing AI patient briefing.
/// Rendered in the shell so it appears on any screen when briefing is generated.
class PatientBriefingPanel extends ConsumerStatefulWidget {
  const PatientBriefingPanel({super.key, this.bottomInset = 12});

  final double bottomInset;

  @override
  ConsumerState<PatientBriefingPanel> createState() =>
      _PatientBriefingPanelState();
}

class _PatientBriefingPanelState extends ConsumerState<PatientBriefingPanel> {
  bool _expanded = true;

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(patientBriefingProvider);
    final canUseBriefing =
        ref.watch(doctorFeatureProvider(DoctorFeature.patientBriefing));
    if (!canUseBriefing || !state.isVisible) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context)!;
    final screenSize = MediaQuery.sizeOf(context);
    final useSinglePane = PlatformLayout.useSinglePane(context);
    final panelHeight = (screenSize.height * (useSinglePane ? 0.45 : 0.5))
        .clamp(useSinglePane ? 220.0 : 280.0, useSinglePane ? 420.0 : 500.0);
    final panelWidth = useSinglePane
        ? screenSize.width - 24
        : Responsive.overlayWidth(context, 380).clamp(280.0, 380.0);

    return Padding(
      padding: EdgeInsets.only(
        right: 12,
        left: useSinglePane ? 12 : 0,
        bottom: widget.bottomInset,
      ),
      child: Material(
        elevation: 8,
        borderRadius: BorderRadius.circular(16),
        clipBehavior: Clip.antiAlias,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 250),
          width: panelWidth,
          height: _expanded ? panelHeight : 56,
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: Colors.grey.shade300),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              InkWell(
                onTap: () => setState(() => _expanded = !_expanded),
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(16)),
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(
                    color: Theme.of(context)
                        .colorScheme
                        .primary
                        .withOpacity(0.08),
                    borderRadius:
                        const BorderRadius.vertical(top: Radius.circular(16)),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        _expanded
                            ? Icons.keyboard_arrow_down
                            : Icons.keyboard_arrow_up,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          state.patientName != null
                              ? '${l10n.patientBriefingTitle} — ${state.patientName}'
                              : l10n.patientBriefingTitle,
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            color: Theme.of(context).colorScheme.primary,
                            fontSize: 14,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, size: 20),
                        onPressed: () =>
                            ref.read(patientBriefingProvider.notifier).close(),
                        padding: EdgeInsets.zero,
                        constraints:
                            const BoxConstraints(minWidth: 32, minHeight: 32),
                      ),
                    ],
                  ),
                ),
              ),
              if (_expanded)
                Expanded(
                  child: state.isLoading
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const CircularProgressIndicator(),
                                const SizedBox(height: 16),
                                Text(l10n.patientBriefingGenerating),
                              ],
                            ),
                          ),
                        )
                      : state.isError
                          ? Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  Icon(
                                    Icons.error_outline,
                                    color:
                                        Theme.of(context).colorScheme.error,
                                    size: 32,
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    state.errorMessage ??
                                        l10n.patientBriefingError,
                                    style: TextStyle(
                                      color:
                                          Theme.of(context).colorScheme.error,
                                      fontSize: 13,
                                    ),
                                  ),
                                ],
                              ),
                            )
                          : SingleChildScrollView(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  if (state.documentCount > 0 ||
                                      state.appointmentCount > 0)
                                    Padding(
                                      padding:
                                          const EdgeInsets.only(bottom: 8),
                                      child: Text(
                                        state.documentCount > 0 &&
                                                state.appointmentCount > 0
                                            ? l10n.patientBriefingSourcesWithAppointments(
                                                state.documentCount,
                                                state.appointmentCount,
                                              )
                                            : state.documentCount > 0
                                                ? l10n.patientBriefingSources(
                                                    state.documentCount,
                                                  )
                                                : l10n
                                                    .patientBriefingSourcesAppointmentsOnly(
                                                    state.appointmentCount,
                                                  ),
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodySmall
                                            ?.copyWith(
                                              color: Colors.grey.shade600,
                                            ),
                                      ),
                                    ),
                                  Builder(
                                    builder: (context) {
                                      final raw = state.briefingText ?? '';
                                      final cleaned = TextCleaner.clean(raw);
                                      var display = cleaned.replaceAllMapped(
                                        RegExp(r'\*\*(.*?)\*\*', dotAll: true),
                                        (m) => m.group(1) ?? '',
                                      );
                                      display = display.replaceAll(
                                        RegExp(r'^- ', multiLine: true),
                                        '• ',
                                      );
                                      return SelectableText(
                                        display.trim(),
                                        style: const TextStyle(
                                          fontSize: 14,
                                          height: 1.4,
                                        ),
                                      );
                                    },
                                  ),
                                  const SizedBox(height: 12),
                                  ShifaSecondaryButton(
                                    label: l10n.patientBriefingCopy,
                                    icon: Icons.copy,
                                    onPressed: () {
                                      final text = state.briefingText ?? '';
                                      if (text.isNotEmpty) {
                                        Clipboard.setData(
                                          ClipboardData(text: text),
                                        );
                                        ScaffoldMessenger.of(context)
                                            .showSnackBar(
                                          SnackBar(
                                            content:
                                                Text(l10n.patientBriefingCopied),
                                          ),
                                        );
                                      }
                                    },
                                  ),
                                ],
                              ),
                            ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
