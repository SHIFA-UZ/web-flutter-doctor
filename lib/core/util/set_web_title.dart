// Conditional export: use web implementation when dart.library.html exists.

export 'set_web_title_stub.dart' if (dart.library.html) 'set_web_title_web.dart';
