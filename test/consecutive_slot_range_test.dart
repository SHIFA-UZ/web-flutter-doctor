import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/consecutive_slot_range.dart';

CalendarEntry freeSlotUtc({
  required String startUtc,
  required String endUtc,
  int? locationId,
}) {
  return CalendarEntry.freeSlot(
    start: const TimeOfDay(hour: 0, minute: 0),
    end: const TimeOfDay(hour: 0, minute: 1),
    startAtUtc: startUtc,
    endAtUtc: endUtc,
    locationId: locationId,
  );
}

void main() {
  test('covers two consecutive hourly slots same venue', () {
    final s0 =
        freeSlotUtc(
          startUtc: '2026-06-01T07:00:00.000Z',
          endUtc: '2026-06-01T08:00:00.000Z',
          locationId: 1,
        );

    final s1 =
        freeSlotUtc(
          startUtc: '2026-06-01T08:00:00.000Z',
          endUtc: '2026-06-01T09:00:00.000Z',
          locationId: 1,
        );

    expect(
      coversUtcRangeWithFreeSlotChain(
        dayFreeSlots: [s0, s1],
        rangeStartInclusiveUtc: utcInstantFromBackendIso(s0.startAtUtc!),
        rangeEndExclusiveUtc: utcInstantFromBackendIso(s1.endAtUtc!),
        anchorVenueId: 1,
      ),
      isTrue,
    );
  });

  test('reject gap between slots', () {
    final s0 =
        freeSlotUtc(
          startUtc: '2026-06-01T07:00:00.000Z',
          endUtc: '2026-06-01T08:00:00.000Z',
          locationId: null,
        );

    final sSkip =
        freeSlotUtc(
          startUtc: '2026-06-01T09:00:00.000Z',
          endUtc: '2026-06-01T10:00:00.000Z',
          locationId: null,
        );

    expect(
      coversUtcRangeWithFreeSlotChain(
        dayFreeSlots: [s0, sSkip],
        rangeStartInclusiveUtc: utcInstantFromBackendIso(s0.startAtUtc!),
        rangeEndExclusiveUtc: utcInstantFromBackendIso(sSkip.endAtUtc!),
        anchorVenueId: null,
      ),
      isFalse,
    );
  });

  test('consecutive ends includes full chain', () {
    final slots = [
      freeSlotUtc(
        startUtc: '2026-06-01T07:00:00.000Z',
        endUtc: '2026-06-01T08:00:00.000Z',
        locationId: 5,
      ),

      freeSlotUtc(
        startUtc: '2026-06-01T08:00:00.000Z',
        endUtc: '2026-06-01T09:00:00.000Z',
        locationId: 5,
      ),
    ];

    final ends =
        consecutiveEndTimesForFreeSlot(
          dayEntries: slots,
          startSlot: slots.first,
          doctorTimeZone: 'UTC',
        );

    expect(ends.length, 2);
    expect(
      bookingSlotMinutesForRange(
        freeSlotStart: slots.first,
        endExclusiveWall: ends.last,
        calendarDay:
            DateTime.utc(2026, 06, 01),
        doctorTimeZone: 'UTC',
      ),
      120,
    );

  });


}
