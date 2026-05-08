// lib/features/tasks/presentation/create_task_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/tasks/task_actions.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

/// Schedule mode for the create-task form.
/// - [evenSpacing]: simple `(startTime + intervalHours) × timesPerDay` schedule.
/// - [customTimes]: explicit list of HH:mm slot times — supports arbitrary
///   non-uniform medication patterns (e.g. "first 3 doses 2h apart, then
///   the last 2 doses 5h apart").
enum _ScheduleMode { evenSpacing, customTimes }

class CreateTaskScreen extends ConsumerStatefulWidget {
  final int? patientId;
  /// When set, form is pre-filled from this template (e.g. from "Use template").
  final TaskTemplate? template;

  const CreateTaskScreen({Key? key, this.patientId, this.template}) : super(key: key);

  @override
  ConsumerState<CreateTaskScreen> createState() => _CreateTaskScreenState();
}

class _CreateTaskScreenState extends ConsumerState<CreateTaskScreen> {
  final _formKey = GlobalKey<FormState>();
  final _taskNameController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _inputLabelController = TextEditingController();
  final _notesLabelController = TextEditingController();

  int? _selectedPatientId;
  TaskCategory _selectedCategory = TaskCategory.vital;
  TaskInputType _selectedInputType = TaskInputType.numeric;
  int _timesPerDay = 1;
  TimeOfDay _startTime = const TimeOfDay(hour: 8, minute: 0);
  // Hours between consecutive slots when timesPerDay >= 2. Range 1–24 to
  // cover common pharmacy schedules (every 4h / 6h / 8h / 12h / 24h).
  int _intervalHours = 1;
  DateTime _startDate = DateTime.now();
  DateTime? _endDate;
  int? _durationDays;
  bool _useEndDate = true;
  bool _notesRequired = false;
  bool _isSaving = false;

  /// Selectable interval values (in hours). Matches typical medication
  /// dosing schedules; the backend accepts any value 1–24 so this list
  /// can be expanded freely.
  static const List<int> _intervalOptions = [1, 2, 3, 4, 6, 8, 12, 24];

  /// Schedule mode. `evenSpacing` keeps the simple form (start time +
  /// interval + count). `customTimes` lets the doctor enter an explicit
  /// list of slot times to support arbitrary patterns like
  /// "08:00, 10:00, 12:00, 17:00, 22:00".
  _ScheduleMode _scheduleMode = _ScheduleMode.evenSpacing;
  final List<TimeOfDay> _customTimes = [const TimeOfDay(hour: 8, minute: 0)];

  @override
  void initState() {
    super.initState();
    _selectedPatientId = widget.patientId;
    final t = widget.template;
    if (t != null) {
      _taskNameController.text = t.taskName;
      _descriptionController.text = t.description ?? '';
      _inputLabelController.text = t.inputLabel ?? '';
      _notesLabelController.text = t.notesLabel ?? '';
      _selectedCategory = t.category;
      _selectedInputType = t.inputType;
      _timesPerDay = t.timesPerDay.clamp(1, 15);
      _startTime = t.startTime ?? const TimeOfDay(hour: 8, minute: 0);
      _intervalHours = (t.intervalHours ?? 1).clamp(1, 24);
      // Snap the loaded value to the nearest available option so the
      // dropdown can render it without throwing a "value not in items" error.
      if (!_intervalOptions.contains(_intervalHours)) {
        _intervalHours = _intervalOptions.firstWhere(
          (h) => h >= _intervalHours,
          orElse: () => _intervalOptions.last,
        );
      }
      // Populate custom-times mode if the template carries explicit slot
      // times. Otherwise leave the mode in `evenSpacing` with the default
      // single 08:00 entry so toggling to custom-times in the UI starts
      // from a sensible baseline.
      final templateCustom = t.customTimes;
      if (templateCustom != null && templateCustom.isNotEmpty) {
        _scheduleMode = _ScheduleMode.customTimes;
        _customTimes
          ..clear()
          ..addAll(templateCustom);
      }
      _notesRequired = t.notesRequired;
    }
  }

