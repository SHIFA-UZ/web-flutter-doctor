// Drop-in: submits registration and goes straight to Login (no schedule)
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/services/timezone_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/registration_email_verification.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/searchable_clinic_dropdown.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/searchable_profession_dropdown.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/searchable_timezone_dropdown.dart';
import 'package:shifa_doc_app_v1/state/auth/registration_state.dart';

class AccountInformationScreen extends ConsumerStatefulWidget {
  const AccountInformationScreen({super.key});
  @override
  ConsumerState<AccountInformationScreen> createState() =>
      _AccountInformationScreenState();
}

class _AccountInformationScreenState
    extends ConsumerState<AccountInformationScreen> {
  DateTime? _dob;
  String? _selectedGender;
  String? _selectedProfession;
  String? _selectedTimeZone; // IANA e.g. Europe/Berlin; detected on init, editable
  int? _selectedClinicId;
  String? _selectedClinicName;
  final _addressCtrl = TextEditingController();
  bool _isSubmitting = false;
  bool _timeZoneDetecting = true;

  @override
  void initState() {
    super.initState();
    _detectTimeZone();
  }

  Future<void> _detectTimeZone() async {
    final detected = await getDetectedTimeZone();
    if (mounted) {
      setState(() {
        _timeZoneDetecting = false;
        if (detected != null && detected.isNotEmpty) {
          _selectedTimeZone = detected;
        } else {
          _selectedTimeZone = 'UTC';
        }
      });
    }
  }

  @override
  void dispose() {
    _addressCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDob() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      locale: localeForMaterialIntl(Localizations.localeOf(context)),
      initialDate: _dob ?? DateTime(now.year - 25, now.month, now.day),
      firstDate: DateTime(1900),
      lastDate: now,
    );
    if (picked != null) setState(() => _dob = picked);
  }

  Future<void> _submit() async {
    // Store extras (they’ll be patched after first login)
    ref
        .read(registrationProvider.notifier)
        .setAccountInfo(
          dob: _dob,
          gender: _selectedGender,
          address: _addressCtrl.text.trim(),
          clinic: _selectedClinicName,
          clinicId: _selectedClinicId,
          profession: _selectedProfession,
          timeZone: _selectedTimeZone?.trim().isNotEmpty == true
              ? _selectedTimeZone!.trim()
              : 'UTC',
        );

    setState(() => _isSubmitting = true);
    try {
      final reg = ref.read(registrationProvider);
      final email = reg.email;
      if (email == null || email.trim().isEmpty || !email.contains('@')) {
        throw Exception(AppLocalizations.of(context)!.enterEmail);
      }

      final otpVerified = await runRegistrationEmailVerification(
        context: context,
        ref: ref,
        email: email.trim(),
      );
      if (!otpVerified) return;

      final token = await ref.read(registrationProvider.notifier).submitRegistration();
      if (token != null && token.isNotEmpty) {
        await ref.read(authProvider.notifier).setSessionFromToken(token);
      } else {
        // Fallback (older backend): login with the same credentials used for registration.
        final username = reg.email?.trim().isNotEmpty == true
            ? reg.email!.trim()
            : reg.phone?.trim();
        if (username == null || username.isEmpty) {
          throw Exception(AppLocalizations.of(context)!.enterEmailOrPhone);
        }
        await ref.read(authProvider.notifier).login(
              username,
              reg.password!.trim(),
            );
      }

      await ref
          .read(registrationProvider.notifier)
          .applyOptionalProfileExtrasAfterLogin();
      if (!mounted) return;
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.shell, (r) => false);
    } catch (e) {
      _snack(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  void _snack(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 12),
            child: Center(child: LanguageMiniToggle()),
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Text(
                AppLocalizations.of(context)!.accountInformation,
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 24),
              TextField(
                readOnly: true,
                decoration: InputDecoration(
                  hintText: _dob == null
                      ? AppLocalizations.of(context)!.dateOfBirth
                      : '${_dob!.year}-${_dob!.month.toString().padLeft(2, '0')}-${_dob!.day.toString().padLeft(2, '0')}',
                  suffixIcon: Icon(
                    Icons.calendar_today,
                    color: Colors.grey.shade600,
                  ),
                ),
                onTap: _pickDob,
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                decoration: InputDecoration(
                  hintText: AppLocalizations.of(context)!.gender,
                ),
                value: _selectedGender,
                items: ['Male', 'Female', 'Other']
                    .map((e) => DropdownMenuItem(
                          value: e,
                          child: Text(
                            e == 'Male'
                                ? AppLocalizations.of(context)!.male
                                : e == 'Female'
                                    ? AppLocalizations.of(context)!.female
                                    : AppLocalizations.of(context)!.other,
                          ),
                        ))
                    .toList(),
                onChanged: (v) => setState(() => _selectedGender = v),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _addressCtrl,
                decoration: InputDecoration(
                  hintText: AppLocalizations.of(context)!.address,
                ),
              ),
              const SizedBox(height: 16),
              SearchableClinicDropdown(
                value: _selectedClinicId,
                hintText: AppLocalizations.of(context)!.clinic,
                labelText: AppLocalizations.of(context)!.clinic,
                onChanged: (clinic) {
                  setState(() {
                    _selectedClinicId = clinic?.id;
                    _selectedClinicName = clinic?.name;
                  });
                },
              ),
              const SizedBox(height: 16),
              SearchableProfessionDropdown(
                value: _selectedProfession,
                hintText: AppLocalizations.of(context)!.profession,
                labelText: AppLocalizations.of(context)!.profession,
                onChanged: (v) => setState(() => _selectedProfession = v),
              ),
              const SizedBox(height: 16),
              _timeZoneDetecting
                  ? InputDecorator(
                      decoration: InputDecoration(
                        labelText: AppLocalizations.of(context)!.practiceTimezone,
                        border: const OutlineInputBorder(),
                        suffixIcon: const SizedBox(
                          width: 20,
                          height: 20,
                          child: Padding(
                            padding: EdgeInsets.all(12.0),
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        ),
                      ),
                      child: Text(AppLocalizations.of(context)!.detecting),
                    )
                  : SearchableTimezoneDropdown(
                      value: _selectedTimeZone,
                      hintText: AppLocalizations.of(context)!.practiceTimezonePlaceholder,
                      labelText: AppLocalizations.of(context)!.practiceTimezone,
                      onChanged: (v) => setState(() => _selectedTimeZone = v),
                    ),
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: const [
                  _Dot(),
                  SizedBox(width: 8),
                  _Dot(active: true),
                  SizedBox(width: 8),
                  _Dot(),
                ],
              ),
              const SizedBox(height: 24),
              ShifaPrimaryButton(
                label: AppLocalizations.of(context)!.createAccount,
                onPressed: _isSubmitting ? null : _submit,
                isLoading: _isSubmitting,
                width: ButtonWidth.fill,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Dot extends StatelessWidget {
  const _Dot({this.active = false});
  final bool active;
  @override
  Widget build(BuildContext context) {
    return Container(
      width: 8,
      height: 8,
      decoration: BoxDecoration(
        color: active ? AppColors.primaryTeal : Colors.grey.shade300,
        shape: BoxShape.circle,
      ),
    );
  }
}
