// lib/features/appointments/presentation/video_call_screen.dart
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/app/router.dart';

class VideoCallScreen extends StatelessWidget {
  const VideoCallScreen({super.key, required this.appointment});
  final Appointment appointment;

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);

    final documents = const [
      _Doc(title: 'Blood test', date: '15.09.2025'),
      _Doc(title: 'Cancer screening results', date: '13.02.2025'),
    ];

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          appointment.patientName,
          style: const TextStyle(color: Colors.black),
        ),
        centerTitle: false,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            Expanded(
              child: Row(
                children: [
                  // Left: video canvas + controls
                  Expanded(
                    flex: 3,
                    child: Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // Video canvas (placeholder)
                          Expanded(
                            child: Container(
                              decoration: BoxDecoration(
                                color: Colors.black12,
                                borderRadius: BorderRadius.circular(12),
                                image: const DecorationImage(
                                  image: AssetImage(
                                    'assets/images/video_placeholder.jpg',
                                  ),
                                  fit: BoxFit.cover,
                                ),
                              ),
                              child: Align(
                                alignment: Alignment.bottomCenter,
                                child: _CallControls(),
                              ),
                            ),
                          ),
                          SizedBox(
                            width: double.infinity,
                            child: ElevatedButton(
                              onPressed: () {
                                Navigator.pushNamedAndRemoveUntil(
                                  context,
                                  AppRoutes.shell,
                                  (route) => false,
                                );
                              },
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFFE75656),
                              ),
                              child: const Text('End Appointment'),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(width: 24),

                  // Right: Documents + Notes
                  Expanded(
                    flex: 2,
                    child: Column(
                      children: [
                        // Documents
                        Expanded(
                          child: _CardBox(
                            title: 'Documents',
                            child: Column(
                              children: [
                                Expanded(
                                  child: ListView.separated(
                                    itemCount: documents.length,
                                    separatorBuilder: (_, __) =>
                                        const SizedBox(height: 8),
                                    itemBuilder: (_, i) =>
                                        _DocTile(documents[i], brand),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                SizedBox(
                                  width: double.infinity,
                                  child: ElevatedButton(
                                    onPressed: () {},
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: brand,
                                    ),
                                    child: const Text('Save'),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 24),
                        // Notes
                        Expanded(
                          child: _CardBox(
                            title: 'Notes',
                            child: Column(
                              children: [
                                Expanded(
                                  child: Container(
                                    decoration: BoxDecoration(
                                      color: Colors.white,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Row(
                                  children: [
                                    Expanded(
                                      child: TextField(
                                        decoration: InputDecoration(
                                          hintText: 'Type a note',
                                          border: OutlineInputBorder(
                                            borderRadius: BorderRadius.circular(
                                              12,
                                            ),
                                            borderSide: BorderSide(
                                              color: Colors.grey.shade300,
                                            ),
                                          ),
                                          prefixIcon: const Icon(Icons.edit),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    SizedBox(
                                      height: 44,
                                      child: ElevatedButton(
                                        onPressed: () {},
                                        child: const Icon(Icons.send),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// --- controls and helpers ---
class _CallControls extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final bg = Colors.black.withOpacity(0.35);
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: const [
          _RoundBtn(icon: Icons.videocam),
          SizedBox(width: 8),
          _RoundBtn(icon: Icons.mic),
          SizedBox(width: 8),
          _RoundBtn(icon: Icons.screen_share),
          SizedBox(width: 8),
          _RoundBtn(icon: Icons.more_horiz),
          SizedBox(width: 8),
          _RoundBtn(icon: Icons.call_end, color: Color(0xFFE75656)),
        ],
      ),
    );
  }
}

class _RoundBtn extends StatelessWidget {
  const _RoundBtn({required this.icon, this.color});
  final IconData icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return CircleAvatar(
      radius: 22,
      backgroundColor: Colors.white,
      child: Icon(icon, color: color ?? Colors.black87),
    );
  }
}

class _CardBox extends StatelessWidget {
  const _CardBox({required this.title, required this.child});
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 12),
          Expanded(child: child),
        ],
      ),
    );
  }
}

class _Doc {
  final String title;
  final String date;
  const _Doc({required this.title, required this.date});
}

class _DocTile extends StatelessWidget {
  const _DocTile(this.doc, this.brand);
  final _Doc doc;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  doc.title,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 4),
                Text(doc.date, style: TextStyle(color: Colors.grey.shade600)),
              ],
            ),
          ),
          IconButton.filledTonal(
            onPressed: () {},
            icon: const Icon(Icons.download),
            style: ButtonStyle(
              backgroundColor: WidgetStatePropertyAll(brand.withOpacity(0.15)),
              foregroundColor: WidgetStatePropertyAll(brand),
            ),
          ),
        ],
      ),
    );
  }
}