  /// Sort the in-memory custom-times list ascending so the editor and
  /// preview always show entries in chronological order.
  void _sortCustomTimes() {
    _customTimes.sort((a, b) {
      final am = a.hour * 60 + a.minute;
      final bm = b.hour * 60 + b.minute;
      return am.compareTo(bm);
    });
  }

  /// Format a [TimeOfDay] as "HH:mm" for the wire and for display in the
  /// editor (avoids relying on the device's locale 12/24h formatting).
  String _formatTime24(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  /// Compute the slot times that will be generated for the current
  /// `(startTime, intervalHours, timesPerDay)` configuration. Mirrors the
  /// backend logic in [RemoteCareTaskController.generateCheckIns] so the
  /// doctor sees an accurate preview as they tweak the values.
  List<TimeOfDay> _previewSlots() {
    if (_timesPerDay <= 1) return [_startTime];
    final startMin = _startTime.hour * 60 + _startTime.minute;
    final intervalMin = _intervalHours * 60;
    final result = <TimeOfDay>[];
    var min = startMin;
    var count = 0;
    while (count < _timesPerDay && min < 24 * 60) {
      result.add(TimeOfDay(hour: min ~/ 60, minute: min % 60));
      min += intervalMin;
      count++;
    }
    return result;
  }

  @override
  void dispose() {
    _taskNameController.dispose();
    _descriptionController.dispose();
    _inputLabelController.dispose();
    _notesLabelController.dispose();
    super.dispose();
  }

  Future<void> _saveTask() async {
    final l10n = AppLocalizations.of(context)!;
    if (!_formKey.currentState!.validate()) return;
    if (_selectedPatientId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.pleaseSelectPatient)),
      );
      return;
    }

    final isCustomMode = _scheduleMode == _ScheduleMode.customTimes;
    if (isCustomMode && _customTimes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('customTimesAddAtLeastOne') ??
                'Add at least one time slot',
          ),
        ),
      );
      return;
    }

    setState(() => _isSaving = true);

    try {
      final client = ref.read(apiClientProvider);
      // In custom-times mode the slot times override the start/interval/count
      // tuple. We still pass startTime so existing fallbacks (e.g. legacy
      // clients) have something sensible, and timesPerDay tracks the count.
      final customTimesPayload = isCustomMode
          ? (_customTimes.toList()..sort((a, b) {
              final am = a.hour * 60 + a.minute;
              final bm = b.hour * 60 + b.minute;
              return am.compareTo(bm);
            }))
          : null;
      final effectiveTimesPerDay = isCustomMode
          ? _customTimes.length.clamp(1, 15)
          : _timesPerDay;
      final effectiveStartTime = isCustomMode && customTimesPayload!.isNotEmpty
          ? customTimesPayload.first
          : _startTime;

      await createTaskWithClient(
        client: client,
        patientId: _selectedPatientId!,
        taskName: _taskNameController.text.trim(),
        description: _descriptionController.text.trim().isEmpty
            ? null
            : _descriptionController.text.trim(),
        category: _selectedCategory,
        timesPerDay: effectiveTimesPerDay,
        startTime: effectiveStartTime,
        intervalHours: isCustomMode
            ? null
            : (_timesPerDay >= 2 ? _intervalHours : null),
        customTimes: customTimesPayload,
        startDate: _startDate,
        endDate: _useEndDate ? _endDate : null,
        durationDays: _useEndDate ? null : _durationDays,
        inputType: _selectedInputType,
        inputLabel: _inputLabelController.text.trim().isEmpty
            ? null
            : _inputLabelController.text.trim(),
        notesRequired: _notesRequired,
        notesLabel: _notesLabelController.text.trim().isEmpty
            ? null
            : _notesLabelController.text.trim(),
      );

      // Refresh tasks list
      ref.read(tasksProvider.notifier).loadTasks();

      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.taskCreated)),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.failedToCreateTask}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final patients = ref.watch(patientsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.createRemoteCareTask),
        backgroundColor: brand,
        foregroundColor: Colors.white,
      ),
      body: Form(
        key: _formKey,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Patient Selection with Search
              _SearchablePatientDropdown(
                selectedPatientId: _selectedPatientId,
                patients: patients,
                onPatientSelected: (patientId) {
                  setState(() => _selectedPatientId = patientId);
                },
                validator: (value) =>
                    value == null ? l10n.pleaseSelectPatient : null,
              ),
              const SizedBox(height: 16),

              // Task Name
              TextFormField(
                controller: _taskNameController,
                decoration: InputDecoration(
                  labelText: '${l10n.taskName} *',
                  border: const OutlineInputBorder(),
                ),
                validator: (value) =>
                    value?.isEmpty ?? true ? l10n.pleaseEnterTaskName : null,
              ),
              const SizedBox(height: 16),

              // Description
              TextFormField(
                controller: _descriptionController,
                decoration: InputDecoration(
                  labelText: l10n.description,
                  border: const OutlineInputBorder(),
                  hintText: l10n.taskDescription,
                ),
                maxLines: 3,
              ),
              const SizedBox(height: 16),

              // Category
              DropdownButtonFormField<TaskCategory>(
                value: _selectedCategory,
                decoration: InputDecoration(
                  labelText: '${l10n.category} *',
                  border: const OutlineInputBorder(),
                ),
                items: TaskCategory.values.map((cat) {
                  String categoryName;
                  switch (cat) {
                    case TaskCategory.vital:
                      categoryName = l10n.vital;
                      break;
                    case TaskCategory.exercise:
                      categoryName = l10n.exercise;
                      break;
                    case TaskCategory.medication:
                      categoryName = l10n.medication;
                      break;
                    case TaskCategory.other:
                      categoryName = l10n.taskOther;
                      break;
                  }
                  return DropdownMenuItem(
                    value: cat,
                    child: Text(categoryName),
                  );
                }).toList(),
                onChanged: (value) =>
                    setState(() => _selectedCategory = value!),
              ),
              const SizedBox(height: 16),

              // Input Type
              DropdownButtonFormField<TaskInputType>(
                value: _selectedInputType,
                decoration: InputDecoration(
                  labelText: '${l10n.inputType} *',
                  border: const OutlineInputBorder(),
                ),
                items: TaskInputType.values.map((type) {
                  String typeName;
                  switch (type) {
                    case TaskInputType.numeric:
                      typeName = l10n.numeric;
                      break;
                    case TaskInputType.text:
                      typeName = l10n.text;
                      break;
                    case TaskInputType.boolean:
                      typeName = l10n.boolean;
                      break;
                  }
                  return DropdownMenuItem(
                    value: type,
                    child: Text(typeName),
                  );
                }).toList(),
                onChanged: (value) =>
                    setState(() => _selectedInputType = value!),
              ),
              const SizedBox(height: 16),

              // Input Label
              TextFormField(
                controller: _inputLabelController,
                decoration: InputDecoration(
                  labelText: l10n.inputLabel,
                  border: const OutlineInputBorder(),
                  hintText: l10n.enterInputLabel,
                ),
              ),
              const SizedBox(height: 16),

              // ── Schedule mode selector ──────────────────────────────
              // Even spacing: simple (start time + interval + count).
              // Custom times: explicit list of slots — supports any pattern.
              Text(
                l10n.translate('scheduleMode') ?? 'Schedule',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              SegmentedButton<_ScheduleMode>(
                segments: [
                  ButtonSegment(
                    value: _ScheduleMode.evenSpacing,
                    icon: const Icon(Icons.timer_outlined, size: 18),
                    label: Text(
                      l10n.translate('scheduleModeEvenSpacing') ??
                          'Even spacing',
                    ),
                  ),
                  ButtonSegment(
                    value: _ScheduleMode.customTimes,
                    icon: const Icon(Icons.schedule_outlined, size: 18),
                    label: Text(
                      l10n.translate('scheduleModeCustomTimes') ??
                          'Custom times',
                    ),
                  ),
                ],
                selected: {_scheduleMode},
                onSelectionChanged: (s) {
                  setState(() {
                    _scheduleMode = s.first;
                    if (_scheduleMode == _ScheduleMode.customTimes &&
                        _customTimes.isEmpty) {
                      // Seed with the current start time so the doctor has a
                      // sensible starting point when switching modes.
                      _customTimes.add(_startTime);
                    }
                  });
                },
              ),
              const SizedBox(height: 12),

              if (_scheduleMode == _ScheduleMode.evenSpacing) ...[
                // Times Per Day (1–15)
                Row(
                  children: [
                    Text('${l10n.timesPerDay}: '),
                    const SizedBox(width: 16),
                    DropdownButton<int>(
                      value: _timesPerDay,
                      items: List.generate(15, (i) => i + 1)
                          .map((n) => DropdownMenuItem(value: n, child: Text('$n')))
                          .toList(),
                      onChanged: (value) =>
                          setState(() => _timesPerDay = value!),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Start Time (first slot of the day). Subsequent slots are
                // [intervalHours] apart and fit until midnight on the same day.
                ListTile(
                  title: Text(l10n.startTime),
                  trailing: Text(_startTime.format(context)),
                  onTap: () async {
                    final time = await showTimePicker(
                      context: context,
                      initialTime: _startTime,
                    );
                    if (time != null) {
                      setState(() => _startTime = time);
                    }
                  },
                ),
                // When 2+ times per day: interval between tasks (1–24 hours).
                // Doctors picking large intervals + many slots may not fit
                // every slot in a single day; the live preview below shows
                // the actual times that will be generated so they can adjust.
                if (_timesPerDay >= 2) ...[
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${l10n.translate('intervalBetweenTasks') ?? 'Interval between tasks'}: ',
                        ),
                      ),
                      DropdownButton<int>(
                        value: _intervalHours,
                        items: _intervalOptions
                            .map((h) => DropdownMenuItem(
                                  value: h,
                                  child: Text(
                                    h == 1
                                        ? (l10n.translate('every1Hour') ??
                                            'Every 1 hour')
                                        : (l10n
                                                .translate('everyNHours')
                                                ?.replaceAll('%d', '$h') ??
                                            'Every $h hours'),
                                  ),
                                ))
                            .toList(),
                        onChanged: (value) =>
                            setState(() => _intervalHours = value!),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  _SlotPreview(
                    slots: _previewSlots(),
                    expectedCount: _timesPerDay,
                    l10n: l10n,
                  ),
                ],
              ] else ...[
                // ── Custom times editor ───────────────────────────────
                _CustomTimesEditor(
                  times: _customTimes,
                  formatTime: _formatTime24,
                  l10n: l10n,
                  onAdd: () async {
                    final picked = await showTimePicker(
                      context: context,
                      initialTime: _customTimes.isEmpty
                          ? const TimeOfDay(hour: 8, minute: 0)
                          : _customTimes.last,
                    );
                    if (picked == null) return;
                    setState(() {
                      // Avoid exact duplicates; otherwise add and sort.
                      final dup = _customTimes.any(
                        (t) => t.hour == picked.hour && t.minute == picked.minute,
                      );
                      if (!dup) _customTimes.add(picked);
                      _sortCustomTimes();
                    });
                  },
                  onEdit: (index) async {
                    final picked = await showTimePicker(
                      context: context,
                      initialTime: _customTimes[index],
                    );
                    if (picked == null) return;
                    setState(() {
                      _customTimes[index] = picked;
                      _sortCustomTimes();
                    });
                  },
                  onRemove: (index) {
                    setState(() {
                      if (_customTimes.length > 1) {
                        _customTimes.removeAt(index);
                      }
                    });
                  },
                ),
              ],
              const SizedBox(height: 16),

              // Start Date
              ListTile(
                title: Text('${l10n.startDate} *'),
                trailing: Text(
                  '${_startDate.year}-${_startDate.month.toString().padLeft(2, '0')}-${_startDate.day.toString().padLeft(2, '0')}',
                ),
                onTap: () async {
                  final date = await showDatePicker(
                    context: context,
                    initialDate: _startDate,
                    firstDate: DateTime.now(),
                    lastDate: DateTime.now().add(const Duration(days: 365)),
                  );
                  if (date != null) {
                    setState(() => _startDate = date);
                  }
                },
              ),
              const SizedBox(height: 16),

              // End Date or Duration
              Row(
                children: [
                  Checkbox(
                    value: _useEndDate,
                    onChanged: (value) => setState(() => _useEndDate = value!),
                  ),
                  Text(l10n.useEndDate),
                ],
              ),
              if (_useEndDate)
                ListTile(
                  title: Text(l10n.endDate),
                  trailing: _endDate != null
                      ? Text(
                          '${_endDate!.year}-${_endDate!.month.toString().padLeft(2, '0')}-${_endDate!.day.toString().padLeft(2, '0')}',
                        )
                      : Text(l10n.notSet),
                  onTap: () async {
                    final date = await showDatePicker(
                      context: context,
                      initialDate: _endDate ?? _startDate.add(const Duration(days: 30)),
                      firstDate: _startDate,
                      lastDate: DateTime.now().add(const Duration(days: 365)),
                    );
                    if (date != null) {
                      setState(() => _endDate = date);
                    }
                  },
                )
              else
                TextFormField(
                  decoration: InputDecoration(
                    labelText: l10n.durationDays,
                    border: const OutlineInputBorder(),
                  ),
                  keyboardType: TextInputType.number,
                  onChanged: (value) {
                    _durationDays = value.isEmpty ? null : int.tryParse(value);
                  },
                ),
              const SizedBox(height: 16),

              // Notes Required
              CheckboxListTile(
                title: Text(l10n.notesRequired),
                value: _notesRequired,
                onChanged: (value) => setState(() => _notesRequired = value!),
              ),
              const SizedBox(height: 8),

              // Notes Label
              TextFormField(
                controller: _notesLabelController,
                decoration: InputDecoration(
                  labelText: l10n.notesLabel,
                  border: const OutlineInputBorder(),
                  hintText: l10n.enterNotesLabel,
                ),
              ),
              const SizedBox(height: 24),

              // Save Button
              ShifaPrimaryButton(
                label: l10n.createRemoteCareTask,
                width: ButtonWidth.fill,
                isLoading: _isSaving,
                onPressed: _isSaving ? null : _saveTask,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// Searchable Patient Dropdown Widget
class _SearchablePatientDropdown extends StatefulWidget {
  final int? selectedPatientId;
  final List<Patient> patients;
  final ValueChanged<int?> onPatientSelected;
  final String? Function(int?)? validator;

  const _SearchablePatientDropdown({
    required this.selectedPatientId,
    required this.patients,
    required this.onPatientSelected,
    this.validator,
  });

  @override
  State<_SearchablePatientDropdown> createState() => _SearchablePatientDropdownState();
}

class _SearchablePatientDropdownState extends State<_SearchablePatientDropdown> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  String _getPatientDisplayText(Patient patient) {
    return '${patient.name} (ID: ${patient.id})';
  }

  String? _getSelectedPatientName() {
    if (widget.selectedPatientId == null) return null;
    try {
      final patient = widget.patients.firstWhere(
        (p) => int.parse(p.id) == widget.selectedPatientId,
      );
      return _getPatientDisplayText(patient);
    } catch (e) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        GestureDetector(
          onTap: () => _showPatientSearchDialog(context),
          child: AbsorbPointer(
            child: TextFormField(
              controller: TextEditingController(text: _getSelectedPatientName()),
                    decoration: InputDecoration(
                      labelText: '${AppLocalizations.of(context)!.patient} *',
                      border: const OutlineInputBorder(),
                      suffixIcon: const Icon(Icons.arrow_drop_down),
                      hintText: AppLocalizations.of(context)!.tapToSearch,
                    ),
              validator: widget.validator != null
                  ? (_) => widget.validator!(widget.selectedPatientId)
                  : null,
            ),
          ),
        ),
      ],
    );
  }

  void _showPatientSearchDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (dialogContext) => _PatientSearchDialog(
        patients: widget.patients,
        selectedPatientId: widget.selectedPatientId,
        onPatientSelected: (patientId) {
          widget.onPatientSelected(patientId);
          Navigator.pop(dialogContext);
        },
      ),
    );
  }
}

