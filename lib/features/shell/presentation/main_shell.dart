import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/chat_screen.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/home_screen.dart';
import 'package:shifa_doc_app_v1/features/calendar/presentation/calendar_screen.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patients_screen.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/profile_screen.dart';

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _selectedIndex = 1; // Home by default
  final List<Widget> _screens = const [
    ChatScreen(),
    HomeScreen(),
    CalendarScreen(),
    PatientsScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
          // Side bar
          Container(
            width: 80,
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Color(0xFF17C3B2), Color(0xFF13A89E)],
              ),
            ),
            child: Column(
              children: [
                const SizedBox(height: 20),
                IconButton(
                  icon: const Icon(Icons.menu, color: Colors.white, size: 28),
                  onPressed: () {},
                ),
                const SizedBox(height: 40),
                _buildNavItem(Icons.chat_bubble_outline, 0),
                const SizedBox(height: 20),
                _buildNavItem(Icons.home_outlined, 1),
                const SizedBox(height: 20),
                _buildNavItem(Icons.calendar_today_outlined, 2),
                const SizedBox(height: 20),
                _buildNavItem(Icons.people_outline, 3),
                const Spacer(),
                _buildNavItem(Icons.person, 4),
                const SizedBox(height: 20),
              ],
            ),
          ),
          // Content
          Expanded(child: _screens[_selectedIndex]),
        ],
      ),
    );
  }

  Widget _buildNavItem(IconData icon, int index) {
    final isSelected = _selectedIndex == index;
    return InkWell(
      onTap: () => setState(() => _selectedIndex = index),
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(
          icon,
          color: isSelected ? const Color(0xFF17C3B2) : Colors.white,
          size: 28,
        ),
      ),
    );
  }
}
