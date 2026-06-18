import 'package:flutter/foundation.dart';

/// Permanent (adult) vs primary (deciduous) dentition for dental charts.
enum DentalDentition { permanent, primary }

/// ISO 3950 / FDI two-digit tooth notation for form 025-2.
/// Permanent quadrants: 1 = UR, 2 = UL, 3 = LL, 4 = LR (teeth 1–8).
/// Primary quadrants: 5 = UR, 6 = UL, 7 = LL, 8 = LR (teeth 1–5).
class DentalChartCodec {
  static const List<String> fdiUpperRightDisplay = ['18', '17', '16', '15', '14', '13', '12', '11'];
  static const List<String> fdiUpperLeftDisplay = ['21', '22', '23', '24', '25', '26', '27', '28'];
  static const List<String> fdiLowerRightDisplay = ['48', '47', '46', '45', '44', '43', '42', '41'];
  static const List<String> fdiLowerLeftDisplay = ['31', '32', '33', '34', '35', '36', '37', '38'];

  static const List<String> fdiPrimaryUpperRightDisplay = ['55', '54', '53', '52', '51'];
  static const List<String> fdiPrimaryUpperLeftDisplay = ['61', '62', '63', '64', '65'];
  static const List<String> fdiPrimaryLowerRightDisplay = ['85', '84', '83', '82', '81'];
  static const List<String> fdiPrimaryLowerLeftDisplay = ['71', '72', '73', '74', '75'];

  /// Stored in form 025-2 dental chart maps to remember adult vs child diagram.
  static const String dentitionMetaKey = '__dentition__';

  /// Appointment visit documentation: services not tied to a tooth.
  static const String generalServicesKey = '__general__';

  DentalChartCodec._();

  static const int cellsPerJawRow = 16;
  static const int primaryCellsPerJawRow = 10;

  static int cellsPerJawRowFor(DentalDentition dentition) =>
      dentition == DentalDentition.primary ? primaryCellsPerJawRow : cellsPerJawRow;

  static int teethPerQuadrant(DentalDentition dentition) =>
      dentition == DentalDentition.primary ? 5 : 8;

  static DentalDentition dentitionFromChart(Map<String, String> chart) {
    final raw = chart[dentitionMetaKey]?.trim().toLowerCase();
    if (raw == 'primary') return DentalDentition.primary;
    return DentalDentition.permanent;
  }

  static List<String> fdiUpperRightFor(DentalDentition d) =>
      d == DentalDentition.primary ? fdiPrimaryUpperRightDisplay : fdiUpperRightDisplay;

  static List<String> fdiUpperLeftFor(DentalDentition d) =>
      d == DentalDentition.primary ? fdiPrimaryUpperLeftDisplay : fdiUpperLeftDisplay;

  static List<String> fdiLowerRightFor(DentalDentition d) =>
      d == DentalDentition.primary ? fdiPrimaryLowerRightDisplay : fdiLowerRightDisplay;

  static List<String> fdiLowerLeftFor(DentalDentition d) =>
      d == DentalDentition.primary ? fdiPrimaryLowerLeftDisplay : fdiLowerLeftDisplay;

  /// All tooth keys for visit documentation (FDI strings), in chart display order.
  static List<String> visitDocTeethOrder(DentalDentition dentition) {
    if (dentition == DentalDentition.primary) {
      return [
        ...fdiPrimaryUpperRightDisplay,
        ...fdiPrimaryUpperLeftDisplay,
        ...fdiPrimaryLowerRightDisplay,
        ...fdiPrimaryLowerLeftDisplay,
      ];
    }
    return [
      ...fdiUpperRightDisplay,
      ...fdiUpperLeftDisplay,
      ...fdiLowerRightDisplay,
      ...fdiLowerLeftDisplay,
    ];
  }

  /// Tooth numbers 1..N within a quadrant for chart rendering (outer → inner on right side).
  static List<int> rightQuadrantToothNums(DentalDentition dentition) {
    final n = teethPerQuadrant(dentition);
    return [for (var i = n; i >= 1; i--) i];
  }

  static List<int> leftQuadrantToothNums(DentalDentition dentition) =>
      [for (var i = 1; i <= teethPerQuadrant(dentition); i++) i];

  static double quadrantRowWidth(DentalDentition dentition) =>
      49.0 * teethPerQuadrant(dentition);

  static int quadrantDigit(String q, {DentalDentition dentition = DentalDentition.permanent}) {
    final base = switch (q) {
      'UR' => 1,
      'UL' => 2,
      'LL' => 3,
      'LR' => 4,
      _ => 0,
    };
    if (base == 0) return 0;
    if (dentition == DentalDentition.primary) return base + 4;
    return base;
  }

  /// e.g. UR + 1 → "11" (permanent) or "51" (primary); UR + 8 → "18"
  static String fdiKey(
    String quadrant,
    int toothNum, {
    DentalDentition dentition = DentalDentition.permanent,
  }) =>
      '${quadrantDigit(quadrant, dentition: dentition)}$toothNum';

