// lib/features/appointments/presentation/waiting_room_screen.dart
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class WaitingRoomScreen extends StatelessWidget {
  const WaitingRoomScreen({super.key, required this.appointment});
  final Appointment appointment;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.cardboard,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          AppLocalizations.of(context)!.translate('waitingRoom') ?? 'Waiting room',
          style: const TextStyle(color: Colors.black),
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
                    '${appointment.patientName} ${AppLocalizations.of(context)!.translate('isWaiting') ?? 'is waiting'}.',
                    style: TextStyle(
                      color: Colors.grey.shade800,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    AppLocalizations.of(context)!.translate('openRoomWhenReady') ?? "Open the room when you're ready to start the call",
                    style: TextStyle(color: Colors.grey.shade600),
                  ),
                ],
              ),
            ),
            const Spacer(),
            Row(
              children: [
                Expanded(
                  child: ShifaSecondaryButton(
                    label: AppLocalizations.of(context)!.back,
                    onPressed: () => Navigator.pop(context),
                    icon: Icons.arrow_back,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ShifaPrimaryButton(
                    label: AppLocalizations.of(context)!.openRoom,
                    onPressed: () {
                      ShellScope.pushNamed(
                        context,
                        AppRoutes.videoCall,
                        arguments: appointment,
                      );
                    },
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
