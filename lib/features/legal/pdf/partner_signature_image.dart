// lib/features/legal/pdf/partner_signature_image.dart

import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/services.dart';

/// Renders a cursive partner signature PNG from the doctor's first and last name.
Future<Uint8List?> renderPartnerSignatureImage(String signatureText) async {
  final text = signatureText.trim();
  if (text.isEmpty) return null;

  try {
    final fontData = await rootBundle.load('assets/fonts/DancingScript-Regular.ttf');
    final loader = FontLoader('DancingScript')..addFont(Future.value(fontData));
    await loader.load();
  } catch (_) {
    return null;
  }

  const color = ui.Color(0xFF0D47A1);
  const fontSize = 38.0;
  const padH = 6.0;
  const padV = 4.0;

  final paragraphStyle = ui.ParagraphStyle(
    textAlign: ui.TextAlign.left,
    maxLines: 1,
  );
  final textStyle = ui.TextStyle(
    color: color,
    fontFamily: 'DancingScript',
    fontSize: fontSize,
  );

  final builder = ui.ParagraphBuilder(paragraphStyle)
    ..pushStyle(textStyle)
    ..addText(text);

  final paragraph = builder.build()
    ..layout(const ui.ParagraphConstraints(width: 280));

  final width = (paragraph.maxIntrinsicWidth + padH * 2).ceil().clamp(80, 320);
  final height = (paragraph.height + padV * 2 + 6).ceil().clamp(36, 80);

  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);

  canvas.drawParagraph(paragraph, ui.Offset(padH, padV));

  final lineY = padV + paragraph.height + 2;
  final linePaint = ui.Paint()
    ..color = const ui.Color(0xFF616161)
    ..strokeWidth = 1.2
    ..style = ui.PaintingStyle.stroke;
  canvas.drawLine(
    ui.Offset(padH, lineY),
    ui.Offset(width - padH, lineY),
    linePaint,
  );

  final picture = recorder.endRecording();
  final image = await picture.toImage(width, height);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  return bytes?.buffer.asUint8List();
}
