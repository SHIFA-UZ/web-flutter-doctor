part of 'package:shifa_doc_app_v1/features/patients/presentation/patients_screen.dart';

/// Right-hand patient detail workspace matching the Patients mockup layout.
class PatientDetailPanel extends ConsumerStatefulWidget {
  final Patient? patient;
  final Color brand;
  final int? clinicWorkspaceId;
  final void Function(Patient) onUploadOptions;
  final void Function(Patient) onCreateForm;
  final String Function(DateTime) formatDate;
  final String? selectedDocumentId;
  final String? documentTitleForViewer;
  final bool openDocumentViewer;
  final bool isFavorite;
  final VoidCallback? onToggleFavorite;
  /// When set (e.g. clinic split view), called after profile/general updates instead of only [loadPatients].
  final VoidCallback? onPatientDataRefresh;

  const PatientDetailPanel({
    Key? key,
    required this.patient,
    required this.brand,
    this.clinicWorkspaceId,
    required this.onUploadOptions,
    required this.onCreateForm,
    required this.formatDate,
    this.selectedDocumentId,
    this.documentTitleForViewer,
    this.openDocumentViewer = false,
    this.isFavorite = false,
    this.onToggleFavorite,
    this.onPatientDataRefresh,
  }) : super(key: key);

  @override
  ConsumerState<PatientDetailPanel> createState() =>
      _PatientDetailPanelState();
}

