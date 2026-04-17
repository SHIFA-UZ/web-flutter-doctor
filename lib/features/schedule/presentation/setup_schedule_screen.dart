import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/schedule/domain/schedule_models.dart';

class SetupScheduleScreen extends StatefulWidget {
  const SetupScheduleScreen({super.key});

  @override
  State<SetupScheduleScreen> createState() => _SetupScheduleScreenState();
}

// ---- original helpers and state kept intact ----
class _SetupScheduleScreenState extends State<SetupScheduleScreen> {
  final Map<String, bool> selectedDays = {
    'Monday': false,
    'Tuesday': false,
    'Wednesday': false,
    'Thursday': false,
    'Friday': false,
    'Saturday': false,
  };

  final Map<String, List<TimeSlot>> dayTimeSlots = {
    'Monday': [],
    'Tuesday': [],
    'Wednesday': [],
    'Thursday': [],
    'Friday': [],
    'Saturday': [],
  };

  final Map<String, bool> expanded = {
    'Monday': false,
    'Tuesday': false,
    'Wednesday': false,
    'Thursday': false,
    'Friday': false,
    'Saturday': false,
  };

  Future<TimeOfDay?> _pickTime(TimeOfDay initial) {
    return showTimePicker(
      context: context,
      initialTime: initial,
      builder: (context, child) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
          child: child!,
        );
      },
    );
  }

  String _two(int n) => n.toString().padLeft(2, '0');
  String _fmt(TimeOfDay t) => '${_two(t.hour)}:${_two(t.minute)}';

  (TimeOfDay, TimeOfDay)? _parsePeriod(String period) {
    try {
      final parts = period.split('-');
      final s = parts[0].split(':');
      final e = parts[1].split(':');
      return (
        TimeOfDay(hour: int.parse(s[0]), minute: int.parse(s[1])),
        TimeOfDay(hour: int.parse(e[0]), minute: int.parse(e[1])),
      );
    } catch (_) {
      return null;
    }
  }

  bool _endAfterStart(TimeOfDay s, TimeOfDay e) {
    final sm = s.hour * 60 + s.minute;
    final em = e.hour * 60 + e.minute;
    return em > sm;
  }

  Future<void> _editPeriod(String day, int index) async {
    final slot = dayTimeSlots[day]![index];
    final parsed = _parsePeriod(slot.startTime);
    final now = TimeOfDay.now();
    final initialStart = parsed?.$1 ?? now;
    final initialEnd =
        parsed?.$2 ?? TimeOfDay(hour: (now.hour + 1) % 24, minute: now.minute);

    final start = await _pickTime(initialStart);
    if (start == null) return;
    final end = await _pickTime(initialEnd);
    if (end == null) return;
    if (!_endAfterStart(start, end)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('End time must be after start time.')),
      );
      return;
    }
    setState(() {
      slot.startTime = '${_fmt(start)}-${_fmt(end)}';
    });
  }

  Future<void> _editDuration(String day, int index) async {
    final durations = <String>[
      '00:10',
      '00:15',
      '00:20',
      '00:30',
      '00:45',
      '01:00',
    ];
    final selected = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView.separated(
          shrinkWrap: true,
          itemCount: durations.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, i) => ListTile(
            title: Text(durations[i]),
            onTap: () => Navigator.pop(context, durations[i]),
          ),
        ),
      ),
    );
    if (selected == null) return;
    setState(() {
      dayTimeSlots[day]![index].endTime = selected;
    });
  }

  void _addDefaultSlot(String day) {
    setState(() {
      dayTimeSlots[day]!.add(
        TimeSlot(startTime: '08:00-17:00', endTime: '00:30'),
      );
    });
  }

  void _removeSlot(String day, int index) {
    setState(() => dayTimeSlots[day]!.removeAt(index));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Text(
                'Setup your Schedule',
                style: TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Select which days you are working, and offering booking slots to your clients',
                style: TextStyle(fontSize: 14, color: Colors.grey.shade600),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              Expanded(
                child: SingleChildScrollView(
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      const cross = 2;
                      const spacing = 16.0;
                      final tileWidth =
                          (constraints.maxWidth - spacing * (cross - 1)) /
                          cross;
                      return Wrap(
                        spacing: spacing,
                        runSpacing: spacing,
                        children: selectedDays.entries.map((entry) {
                          final day = entry.key;
                          final isSelected = entry.value;
                          final isExpanded = expanded[day] ?? false;
                          return SizedBox(
                            width: tileWidth,
                            child: _buildDayCard(day, isSelected, isExpanded),
                          );
                        }).toList(),
                      );
                    },
                  ),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () =>
                      Navigator.pushReplacementNamed(context, AppRoutes.shell),
                  child: const Text(
                    'Complete',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDayCard(String day, bool isSelected, bool isExpanded) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isSelected ? const Color(0xFF17C3B2) : Colors.transparent,
          width: 2,
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(10),
            onTap: () {
              setState(() {
                selectedDays[day] = !isSelected;
                if (selectedDays[day] == true) {
                  expanded[day] = true;
                  if (dayTimeSlots[day]!.isEmpty) {
                    dayTimeSlots[day]!.add(
                      TimeSlot(startTime: '08:00-17:00', endTime: '00:30'),
                    );
                  }
                } else {
                  expanded[day] = false;
                  dayTimeSlots[day] = [];
                }
              });
            },
            child: Container(
              height: 48,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: [
                  Checkbox(
                    value: isSelected,
                    onChanged: (val) {
                      setState(() {
                        selectedDays[day] = val ?? false;
                        if (val == true) {
                          expanded[day] = true;
                          if (dayTimeSlots[day]!.isEmpty) {
                            dayTimeSlots[day]!.add(
                              TimeSlot(
                                startTime: '08:00-17:00',
                                endTime: '00:30',
                              ),
                            );
                          }
                        } else {
                          expanded[day] = false;
                          dayTimeSlots[day] = [];
                        }
                      });
                    },
                    activeColor: const Color(0xFF17C3B2),
                  ),
                  Text(
                    day,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const Spacer(),
                  IconButton(
                    icon: Icon(
                      isExpanded
                          ? Icons.keyboard_arrow_up
                          : Icons.keyboard_arrow_down,
                      color: Colors.grey.shade600,
                    ),
                    onPressed: selectedDays[day] == true
                        ? () => setState(() => expanded[day] = !isExpanded)
                        : null,
                  ),
                ],
              ),
            ),
          ),
          if (isSelected && isExpanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: _SlotEditorInline(
                day: day,
                slots: dayTimeSlots[day]!,
                onAdd: () => _addDefaultSlot(day),
                onEditPeriod: (i) => _editPeriod(day, i),
                onEditDuration: (i) => _editDuration(day, i),
                onDelete: (i) => _removeSlot(day, i),
              ),
            ),
        ],
      ),
    );
  }
}

