import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/widgets/ai_response_text.dart';

void main() {
  test('parseBlocks turns headings, bullets, and leftover paragraphs', () {
    const raw = '''
## Snapshot
Adult with recurrent caries.

## Video consultations
- Pain on **14**, plan restoration
- Follow-up in **1 week**
''';
    final blocks = AiResponseText.parseBlocks(raw);
    expect(
      blocks.where((b) => b.kind == AiTextBlockKind.heading).map((b) => b.text),
      ['Snapshot', 'Video consultations'],
    );
    expect(
      blocks.where((b) => b.kind == AiTextBlockKind.bullet).length,
      2,
    );
  });

  test('inlineSpans bolds marked phrases', () {
    const style = TextStyle();
    final spans = AiResponseText.inlineSpans('Caries on **14** and **15**.', style);
    expect(spans.length, 5);
    expect((spans[1] as TextSpan).text, '14');
    expect((spans[1] as TextSpan).style?.fontWeight, FontWeight.w700);
    expect((spans[3] as TextSpan).text, '15');
  });
}
