import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/schedule/presentation/setup_schedule_screen.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

// ============================ Calendar Screen ============================
class CalendarScreen extends StatefulWidget {
  const CalendarScreen({Key? key}) : super(key: key);

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  DateTime _focusedDay = DateTime.now();
  DateTime? _selectedDay;

  // Events by normalized date (yyyy-mm-dd @ 00:00:00)
  final Map<DateTime, List<CalendarEntry>> _entriesByDay = {};

  CalendarEntry? _selectedEntry;

  // --- Utils ---
  DateTime _dayKey(DateTime d) => DateTime(d.year, d.month, d.day);
  String _two(int n) => n.toString().padLeft(2, '0');

  // Create some demo data (you can replace this with real data from backend)
  void _ensureDemoFor(DateTime day) {
    final key = _dayKey(day);
    if (_entriesByDay.containsKey(key)) return;

    // Normalize day to midnight
    final dayNorm = DateTime(day.year, day.month, day.day);

    // Example like your mockups (with free slots in between).
    _entriesByDay[key] = [
      CalendarEntry.appointment(
        startTime: dayNorm.add(const Duration(hours: 9)),
        endTime: dayNorm.add(const Duration(hours: 9, minutes: 30)),
        patientName: 'Jasur Karimov',
        location: 'Tashkent Med Center',
        reason: 'Check Up',
      ),
      CalendarEntry.freeSlot(
        startTime: dayNorm.add(const Duration(hours: 9, minutes: 30)),
        endTime: dayNorm.add(const Duration(hours: 10)),
      ),
      CalendarEntry.appointment(
        startTime: dayNorm.add(const Duration(hours: 10)),
        endTime: dayNorm.add(const Duration(hours: 10, minutes: 30)),
        patientName: 'Gulnora Yusupova',
        location: 'Video Consultation',
        reason: 'Check Up',
      ),
      CalendarEntry.freeSlot(
        startTime: dayNorm.add(const Duration(hours: 10, minutes: 30)),
        endTime: dayNorm.add(const Duration(hours: 11)),
      ),
      CalendarEntry.appointment(
        startTime: dayNorm.add(const Duration(hours: 12)),
        endTime: dayNorm.add(const Duration(hours: 12, minutes: 30)),
        patientName: 'Ulugbek Tursunov',
        location: 'Video Consultation',
        reason: 'Check Up',
      ),
      CalendarEntry.appointment(
        startTime: dayNorm.add(const Duration(hours: 15)),
        endTime: dayNorm.add(const Duration(hours: 15, minutes: 30)),
        patientName: 'Dilshoda Rasulova',
        location: 'Video Consultation',
        reason: 'Check Up',
      ),
      CalendarEntry.appointment(
        startTime: dayNorm.add(const Duration(hours: 13, minutes: 30)),
        endTime: dayNorm.add(const Duration(hours: 14)),
        patientName: 'Shavkat Nematov',
        location: 'Tashkent Med Center',
        reason: 'Check Up',
      ),
    ];
  }

  List<CalendarEntry> _entriesFor(DateTime? day) {
    if (day == null) return [];
    return _entriesByDay[_dayKey(day)] ?? [];
  }

  bool get _hasFreeSlotsOnSelected {
    final items = _entriesFor(_selectedDay);
    return items.any((e) => e.type == EntryType.freeSlot);
  }

