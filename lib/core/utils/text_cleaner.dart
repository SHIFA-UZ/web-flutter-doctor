/// Minimal normalizer for Shifa AI streamed text.
///
/// 1. Repairs missing spaces at word boundaries (streaming often merges tokens):
///    - lowercase letter + uppercase letter -> space between
///    - punctuation [.!?,;:] + letter (no space) -> space after punctuation
/// 2. Collapses multiple spaces, limits newlines, trims.
/// Does NOT split words or change apostrophes (e.g. Uzbek).
class TextCleaner {
  static String clean(String text) {
    if (text.isEmpty) return text;
    // Insert space at clear word boundaries (e.g. "causesHere" -> "causes Here")
    String out = text.replaceAllMapped(
      RegExp(r'([a-z])([A-Z])'),
      (m) => '${m[1]} ${m[2]}',
    );
    // Insert space after sentence-ending punctuation when next char is letter
    out = out.replaceAllMapped(
      RegExp(r'([.!?,;:])([A-Za-z])'),
      (m) => '${m[1]} ${m[2]}',
    );
    out = out
        .replaceAll(RegExp(r'[ \t]{2,}'), ' ')
        .replaceAll(RegExp(r'\n{3,}'), '\n\n')
        .trim();
    return out;
  }
}
