import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_chart_codec.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// FDI tooth diagram (Roman quadrants I–IV, anatomy labels, [DentalToothPainter] shapes).
/// Supports permanent (32 teeth) and primary/deciduous (20 teeth) dentition.
/// For visit documentation, each tooth is tappable and shows a service-count badge when applicable.
/// Visual state for read-only plan progress on the FDI chart.
enum DentalToothPlanState { none, planned, completed, partial }

class DentalFdiChart extends StatelessWidget {
  const DentalFdiChart({
    super.key,
    required this.brand,
    required this.toothServiceCounts,
    required this.onToothTap,
    this.showTitle = false,
    this.dentition = DentalDentition.permanent,
    this.onDentitionChanged,
    this.showDentitionToggle = true,
    this.toothPlanStates = const {},
  });

  final Color brand;
  final Map<String, int> toothServiceCounts;
  /// Callback receives canonical FDI key ("11", "51", …) or legacy compact key.
  final ValueChanged<String> onToothTap;
  final bool showTitle;
  final DentalDentition dentition;
  final ValueChanged<DentalDentition>? onDentitionChanged;
  final bool showDentitionToggle;
  /// Optional per-tooth plan progress (read-only treatment plan views).
  final Map<String, DentalToothPlanState> toothPlanStates;

  int _countFor(String fdi, String legacy) =>
      toothServiceCounts[fdi] ?? toothServiceCounts[legacy] ?? 0;

  DentalToothPlanState _planStateFor(String fdi, String legacy) =>
      toothPlanStates[fdi] ??
      toothPlanStates[legacy] ??
      DentalToothPlanState.none;

  @override
  Widget build(BuildContext context) {
    final themeBrand = Theme.of(context).colorScheme.primary;
    final paintBrand = brand;
    final l10n = AppLocalizations.of(context)!;
    final rowWidth = DentalChartCodec.quadrantRowWidth(dentition);
    final rightNums = DentalChartCodec.rightQuadrantToothNums(dentition);
    final leftNums = DentalChartCodec.leftQuadrantToothNums(dentition);

    Widget buildTooth(String quadrant, int toothNum, {required bool isUpper}) {
      final legacy = '$quadrant$toothNum';
      final fdi = DentalChartCodec.fdiKey(quadrant, toothNum, dentition: dentition);
      final count = _countFor(fdi, legacy);
      final has = count > 0;
      final planState = _planStateFor(fdi, legacy);
      final fillColor = switch (planState) {
        DentalToothPlanState.completed => paintBrand.withValues(alpha: 0.28),
        DentalToothPlanState.planned => paintBrand.withValues(alpha: 0.12),
        DentalToothPlanState.partial => Colors.orange.withValues(alpha: 0.22),
        DentalToothPlanState.none =>
          has ? paintBrand.withValues(alpha: 0.15) : Colors.grey.shade50,
      };
      final borderColor = switch (planState) {
        DentalToothPlanState.completed => paintBrand,
        DentalToothPlanState.planned => paintBrand.withValues(alpha: 0.55),
        DentalToothPlanState.partial => Colors.orange.shade700,
        DentalToothPlanState.none => has ? paintBrand : Colors.grey.shade400,
      };
      final borderWidth = has || planState != DentalToothPlanState.none ? 2.0 : 1.5;
      final isIncisor = toothNum == 1 || toothNum == 2;
      final isCanine = toothNum == 3;
      final isPremolar = dentition == DentalDentition.permanent && (toothNum == 4 || toothNum == 5);
      final isMolar = dentition == DentalDentition.primary
          ? toothNum >= 4
          : toothNum >= 6;

      return Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => onToothTap(fdi),
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
              color: fillColor,
              border: Border.all(
                color: borderColor,
                width: borderWidth,
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
                      fdi,
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

    String anatomyLabel(int num, {required bool isRightSide}) {
      if (dentition == DentalDentition.primary) {
        if (num == 1 || num == 2) return 'incisors';
        if (num == 3) return 'Canine';
        return 'molars';
      }
      if (num == 8) return 'wisdom';
      if (num >= 6) return 'molars';
      if (num >= 4) return 'pre-molars';
      if (num == 3) return 'Canine';
      return 'incisors';
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
                l10n.toothMap.toUpperCase(),
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: themeBrand,
                ),
              ),
              const SizedBox(height: 8),
            ],
            if (showDentitionToggle && onDentitionChanged != null) ...[
              Align(
                alignment: Alignment.center,
                child: SegmentedButton<DentalDentition>(
                  segments: [
                    ButtonSegment(
                      value: DentalDentition.permanent,
                      label: Text(l10n.translate('dentalDentitionPermanent')),
                    ),
                    ButtonSegment(
                      value: DentalDentition.primary,
                      label: Text(l10n.translate('dentalDentitionPrimary')),
                    ),
                  ],
                  selected: {dentition},
                  onSelectionChanged: (s) => onDentitionChanged!(s.first),
                ),
              ),
              const SizedBox(height: 12),
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
                        width: rowWidth,
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
                        width: rowWidth,
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
                      ...rightNums.map((num) => buildTooth('UR', num, isUpper: true)),
                      const SizedBox(width: 16),
                      ...leftNums.map((num) => buildTooth('UL', num, isUpper: true)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ...rightNums.map((num) {
                        return SizedBox(
                          width: 49,
                          child: Text(
                            anatomyLabel(num, isRightSide: true),
                            style: TextStyle(
                              fontSize: 8,
                              color: Colors.grey.shade600,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        );
                      }),
                      const SizedBox(width: 16),
                      ...leftNums.map((num) {
                        return SizedBox(
                          width: 49,
                          child: Text(
                            anatomyLabel(num, isRightSide: false),
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
                      ...rightNums.map((num) => buildTooth('LR', num, isUpper: false)),
                      const SizedBox(width: 16),
                      ...leftNums.map((num) => buildTooth('LL', num, isUpper: false)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                        width: rowWidth,
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
                        width: rowWidth,
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