  @override
  void initState() {
    super.initState();
    // Preload a demo day similar to your screenshot (e.g., Jan 13, 2025)
    _ensureDemoFor(DateTime(2025, 1, 13));
  }

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);
    final dateLabel = _selectedDay == null
        ? null
        : '${_selectedDay!.day} ${_monthName(_selectedDay!.month)} ${_selectedDay!.year}';

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            // ---------------- Left: list & header ----------------
            Expanded(
              flex: 3,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Top bar: title + date chip + filters (placeholder)
                  Row(
                    children: [
                      const Text(
                        'Calendar',
                        style: TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(width: 12),
                      if (dateLabel != null)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(color: brand.withOpacity(0.4)),
                          ),
                          child: Text(
                            dateLabel!,
                            style: TextStyle(
                              color: brand,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      const Spacer(),
                      OutlinedButton.icon(
                        onPressed: () {
                          // TODO: filters
                        },
                        icon: const Icon(Icons.tune, size: 18),
                        label: const Text('Filters'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.black87,
                          side: BorderSide(color: Colors.grey.shade300),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(20),
                          ),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Content list
                  Expanded(
                    child: _selectedDay == null
                        ? _EmptyCalendarHint(brand: brand)
                        : _DayEntriesList(
                            entries: _entriesFor(_selectedDay),
                            onTap: (entry) {
                              setState(() => _selectedEntry = entry);
                            },
                            selected: _selectedEntry,
                            brand: brand,
                          ),
                  ),
                ],
              ),
            ),

            const SizedBox(width: 24),

            // ---------------- Right: calendar OR slot details ----------------
            Expanded(
              flex: 2,
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 250),
                switchInCurve: Curves.easeOut,
                switchOutCurve: Curves.easeIn,
                child: _selectedEntry == null
                    ? _CalendarPanel(
                        key: const ValueKey('calendar'),
                        focusedDay: _focusedDay,
                        selectedDay: _selectedDay,
                        onChanged: (d) {
                          setState(() {
                            _selectedDay = d;
                            _focusedDay = d;
                            _ensureDemoFor(d);
                            _selectedEntry = null; // ensure details are closed
                          });
                        },
                        showUpdateCard:
                            _selectedDay != null && !_hasFreeSlotsOnSelected,
                        onGoToSchedule: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => const SetupScheduleScreen(),
                            ),
                          );
                        },
                      )
                    : _SlotDetailsPanel(
                        key: const ValueKey('details'),
                        entry: _selectedEntry!,
                        day: _selectedDay!,
                        onSave: () {
                          // TODO: persist changes
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Saved')),
                          );
                        },
                        onDiscard: () {
                          setState(() => _selectedEntry = null);
                        },
                        onChangeDateTime: (newDay, newStart, newEnd) {
                          final oldKey = _dayKey(_selectedDay!);
                          final newKey = _dayKey(newDay);

                          setState(() {
                            // remove from old day
                            _entriesByDay[oldKey]?.remove(_selectedEntry);
                            // update entry with new times
                            _selectedEntry = _selectedEntry!.copyWith(
                              startTime: newStart,
                              endTime: newEnd,
                            );
                            // add to new day
                            _entriesByDay.putIfAbsent(newKey, () => []);
                            _entriesByDay[newKey]!.add(_selectedEntry!);
                            _selectedDay = newDay;
                          });
                        },
                        onChangeDuration: (minutes) {
                          final newEnd = _selectedEntry!.startTime
                              .add(Duration(minutes: minutes));
                          setState(() {
                            _selectedEntry = _selectedEntry!.copyWith(
                              endTime: newEnd,
                            );
                          });
                        },
                        onChangePlace: (place) {
                          if (_selectedEntry!.type != EntryType.appointment) {
                            return;
                          }
                          setState(() {
                            _selectedEntry = _selectedEntry!.copyWith(
                              location: place,
                            );
                          });
                        },
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _monthName(int m) {
    const months = [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ];
    return months[m - 1];
  }
}

// ---------------- Left: Empty state hint ----------------
class _EmptyCalendarHint extends StatelessWidget {
  const _EmptyCalendarHint({required this.brand, Key? key}) : super(key: key);
  final Color brand;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Illustration placeholder
          Container(
            width: 160,
            height: 160,
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(80),
              boxShadow: [
                BoxShadow(
                  color: Colors.black12,
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Icon(Icons.event_available, color: brand, size: 64),
          ),
          const SizedBox(height: 12),
          Text(
            'Select dates to see your schedule',
            style: TextStyle(color: Colors.grey.shade600),
          ),
        ],
      ),
    );
  }
}

// ---------------- Left: Day entries list ----------------
class _DayEntriesList extends StatelessWidget {
  const _DayEntriesList({
    Key? key,
    required this.entries,
    required this.onTap,
    required this.selected,
    required this.brand,
  }) : super(key: key);