// Separate StatefulWidget for the search dialog to properly manage state
class _PatientSearchDialog extends StatefulWidget {
  final List<Patient> patients;
  final int? selectedPatientId;
  final ValueChanged<int> onPatientSelected;

  const _PatientSearchDialog({
    required this.patients,
    required this.selectedPatientId,
    required this.onPatientSelected,
  });

  @override
  State<_PatientSearchDialog> createState() => _PatientSearchDialogState();
}

class _PatientSearchDialogState extends State<_PatientSearchDialog> {
  final TextEditingController _searchController = TextEditingController();
  List<Patient> _filteredPatients = [];

  @override
  void initState() {
    super.initState();
    _filteredPatients = widget.patients;
    _searchController.addListener(_filterPatients);
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _filterPatients() {
    final query = _searchController.text.toLowerCase();
    setState(() {
      if (query.isEmpty) {
        _filteredPatients = widget.patients;
      } else {
        _filteredPatients = widget.patients.where((p) {
          final name = p.name.toLowerCase();
          final id = p.id.toLowerCase();
          return name.contains(query) || id.contains(query);
        }).toList();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Container(
        width: MediaQuery.of(context).size.width * 0.9,
        height: MediaQuery.of(context).size.height * 0.7,
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Search Field
            TextField(
              controller: _searchController,
                            decoration: InputDecoration(
                              labelText: AppLocalizations.of(context)!.searchByNameOrId,
                              prefixIcon: const Icon(Icons.search),
                              border: const OutlineInputBorder(),
                            ),
              autofocus: true,
            ),
            const SizedBox(height: 16),
            // Patient List
            Expanded(
              child: _filteredPatients.isEmpty
                  ? Center(
                      child: Text(AppLocalizations.of(context)!.noPatientsFound),
                    )
                  : ListView.builder(
                      itemCount: _filteredPatients.length,
                      itemBuilder: (context, index) {
                        final patient = _filteredPatients[index];
                        final patientId = int.parse(patient.id);
                        final isSelected = patientId == widget.selectedPatientId;

                        return ListTile(
                          title: Text(patient.name),
                          subtitle: Text('${AppLocalizations.of(context)!.id}: ${patient.id}'),
                          selected: isSelected,
                          selectedTileColor: Theme.of(context).colorScheme.primary.withOpacity(0.1),
                          trailing: isSelected
                              ? Icon(Icons.check, color: Theme.of(context).colorScheme.primary)
                              : null,
                          onTap: () {
                            widget.onPatientSelected(patientId);
                          },
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Tiny inline preview of the slot times that will be generated for the
/// task. Helps the doctor verify the (start time × interval × times-per-day)
/// combo before saving — large intervals can cause slots to be silently
/// dropped if they would cross midnight on the same day.
class _SlotPreview extends StatelessWidget {
  final List<TimeOfDay> slots;
  final int expectedCount;
  final AppLocalizations l10n;

  const _SlotPreview({
    required this.slots,
    required this.expectedCount,
    required this.l10n,
  });

  String _formatTime(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fits = slots.length >= expectedCount;
    final color = fits ? theme.colorScheme.primary : Colors.orange.shade800;
    final icon = fits ? Icons.event_available : Icons.warning_amber_rounded;
    final label = l10n.translate('slotsPreviewLabel') ?? 'Daily slots';
    final preview = slots.map(_formatTime).join(' · ');
    final fitMsg = fits
        ? null
        : (l10n.translate('slotsPreviewClipped')?.replaceAll(
                  '%d',
                  (expectedCount - slots.length).toString(),
                ) ??
            '${expectedCount - slots.length} slot(s) won\'t fit before midnight; '
                'pick a smaller interval or an earlier start time.');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$label: $preview',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: color,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (fitMsg != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    fitMsg,
                    style: theme.textTheme.bodySmall?.copyWith(color: color),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Editable list of explicit slot times. Doctors use this to build any
/// custom medication pattern (e.g. "08:00, 10:00, 12:00, 17:00, 22:00")
/// that the simple even-spacing form can't express.
class _CustomTimesEditor extends StatelessWidget {
  final List<TimeOfDay> times;
  final String Function(TimeOfDay) formatTime;
  final AppLocalizations l10n;
  final VoidCallback onAdd;
  final void Function(int index) onEdit;
  final void Function(int index) onRemove;

  const _CustomTimesEditor({
    required this.times,
    required this.formatTime,
    required this.l10n,
    required this.onAdd,
    required this.onEdit,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final addLabel =
        l10n.translate('customTimesAddSlot') ?? 'Add time slot';
    final emptyLabel =
        l10n.translate('customTimesEmpty') ?? 'No slots yet — add one below.';
    final hint = l10n.translate('customTimesHint') ??
        'Define each slot explicitly to support non-uniform schedules.';
    final countLabel = l10n.translate('customTimesCount') ??
        '%d slot(s) per day';

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey.shade300),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.translate('customTimesLabel') ?? 'Daily slot times',
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          Text(
            hint,
            style: theme.textTheme.bodySmall?.copyWith(
              color: Colors.grey.shade700,
            ),
          ),
          const SizedBox(height: 12),
          if (times.isEmpty)
            Text(
              emptyLabel,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: Colors.grey.shade600,
              ),
            )
          else
            ...List.generate(times.length, (index) {
              final t = times[index];
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primary.withValues(alpha: 0.12),
                        shape: BoxShape.circle,
                      ),
                      child: Text(
                        '${index + 1}',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: InkWell(
                        onTap: () => onEdit(index),
                        borderRadius: BorderRadius.circular(6),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 10,
                          ),
                          child: Text(
                            formatTime(t),
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontFeatures: const [FontFeature.tabularFigures()],
                            ),
                          ),
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: l10n.translate('edit') ?? 'Edit',
                      icon: const Icon(Icons.edit_outlined, size: 20),
                      onPressed: () => onEdit(index),
                    ),
                    IconButton(
                      tooltip: l10n.translate('remove') ?? 'Remove',
                      icon: Icon(
                        Icons.delete_outline,
                        size: 20,
                        color: times.length > 1
                            ? Colors.red.shade400
                            : Colors.grey.shade400,
                      ),
                      onPressed: times.length > 1 ? () => onRemove(index) : null,
                    ),
                  ],
                ),
              );
            }),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                countLabel.replaceAll('%d', '${times.length}'),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: Colors.grey.shade700,
                ),
              ),
              TextButton.icon(
                onPressed: onAdd,
                icon: const Icon(Icons.add, size: 18),
                label: Text(addLabel),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