  static final RegExp _fdiToothKey = RegExp(r'^[1-8][1-8]$');

  /// Converts compact legacy UR8 / spaced UR 8 to ISO FDI display ("18").
  /// Already-FDI keys and [generalServicesKey] pass through unchanged.
  static String toFdiDisplay(String compactOrSpaced, {DentalDentition? dentitionHint}) {
    final s = compactOrSpaced.replaceAll(' ', '');
    if (s == generalServicesKey) return s;
    if (_fdiToothKey.hasMatch(s)) return s;
    final m = _legacyToothKey.firstMatch(s);
    if (m == null) return compactOrSpaced.replaceAll(' ', '');
    final quad = m.group(1)!;
    final n = int.tryParse(m.group(2) ?? '') ?? 0;
    if (n < 1) return s;
    final d = dentitionHint ?? DentalDentition.permanent;
    final digit = quadrantDigit(quad, dentition: d);
    if (digit == 0) return s;
    return '$digit$n';
  }

  /// Resolves any stored tooth key to canonical FDI for maps keyed by ISO codes.
  static String normalizeToothKey(String key, {DentalDentition dentition = DentalDentition.permanent}) {
    final s = key.replaceAll(' ', '');
    if (s == generalServicesKey) return s;
    if (_fdiToothKey.hasMatch(s)) return s;
    final m = _legacyToothKey.firstMatch(s);
    if (m == null) return s;
    return fdiKey(m.group(1)!, int.parse(m.group(2)!), dentition: dentition);
  }

  static final RegExp _legacyToothKey = RegExp(r'^(UR|UL|LR|LL)([1-8])$');

  /// `TOP_0_3`, `BOTTOM_1_12` — editable data rows only (not metadata).
  static final RegExp _editableRowDataKey = RegExp(r'^(TOP|BOTTOM)_(\d+)_(\d+)$');

  static bool isEditableRowDataKey(String key) =>
      _editableRowDataKey.hasMatch(key);

  static final RegExp _histRowCellKey = RegExp(r'^(TOP|BOTTOM)_HIST_(\d+)_(\d+)$');

  static final RegExp _histMetaKey =
      RegExp(r'^(TOP|BOTTOM)_HIST_(\d+)_(date|doctor)$');

  /// Value for a tooth cell: prefer FDI key, then legacy UR1-style.
  static String toothValue(
    Map<String, String> map,
    String quadrant,
    int toothNum, {
    DentalDentition dentition = DentalDentition.permanent,
  }) {
    final fdi = fdiKey(quadrant, toothNum, dentition: dentition);
    return map[fdi] ?? map['$quadrant$toothNum'] ?? '';
  }

  /// Copies [source] and maps legacy per-tooth keys (UR1 …) to FDI ("11" …).
  /// Preserves row keys, history keys, and existing FDI keys.
  static Map<String, String> migrateLegacyToothKeys(Map<String, String> source) {
    final out = <String, String>{};
    for (final e in source.entries) {
      final k = e.key;
      final m = _legacyToothKey.firstMatch(k);
      if (m == null) {
        out[k] = e.value;
        continue;
      }
      final quad = m.group(1)!;
      final n = int.tryParse(m.group(2) ?? '') ?? 0;
      if (n < 1 || n > 8) continue;
      final fdi = fdiKey(quad, n);
      out.putIfAbsent(fdi, () => e.value);
    }
    return out;
  }

  static List<List<String>> parseEditableRows(Map<String, String> chart, String jaw) {
    // jaw: 'TOP' or 'BOTTOM'
    final cells = cellsPerJawRowFor(dentitionFromChart(chart));
    final rowMap = <int, List<String>>{};
    for (final e in chart.entries) {
      final key = e.key;
      if (!key.startsWith('${jaw}_')) continue;
      if (key.startsWith('${jaw}_HIST')) continue;
      final m = _editableRowDataKey.firstMatch(key);
      if (m == null || m.group(1) != jaw) continue;
      final rowIndex = int.tryParse(m.group(2) ?? '') ?? 0;
      final cellIndex = int.tryParse(m.group(3) ?? '') ?? 0;
      rowMap.putIfAbsent(rowIndex, () => List.filled(cells, ''));
      if (cellIndex >= 0 && cellIndex < cells) {
        rowMap[rowIndex]![cellIndex] = e.value;
      }
    }
    if (rowMap.isEmpty) return [];
    final sorted = rowMap.keys.toList()..sort();
    return sorted.map((i) => List<String>.from(rowMap[i]!)).toList();
  }