class _PatientDetailPanelState extends ConsumerState<PatientDetailPanel>
    with SingleTickerProviderStateMixin {
  final TextEditingController _documentSearchController =
      TextEditingController();
  final TextEditingController _aiCopilotController = TextEditingController();
  String _documentSearchQuery = '';
  bool _documentViewerOpenedFromDeepLink = false;
  TabController? _tabController;
  static const int _tabCount = 6;

  PatientDocumentsKey _docKey(String patientId) => PatientDocumentsKey(
        patientId: patientId,
        clinicId: widget.clinicWorkspaceId,
      );

  @override
  void initState() {
    super.initState();
    _syncTabController();
    _documentSearchController.addListener(() {
      setState(() {
        _documentSearchQuery = _documentSearchController.text;
      });
    });
    if (widget.patient != null) {
      ref.refresh(patientDocumentsProvider(_docKey(widget.patient!.id)));
    }
  }

  void _syncTabController() {
    if (_tabController == null) {
      _tabController = TabController(length: _tabCount, vsync: this);
      return;
    }
    if (_tabController!.length != _tabCount) {
      _tabController!.dispose();
      _tabController = TabController(length: _tabCount, vsync: this);
    }
  }

  @override
  void didUpdateWidget(covariant PatientDetailPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.clinicWorkspaceId != widget.clinicWorkspaceId) {
      _syncTabController();
    }
    if (widget.patient != null && oldWidget.patient?.id != widget.patient?.id) {
      ref.refresh(patientDocumentsProvider(_docKey(widget.patient!.id)));
      _documentViewerOpenedFromDeepLink = false;
    }
  }

  @override
  void dispose() {
    _documentSearchController.dispose();
    _aiCopilotController.dispose();
    _tabController?.dispose();
    super.dispose();
  }

  void _showHeroMoreActions(BuildContext context, Patient p, Color brand) {
    final l10n = AppLocalizations.of(context)!;
    final canUseBriefing = ref.watch(
      doctorFeatureProvider(DoctorFeature.patientBriefing),
    );
    showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(Icons.calendar_today, color: brand),
              title: Text(l10n.translate('makeAppointment') ?? 'Make appointment'),
              onTap: () {
                Navigator.pop(ctx);
                _showMakeAppointmentDialog(context, ref, p, brand);
              },
            ),
            ListTile(
              leading: Icon(Icons.upload, color: brand),
              title: Text(l10n.uploadDocument),
              onTap: () {
                Navigator.pop(ctx);
                widget.onUploadOptions(p);
              },
            ),
            ListTile(
              leading: Icon(Icons.description, color: brand),
              title: Text(l10n.translate('createForm') ?? 'Create Form'),
              onTap: () {
                Navigator.pop(ctx);
                widget.onCreateForm(p);
              },
            ),
            if (widget.clinicWorkspaceId != null)
              ListTile(
                leading: Icon(Icons.assignment, color: brand),
                title: Text(
                  l10n.translate('createTreatmentPlan') ?? 'Create treatment plan',
                ),
                onTap: () {
                  Navigator.pop(ctx);
                  TreatmentPlanWizardSheet.show(
                    context,
                    ref,
                    clinicId: widget.clinicWorkspaceId!,
                    initialPatientId: int.parse(p.id),
                  );
                },
              ),
            if (canUseBriefing)
              ListTile(
                leading: Icon(Icons.summarize, color: brand),
                title: Text(l10n.generateBriefing),
                onTap: () {
                  Navigator.pop(ctx);
                  ref.read(patientBriefingProvider.notifier).generate(p.id, p.name);
                },
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSummaryRow(
    BuildContext context,
    Patient p, {
    String? genderFromForm,
  }) {
    final l10n = AppLocalizations.of(context)!;
    final age = patientAge(p);
    final stats = [
      PatientSummaryStat(
        label: l10n.translate('age') ?? 'Age',
        value: age?.toString() ?? '—',
      ),
      PatientSummaryStat(
        label: l10n.gender,
        value: patientGenderLabel(p, l10n, fromForm: genderFromForm),
      ),
      PatientSummaryStat(
        label: l10n.phoneNumber,
        value: patientPhoneDisplay(p),
      ),
      PatientSummaryStat(
        label: l10n.birthDate,
        value: formatPatientBirthDate(p),
      ),
      PatientSummaryStat(
        label: l10n.patientId,
        value: patientDisplayId(p),
      ),
      PatientSummaryStat(
        label: l10n.language,
        value: patientLanguageDisplay(p),
      ),
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 720) {
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (var i = 0; i < stats.length; i++) ...[
                  SizedBox(width: 132, child: stats[i]),
                  if (i < stats.length - 1) const SizedBox(width: 10),
                ],
              ],
            ),
          );
        }
        return Row(
          children: [
            for (var i = 0; i < stats.length; i++) ...[
              Expanded(child: stats[i]),
              if (i < stats.length - 1) const SizedBox(width: 10),
            ],
          ],
        );
      },
    );
  }

  Widget _buildOverviewTab(
    BuildContext context,
    Patient p,
    Color brand,
    List<PatientActivityItem> activities,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final suggestions = buildAiFollowUpSuggestions(p, l10n);
    final smsAllowed =
        ref.watch(profileAllProvider).valueOrNull?.profile['smsRemindersAllowed'] ==
            true;

    return SingleChildScrollView(
      padding: const EdgeInsets.only(top: 16, bottom: 8),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final isWide = constraints.maxWidth >= 760;
          final leftColumn = Column(
            children: [
              ClinicalSummaryCard(patient: p),
              const SizedBox(height: AppDesignSystem.sectionGap),
              RecentActivityCard(
                activities: activities,
                brand: brand,
                onViewFullHistory: () => _tabController?.animateTo(5),
              ),
            ],
          );
          final rightColumn = Column(
            children: [
              QuickActionsCard(
                brand: brand,
                onNewAppointment: () =>
                    _showMakeAppointmentDialog(context, ref, p, brand),
                onSendMessage: () => openChatWithPatient(ref, p.id),
                onCreateDocument: () => widget.onUploadOptions(p),
                onMoreActions: () => _showHeroMoreActions(context, p, brand),
              ),
              const SizedBox(height: AppDesignSystem.sectionGap),
              if (smsAllowed)
                _SmsReminderCard(
                  patient: p,
                  brand: brand,
                  onUpdated: () {
                    if (widget.onPatientDataRefresh != null) {
                      widget.onPatientDataRefresh!();
                    } else {
                      ref.read(patientsProvider.notifier).loadPatients();
                    }
                    ref.invalidate(patientByIdProvider(p.id));
                  },
                ),
              if (smsAllowed) const SizedBox(height: AppDesignSystem.sectionGap),
              _PatientPortalCard(
                patient: p,
                brand: brand,
                onCreateAccount: () => _createAccount(context, ref, p),
              ),
              const SizedBox(height: AppDesignSystem.sectionGap),
              AiFollowUpSuggestionsCard(
                suggestions: suggestions,
                brand: brand,
              ),
            ],
          );

          if (isWide) {
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(flex: 3, child: leftColumn),
                const SizedBox(width: AppDesignSystem.sectionGap),
                Expanded(flex: 2, child: rightColumn),
              ],
            );
          }
          return Column(
            children: [
              leftColumn,
              const SizedBox(height: AppDesignSystem.sectionGap),
              rightColumn,
            ],
          );
        },
      ),
    );
  }

  Widget _buildMedicalInfoTab(Patient p) {
    return SingleChildScrollView(
      padding: const EdgeInsets.only(top: 16, bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _GeneralInfo(
            general: p.general,
            patientId: p.id,
            onUpdate: () {
              if (widget.onPatientDataRefresh != null) {
                widget.onPatientDataRefresh!();
              } else {
                ref.read(patientsProvider.notifier).loadPatients();
              }
              ref.invalidate(patientByIdProvider(p.id));
            },
          ),
          if (widget.clinicWorkspaceId != null) ...[
            const SizedBox(height: AppDesignSystem.sectionGap),
            _SectionCardWrapper(
              title: AppLocalizations.of(context)!
                      .translate('patientDetailTabProphylaxis') ??
                  'Prophylaxis',
              child: _ClinicProphylaxisEditor(
                patientId: p.id,
                clinicId: widget.clinicWorkspaceId!,
                brand: widget.brand,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAppointmentsTab(Patient p, Color brand) {
    final l10n = AppLocalizations.of(context)!;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.calendar_month_outlined, size: 48, color: brand),
            const SizedBox(height: 16),
            Text(
              l10n.translate('appointmentsTabHint') ??
                  'Schedule and manage appointments for this patient.',
              textAlign: TextAlign.center,
              style: AppDesignSystem.body1(context),
            ),
            const SizedBox(height: 20),
            ShifaPrimaryButton(
              onPressed: () => _showMakeAppointmentDialog(context, ref, p, brand),
              icon: Icons.add,
              label: l10n.translate('newAppointment') ?? 'New Appointment',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPrescriptionsTab(Patient p, List<PatientForm> forms, Color brand) {
    final l10n = AppLocalizations.of(context)!;
    if (forms.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.medication_outlined, size: 48, color: brand),
            const SizedBox(height: 12),
            Text(l10n.translate('noPrescriptions') ?? 'No prescriptions yet'),
            const SizedBox(height: 16),
            ShifaSecondaryButton(
              onPressed: () => widget.onCreateForm(p),
              label: l10n.translate('createForm') ?? 'Create Form',
            ),
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.only(top: 16),
      itemCount: forms.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final form = forms[index];
        return _CardBox(
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(Icons.description, color: brand),
            title: Text(form.templateId),
            subtitle: Text(widget.formatDate(form.date)),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              ShellScope.pushNamed(
                context,
                AppRoutes.patientForm,
                arguments: {
                  'patient': p,
                  'templateId': form.templateId,
                  'existingForm': form,
                },
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildHistoryTab(List<PatientActivityItem> activities, Color brand) {
    final l10n = AppLocalizations.of(context)!;
    String two(int n) => n.toString().padLeft(2, '0');
    if (activities.isEmpty) {
      return Center(
        child: Text(
          l10n.translate('noRecentActivity') ?? 'No recent activity',
          style: AppDesignSystem.body2(context),
        ),
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.only(top: 16),
      itemCount: activities.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (context, index) {
        final item = activities[index];
        return _CardBox(
          child: Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: item.iconColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(item.icon, size: 18, color: item.iconColor),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.title, style: AppDesignSystem.h2(context)),
                    Text(item.subtitle, style: AppDesignSystem.body2(context)),
                  ],
                ),
              ),
              Text(
                '${two(item.date.day)}.${two(item.date.month)}.${item.date.year}',
                style: AppDesignSystem.caption(context),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _showMakeAppointmentDialog(
    BuildContext context,
    WidgetRef ref,
    Patient p,
    Color brand,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    DateTime? selectedDate;
    CalendarEntry? selectedSlot;
    TimeOfDay? bookingEndOverride;
    bool isVideo = false;
    bool loadingSlots = false;
    List<CalendarEntry> freeSlots = [];
    bool saving = false;

    Future<void> loadSlots(DateTime day) async {
      final tz =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      if (tz == null || tz.trim().isEmpty) {
        freeSlots = [];
        return;
      }
      try {
        await ref
            .read(calendarProvider.notifier)
            .loadDay(day: day, doctorTimeZone: tz);
        final entries =
            ref.read(calendarProvider)[DateTime(
              day.year,
              day.month,
              day.day,
            )] ??
            [];
        freeSlots = entries.where((e) => e.type == EntryType.freeSlot).toList();
        // Use doctor's timezone to check if day is in the past
        final todayInDoctorZone = getTodayInTimezone(tz);
        final slotDay = DateTime(day.year, day.month, day.day);
        if (slotDay.isBefore(todayInDoctorZone)) freeSlots = [];
      } catch (_) {
        freeSlots = [];
      }
    }

    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            title: Text(
              l10n.translate('makeAppointment') ?? 'Make appointment',
            ),
            content: SingleChildScrollView(
              child: SizedBox(
                width: double.maxFinite,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1) Date
                    ListTile(
                      title: Text(
                        l10n.translate('selectDate') ?? 'Select Date',
                      ),
                      subtitle: Text(
                        selectedDate != null
                            ? '${selectedDate!.day}.${selectedDate!.month}.${selectedDate!.year}'
                            : l10n.translate('notSelected') ?? 'Not selected',
                      ),
                      trailing: const Icon(Icons.calendar_today),
                      onTap: () async {
                        // Use doctor's timezone to prevent booking past days in doctor's calendar
                        final doctorTz =
                            ref
                                    .read(profileAllProvider)
                                    .valueOrNull
                                    ?.profile['timeZone']
                                as String?;
                        final todayInDoctorZone = getTodayInTimezone(doctorTz);
                        final picked = await showDatePicker(
                          context: context,
                          locale: localeForMaterialIntl(
                            Localizations.localeOf(context),
                          ),
                          initialDate: selectedDate ?? todayInDoctorZone,
                          firstDate: todayInDoctorZone,
                          lastDate: DateTime(2030),
                        );
                        if (picked != null) {
                          setDialogState(() {
                            selectedDate = DateTime(
                              picked.year,
                              picked.month,
                              picked.day,
                            );
                            selectedSlot = null;
                            bookingEndOverride = null;
                            freeSlots = [];
                          });
                          setDialogState(() => loadingSlots = true);
                          await loadSlots(selectedDate!);
                          if (context.mounted)
                            setDialogState(() => loadingSlots = false);
                        }
                      },
                    ),
                    const Divider(height: 1),
                    // 2) Slots
                    const SizedBox(height: 8),
                    Text(
                      l10n.translate('availableSlots') ?? 'Available Slots',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    if (selectedDate == null)
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          l10n.translate('pleaseSelectDateFirst') ??
                              'Please select a date first',
                          style: TextStyle(color: Colors.grey.shade600),
                        ),
                      )
                    else if (loadingSlots)
                      const Padding(
                        padding: EdgeInsets.all(16.0),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (freeSlots.isEmpty)
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          l10n.translate('noSlotsAvailable') ??
                              'No slots available',
                          style: TextStyle(color: Colors.grey.shade600),
                        ),
                      )
                    else
                      ConstrainedBox(
                        constraints: const BoxConstraints(maxHeight: 220),
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: freeSlots.length,
                          itemBuilder: (context, index) {
                            final slot = freeSlots[index];
                            final two = (int n) => n.toString().padLeft(2, '0');
                            final timeStr =
                                '${two(slot.start.hour)}:${two(slot.start.minute)} - ${two(slot.end.hour)}:${two(slot.end.minute)}';
                            final isSelected =
                                selectedSlot != null &&
                                selectedSlot!.start == slot.start &&
                                selectedSlot!.end == slot.end;
                            return ListTile(
                              title: Text(timeStr),
                              selected: isSelected,
                              onTap: () =>
                                  setDialogState(() {
                                    selectedSlot = slot;
                                    bookingEndOverride = null;
                                  }),
                              trailing: isSelected
                                  ? const Icon(Icons.check, color: Colors.teal)
                                  : null,
                            );
                          },
                        ),
                      ),
                    if (selectedSlot != null) ...[
                      Builder(
                        builder: (_) {
                          final tz =
                              ref.read(profileAllProvider).valueOrNull
                                  ?.profile['timeZone'] as String?;
                          final tzEff =
                              tz == null || tz.trim().isEmpty ? 'UTC' : tz.trim();
                          final endOptions =
                              consecutiveEndTimesForFreeSlot(
                                dayEntries: freeSlots,
                                startSlot: selectedSlot!,
                                doctorTimeZone: tzEff,
                              );
                          if (endOptions.length <= 1) {
                            return const SizedBox.shrink();
                          }

                          TimeOfDay match(TimeOfDay want) {
                            final k = want.hour * 60 + want.minute;
                            for (final o in endOptions) {
                              if (o.hour * 60 + o.minute == k) return o;
                            }
                            return endOptions.first;
                          }

                          final two = (int n) =>
                              n.toString().padLeft(2, '0');
                          final effective =
                              match(bookingEndOverride ?? selectedSlot!.end);

                          return Padding(
                            padding: const EdgeInsets.only(top: 10),
                            child: DropdownButtonFormField<TimeOfDay>(
                              decoration: InputDecoration(
                                labelText:
                                    l10n.translate('bookingEndTime') ??
                                    'End time',
                                border: const OutlineInputBorder(),
                              ),
                              isExpanded: true,
                              value: effective,
                              items: [
                                for (final o in endOptions)
                                  DropdownMenuItem(
                                    value: o,
                                    child: Text(
                                      '${two(o.hour)}:${two(o.minute)}',
                                    ),
                                  ),
                              ],
                              onChanged: (v) {
                                if (v == null) return;
                                setDialogState(() => bookingEndOverride = v);
                              },
                            ),
                          );

                        },

                      ),

                    ],

                    const SizedBox(height: 12),
                    const Divider(height: 1),
                    // 3) Appointment type
                    Text(
                      l10n.translate('appointmentType') ?? 'Appointment type',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        ChoiceChip(
                          label: Text(
                            l10n.translate('clinicAddress') ?? 'Clinic',
                          ),
                          selected: !isVideo,
                          onSelected: (v) =>
                              setDialogState(() => isVideo = false),
                          selectedColor: brand.withOpacity(0.3),
                        ),
                        const SizedBox(width: 8),
                        ChoiceChip(
                          label: Text(l10n.videoCall),
                          selected: isVideo,
                          onSelected: (v) =>
                              setDialogState(() => isVideo = true),
                          selectedColor: brand.withOpacity(0.3),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: saving ? null : () => Navigator.of(ctx).pop(),
                child: Text(l10n.cancel),
              ),
              ShifaPrimaryButton(
                isLoading: saving,
                onPressed:
                    saving || selectedDate == null || selectedSlot == null
                    ? null
                    : () async {
                        setDialogState(() => saving = true);
                        final doctorTimeZone =
                            ref
                                    .read(profileAllProvider)
                                    .valueOrNull
                                    ?.profile['timeZone']
                                as String?;
                        if (doctorTimeZone == null ||
                            doctorTimeZone.trim().isEmpty) {
                          setDialogState(() => saving = false);
                          if (ctx.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.translate('failedToAssign') ??
                                      'Profile timezone not available.',
                                ),
                              ),
                            );
                          }
                          return;
                        }
                        try {
                          final patientId = int.parse(p.id);
                          await ref
                              .read(calendarProvider.notifier)
                              .bookFreeSlotRemote(
                                day: selectedDate!,
                                slot: selectedSlot!,
                                patientId: patientId,
                                doctorTimeZone: doctorTimeZone,
                                location: isVideo
                                    ? 'Video Consultation'
                                    : 'Clinic Address',
                                reason: 'Check Up',
                                isVideo: isVideo,
                                endExclusive:
                                    bookingEndOverride ?? selectedSlot!.end,
                              );
                          await invalidateAppointmentRelatedProviders(ref);
                          await refreshCalendarDay(
                            ref,
                            selectedDate!,
                            doctorTimeZone!,
                          );
                          if (ctx.mounted) {
                            Navigator.of(ctx).pop();
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.translate('patientAssigned') ??
                                      'Patient assigned',
                                ),
                              ),
                            );
                          }
                        } catch (e) {
                          if (ctx.mounted) {
                            setDialogState(() => saving = false);
                            await refreshCalendarDay(
                              ref,
                              selectedDate!,
                              doctorTimeZone!,
                            );
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.translate('bookingRangeUnavailable') ??
                                      'Selected time range is no longer fully available — calendar refreshed.',
                                ),
                              ),
                            );
                          }
                        }
                      },
                label: l10n.translate('confirm') ?? 'Confirm',
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final patient = widget.patient;
    final brand = widget.brand;

    if (patient == null) {
      final l10n = AppLocalizations.of(context)!;
      return Container(
        decoration: AppDesignSystem.cardDecoration(),
        alignment: Alignment.center,
        child: Text(
          l10n.translate('selectPatientHint') ??
              'Select a patient to view details',
          style: AppDesignSystem.body2(context),
        ),
      );
    }
    final p = patient;
    final l10n = AppLocalizations.of(context)!;
    final docsAsync = ref.watch(patientDocumentsProvider(_docKey(p.id)));
    final formsAsync = ref.watch(patientFormsProvider(p.id));
    final canUseBriefing = ref.watch(
      doctorFeatureProvider(DoctorFeature.patientBriefing),
    );

    final activities = docsAsync.maybeWhen(
      data: (docs) => buildPatientActivities(
        p.copyWith(documents: docs),
        l10n,
        brand,
      ),
      orElse: () => buildPatientActivities(p, l10n, brand),
    );
    final genderFromForm = formsAsync.maybeWhen(
      data: (forms) {
        for (final form in forms) {
          final gender = form.gender;
          if (gender != null && gender.trim().isNotEmpty) return gender.trim();
        }
        return null;
      },
      orElse: () => null,
    );

    return Container(
      decoration: AppDesignSystem.cardDecoration(
        borderOverride: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.6)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                PatientHeroHeader(
                  patient: p,
                  brand: brand,
                  isFavorite: widget.isFavorite,
                  onToggleFavorite: widget.onToggleFavorite ?? () {},
                  onMoreActions: () => _showHeroMoreActions(context, p, brand),
                  onAiSummary: () =>
                      ref.read(patientBriefingProvider.notifier).generate(p.id, p.name),
                  showAiSummary: canUseBriefing,
                ),
                const SizedBox(height: AppDesignSystem.sectionGap),
                _buildSummaryRow(
                  context,
                  p,
                  genderFromForm: genderFromForm,
                ),
                const SizedBox(height: AppDesignSystem.sectionGap),
                PatientAiCopilotCard(
                  patientName: p.name,
                  brand: brand,
                  controller: _aiCopilotController,
                  onAsk: () {
                    final query = _aiCopilotController.text.trim();
                    if (query.isEmpty) return;
                    ref
                        .read(patientBriefingProvider.notifier)
                        .generate(p.id, p.name);
                    _aiCopilotController.clear();
                  },
                ),
              ],
            ),
          ),
          TabBar(
            controller: _tabController!,
            isScrollable: true,
            labelColor: brand,
            unselectedLabelColor: AppDesignSystem.textSecondary,
            indicatorColor: brand,
            indicatorWeight: 2.5,
            tabs: [
              Tab(text: l10n.translate('overview') ?? 'Overview'),
              Tab(text: l10n.translate('medicalInfo') ?? 'Medical Info'),
              Tab(text: l10n.translate('appointments') ?? 'Appointments'),
              Tab(text: l10n.translate('documents') ?? 'Documents'),
              Tab(text: l10n.translate('prescriptions') ?? 'Prescriptions'),
              Tab(text: l10n.translate('history') ?? 'History'),
            ],
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                AppDesignSystem.cardPadding,
                0,
                AppDesignSystem.cardPadding,
                AppDesignSystem.cardPadding,
              ),
              child: TabBarView(
                controller: _tabController!,
                children: [
                  _buildOverviewTab(context, p, brand, activities),
                  _buildMedicalInfoTab(p),
                  _buildAppointmentsTab(p, brand),
                  _buildDocumentsTab(context, p, brand, docsAsync, formsAsync),
                  formsAsync.when(
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (_, __) => _buildPrescriptionsTab(p, const [], brand),
                    data: (forms) => _buildPrescriptionsTab(p, forms, brand),
                  ),
                  _buildHistoryTab(activities, brand),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDocumentsTab(
    BuildContext context,
    Patient p,
    Color brand,
    AsyncValue<List<PatientDocument>> docsAsync,
    AsyncValue<List<PatientForm>> formsAsync,
  ) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                l10n.translate('documentHistory') ?? 'Document History',
                style: AppDesignSystem.h2(context),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.refresh, size: 20),
              onPressed: () {
                ref.refresh(patientDocumentsProvider(_docKey(p.id)));
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        l10n.translate('refreshing') ?? 'Refreshing...',
                      ),
                      duration: const Duration(seconds: 1),
                    ),
                  );
                }
              },
              tooltip: l10n.translate('refresh') ?? 'Refresh list',
            ),
            PopupMenuButton<String>(
              icon: Icon(Icons.more_vert, color: brand),
              onSelected: (value) {
                switch (value) {
                  case 'upload':
                    widget.onUploadOptions(p);
                    break;
                  case 'form':
                    widget.onCreateForm(p);
                    break;
                  case 'task':
                    ShellScope.pushNamed(
                      context,
                      AppRoutes.createTask,
                      arguments: {'patientId': int.parse(p.id)},
                    );
                    break;
                }
              },
              itemBuilder: (context) => [
                PopupMenuItem(
                  value: 'upload',
                  child: Row(
                    children: [
                      Icon(Icons.upload, size: 18, color: brand),
                      const SizedBox(width: 8),
                      Text(l10n.uploadDocument),
                    ],
                  ),
                ),
                PopupMenuItem(
                  value: 'form',
                  child: Row(
                    children: [
                      Icon(Icons.description, size: 18, color: brand),
                      const SizedBox(width: 8),
                      Text(l10n.translate('createForm') ?? 'Create Form'),
                    ],
                  ),
                ),
                PopupMenuItem(
                  value: 'task',
                  child: Row(
                    children: [
                      Icon(Icons.task, size: 18, color: brand),
                      const SizedBox(width: 8),
                      Text(l10n.createTask),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _documentSearchController,
          decoration: InputDecoration(
            hintText: l10n.search,
            prefixIcon: const Icon(Icons.search, size: 18),
            filled: true,
            fillColor: AppDesignSystem.backgroundSecondary,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 8,
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppDesignSystem.border),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppDesignSystem.border),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: brand, width: 1.5),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: docsAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, st) {
              final safeMessage = sanitizeErrorMessage(e, l10n);
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.error_outline,
                        color: Colors.red.shade400,
                        size: 28,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '${l10n.error}: $safeMessage',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 8),
                      TextButton.icon(
                        onPressed: () =>
                            ref.refresh(patientDocumentsProvider(_docKey(p.id))),
                        icon: const Icon(Icons.refresh, size: 16),
                        label: Text(l10n.retry),
                      ),
                    ],
                  ),
                ),
              );
            },
            data: (docs) {
              final filteredDocs = _documentSearchQuery.isEmpty
                  ? docs
                  : docs
                      .where(
                        (doc) => doc.title.toLowerCase().contains(
                              _documentSearchQuery.toLowerCase(),
                            ),
                      )
                      .toList();
              if (widget.openDocumentViewer &&
                  widget.selectedDocumentId != null &&
                  widget.patient != null &&
                  !_documentViewerOpenedFromDeepLink &&
                  docs.any(
                    (d) => d.id.toString() == widget.selectedDocumentId,
                  )) {
                _documentViewerOpenedFromDeepLink = true;
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (!mounted) return;
                  ShellScope.push(
                    context,
                    MaterialPageRoute<void>(
                      builder: (_) => DocumentViewerScreen(
                        patientId: widget.patient!.id,
                        documentId: widget.selectedDocumentId!,
                        title: widget.documentTitleForViewer ?? 'Document',
                        clinicWorkspaceId: widget.clinicWorkspaceId,
                      ),
                    ),
                  );
                });
              }
              return formsAsync.when(
                loading: () => filteredDocs.isEmpty
                    ? Center(child: Text(l10n.noDocuments))
                    : _buildDocumentList(
                        context,
                        ref,
                        filteredDocs,
                        [],
                        brand,
                        widget.formatDate,
                        p,
                      ),
                error: (_, __) => filteredDocs.isEmpty
                    ? Center(child: Text(l10n.noDocuments))
                    : _buildDocumentList(
                        context,
                        ref,
                        filteredDocs,
                        [],
                        brand,
                        widget.formatDate,
                        p,
                      ),
                data: (forms) => filteredDocs.isEmpty
                    ? Center(child: Text(l10n.noDocuments))
                    : _buildDocumentList(
                        context,
                        ref,
                        filteredDocs,
                        forms,
                        brand,
                        widget.formatDate,
                        p,
                      ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildDocumentList(
    BuildContext context,
    WidgetRef ref,
    List<PatientDocument> docs,
    List<PatientForm> forms,
    Color brand,
    String Function(DateTime) formatDate,
    Patient patient,
  ) {
    // Find form linked to a document by documentId
    PatientForm? findFormForDocument(PatientDocument doc) {
      if (forms.isEmpty) return null;

      // Match by documentId (most reliable)
      try {
        return forms.firstWhere(
          (f) => f.documentId != null && f.documentId == doc.id,
        );
      } catch (e) {
        // Fallback: try matching by title pattern if documentId not available
        if (doc.title.startsWith('Form ')) {
          final titleMatch = RegExp(r'Form (\w+)').firstMatch(doc.title);
          if (titleMatch != null) {
            final templateId = titleMatch.group(1);
            if (templateId != null) {
              try {
                return forms.firstWhere(
                  (f) =>
                      f.templateId == templateId &&
                      f.date.year == doc.date.year &&
                      f.date.month == doc.date.month &&
                      f.date.day == doc.date.day,
                );
              } catch (e) {
                // Last resort: just match template
                try {
                  return forms.firstWhere((f) => f.templateId == templateId);
                } catch (e) {
                  return null;
                }
              }
            }
          }
        }
        return null;
      }
    }

    return ListView.separated(
      itemCount: docs.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final d = docs[index];
        final urlToOpen = d.canView ? (d.url ?? '') : '';
        final linkedForm = findFormForDocument(d);
        final isForm = linkedForm != null;
        final l10n = AppLocalizations.of(context)!;
        final isLocked = !d.canView;
        // Show localized form title for 025-2 (e.g. "025-2 raqamli tibbiy hujjat" in Uzbek)
        final isForm0252 =
            (isForm && linkedForm?.templateId == '025-2') ||
            d.title.startsWith('Form 025-2') ||
            RegExp(r'Form 025-2\s*\([\d-]+\)').hasMatch(d.title);
        final displayTitle = isForm0252
            ? '${l10n.form0252MedicalDocument} (${formatDate(d.date)})'
            : d.title;
        final isSelected =
            widget.selectedDocumentId != null &&
            d.id.toString() == widget.selectedDocumentId;

        final docCategory = findDoctorCategory(d.category);
        final card = _CardBox(
          child: Row(
            children: [
              // Doc info
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (isForm)
                          Icon(Icons.description, size: 16, color: brand),
                        if (isForm) const SizedBox(width: 4),
                        if (isLocked)
                          Icon(
                            Icons.lock_outline,
                            size: 16,
                            color: Colors.grey.shade600,
                          ),
                        if (isLocked) const SizedBox(width: 4),
                        Expanded(
                          child: Text(
                            displayTitle,
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Text(
                          formatDate(d.date),
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey.shade600,
                          ),
                        ),
                        if (docCategory != null) ...[
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: docCategory.isMedicalResult
                                  ? Colors.teal.withOpacity(0.12)
                                  : Colors.grey.shade200,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  docCategory.icon,
                                  size: 10,
                                  color: docCategory.isMedicalResult
                                      ? Colors.teal.shade700
                                      : Colors.grey.shade700,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  docCategory.label(l10n),
                                  style: TextStyle(
                                    fontSize: 10,
                                    color: docCategory.isMedicalResult
                                        ? Colors.teal.shade700
                                        : Colors.grey.shade700,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                        if (d.isSharedWithTeam) ...[
                          const SizedBox(width: 6),
                          Tooltip(
                            message: l10n.translate('sharedWithTeamTooltip') ??
                                'Visible to all doctors of this patient',
                            child: Icon(
                              Icons.group,
                              size: 12,
                              color: Colors.teal.shade600,
                            ),
                          ),
                        ],
                      ],
                    ),
                    if (isLocked && d.creatorLabel.isNotEmpty) ...[
                      const SizedBox(height: 2),
                      Builder(
                        builder: (context) {
                          final locale = Localizations.localeOf(
                            context,
                          ).languageCode.toLowerCase();
                          final creator = d.creatorLabel == 'Unknown'
                              ? (l10n.translate('anotherUser') ??
                                    'Another user')
                              : d.creatorLabel;
                          final byline = locale == 'uz'
                              ? '$creator tomonidan yuklangan'
                              : '${l10n.uploadedBy} $creator';
                          return Text(
                            byline,
                            style: TextStyle(
                              fontSize: 11,
                              color: Colors.grey.shade600,
                            ),
                          );
                        },
                      ),
                    ],
                  ],
                ),
              ),
              // Edit button for forms (only when not locked)
              if (isForm && linkedForm != null && !isLocked)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: IconButton.filledTonal(
                    style: ButtonStyle(
                      backgroundColor: MaterialStatePropertyAll(
                        brand.withOpacity(0.15),
                      ),
                      foregroundColor: MaterialStatePropertyAll(brand),
                    ),
                    onPressed: () {
                      ShellScope.pushNamed(
                        context,
                        AppRoutes.patientForm,
                        arguments: {
                          'patient': patient,
                          'templateId': linkedForm!.templateId,
                          'existingForm': linkedForm!,
                        },
                      ).then((_) {
                        ref.refresh(patientFormsProvider(patient.id));
                        ref.refresh(patientDocumentsProvider(_docKey(patient.id)));
                      });
                    },
                    icon: const Icon(Icons.edit, size: 20),
                    tooltip: l10n.edit,
                  ),
                ),
              // Three-dot menu for locked docs (Request access)
              if (isLocked)
                PopupMenuButton<String>(
                  icon: Icon(Icons.more_vert, color: Colors.grey.shade700),
                  onSelected: (value) async {
                    if (value == 'request_access') {
                      try {
                        final client = ref.read(apiClientProvider);
                        await requestDocumentAccessWithClient(
                          client: client,
                          patientId: patient.id,
                          documentId: d.id,
                          clinicId: widget.clinicWorkspaceId,
                        );
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(l10n.requestAccessSent)),
                          );
                          ref.refresh(patientDocumentsProvider(_docKey(patient.id)));
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('${l10n.error}: $e'),
                              backgroundColor: Colors.red,
                            ),
                          );
                        }
                      }
                    }
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: 'request_access',
                      child: Text(l10n.requestAccess),
                    ),
                  ],
                ),
              // Open in viewer: prefer authenticated download (works after access granted)
              IconButton.filledTonal(
                style: ButtonStyle(
                  backgroundColor: MaterialStatePropertyAll(
                    brand.withOpacity(0.15),
                  ),
                  foregroundColor: MaterialStatePropertyAll(brand),
                ),
                onPressed: urlToOpen.isNotEmpty
                    ? () => _openDocument(
                        context,
                        ref: ref,
                        patientId: patient.id,
                        documentId: d.id,
                        title: d.title,
                        l10n: l10n,
                        clinicWorkspaceId: widget.clinicWorkspaceId,
                      )
                    : null,
                icon: const Icon(Icons.picture_as_pdf, size: 20),
                tooltip: l10n.openDocument,
              ),
            ],
          ),
        );
        if (isSelected) {
          return Container(
            decoration: BoxDecoration(
              border: Border.all(color: brand, width: 2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: card,
          );
        }
        return card;
      },
    );
  }
}

