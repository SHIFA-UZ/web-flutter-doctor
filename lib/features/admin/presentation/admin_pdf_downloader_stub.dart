import 'dart:typed_data';

Future<void> downloadPdfBytes(Uint8List bytes, {required String filename}) async =>
    downloadBytes(bytes, filename: filename, mimeType: 'application/pdf');

Future<void> downloadBytes(
  Uint8List bytes, {
  required String filename,
  String mimeType = 'application/octet-stream',
}) async {
  throw UnsupportedError('Download is only supported on web.');
}