  static List<DentalHistRowSnapshot> parseHistRows(
    Map<String, String> chart, {
    required bool isTop,
  }) {
    final jaw = isTop ? 'TOP' : 'BOTTOM';
    final cells = cellsPerJawRowFor(dentitionFromChart(chart));
    final rowCells = <int, Map<int, String>>{};
    final dates = <int, String>{};
    final doctors = <int, String>{};

    for (final e in chart.entries) {
      final key = e.key;
      if (!key.startsWith('${jaw}_HIST_')) continue;
      final meta = _histMetaKey.firstMatch(key);
      if (meta != null && meta.group(1) == jaw) {
        final ri = int.tryParse(meta.group(2) ?? '') ?? -1;
        if (ri < 0) continue;
        final field = meta.group(3)!;
        if (field == 'date') {
          dates[ri] = e.value;
        } else {
          doctors[ri] = e.value;
        }
        continue;
      }
      final cell = _histRowCellKey.firstMatch(key);
      if (cell != null && cell.group(1) == jaw) {
        final ri = int.tryParse(cell.group(2) ?? '') ?? -1;
        final ci = int.tryParse(cell.group(3) ?? '') ?? -1;
        if (ri < 0 || ci < 0 || ci >= cells) continue;
        rowCells.putIfAbsent(ri, () => {});
        rowCells[ri]![ci] = e.value;
      }
    }

    final indices = rowCells.keys.toList()..sort();
    return [
      for (final i in indices)
        DentalHistRowSnapshot(
          sortIndex: i,
          cells: _cellsFromSparse(rowCells[i]!, cells),
          dateIso: dates[i],
          doctor: doctors[i],
        ),
    ];
  }

  static List<String> _cellsFromSparse(Map<int, String> sparse, int cells) {
    final row = List<String>.filled(cells, '');
    for (final e in sparse.entries) {
      if (e.key >= 0 && e.key < cells) row[e.key] = e.value;
    }
    return row;
  }

  /// Next free history row index for [jaw] (`TOP` / `BOTTOM`).
  static int nextHistRowIndex(Map<String, String> chart, String jaw) {
    var maxV = -1;
    for (final k in chart.keys) {
      if (!k.startsWith('${jaw}_HIST_')) continue;
      final meta = _histMetaKey.firstMatch(k);
      if (meta != null && meta.group(1) == jaw) {
        final ri = int.tryParse(meta.group(2) ?? '') ?? -1;
        if (ri > maxV) maxV = ri;
        continue;
      }
      final cell = _histRowCellKey.firstMatch(k);
      if (cell != null && cell.group(1) == jaw) {
        final ri = int.tryParse(cell.group(2) ?? '') ?? -1;
        if (ri > maxV) maxV = ri;
      }
    }
    return maxV + 1;
  }

  /// When opening a **new** 025-2 form: carry tooth diagram + history forward,
  /// move the latest form's editable rows into new history rows ([visitDate], [visitDoctor]).
  static Map<String, String> prefillFromLatest0252({
    required Map<String, String> latestChart,
    required DateTime visitDate,
    required String visitDoctor,
  }) {
    final migrated = migrateLegacyToothKeys(latestChart);
    final cells = cellsPerJawRowFor(dentitionFromChart(migrated));
    final topEditable = parseEditableRows(migrated, 'TOP');
    final bottomEditable = parseEditableRows(migrated, 'BOTTOM');
    final out = Map<String, String>.from(migrated);
    out.removeWhere((k, _) => isEditableRowDataKey(k));

    var nextTop = nextHistRowIndex(out, 'TOP');
    for (final row in topEditable) {
      if (row.every((c) => c.trim().isEmpty)) continue;
      for (var c = 0; c < cells; c++) {
        final v = row[c].trim();
        if (v.isNotEmpty) out['TOP_HIST_${nextTop}_$c'] = v;
      }
      out['TOP_HIST_${nextTop}_date'] = _formatIsoDate(visitDate);
      out['TOP_HIST_${nextTop}_doctor'] = visitDoctor;
      nextTop++;
    }

    var nextBot = nextHistRowIndex(out, 'BOTTOM');
    for (final row in bottomEditable) {
      if (row.every((c) => c.trim().isEmpty)) continue;
      for (var c = 0; c < cells; c++) {
        final v = row[c].trim();
        if (v.isNotEmpty) out['BOTTOM_HIST_${nextBot}_$c'] = v;
      }
      out['BOTTOM_HIST_${nextBot}_date'] = _formatIsoDate(visitDate);
      out['BOTTOM_HIST_${nextBot}_doctor'] = visitDoctor;
      nextBot++;
    }

    return out;
  }

  static String _formatIsoDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  /// For PDF / debug: parse ISO `YYYY-MM-DD` to dd.MM.yyyy
  static String formatHistDateForDisplay(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final parts = iso.split('-');
    if (parts.length == 3) {
      return '${parts[2].padLeft(2, '0')}.${parts[1].padLeft(2, '0')}.${parts[0]}';
    }
    return iso;
  }
}

@immutable
class DentalHistRowSnapshot {
  const DentalHistRowSnapshot({
    required this.sortIndex,
    required this.cells,
    this.dateIso,
    this.doctor,
  });

  final int sortIndex;
  final List<String> cells;
  final String? dateIso;
  final String? doctor;
}