class _ClinicProphylaxisEditor extends ConsumerStatefulWidget {
  const _ClinicProphylaxisEditor({
    required this.patientId,
    required this.clinicId,
    required this.brand,
  });

  final String patientId;
  final int clinicId;
  final Color brand;

  @override
  ConsumerState<_ClinicProphylaxisEditor> createState() =>
      _ClinicProphylaxisEditorState();
}

class _ClinicProphylaxisEditorState
    extends ConsumerState<_ClinicProphylaxisEditor> {
  bool _loading = true;
  bool _saving = false;
  int _intervalMonths = 12;
  bool _enabled = true;
  String? _lastSentAt;

  @override
  void initState() {
    super.initState();
    Future<void>.microtask(_load);
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() => _loading = true);
    try {
      final client = ref.read(apiClientProvider);
      final s = await fetchProphylaxisSettingsWithClient(
        client: client,
        patientId: widget.patientId,
        clinicId: widget.clinicId,
      );
      if (!mounted) return;
      setState(() {
        _loading = false;
        if (s != null) {
          _intervalMonths = s.intervalMonths.clamp(1, 60);
          _enabled = s.enabled;
          _lastSentAt = s.lastSentAt;
        }
      });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    final l10n = AppLocalizations.of(context)!;
    setState(() => _saving = true);
    try {
      final client = ref.read(apiClientProvider);
      await upsertProphylaxisSettingsWithClient(
        client: client,
        patientId: widget.patientId,
        clinicId: widget.clinicId,
        intervalMonths: _intervalMonths.clamp(1, 60),
        enabled: _enabled,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('prophylaxisSaved') ?? 'Saved',
            ),
          ),
        );
        await _load();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.error}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(child: CircularProgressIndicator()),
      );
    }

    final intervalItems = List<int>.generate(60, (i) => i + 1);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.translate('prophylaxisIntervalMonths') ?? 'Interval (months)',
          style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
        ),
        const SizedBox(height: 6),
        DropdownButtonFormField<int>(
          value: _intervalMonths.clamp(1, 60),
          decoration: InputDecoration(
            filled: true,
            fillColor: Colors.white,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: BorderSide(color: Colors.grey.shade300),
            ),
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          ),
          items: intervalItems
              .map(
                (m) => DropdownMenuItem<int>(
                  value: m,
                  child: Text('$m'),
                ),
              )
              .toList(),
          onChanged: (v) {
            if (v != null) setState(() => _intervalMonths = v);
          },
        ),
        const SizedBox(height: 12),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(
            l10n.translate('prophylaxisEnabled') ?? 'Enabled',
            style: const TextStyle(fontSize: 14),
          ),
          value: _enabled,
          activeColor: widget.brand,
          onChanged: (v) => setState(() => _enabled = v),
        ),
        if (_lastSentAt != null && _lastSentAt!.isNotEmpty) ...[
          const SizedBox(height: 4),
          Text(
            (l10n.translate('prophylaxisLastSent') ?? 'Last sent: {{date}}')
                .replaceAll('{{date}}', _lastSentAt!),
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
          ),
        ],
        const SizedBox(height: 12),
        ShifaPrimaryButton(
          width: ButtonWidth.fill,
          isLoading: _saving,
          onPressed: _saving ? null : _save,
          label: l10n.translate('prophylaxisSave') ?? 'Save',
          icon: Icons.save_outlined,
        ),
      ],
    );
  }
}

