import 'consultation_dropped_file.dart';

/// Non-web: OS file drop is not registered.
String? consultationRegisterDropView({
  required void Function(List<ConsultationDroppedFile> files) onDropped,
  required void Function() onBrowseTap,
}) =>
    null;
