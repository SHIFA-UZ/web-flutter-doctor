import 'package:flutter_riverpod/flutter_riverpod.dart';

// Profile state model
class ProfileState {
  final String name;
  final DateTime dob;
  final String address;
  final String phone;
  final String email;
  final String billingName;
  final String billingEmail;
  final String iban;
  final String taxId;
  final String country;
  final String language;
  final bool twoFA;
  final bool encryptedDocs;

  ProfileState({
    required this.name,
    required this.dob,
    required this.address,
    required this.phone,
    required this.email,
    required this.billingName,
    required this.billingEmail,
    required this.iban,
    required this.taxId,
    required this.country,
    required this.language,
    required this.twoFA,
    required this.encryptedDocs,
  });

  ProfileState copyWith({
    String? name,
    DateTime? dob,
    String? address,
    String? phone,
    String? email,
    String? billingName,
    String? billingEmail,
    String? iban,
    String? taxId,
    String? country,
    String? language,
    bool? twoFA,
    bool? encryptedDocs,
  }) {
    return ProfileState(
      name: name ?? this.name,
      dob: dob ?? this.dob,
      address: address ?? this.address,
      phone: phone ?? this.phone,
      email: email ?? this.email,
      billingName: billingName ?? this.billingName,
      billingEmail: billingEmail ?? this.billingEmail,
      iban: iban ?? this.iban,
      taxId: taxId ?? this.taxId,
      country: country ?? this.country,
      language: language ?? this.language,
      twoFA: twoFA ?? this.twoFA,
      encryptedDocs: encryptedDocs ?? this.encryptedDocs,
    );
  }
}

// Profile controller
class ProfileController extends StateNotifier<ProfileState> {
  ProfileController()
    : super(
        ProfileState(
          name: 'Ulugbek Karimov',
          dob: DateTime(1999, 9, 19),
          address: 'Fargona yuli 17, Yashnabod, Tashkent, Uzbekistan',
          phone: '+49 123456 4445',
          email: 'doctor@clinic.com',
          billingName: 'Ulugbek Karimov',
          billingEmail: 'billing@clinic.com',
          iban: 'DE12 3456 7890 1234 5678 90',
          taxId: 'DE-123456789',
          country: 'Germany',
          language: 'English',
          twoFA: false,
          encryptedDocs: true,
        ),
      );

  // Section updaters
  void updateProfileInfo({String? name, DateTime? dob, String? address}) {
    state = state.copyWith(name: name, dob: dob, address: address);
  }

  void updateContact({String? phone, String? email}) {
    state = state.copyWith(phone: phone, email: email);
  }

  void updatePayment({
    String? billingName,
    String? billingEmail,
    String? iban,
    String? taxId,
  }) {
    state = state.copyWith(
      billingName: billingName,
      billingEmail: billingEmail,
      iban: iban,
      taxId: taxId,
    );
  }

  void updateSettings({
    String? country,
    String? language,
    bool? twoFA,
    bool? encryptedDocs,
  }) {
    state = state.copyWith(
      country: country,
      language: language,
      twoFA: twoFA,
      encryptedDocs: encryptedDocs,
    );
  }

  // Password change would be handled separately (not stored in state)
}

final profileProvider = StateNotifierProvider<ProfileController, ProfileState>(
  (ref) => ProfileController(),
);