/// Open document in the in-app viewer (browser-like window). No download, no external app.
void _openDocument(
  BuildContext context, {
  required WidgetRef ref,
  required String patientId,
  required String documentId,
  required String title,
  required AppLocalizations l10n,
  int? clinicWorkspaceId,
}) {
  ShellScope.push(
    context,
    MaterialPageRoute<void>(
      builder: (_) => DocumentViewerScreen(
        patientId: patientId,
        documentId: documentId,
        title: title,
        clinicWorkspaceId: clinicWorkspaceId,
      ),
    ),
  ).then((_) {
    // Refresh document list when returning (e.g. after patient approved access in another tab)
    ref.refresh(patientDocumentsProvider(PatientDocumentsKey(
      patientId: patientId,
      clinicId: clinicWorkspaceId,
    )));
  });
}

/// ---------------- Bits & pieces ----------------
class _Avatar extends StatelessWidget {
  final String? photoUrl;
  final String name;
  final double size;
  const _Avatar({Key? key, required this.name, this.photoUrl, this.size = 24})
    : super(key: key);

  String get initials {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      return parts.first.isNotEmpty
          ? parts.first.substring(0, 1).toUpperCase()
          : '?';
    }
    final first = parts.first.isNotEmpty ? parts.first.substring(0, 1) : '?';
    final last = parts.last.isNotEmpty ? parts.last.substring(0, 1) : '?';
    return (first + last).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final bg = Colors.grey.shade300;
    final hasUrl = photoUrl != null && photoUrl!.isNotEmpty;
    return CircleAvatar(
      radius: size,
      backgroundColor: bg,
      backgroundImage: hasUrl ? NetworkImage(photoUrl!) : null,
      child: hasUrl
          ? null
          : Text(
              initials,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: Colors.grey.shade700,
                fontSize: size * 0.8,
              ),
            ),
    );
  }
}

