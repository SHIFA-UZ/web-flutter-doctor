import 'package:flutter/material.dart';

class PersonAvatar extends StatelessWidget {
  final String name;
  final String? photoUrl;
  final double radius;

  const PersonAvatar({
    super.key,
    required this.name,
    this.photoUrl,
    this.radius = 20,
  });

  String get initials {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      return parts.first.isNotEmpty
          ? parts.first.substring(0, 1).toUpperCase()
          : '?';
    }
    final first = parts.first.isNotEmpty ? parts.first[0] : '?';
    final last = parts.last.isNotEmpty ? parts.last[0] : '?';
    return (first + last).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final hasUrl = photoUrl != null && photoUrl!.isNotEmpty;

    return CircleAvatar(
      radius: radius,
      backgroundColor: Colors.grey.shade300,
      backgroundImage: hasUrl ? NetworkImage(photoUrl!) : null,
      child: hasUrl
          ? null
          : Text(
              initials,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: radius * 0.9,
                color: Colors.grey.shade700,
              ),
            ),
    );
  }
}
