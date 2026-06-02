import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';

void main() {
  test('PlatformLayout.isNativeMobile is false in VM tests', () {
    expect(PlatformLayout.isNativeMobile, isFalse);
  });
}
