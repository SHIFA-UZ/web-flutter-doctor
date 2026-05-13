import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Same FDI tooth diagram as form 025-2 (Roman quadrants I–IV, anatomy labels, [DentalToothPainter] shapes).
/// For visit documentation, each tooth is tappable and shows a service-count badge when applicable.
class DentalFdiChart extends StatelessWidget {
  const DentalFdiChart({
    super.key,
    required this.brand,
    required this.toothServiceCounts,
    required this.onToothTap,
    this.showTitle = false,
  });

  final Color brand;
  final Map<String, int> toothServiceCounts;
  final ValueChanged<String> onToothTap;
  final bool showTitle;

  @override
  Widget build(BuildContext context) {
    final themeBrand = Theme.of(context).colorScheme.primary;
    final paintBrand = brand;

    Widget buildTooth(String quadrant, int toothNum, {required bool isUpper}) {
      final key = '$quadrant$toothNum';
      final count = toothServiceCounts[key] ?? 0;
      final has = count > 0;
      final isIncisor = toothNum == 1 || toothNum == 2;
      final isCanine = toothNum == 3;
      final isPremolar = toothNum == 4 || toothNum == 5;
      final isMolar = toothNum >= 6;

      final toothLabel = key;
      return Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => onToothTap(key),
          borderRadius: BorderRadius.only(
            topLeft: isUpper ? const Radius.circular(20) : const Radius.circular(4),
            topRight: isUpper ? const Radius.circular(20) : const Radius.circular(4),
            bottomLeft: isUpper ? const Radius.circular(4) : const Radius.circular(20),
            bottomRight: isUpper ? const Radius.circular(4) : const Radius.circular(20),
          ),
          child: Container(
            width: 45,
            height: 60,
            margin: const EdgeInsets.all(2),
            decoration: BoxDecoration(
              color: has ? paintBrand.withValues(alpha: 0.15) : Colors.grey.shade50,
              border: Border.all(
                color: has ? paintBrand : Colors.grey.shade400,
                width: has ? 2 : 1.5,
              ),
              borderRadius: BorderRadius.only(
                topLeft: isUpper ? const Radius.circular(20) : const Radius.circular(4),
                topRight: isUpper ? const Radius.circular(20) : const Radius.circular(4),
                bottomLeft: isUpper ? const Radius.circular(4) : const Radius.circular(20),
                bottomRight: isUpper ? const Radius.circular(4) : const Radius.circular(20),
              ),
            ),
            child: Stack(
              children: [
                Positioned.fill(
                  child: CustomPaint(
                    painter: DentalToothPainter(
                      isIncisor: isIncisor,
                      isCanine: isCanine,
                      isPremolar: isPremolar,
                      isMolar: isMolar,
                      isUpper: isUpper,
                      hasCondition: has,
                      color: paintBrand,
                    ),
                  ),
                ),
                Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      toothLabel,
                      style: TextStyle(
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                        color: has ? paintBrand : Colors.grey.shade600,
                      ),
                    ),
                    const SizedBox(height: 4),
                    if (has)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                        decoration: BoxDecoration(
                          color: paintBrand,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          '$count',
                          style: const TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w600,
                            color: Colors.white,
                          ),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      );
    }

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Container(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            if (showTitle) ...[
              Text(
                AppLocalizations.of(context)!.toothMap.toUpperCase(),
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: themeBrand,
                ),
              ),
              const SizedBox(height: 8),
            ],
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                        width: 49 * 8,
                        child: Text(
                          'I',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 16),
                      SizedBox(
                        width: 49 * 8,
                        child: Text(
                          'II',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ...[8, 7, 6, 5, 4, 3, 2, 1]
                          .map((num) => buildTooth('UR', num, isUpper: true)),
                      const SizedBox(width: 16),
                      ...[1, 2, 3, 4, 5, 6, 7, 8]
                          .map((num) => buildTooth('UL', num, isUpper: true)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ...[8, 7, 6, 5, 4, 3, 2, 1].map((num) {
                        String label;
                        if (num == 8) {
                          label = 'wisdom';
                        } else if (num >= 6) {
                          label = 'molars';
                        } else if (num >= 4) {
                          label = 'pre-molars';
                        } else if (num == 3) {
                          label = 'Canine';
                        } else {
                          label = 'incisors';
                        }
                        return SizedBox(
                          width: 49,
                          child: Text(
                            label,
                            style: TextStyle(
                              fontSize: 8,
                              color: Colors.grey.shade600,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        );
                      }),
                      const SizedBox(width: 16),
                      ...[1, 2, 3, 4, 5, 6, 7, 8].map((num) {
                        String label = 'incisors';
                        if (num == 1 || num == 2) {
                          label = 'incisors';
                        } else if (num == 3) {
                          label = 'Canine';
                        } else if (num == 4 || num == 5) {
                          label = 'pre-molars';
                        } else if (num >= 6) {
                          label = 'molars';
                        }
                        if (num == 8) label = 'wisdom';
                        return SizedBox(
                          width: 49,
                          child: Text(
                            label,
                            style: TextStyle(
                              fontSize: 8,
                              color: Colors.grey.shade600,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        );
                      }),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ...[8, 7, 6, 5, 4, 3, 2, 1]
                          .map((num) => buildTooth('LR', num, isUpper: false)),
                      const SizedBox(width: 16),
                      ...[1, 2, 3, 4, 5, 6, 7, 8]
                          .map((num) => buildTooth('LL', num, isUpper: false)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                        width: 49 * 8,
                        child: Text(
                          'IV',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 16),
                      SizedBox(
                        width: 49 * 8,
                        child: Text(
                          'III',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Colors.blue.shade700,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Shared with form 025-2 — same shapes as the dental chart in [PatientFormScreen].
class DentalToothPainter extends CustomPainter {
  DentalToothPainter({
    required this.isIncisor,
    required this.isCanine,
    required this.isPremolar,
    required this.isMolar,
    required this.isUpper,
    required this.hasCondition,
    required this.color,
  });

  final bool isIncisor;
  final bool isCanine;
  final bool isPremolar;
  final bool isMolar;
  final bool isUpper;
  final bool hasCondition;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = hasCondition ? color.withValues(alpha: 0.2) : Colors.grey.shade200
      ..style = PaintingStyle.fill;

    final borderPaint = Paint()
      ..color = hasCondition ? color : Colors.grey.shade300
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;

    final path = Path();

    if (isIncisor) {
      path.addRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(
            size.width * 0.2,
            size.height * 0.1,
            size.width * 0.6,
            size.height * 0.8,
          ),
          const Radius.circular(4),
        ),
      );
    } else if (isCanine) {
      path.moveTo(size.width * 0.5, size.height * 0.1);
      path.lineTo(size.width * 0.3, size.height * 0.5);
      path.lineTo(size.width * 0.2, size.height * 0.9);
      path.lineTo(size.width * 0.8, size.height * 0.9);
      path.lineTo(size.width * 0.7, size.height * 0.5);
      path.close();
    } else if (isPremolar) {
      path.addRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(
            size.width * 0.15,
            size.height * 0.1,
            size.width * 0.7,
            size.height * 0.8,
          ),
          const Radius.circular(6),
        ),
      );
    } else {
      path.addRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(
            size.width * 0.1,
            size.height * 0.1,
            size.width * 0.8,
            size.height * 0.8,
          ),
          const Radius.circular(8),
        ),
      );
    }

    canvas.drawPath(path, paint);
    canvas.drawPath(path, borderPaint);

    if (isUpper) {
      final rootPaint = Paint()
        ..color = Colors.grey.shade400
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1;

      if (isMolar) {
        canvas.drawLine(
          Offset(size.width * 0.3, size.height * 0.9),
          Offset(size.width * 0.3, size.height),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.5, size.height * 0.9),
          Offset(size.width * 0.5, size.height),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.7, size.height * 0.9),
          Offset(size.width * 0.7, size.height),
          rootPaint,
        );
      } else {
        canvas.drawLine(
          Offset(size.width * 0.4, size.height * 0.9),
          Offset(size.width * 0.4, size.height),
          rootPaint,
        );
        if (isPremolar) {
          canvas.drawLine(
            Offset(size.width * 0.6, size.height * 0.9),
            Offset(size.width * 0.6, size.height),
            rootPaint,
          );
        }
      }
    } else {
      final rootPaint = Paint()
        ..color = Colors.grey.shade400
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1;

      if (isMolar) {
        canvas.drawLine(
          Offset(size.width * 0.3, size.height * 0.1),
          Offset(size.width * 0.3, 0),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.5, size.height * 0.1),
          Offset(size.width * 0.5, 0),
          rootPaint,
        );
        canvas.drawLine(
          Offset(size.width * 0.7, size.height * 0.1),
          Offset(size.width * 0.7, 0),
          rootPaint,
        );
      } else {
        canvas.drawLine(
          Offset(size.width * 0.4, size.height * 0.1),
          Offset(size.width * 0.4, 0),
          rootPaint,
        );
        if (isPremolar) {
          canvas.drawLine(
            Offset(size.width * 0.6, size.height * 0.1),
            Offset(size.width * 0.6, 0),
            rootPaint,
          );
        }
      }
    }
  }

  @override
  bool shouldRepaint(covariant DentalToothPainter oldDelegate) =>
      oldDelegate.hasCondition != hasCondition ||
      oldDelegate.color != color ||
      oldDelegate.isUpper != isUpper ||
      oldDelegate.isIncisor != isIncisor ||
      oldDelegate.isCanine != isCanine ||
      oldDelegate.isPremolar != isPremolar ||
      oldDelegate.isMolar != isMolar;
}
