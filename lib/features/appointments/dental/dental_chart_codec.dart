import 'package:flutter/foundation.dart';

/// ISO 3950 / FDI two-digit tooth notation for form 025-2.
/// Quadrant digit: 1 = UR, 2 = UL, 3 = LL, 4 = LR. Tooth digit 1–8.
class DentalChartCodec {
  static const List<String> fdiUpperRightDisplay = ['18', '17', '16', '15', '14', '13', '12', '11'];
  static const List<String> fdiUpperLeftDisplay = ['21', '22', '23', '24', '25', '26', '27', '28'];
  static const List<String> fdiLowerRightDisplay = ['48', '47', '46', '45', '44', '43', '42', '41'];
  static const List<String> fdiLowerLeftDisplay = ['31', '32', '33', '34', '35', '36', '37', '38'];

  DentalChartCodec._();

  static const int cellsPerJawRow = 16;

  static int quadrantDigit(String q) {
    switch (q) {
      case 'UR':
        return 1;
      case 'UL':
        return 2;
      case 'LL':
        return 3;
      case 'LR':
        return 4;
      default:
        return 0;
    }
  }

  /// e.g. UR + 1 → "11", UR + 8 → "18"
  static String fdiKey(String quadrant, int toothNum) =>
      '${quadrantDigit(quadrant)}$toothNum';

  static final RegExp _legacyToothKey = RegExp(r'^(UR|UL|LR|LL)([1-8])$');

  /// `TOP_0_3`, `BOTTOM_1_12` — editable data rows only (not metadata).
  static final RegExp _editableRowDataKey = RegExp(r'^(TOP|BOTTOM)_(\d+)_(\d+)$');

  static bool isEditableRowDataKey(String key) =>
      _editableRowDataKey.hasMatch(key);

  static final RegExp _histRowCellKey = RegExp(r'^(TOP|BOTTOM)_HIST_(\d+)_(\d+)$');

  static final RegExp _histMetaKey =
      RegExp(r'^(TOP|BOTTOM)_HIST_(\d+)_(date|doctor)$');

  /// Value for a tooth cell: prefer FDI key, then legacy UR1-style.
  static String toothValue(Map<String, String> map, String quadrant, int toothNum) {
    final fdi = fdiKey(quadrant, toothNum);
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
    final rowMap = <int, List<String>>{};
    for (final e in chart.entries) {
      final key = e.key;
      if (!key.startsWith('${jaw}_')) continue;
      if (key.startsWith('${jaw}_HIST')) continue;
      final m = _editableRowDataKey.firstMatch(key);
      if (m == null || m.group(1) != jaw) continue;
      final rowIndex = int.tryParse(m.group(2) ?? '') ?? 0;
      final cellIndex = int.tryParse(m.group(3) ?? '') ?? 0;
      rowMap.putIfAbsent(rowIndex, () => List.filled(cellsPerJawRow, ''));
      if (cellIndex >= 0 && cellIndex < cellsPerJawRow) {
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
        if (ri < 0 || ci < 0 || ci >= cellsPerJawRow) continue;
        rowCells.putIfAbsent(ri, () => {});
        rowCells[ri]![ci] = e.value;
      }
    }

    final indices = rowCells.keys.toList()..sort();
    return [
      for (final i in indices)
        DentalHistRowSnapshot(
          sortIndex: i,
          cells: _cellsFromSparse(rowCells[i]!),
          dateIso: dates[i],
          doctor: doctors[i],
        ),
    ];
  }

  static List<String> _cellsFromSparse(Map<int, String> sparse) {
    final row = List<String>.filled(cellsPerJawRow, '');
    for (final e in sparse.entries) {
      if (e.key >= 0 && e.key < cellsPerJawRow) row[e.key] = e.value;
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
    final topEditable = parseEditableRows(migrated, 'TOP');
    final bottomEditable = parseEditableRows(migrated, 'BOTTOM');
    final out = Map<String, String>.from(migrated);
    out.removeWhere((k, _) => isEditableRowDataKey(k));

    var nextTop = nextHistRowIndex(out, 'TOP');
    for (final row in topEditable) {
      if (row.every((c) => c.trim().isEmpty)) continue;
      for (var c = 0; c < cellsPerJawRow; c++) {
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
      for (var c = 0; c < cellsPerJawRow; c++) {
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
