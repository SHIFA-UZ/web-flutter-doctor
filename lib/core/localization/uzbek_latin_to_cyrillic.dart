/// Uzbek (Latin) → Uzbek (Cyrillic) for UI strings.
///
/// Preserves `{placeholder}` and `{{placeholder}}` segments. Other text is
/// converted using standard Uzbek orthography mappings (including `o'→ў`, `g'→ғ`,
/// `sh→ш`, `ch→ч`, `ng→нг`).
String transliterateUzbekLatinToCyrillicUi(String input) {
  if (input.isEmpty) return input;
  final out = StringBuffer();
  final re = RegExp(r'\{\{[^}]+\}\}|\{[^}]+\}');
  var start = 0;
  for (final m in re.allMatches(input)) {
    out.write(_transliteratePlainLatinSegment(input.substring(start, m.start)));
    out.write(m.group(0));
    start = m.end;
  }
  out.write(_transliteratePlainLatinSegment(input.substring(start)));
  return out.toString();
}

bool _isApostrophe(String s, int i) {
  if (i < 0 || i >= s.length) return false;
  final c = s.codeUnitAt(i);
  return c == 0x27 || // '
      c == 0x2019 || // ’
      c == 0x02BC; // ʼ
}

String _mapDigraphCase(String twoLatin, String cyrillicLower) {
  assert(twoLatin.length == 2);
  final a = twoLatin.codeUnitAt(0);
  final b = twoLatin.codeUnitAt(1);
  final upA = a >= 65 && a <= 90 || a >= 192 && a <= 223;
  final upB = b >= 65 && b <= 90 || b >= 192 && b <= 223;
  final cy = cyrillicLower;
  if (upA && upB) return cy.toUpperCase();
  if (upA && !upB) return '${cy[0].toUpperCase()}${cy.substring(1)}';
  return cy;
}

String _mapSingleCase(String chLatin, String cyrillicLower) {
  final u = chLatin.codeUnitAt(0);
  final upper = u >= 65 && u <= 90 || u >= 192 && u <= 223;
  return upper ? cyrillicLower.toUpperCase() : cyrillicLower;
}

String _transliteratePlainLatinSegment(String s) {
  final sb = StringBuffer();
  var i = 0;
  while (i < s.length) {
    final unit = s.codeUnitAt(i);
    // Keep whitespace, digits, Cyrillic already present, symbols mostly as-is
    if (unit < 128 &&
        (unit <= 32 ||
            (unit >= 48 && unit <= 57) ||
            unit == 10 ||
            unit == 13)) {
      sb.writeCharCode(unit);
      i++;
      continue;
    }

    final rest = s.substring(i);

    // Multi-letter Uzbek Latin (longest match first)
    if (rest.length >= 2) {
      final two = rest.substring(0, 2);
      final tl = two.toLowerCase();
      if (tl == 'sh') {
        sb.write(_mapDigraphCase(two, 'ш'));
        i += 2;
        continue;
      }
      if (tl == 'ch') {
        sb.write(_mapDigraphCase(two, 'ч'));
        i += 2;
        continue;
      }
      if (tl == 'ng') {
        sb.write(_mapDigraphCase(two, 'нг'));
        i += 2;
        continue;
      }
      final low0 = String.fromCharCode(rest.codeUnitAt(0)).toLowerCase();
      if ((low0 == 'o' || low0 == 'g') && rest.length >= 2 && _isApostrophe(rest, 1)) {
        final letter = rest.substring(0, 1);
        sb.write(
          low0 == 'o'
              ? _mapSingleCase(letter, 'ў')
              : _mapSingleCase(letter, 'ғ'),
        );
        i += 2;
        continue;
      }
    }

    final ch = rest.substring(0, 1);
    final lc = ch.toLowerCase();
    switch (lc) {
      case 'a':
        sb.write(_mapSingleCase(ch, 'а'));
        break;
      case 'b':
        sb.write(_mapSingleCase(ch, 'б'));
        break;
      case 'd':
        sb.write(_mapSingleCase(ch, 'д'));
        break;
      case 'e':
        sb.write(_mapSingleCase(ch, 'е'));
        break;
      case 'f':
        sb.write(_mapSingleCase(ch, 'ф'));
        break;
      case 'g':
        sb.write(_mapSingleCase(ch, 'г'));
        break;
      case 'h':
        sb.write(_mapSingleCase(ch, 'ҳ'));
        break;
      case 'i':
        sb.write(_mapSingleCase(ch, 'и'));
        break;
      case 'j':
        sb.write(_mapSingleCase(ch, 'ж'));
        break;
      case 'k':
        sb.write(_mapSingleCase(ch, 'к'));
        break;
      case 'l':
        sb.write(_mapSingleCase(ch, 'л'));
        break;
      case 'm':
        sb.write(_mapSingleCase(ch, 'м'));
        break;
      case 'n':
        sb.write(_mapSingleCase(ch, 'н'));
        break;
      case 'o':
        sb.write(_mapSingleCase(ch, 'о'));
        break;
      case 'p':
        sb.write(_mapSingleCase(ch, 'п'));
        break;
      case 'q':
        sb.write(_mapSingleCase(ch, 'қ'));
        break;
      case 'r':
        sb.write(_mapSingleCase(ch, 'р'));
        break;
      case 's':
        sb.write(_mapSingleCase(ch, 'с'));
        break;
      case 't':
        sb.write(_mapSingleCase(ch, 'т'));
        break;
      case 'u':
        sb.write(_mapSingleCase(ch, 'у'));
        break;
      case 'v':
        sb.write(_mapSingleCase(ch, 'в'));
        break;
      case 'x':
        sb.write(_mapSingleCase(ch, 'х'));
        break;
      case 'y':
        sb.write(_mapSingleCase(ch, 'й'));
        break;
      case 'z':
        sb.write(_mapSingleCase(ch, 'з'));
        break;
      case 'c':
        sb.write(_mapSingleCase(ch, 'к'));
        break;
      case 'w':
        sb.write(_mapSingleCase(ch, 'в'));
        break;
      default:
        sb.write(ch);
        break;
    }
    i++;
  }
  return sb.toString();
}
