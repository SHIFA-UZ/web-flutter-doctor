import 'dart:typed_data';

/// One file picked or dropped for consultation document upload.
class ConsultationDroppedFile {
  const ConsultationDroppedFile({
    required this.bytes,
    required this.name,
  });

  final Uint8List bytes;
  final String name;
}
