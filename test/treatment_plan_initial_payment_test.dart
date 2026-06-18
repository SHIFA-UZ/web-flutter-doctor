import 'package:flutter_test/flutter_test.dart';

/// Mirrors wizard balance math: plan total minus initial desk payment.
int balanceAfterInitialMinor(int planTotalMinor, int initialMinor) {
  if (initialMinor < 0) return planTotalMinor;
  final r = planTotalMinor - initialMinor;
  return r < 0 ? 0 : r;
}

void main() {
  test('balance after initial payment subtracts from plan total', () {
    expect(balanceAfterInitialMinor(1_000_000, 200_000), 800_000);
    expect(balanceAfterInitialMinor(500_000, 0), 500_000);
    expect(balanceAfterInitialMinor(100_000, 150_000), 0);
  });
}