class _GeneralInfo extends ConsumerStatefulWidget {
  final PatientGeneral general;
  final String patientId;
  final VoidCallback onUpdate;
  const _GeneralInfo({
    Key? key,
    required this.general,
    required this.patientId,
    required this.onUpdate,
  }) : super(key: key);

  @override
  ConsumerState<_GeneralInfo> createState() => _GeneralInfoState();
}

class _GeneralInfoState extends ConsumerState<_GeneralInfo> {
  static const List<String> chronicDiseases = [
    'None',
    'Diabetes (Type 1)',
    'Diabetes (Type 2)',
    'HIV/AIDS',
    'Hypertension',
    'Heart Disease',
    'Chronic Kidney Disease',
    'Chronic Liver Disease',
    'Asthma',
    'COPD',
    'Cancer',
    'Epilepsy',
    'Multiple Sclerosis',
    'Parkinson\'s Disease',
    'Rheumatoid Arthritis',
    'Lupus',
    'Crohn\'s Disease',
    'Ulcerative Colitis',
    'Hemophilia',
    'Sickle Cell Disease',
    'Thalassemia',
    'Other',
  ];

  static const List<String> bloodGroups = [
    '',
    'A+',
    'A-',
    'B+',
    'B-',
    'AB+',
    'AB-',
    'O+',
    'O-',
  ];

