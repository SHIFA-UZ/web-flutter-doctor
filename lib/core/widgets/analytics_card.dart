import 'package:flutter/material.dart';

class AnalyticsCard extends StatelessWidget {
  final String title;
  final List<AnalyticsRow> rows;

  const AnalyticsCard({super.key, required this.title, required this.rows});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 12,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          ...rows.map((row) => _RowItem(row: row)),
        ],
      ),
    );
  }
}

class AnalyticsRow {
  final IconData icon;
  final String label;
  final String value;

  AnalyticsRow({required this.icon, required this.label, required this.value});
}

class _RowItem extends StatelessWidget {
  final AnalyticsRow row;

  const _RowItem({required this.row});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(row.icon, size: 18, color: Colors.teal),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              row.label,
              style: TextStyle(color: Colors.grey.shade700),
            ),
          ),
          Text(row.value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
