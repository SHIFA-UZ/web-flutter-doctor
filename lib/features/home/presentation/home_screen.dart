import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/calendar/data/calendar_repository_http.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final calRepo = CalendarRepositoryHttp();
  List<CalendarEntry> entries = [];
  int? _selectedIndex;

  @override
  void initState() {
    super.initState();
    _loadToday();
  }

  Future<void> _loadToday() async {
    final e = await calRepo.entriesFor(DateTime.now());
    if (mounted) setState(() => entries = e);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Today',
              style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: ListView.separated(
                itemCount: entries.length,
                separatorBuilder: (_, __) => const SizedBox(height: 12),
                itemBuilder: (_, i) {
                  final e = entries[i];
                  final selected = _selectedIndex == i;
                  return _EntryTile(
                    entry: e,
                    selected: selected,
                    onTap: () => setState(() => _selectedIndex = i),
                    onStart: () {
                      // Convert CalendarEntry to Appointment for navigation
                      final appt = Appointment(
                        id: 'temp-${e.startTime.millisecondsSinceEpoch}',
                        patientName: e.patientName ?? 'Patient',
                        location: e.location.isEmpty
                            ? 'Video Consultation'
                            : e.location,
                        startTime: e.startTime,
                        endTime: e.endTime,
                      );
                      if (appt.isVideo) {
                        Navigator.pushNamed(
                          context,
                          AppRoutes.waitingRoom,
                          arguments: appt,
                        );
                      } else {
                        Navigator.pushNamed(
                          context,
                          AppRoutes.inPerson,
                          arguments: appt,
                        );
                      }
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

class _EntryTile extends StatelessWidget {
  const _EntryTile({
    required this.entry,
    required this.selected,
    required this.onTap,
    required this.onStart,
  });

  final CalendarEntry entry;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);
    final isAppt = entry.type == EntryType.appointment;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.grey.shade50,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: selected ? brand : Colors.grey.shade200,
            width: selected ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            Icon(
              isAppt ? Icons.event_available : Icons.event_busy,
              color: isAppt ? brand : Colors.grey,
              size: 22,
            ),
            const SizedBox(width: 12),
            Text(
              entry.timeRange,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                isAppt ? (entry.patientName ?? 'Appointment') : 'Free slot',
                style: TextStyle(color: Colors.grey.shade700),
              ),
            ),
            if (selected)
              ElevatedButton(
                onPressed: onStart,
                style: ElevatedButton.styleFrom(
                  backgroundColor: brand,
                ),
                child: const Text('Start'),
              ),
          ],
        ),
      ),
    );
  }
}