  final List<CalendarEntry> entries;
  final ValueChanged<CalendarEntry> onTap;
  final CalendarEntry? selected;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    if (entries.isEmpty) {
      return Center(
        child: Text(
          'No items for this day',
          style: TextStyle(color: Colors.grey.shade600),
        ),
      );
    }
    return ListView.separated(
      itemCount: entries.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, i) {
        final e = entries[i];
        final isSelected = identical(e, selected);
        return Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected ? brand : Colors.transparent,
              width: isSelected ? 2 : 1,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 6,
            ),
            onTap: () => onTap(e),
            leading: e.type == EntryType.freeSlot
                ? CircleAvatar(
                    backgroundColor: Colors.white,
                    foregroundColor: brand,
                    child: const Icon(Icons.add),
                  )
                : CircleAvatar(
                    backgroundColor: Colors.grey.shade300,
                    foregroundColor: Colors.white,
                    child: const Icon(Icons.person),
                  ),
            title: e.type == EntryType.freeSlot
                ? const Text(
                    'Free Slot',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  )
                : Text(
                    e.patientName ?? 'Appointment',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
            subtitle: e.type == EntryType.freeSlot
                ? null
                : Row(
                    children: [
                      if ((e.location).toLowerCase().contains('video'))
                        const Icon(
                          Icons.videocam,
                          size: 14,
                          color: Colors.grey,
                        ),
                      if ((e.location).toLowerCase().contains('video'))
                        const SizedBox(width: 4),
                      Text(
                        e.location,
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey.shade600,
                        ),
                      ),
                    ],
                  ),
            trailing: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  e.timeRange,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                if (e.type == EntryType.appointment)
                  Text(
                    e.reason,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

// ---------------- Right: Calendar panel ----------------
class _CalendarPanel extends StatelessWidget {
  const _CalendarPanel({
    Key? key,
    required this.focusedDay,
    required this.selectedDay,
    required this.onChanged,
    required this.showUpdateCard,
    required this.onGoToSchedule,
  }) : super(key: key);

  final DateTime focusedDay;
  final DateTime? selectedDay;
  final ValueChanged<DateTime> onChanged;
  final bool showUpdateCard;
  final VoidCallback onGoToSchedule;

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);
    final year = focusedDay.year;

    return Column(
      key: key,
      children: [
        // Year label
        Align(
          alignment: Alignment.centerLeft,
          child: Text(
            '$year',
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
          ),
        ),
        const SizedBox(height: 12),

        // Calendar in a card
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black12.withOpacity(0.06),
                blurRadius: 12,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          padding: const EdgeInsets.all(8),
          child: CalendarDatePicker(
            initialDate: selectedDay ?? DateTime.now(),
            firstDate: DateTime(2020),
            lastDate: DateTime(2030),
            onDateChanged: onChanged,
          ),
        ),

        const SizedBox(height: 12),

        if (showUpdateCard) ...[
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              boxShadow: [
                BoxShadow(
                  color: Colors.black12.withOpacity(0.06),
                  blurRadius: 12,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: Row(
              children: [
                const Icon(Icons.info, color: Colors.amber),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Update schedule\nYour calendar does not provide booking slots this far ahead. Please update your schedule.',
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: onGoToSchedule,
              style: ElevatedButton.styleFrom(
                backgroundColor: brand,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text('Go To Schedule'),
            ),
          ),
        ],
      ],
    );
  }
}

// ---------------- Right: Slot details panel ----------------
class _SlotDetailsPanel extends StatelessWidget {
  const _SlotDetailsPanel({
    Key? key,
    required this.entry,
    required this.day,
    required this.onSave,
    required this.onDiscard,
    required this.onChangeDateTime,
    required this.onChangeDuration,
    required this.onChangePlace,
  }) : super(key: key);

  final CalendarEntry entry;
  final DateTime day;
  final VoidCallback onSave;
  final VoidCallback onDiscard;

  final void Function(DateTime newDay, DateTime newStart, DateTime newEnd)
      onChangeDateTime;
  final void Function(int minutes) onChangeDuration;
  final void Function(String place) onChangePlace;

  String _two(int n) => n.toString().padLeft(2, '0');
  String _fmtDate(DateTime d) =>
      '${_two(d.day)} ${_monthName(d.month)} ${d.year}';
  String _fmtTime(DateTime t) => '${_two(t.hour)}:${_two(t.minute)}';

  static String _monthName(int m) {
    const months = [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ];
    return months[m - 1];
  }

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);

    return Column(
      key: key,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Text(
            'Slot details',
            style: TextStyle(
              fontSize: 20,
              color: Colors.grey.shade800,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        const SizedBox(height: 12),

        Expanded(
          child: ListView(
            children: [
              // Patient (for free slot, this can be used to assign a patient)
              Card(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 0,
                color: Colors.white,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  leading: CircleAvatar(
                    backgroundColor: Colors.grey.shade300,
                    child: const Icon(Icons.person, color: Colors.white),
                  ),
                  title: Text(entry.patientName ?? 'Name Surname'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () {
                    // TODO: open patient selector
                  },
                ),
              ),

              const SizedBox(height: 10),

              // Date & Time
              Card(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 0,
                color: Colors.white,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  title: const Text(
                    'Date and Time',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    '${_fmtDate(day)},  ${_fmtTime(entry.startTime)} - ${_fmtTime(entry.endTime)}',
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    // Pick date
                    final newDay = await showDatePicker(
                      context: context,
                      initialDate: day,
                      firstDate: DateTime(2020),
                      lastDate: DateTime(2030),
                    );
                    if (newDay == null) return;

                    // Pick start time
                    final newStartTime = await showTimePicker(
                      context: context,
                      initialTime: TimeOfDay(
                        hour: entry.startTime.hour,
                        minute: entry.startTime.minute,
                      ),
                      builder: (ctx, child) {
                        return MediaQuery(
                          data: MediaQuery.of(ctx)
                              .copyWith(alwaysUse24HourFormat: true),
                          child: child!,
                        );
                      },
                    );
                    if (newStartTime == null) return;

                    // Pick end time
                    final newEndTime = await showTimePicker(
                      context: context,
                      initialTime: TimeOfDay(
                        hour: entry.endTime.hour,
                        minute: entry.endTime.minute,
                      ),
                      builder: (ctx, child) {
                        return MediaQuery(
                          data: MediaQuery.of(ctx)
                              .copyWith(alwaysUse24HourFormat: true),
                          child: child!,
                        );
                      },
                    );
                    if (newEndTime == null) return;

                    // Convert TimeOfDay to DateTime
                    final newDayNorm =
                        DateTime(newDay.year, newDay.month, newDay.day);
                    final newStart = newDayNorm.add(Duration(
                      hours: newStartTime.hour,
                      minutes: newStartTime.minute,
                    ));
                    final newEnd = newDayNorm.add(Duration(
                      hours: newEndTime.hour,
                      minutes: newEndTime.minute,
                    ));

                    onChangeDateTime(newDay, newStart, newEnd);
                  },
                ),
              ),

              const SizedBox(height: 10),

              // Duration
              Card(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 0,
                color: Colors.white,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  title: const Text(
                    'Duration',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    '${_duration(entry.startTime, entry.endTime)}',
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    final minutes = await _pickDuration(context);
                    if (minutes != null) onChangeDuration(minutes);
                  },
                ),
              ),

              const SizedBox(height: 10),

              // Place
              Card(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 0,
                color: Colors.white,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  title: const Text(
                    'Place',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    entry.location.isEmpty ? 'Clinic Address' : entry.location,
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    final place = await _editText(
                      context,
                      'Place',
                      entry.location.isEmpty
                          ? 'Clinic Address'
                          : entry.location,
                    );
                    if (place != null && place.trim().isNotEmpty) {
                      onChangePlace(place.trim());
                    }
                  },
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: onSave,
            style: ElevatedButton.styleFrom(
              backgroundColor: brand,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: const Text('Save'),
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: onDiscard,
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFE75656),
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              foregroundColor: Colors.white,
            ),
            child: const Text('Discard'),
          ),
        ),
      ],
    );
  }

  String _duration(DateTime s, DateTime e) {
    final d = e.difference(s).inMinutes.clamp(0, 24 * 60);
    final h = d ~/ 60;
    final m = d % 60;
    return h > 0 ? '${h}h ${m}m' : '${m}m';
  }

  Future<int?> _pickDuration(BuildContext context) async {
    final options = <int>[10, 15, 20, 30, 45, 60];
    return showModalBottomSheet<int>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView.separated(
          shrinkWrap: true,
          itemCount: options.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (_, i) => ListTile(
            title: Text('${options[i]} minutes'),
            onTap: () => Navigator.pop(ctx, options[i]),
          ),
        ),
      ),
    );
  }

  Future<String?> _editText(
    BuildContext context,
    String title,
    String initial,
  ) async {
    final controller = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(controller: controller, autofocus: true),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, controller.text),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }
}