class _SlotEditorInline extends StatelessWidget {
  final String day;
  final List<TimeSlot> slots;
  final VoidCallback onAdd;
  final void Function(int) onEditPeriod;
  final void Function(int) onEditDuration;
  final void Function(int) onDelete;

  const _SlotEditorInline({
    required this.day,
    required this.slots,
    required this.onAdd,
    required this.onEditPeriod,
    required this.onEditDuration,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF17C3B2).withOpacity(0.3)),
      ),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '$day slots',
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 220,
            child: slots.isEmpty
                ? const Center(child: Text('No slots yet'))
                : ListView.separated(
                    itemCount: slots.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final slot = slots[index];
                      return Card(
                        elevation: 0,
                        margin: EdgeInsets.zero,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                          side: BorderSide(color: Colors.grey.shade300),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Time Period',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                              const SizedBox(height: 8),
                              InkWell(
                                borderRadius: BorderRadius.circular(8),
                                onTap: () => onEditPeriod(index),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 12,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.white,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(
                                      color: Colors.grey.shade300,
                                    ),
                                  ),
                                  child: Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: const [
                                      // slot.startTime text
                                    ],
                                  ),
                                ),
                              ),
                              // Re-add visible value (left out above to keep code concise)
                              Padding(
                                padding: const EdgeInsets.only(
                                  top: 8.0,
                                  left: 4,
                                ),
                                child: Text(slot.startTime),
                              ),
                              const SizedBox(height: 12),
                              const Text(
                                'Slot timeframe',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                              const SizedBox(height: 8),
                              InkWell(
                                borderRadius: BorderRadius.circular(8),
                                onTap: () => onEditDuration(index),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 12,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.white,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(
                                      color: Colors.grey.shade300,
                                    ),
                                  ),
                                  child: Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: const [
                                      // slot.endTime text
                                    ],
                                  ),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.only(
                                  top: 8.0,
                                  left: 4,
                                ),
                                child: Text(slot.endTime),
                              ),
                              const SizedBox(height: 8),
                              Align(
                                alignment: Alignment.centerRight,
                                child: TextButton.icon(
                                  onPressed: () => onDelete(index),
                                  icon: const Icon(
                                    Icons.delete_outline,
                                    color: Colors.red,
                                  ),
                                  label: const Text(
                                    'Remove',
                                    style: TextStyle(color: Colors.red),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add, color: Color(0xFF17C3B2)),
              label: const Text(
                'Add',
                style: TextStyle(color: Color(0xFF17C3B2)),
              ),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Color(0xFF17C3B2)),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
