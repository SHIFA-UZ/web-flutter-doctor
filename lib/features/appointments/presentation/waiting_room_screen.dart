// lib/features/appointments/presentation/waiting_room_screen.dart
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';

class WaitingRoomScreen extends StatelessWidget {
  const WaitingRoomScreen({super.key, required this.appointment});
  final Appointment appointment;

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          'Waiting room',
          style: TextStyle(color: Colors.black),
        ),
        centerTitle: false,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            const SizedBox(height: 24),
            // Patient card
            Center(
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 56,
                    backgroundColor: Colors.grey.shade300,
                    child: Icon(
                      Icons.person,
                      color: Colors.grey.shade700,
                      size: 48,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    '${appointment.patientName} is waiting.',
                    style: TextStyle(
                      color: Colors.grey.shade800,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    "Open the room when you're ready to start the call",
                    style: TextStyle(color: Colors.grey.shade600),
                  ),
                ],
              ),
            ),
            const Spacer(),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.arrow_back),
                    label: const Text('Back'),
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: brand),
                      foregroundColor: brand,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.pushNamed(
                        context,
                        AppRoutes.videoCall,
                        arguments: appointment,
                      );
                    },
                    style: ElevatedButton.styleFrom(backgroundColor: brand),
                    child: const Text('Open Room'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