  static const List<String> genderOptions = [
    '',
    'Male',
    'Female',
    'Other',
  ];

  String? _selectedChronicDisease;
  String? _selectedBloodGroup;
  String? _selectedGender;
  late TextEditingController _allergiesCtrl;
  bool _isUpdating = false;

  @override
  void initState() {
    super.initState();
    _selectedChronicDisease = widget.general.chronicDisease ?? 'None';
    _selectedBloodGroup = widget.general.bloodGroup ?? '';
    _selectedGender = widget.general.gender ?? '';
    _allergiesCtrl = TextEditingController(text: widget.general.allergies ?? '');
  }

  @override
  void dispose() {
    _allergiesCtrl.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(_GeneralInfo oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.patientId != widget.patientId ||
        oldWidget.general.chronicDisease != widget.general.chronicDisease ||
        oldWidget.general.bloodGroup != widget.general.bloodGroup ||
        oldWidget.general.gender != widget.general.gender ||
        oldWidget.general.allergies != widget.general.allergies) {
      setState(() {
        _selectedChronicDisease = widget.general.chronicDisease ?? 'None';
        _selectedBloodGroup = widget.general.bloodGroup ?? '';
        _selectedGender = widget.general.gender ?? '';
        _allergiesCtrl.text = widget.general.allergies ?? '';
      });
    }
  }

