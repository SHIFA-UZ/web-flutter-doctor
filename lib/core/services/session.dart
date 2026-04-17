class Session {
  static final Session _s = Session._();
  factory Session() => _s;
  Session._();

  String? token;
  String? doctorId;
  String? doctorName;

  bool get isAuthenticated => token != null && doctorId != null;

  void clear() {
    token = null;
    doctorId = null;
    doctorName = null;
  }
}
