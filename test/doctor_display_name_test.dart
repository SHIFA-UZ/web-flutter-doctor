import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/utils/doctor_display_name.dart';

void main() {
  test('formatDoctorMedName uses full surname and Dr. med. prefix', () {
    expect(
      formatDoctorMedName(firstName: 'Odiljon', lastName: 'Ismoilov'),
      'Dr. med. Odiljon Ismoilov',
    );
  });

  test('formatDoctorMedName without prefix is first and last only', () {
    expect(
      formatDoctorMedName(
        firstName: 'Odiljon',
        lastName: 'Ismoilov',
        includePrefix: false,
      ),
      'Odiljon Ismoilov',
    );
  });
}
