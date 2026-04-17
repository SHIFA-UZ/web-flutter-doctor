import 'package:flutter/material.dart';

class TimeSlot {
  final TimeOfDay start;
  final TimeOfDay end;
  final Duration slotDuration;

  TimeSlot({
    required this.start,
    required this.end,
    required this.slotDuration,
  });
}
