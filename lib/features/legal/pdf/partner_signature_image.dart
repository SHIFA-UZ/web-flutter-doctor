// lib/features/legal/pdf/partner_signature_image.dart

import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';
import 'package:flutter/services.dart';

const _signatureFontFamily = 'Sacramento';

/// Renders a handwritten-style partner signature PNG from first/last name.
Future<Uint8List?> renderPartnerSignatureImage({
  String? firstName,
  String? lastName,
  String? inkText,
}) async {
  final text = inkText?.trim() ?? '';
  if (text.isEmpty) return null;

  final fontFamily = await _loadSignatureFontFamily();

  const inkColor = Color(0xFF0F172A);
  const fontSize = 52.0;
  const padH = 4.0;
  const padTop = 2.0;
  const padBottom = 14.0;

  final textSpan = TextSpan(
    text: text,
    style: TextStyle(
      fontFamily: fontFamily,
      fontSize: fontSize,
      color: inkColor,
      height: 0.95,
      letterSpacing: 0.6,
    ),
  );

  final painter = TextPainter(
    text: textSpan,
    textDirection: TextDirection.ltr,
    maxLines: 1,
  )..layout(maxWidth: 320);

  final width = (painter.width + padH * 2).ceil().clamp(120, 340);
  final height = (painter.height + padTop + padBottom).ceil().clamp(48, 96);

  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);

  canvas.save();
  canvas.translate(padH, padTop);
  canvas.skew(-0.12, 0);
  canvas.rotate(-0.04);
  painter.paint(canvas, Offset.zero);
  canvas.restore();

  _drawSignatureFlourish(
    canvas,
    width: width.toDouble(),
    baselineY: padTop + painter.height + 4,
  );

  final picture = recorder.endRecording();
  final image = await picture.toImage(width, height);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  return bytes?.buffer.asUint8List();
}

Future<String> _loadSignatureFontFamily() async {
  try {
    final fontData = await rootBundle.load('assets/fonts/Sacramento-Regular.ttf');
    final loader = FontLoader(_signatureFontFamily)..addFont(Future.value(fontData));
    await loader.load();
    return _signatureFontFamily;
  } catch (_) {
    final fontData = await rootBundle.load('assets/fonts/DancingScript-Regular.ttf');
    const fallback = 'DancingScript';
    final loader = FontLoader(fallback)..addFont(Future.value(fontData));
    await loader.load();
    return fallback;
  }
}

void _drawSignatureFlourish(
  Canvas canvas, {
  required double width,
  required double baselineY,
}) {
  final paint = Paint()
    ..color = const Color(0xFF334155)
    ..strokeWidth = 1.4
    ..style = PaintingStyle.stroke
    ..strokeCap = StrokeCap.round;

  final startX = 6.0;
  final endX = math.min(width - 8, startX + width * 0.72);

  final path = Path()
    ..moveTo(startX, baselineY)
    ..cubicTo(
      startX + (endX - startX) * 0.35,
      baselineY + 5,
      startX + (endX - startX) * 0.65,
      baselineY - 6,
      endX,
      baselineY - 1,
    );

  canvas.drawPath(path, paint);

  final tail = Path()
    ..moveTo(endX, baselineY - 1)
    ..quadraticBezierTo(
      endX + 12,
      baselineY - 10,
      endX + 22,
      baselineY + 2,
    );
  canvas.drawPath(tail, paint..strokeWidth = 1.1);
}
