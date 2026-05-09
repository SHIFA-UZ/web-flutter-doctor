// ignore_for_file: avoid_web_libraries_in_flutter

import 'dart:async';
import 'dart:html' as html;
import 'dart:typed_data';
import 'dart:ui_web' as ui_web;

import 'consultation_dropped_file.dart';

/// Web-only: transparent platform view that catches drag-and-drop and clicks (browse).
String? consultationRegisterDropView({
  required void Function(List<ConsultationDroppedFile> files) onDropped,
  required void Function() onBrowseTap,
}) {
  final viewType =
      'consultation-doc-drop-${DateTime.now().microsecondsSinceEpoch}';
  ui_web.platformViewRegistry.registerViewFactory(viewType, (int viewId) {
    final div = html.DivElement()
      ..style.width = '100%'
      ..style.height = '100%'
      ..style.cursor = 'pointer';

    div.onDragOver.listen((html.MouseEvent e) {
      e.preventDefault();
      e.stopPropagation();
    });

    div.onDrop.listen((html.MouseEvent e) async {
      e.preventDefault();
      e.stopPropagation();
      final files = e.dataTransfer?.files;
      if (files == null || files.length == 0) return;
      final out = <ConsultationDroppedFile>[];
      for (var i = 0; i < files.length; i++) {
        final f = files[i];
        final reader = html.FileReader();
        final done = reader.onLoadEnd.first;
        reader.readAsArrayBuffer(f);
        await done;
        final result = reader.result;
        if (result is ByteBuffer) {
          out.add(
            ConsultationDroppedFile(bytes: result.asUint8List(), name: f.name),
          );
        }
      }
      if (out.isNotEmpty) onDropped(out);
    });

    div.onClick.listen((html.MouseEvent e) {
      e.preventDefault();
      onBrowseTap();
    });

    return div;
  });
  return viewType;
}