  Future<void> _updateClinicalFields({
    String? chronicDisease,
    String? bloodGroup,
    String? gender,
    String? allergies,
  }) async {
    if (_isUpdating) return;
    setState(() => _isUpdating = true);
    try {
      final client = ref.read(apiClientProvider);
      final updatedPatient = await updatePatientWithClient(
        client: client,
        patientId: widget.patientId,
        chronicDisease: chronicDisease,
        bloodGroup: bloodGroup,
        gender: gender,
        allergies: allergies,
      );
      if (mounted) {
        setState(() {
          _selectedChronicDisease =
              updatedPatient.general.chronicDisease ?? 'None';
          _selectedBloodGroup = updatedPatient.general.bloodGroup ?? '';
          _selectedGender = updatedPatient.general.gender ?? '';
          _allergiesCtrl.text = updatedPatient.general.allergies ?? '';
        });
      }
      widget.onUpdate();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)!.failedToUpdate}: $e',
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isUpdating = false);
    }
  }

  Future<void> _updateChronicDisease(String? value) async {
    if (_isUpdating) return;
    setState(() => _selectedChronicDisease = value);
    final chronicDiseaseValue = value == 'None' || value == null ? '' : value;
    await _updateClinicalFields(chronicDisease: chronicDiseaseValue);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.chronicDiseaseUpdated),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    String two(int n) => n.toString().padLeft(2, '0');
    String? dob;
    if (widget.general.birthDate != null) {
      final d = widget.general.birthDate!;
      dob = '${two(d.day)}.${two(d.month)}.${d.year}';
    }
    final rows = <MapEntry<String, String>>[
      MapEntry(l10n.patientId, widget.patientId),
      if (dob != null) MapEntry(l10n.birthDate, dob),
      if (widget.general.allPhones.isNotEmpty)
        MapEntry(l10n.phoneNumber, widget.general.allPhones.join(', ')),
      if (widget.general.email != null)
        MapEntry(l10n.email, widget.general.email!),
      if (widget.general.formattedLocation != null)
        MapEntry(l10n.address, widget.general.formattedLocation!),
      if (widget.general.language != null)
        MapEntry(l10n.language, widget.general.language!),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.generalInformation,
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: Colors.grey.shade800,
          ),
        ),
        const SizedBox(height: 8),
        ...rows.map(
          (e) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Row(
              children: [
                SizedBox(
                  width: 110,
                  child: Text(
                    e.key,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                ),
                Expanded(
                  child: Text(e.value, style: const TextStyle(fontSize: 12)),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        // Chronic Disease Dropdown
        Row(
          children: [
            SizedBox(
              width: 110,
              child: Text(
                AppLocalizations.of(context)!.chronicDisease,
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ),
            Expanded(
              child: DropdownButtonFormField<String>(
                value: _selectedChronicDisease,
                isExpanded: true,
                decoration: InputDecoration(
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                ),
                items: chronicDiseases.map((disease) {
                  return DropdownMenuItem<String>(
                    value: disease,
                    child: Text(
                      l10n.translateChronicDisease(disease),
                      style: TextStyle(
                        fontSize: 12,
                        color: disease == 'None'
                            ? Colors.grey
                            : disease == 'HIV/AIDS' || disease == 'Cancer'
                            ? Colors.red
                            : Colors.black87,
                      ),
                    ),
                  );
                }).toList(),
                onChanged: _isUpdating
                    ? null
                    : (value) {
                        if (value != null) {
                          _updateChronicDisease(value);
                        }
                      },
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            SizedBox(
              width: 110,
              child: Text(
                l10n.gender,
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ),
            Expanded(
              child: DropdownButtonFormField<String>(
                value: _selectedGender,
                isExpanded: true,
                decoration: InputDecoration(
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                ),
                items: genderOptions
                    .map(
                      (g) => DropdownMenuItem<String>(
                        value: g,
                        child: Text(g.isEmpty ? '—' : g),
                      ),
                    )
                    .toList(),
                onChanged: _isUpdating
                    ? null
                    : (value) {
                        setState(() => _selectedGender = value);
                        _updateClinicalFields(gender: value ?? '');
                      },
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            SizedBox(
              width: 110,
              child: Text(
                l10n.translate('bloodGroup') ?? 'Blood Group',
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ),
            Expanded(
              child: DropdownButtonFormField<String>(
                value: _selectedBloodGroup,
                isExpanded: true,
                decoration: InputDecoration(
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                ),
                items: bloodGroups
                    .map(
                      (g) => DropdownMenuItem<String>(
                        value: g,
                        child: Text(g.isEmpty ? '—' : g),
                      ),
                    )
                    .toList(),
                onChanged: _isUpdating
                    ? null
                    : (value) {
                        setState(() => _selectedBloodGroup = value);
                        _updateClinicalFields(bloodGroup: value ?? '');
                      },
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 110,
              child: Text(
                l10n.translate('allergies') ?? 'Allergies',
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ),
            Expanded(
              child: TextField(
                controller: _allergiesCtrl,
                enabled: !_isUpdating,
                decoration: InputDecoration(
                  isDense: true,
                  hintText: l10n.translate('noKnownAllergies') ??
                      'No known allergies',
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 8,
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: BorderSide(color: Colors.grey.shade300),
                  ),
                ),
                onSubmitted: (value) => _updateClinicalFields(allergies: value),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _CardBox extends StatelessWidget {
  final Widget child;
  const _CardBox({Key? key, required this.child}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: child,
    );
  }
}

class _CollapsibleCard extends StatefulWidget {
  final String title;
  final IconData icon;
  final Color brand;
  final Widget child;

  const _CollapsibleCard({
    Key? key,
    required this.title,
    required this.icon,
    required this.brand,
    required this.child,
  }) : super(key: key);

  @override
  State<_CollapsibleCard> createState() => _CollapsibleCardState();
}

class _CollapsibleCardState extends State<_CollapsibleCard> {
  bool _isExpanded = true;

  @override
  Widget build(BuildContext context) {
    return _CardBox(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => setState(() => _isExpanded = !_isExpanded),
            child: Row(
              children: [
                Icon(widget.icon, size: 16, color: widget.brand),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    widget.title,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                Icon(
                  _isExpanded ? Icons.expand_less : Icons.expand_more,
                  color: Colors.grey.shade600,
                ),
              ],
            ),
          ),
          if (_isExpanded) ...[const SizedBox(height: 12), widget.child],
        ],
      ),
    );
  }
}

class _PatientPortalCard extends StatelessWidget {
  const _PatientPortalCard({
    required this.patient,
    required this.brand,
    required this.onCreateAccount,
  });

  final Patient patient;
  final Color brand;
  final VoidCallback onCreateAccount;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _SectionCardWrapper(
      title: l10n.patientAppAccess,
      child: patient.hasAccount
          ? Row(
              children: [
                const Icon(Icons.check_circle, color: Colors.green, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    patient.username != null && patient.username!.isNotEmpty
                        ? '${l10n.accountAlreadyAvailable} (${patient.username})'
                        : l10n.accountAlreadyAvailable,
                    style: const TextStyle(fontSize: 13, color: Colors.green),
                  ),
                ),
              ],
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l10n.noAccountYet,
                  style: AppDesignSystem.body2(context),
                ),
                const SizedBox(height: 12),
                ShifaPrimaryButton(
                  width: ButtonWidth.fill,
                  onPressed: onCreateAccount,
                  icon: Icons.person_add_alt_1,
                  label: l10n.createPatientAccount,
                ),
              ],
            ),
    );
  }
}

class _SectionCardWrapper extends StatelessWidget {
  const _SectionCardWrapper({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
      decoration: AppDesignSystem.cardDecoration(
        borderOverride: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.7)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppDesignSystem.h2(context)),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }
}

class _SmsReminderCard extends ConsumerStatefulWidget {
  const _SmsReminderCard({
    required this.patient,
    required this.brand,
    required this.onUpdated,
  });

  final Patient patient;
  final Color brand;
  final VoidCallback onUpdated;

  @override
  ConsumerState<_SmsReminderCard> createState() => _SmsReminderCardState();
}

class _SmsReminderCardState extends ConsumerState<_SmsReminderCard> {
  bool _saving = false;
  bool _sendingTest = false;
  late bool _enabled;

  @override
  void initState() {
    super.initState();
    _enabled = widget.patient.general.smsReminderEnabled;
  }

  @override
  void didUpdateWidget(covariant _SmsReminderCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.patient.id != widget.patient.id ||
        oldWidget.patient.general.smsReminderEnabled !=
            widget.patient.general.smsReminderEnabled) {
      _enabled = widget.patient.general.smsReminderEnabled;
    }
  }

  bool get _hasPhone => widget.patient.general.allPhones.isNotEmpty;

  Future<void> _sendTestSms() async {
    if (!_hasPhone || _sendingTest || _saving) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() => _sendingTest = true);
    try {
      await sendPatientTestSmsWithClient(
        client: ref.read(apiClientProvider),
        patientId: widget.patient.id,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('smsTestSent') ??
                  'Test SMS sent. Check the patient phone.',
            ),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.error}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _sendingTest = false);
    }
  }

  Future<void> _onToggle(bool value) async {
    if (!_hasPhone || _saving) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _saving = true;
      _enabled = value;
    });
    try {
      await updatePatientWithClient(
        client: ref.read(apiClientProvider),
        patientId: widget.patient.id,
        smsReminderEnabled: value,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('smsReminderSaved') ??
                  'SMS reminder settings saved',
            ),
          ),
        );
        widget.onUpdated();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _enabled = !value);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.error}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _SectionCardWrapper(
      title: l10n.translate('smsReminderTitle') ?? 'SMS appointment reminders',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.translate('smsReminderDescription') ??
                'Patient receives an SMS 24 hours before each future appointment.',
            style: AppDesignSystem.body2(context),
          ),
          if (!_hasPhone) ...[
            const SizedBox(height: 8),
            Text(
              l10n.translate('smsReminderNoPhone') ??
                  'Add a phone number to enable SMS reminders.',
              style: TextStyle(fontSize: 12, color: Colors.orange.shade800),
            ),
          ],
          const SizedBox(height: 8),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(
              l10n.translate('smsReminderEnabled') ?? 'Send SMS reminders',
              style: const TextStyle(fontSize: 13),
            ),
            value: _enabled,
            onChanged: (!_hasPhone || _saving) ? null : _onToggle,
            activeTrackColor: widget.brand.withValues(alpha: 0.35),
            activeThumbColor: widget.brand,
          ),
          DropdownButtonFormField<String>(
            value: '24h',
            decoration: InputDecoration(
              labelText: l10n.translate('reminderTiming') ?? 'Reminder time',
              filled: true,
              fillColor: AppDesignSystem.backgroundSecondary,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide(color: AppDesignSystem.border),
              ),
            ),
            items: [
              DropdownMenuItem(
                value: '24h',
                child: Text(
                  l10n.translate('reminder24Hours') ?? '24 hours before',
                ),
              ),
            ],
            onChanged: (!_hasPhone || !_enabled) ? null : (_) {},
          ),
          if (_hasPhone) ...[
            const SizedBox(height: 10),
            TextButton.icon(
              onPressed: (_saving || _sendingTest) ? null : _sendTestSms,
              icon: _sendingTest
                  ? SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: widget.brand,
                      ),
                    )
                  : Icon(Icons.send_outlined, size: 16, color: widget.brand),
              label: Text(
                l10n.translate('smsSendTest') ?? 'Send test SMS now',
                style: TextStyle(color: widget.brand, fontWeight: FontWeight.w600),
              ),
            ),
          ],
          if (_saving || _sendingTest)
            const Padding(
              padding: EdgeInsets.only(top: 4),
              child: LinearProgressIndicator(minHeight: 2),
            ),
        ],
      ),
    );
  }
}