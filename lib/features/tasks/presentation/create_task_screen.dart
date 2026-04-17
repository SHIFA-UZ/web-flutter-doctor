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
  int _intervalHours = 1; // used when timesPerDay >= 4: every 1, 2, or 3 hours
  DateTime _startDate = DateTime.now();
  DateTime? _endDate;
  int? _durationDays;
  bool _useEndDate = true;
  bool _notesRequired = false;
  bool _isSaving = false;

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
      _intervalHours = (t.intervalHours ?? 1).clamp(1, 3);
      _notesRequired = t.notesRequired;
    }
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

    setState(() => _isSaving = true);

    try {
      final client = ref.read(apiClientProvider);
      await createTaskWithClient(
        client: client,
        patientId: _selectedPatientId!,
        taskName: _taskNameController.text.trim(),
        description: _descriptionController.text.trim().isEmpty
            ? null
            : _descriptionController.text.trim(),
        category: _selectedCategory,
        timesPerDay: _timesPerDay,
        startTime: _startTime,
        intervalHours: _timesPerDay >= 2 ? _intervalHours : null,
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
                    onChanged: (value) => setState(() => _timesPerDay = value!),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Start Time (first slot; window is start to 8 PM)
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
              // When 2+ times per day: interval between tasks (every 1, 2, or 3 hours)
              if (_timesPerDay >= 2) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    Text('${l10n.translate('intervalBetweenTasks') ?? 'Interval between tasks'}: '),
                    const SizedBox(width: 8),
                    DropdownButton<int>(
                      value: _intervalHours,
                      items: [1, 2, 3]
                          .map((h) => DropdownMenuItem(
                                value: h,
                                child: Text(h == 1 ? (l10n.translate('every1Hour') ?? 'Every 1 hour') : (l10n.translate('everyNHours')?.replaceAll('%d', '$h') ?? 'Every $h hours')),
                              ))
                          .toList(),
                      onChanged: (value) => setState(() => _intervalHours = value!),
                    ),
                  ],
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
