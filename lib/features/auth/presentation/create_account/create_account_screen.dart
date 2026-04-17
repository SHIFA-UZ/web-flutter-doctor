import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/services/timezone_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/utils/password_validation.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/registration_email_verification.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/searchable_timezone_dropdown.dart';
import 'package:shifa_doc_app_v1/state/auth/registration_state.dart';

class CreateAccountScreen extends ConsumerStatefulWidget {
  const CreateAccountScreen({super.key});
  @override
  ConsumerState<CreateAccountScreen> createState() =>
      _CreateAccountScreenState();
}

class _CreateAccountScreenState extends ConsumerState<CreateAccountScreen> {
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _emailController = TextEditingController(); // optional
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  final _formKey = GlobalKey<FormState>();
  final _confirmFormKey = GlobalKey<FormState>();
  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  List<PasswordRequirementResult> _passwordRequirements = const [];

  /// When true, user has been identified as existing patient; show simplified confirm UI.
  bool _showExistingPatientView = false;
  String? _existingPatientPhotoUrl;
  String _existingFirstName = '';
  String _existingLastName = '';
  String _existingPhone = '';
  bool _isCheckingExisting = false;
  bool _isSubmitting = false;
  /// Practice timezone for existing-patient flow; detected when entering confirm view.
  String? _existingPatientTimeZone;
  bool _existingPatientTimeZoneDetecting = false;

  @override
  void initState() {
    super.initState();
    _passwordController.addListener(_onPasswordChanged);
  }

  void _onPasswordChanged() {
    setState(() {
      _passwordRequirements =
          PasswordValidation.getRequirementResults(_passwordController.text);
    });
  }

  @override
  void dispose() {
    _passwordController.removeListener(_onPasswordChanged);
    _firstNameController.dispose();
    _lastNameController.dispose();
    _phoneController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _next() async {
    final form = _formKey.currentState!;
    if (!form.validate()) return;

    final reg = ref.read(registrationProvider);
    if (reg.key == null || reg.key!.isEmpty) {
      _snack(AppLocalizations.of(context)!.pleaseVerifyInvitationKeyFirst);
      Navigator.pushNamed(context, AppRoutes.verify);
      return;
    }

    final firstName = _firstNameController.text.trim();
    final lastName = _lastNameController.text.trim();
    final phone = _phoneController.text.trim();

    setState(() => _isCheckingExisting = true);
    try {
      final result = await ref.read(registrationProvider.notifier).checkExistingPatient(
        firstName: firstName,
        lastName: lastName,
        phone: phone,
      );
      if (!mounted) return;
      if (result['found'] == true) {
        setState(() {
          _showExistingPatientView = true;
          _existingPatientPhotoUrl = result['photoUrl']?.toString();
          _existingFirstName = firstName;
          _existingLastName = lastName;
          _existingPhone = phone;
          _existingPatientTimeZoneDetecting = true;
        });
        _detectTimeZoneForExistingPatient();
        return;
      }
      // No existing patient: store basic info and go to account information
      ref.read(registrationProvider.notifier).setBasicInfo(
        firstName: firstName,
        lastName: lastName,
        phone: phone,
        email: _emailController.text.trim().isEmpty ? null : _emailController.text.trim(),
        password: _passwordController.text.trim(),
      );
      if (!mounted) return;
      Navigator.pushNamed(context, AppRoutes.accountInfo);
    } catch (e) {
      if (mounted) _snack(e.toString());
    } finally {
      if (mounted) setState(() => _isCheckingExisting = false);
    }
  }

  void _backFromExistingPatientView() {
    setState(() {
      _showExistingPatientView = false;
      _existingPatientPhotoUrl = null;
      _existingFirstName = '';
      _existingLastName = '';
      _existingPhone = '';
      _existingPatientTimeZone = null;
      _existingPatientTimeZoneDetecting = false;
    });
  }

  Future<void> _detectTimeZoneForExistingPatient() async {
    final detected = await getDetectedTimeZone();
    if (!mounted) return;
    setState(() {
      _existingPatientTimeZoneDetecting = false;
      _existingPatientTimeZone = (detected != null && detected.isNotEmpty)
          ? detected
          : 'UTC';
    });
  }

  Future<void> _confirmExistingPatientRegistration() async {
    final form = _confirmFormKey.currentState;
    if (form == null || !form.validate()) return;

    final reg = ref.read(registrationProvider);
    if (reg.key == null || reg.key!.isEmpty) {
      _snack(AppLocalizations.of(context)!.pleaseVerifyInvitationKeyFirst);
      return;
    }

    setState(() => _isSubmitting = true);
    try {
      final timeZone = _existingPatientTimeZone?.trim().isNotEmpty == true
          ? _existingPatientTimeZone!.trim()
          : 'UTC';
      ref.read(registrationProvider.notifier).setAccountInfo(timeZone: timeZone);
      ref.read(registrationProvider.notifier).setBasicInfo(
        firstName: _existingFirstName,
        lastName: _existingLastName,
        phone: _existingPhone,
        email: null,
        password: _passwordController.text.trim(),
      );

      final email = _emailController.text.trim();
      if (email.isEmpty || !email.contains('@')) {
        _snack(AppLocalizations.of(context)!.enterEmail);
        return;
      }
      final otpVerified = await runRegistrationEmailVerification(
        context: context,
        ref: ref,
        email: email,
      );
      if (!otpVerified) return;

      final regAfterSet = ref.read(registrationProvider);
      final token = await ref.read(registrationProvider.notifier).submitRegistration();
      if (token != null && token.isNotEmpty) {
        await ref.read(authProvider.notifier).setSessionFromToken(token);
      } else {
        await ref.read(authProvider.notifier).login(
              regAfterSet.phone!.trim(),
              regAfterSet.password!.trim(),
            );
      }
      await ref
          .read(registrationProvider.notifier)
          .applyOptionalProfileExtrasAfterLogin();
      if (!mounted) return;
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.shell, (r) => false);
    } catch (e) {
      if (mounted) _snack(e.toString());
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  String? _nonEmpty(String? v, String label, BuildContext context) {
    if (v == null || v.trim().isEmpty) {
      switch (label) {
        case 'first name':
          return AppLocalizations.of(context)!.enterFirstName;
        case 'last name':
          return AppLocalizations.of(context)!.enterLastName;
        case 'phone number':
          return AppLocalizations.of(context)!.enterPhoneNumber;
        default:
          return '${AppLocalizations.of(context)!.required}: $label';
      }
    }
    return null;
  }

  void _snack(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  @override
  Widget build(BuildContext context) {
    if (_showExistingPatientView) {
      return _buildExistingPatientConfirmView(context);
    }
    return _buildCreateAccountForm(context);
  }

  Widget _buildExistingPatientConfirmView(BuildContext context) {
    final loc = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: _isSubmitting ? null : _backFromExistingPatientView,
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
                loc.createAccount,
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 24),
              CircleAvatar(
                radius: 48,
                backgroundColor: Colors.grey.shade200,
                backgroundImage: _existingPatientPhotoUrl != null &&
                        _existingPatientPhotoUrl!.trim().isNotEmpty
                    ? NetworkImage(_existingPatientPhotoUrl!)
                    : null,
                child: _existingPatientPhotoUrl == null ||
                        _existingPatientPhotoUrl!.trim().isEmpty
                    ? const Icon(Icons.person, size: 48, color: Colors.grey)
                    : null,
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: const Color(0xFFE6FFF9),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.primaryTeal),
                ),
                child: Text(
                  loc.existingPatientCreatingDoctorAccount,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 15,
                    color: Colors.grey.shade800,
                    height: 1.3,
                  ),
                ),
              ),
              const SizedBox(height: 24),
              if (_existingPatientTimeZoneDetecting)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: SizedBox(
                    height: 24,
                    width: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                )
              else
                SearchableTimezoneDropdown(
                  value: _existingPatientTimeZone,
                  onChanged: (v) =>
                      setState(() => _existingPatientTimeZone = v),
                  hintText: AppLocalizations.of(context)!.practiceTimezonePlaceholder,
                  labelText: 'Practice timezone',
                ),
              const SizedBox(height: 16),
              Form(
                key: _confirmFormKey,
                child: Column(
                  children: [
                    TextFormField(
                      controller: _passwordController,
                      decoration: InputDecoration(
                        hintText: loc.password,
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscurePassword
                                ? Icons.visibility_off
                                : Icons.visibility,
                          ),
                          onPressed: () =>
                              setState(() => _obscurePassword = !_obscurePassword),
                        ),
                      ),
                      obscureText: _obscurePassword,
                      validator: (v) {
                        final txt = v?.trim() ?? '';
                        if (txt.isEmpty) {
                          return loc.translate('passwordRequired');
                        }
                        final errorKey = PasswordValidation.validate(txt);
                        if (errorKey != null) {
                          return loc.translate(errorKey);
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _confirmPasswordController,
                      decoration: InputDecoration(
                        hintText: loc.confirmPassword,
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscureConfirmPassword
                                ? Icons.visibility_off
                                : Icons.visibility,
                          ),
                          onPressed: () => setState(
                              () => _obscureConfirmPassword = !_obscureConfirmPassword),
                        ),
                      ),
                      obscureText: _obscureConfirmPassword,
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) {
                          return loc.translate('pleaseConfirmPassword');
                        }
                        if (v.trim() != _passwordController.text.trim()) {
                          return loc.passwordsDoNotMatch;
                        }
                        return null;
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              ShifaPrimaryButton(
                label: loc.confirmRegistration,
                onPressed: _isSubmitting ? null : _confirmExistingPatientRegistration,
                isLoading: _isSubmitting,
                width: ButtonWidth.fill,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCreateAccountForm(BuildContext context) {
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
                AppLocalizations.of(context)!.createAccount,
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 24),
              Consumer(
                builder: (context, ref, _) {
                  final key = ref.watch(registrationProvider).key;
                  return key == null || key.isEmpty
                      ? const SizedBox.shrink()
                      : Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFE6FFF9),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: AppColors.primaryTeal),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.verified,
                                color: AppColors.primaryTeal,
                                size: 18,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                AppLocalizations.of(context)!.keyVerified,
                                style: TextStyle(color: Colors.grey.shade800),
                              ),
                            ],
                          ),
                        );
                },
              ),
              const SizedBox(height: 16),
              Form(
                key: _formKey,
                child: Column(
                  children: [
                    TextFormField(
                      controller: _firstNameController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.firstName,
                      ),
                      validator: (v) => _nonEmpty(v, 'first name', context),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _lastNameController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.lastName,
                      ),
                      validator: (v) => _nonEmpty(v, 'last name', context),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _phoneController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.phoneNumber,
                      ),
                      keyboardType: TextInputType.phone,
                      validator: (v) => _nonEmpty(v, 'phone number', context),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _emailController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.email,
                      ),
                      keyboardType: TextInputType.emailAddress,
                      validator: (v) {
                        final val = (v ?? '').trim();
                        if (val.isEmpty) return AppLocalizations.of(context)!.enterEmail;
                        if (!val.contains('@') || !val.contains('.')) {
                          return AppLocalizations.of(context)!.enterEmail;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.password,
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscurePassword ? Icons.visibility_off : Icons.visibility,
                          ),
                          onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                        ),
                      ),
                      obscureText: _obscurePassword,
                      validator: (v) {
                        final txt = v?.trim() ?? '';
                        if (txt.isEmpty) {
                          return AppLocalizations.of(context)!.translate('passwordRequired');
                        }
                        final errorKey = PasswordValidation.validate(txt);
                        if (errorKey != null) {
                          return AppLocalizations.of(context)!.translate(errorKey);
                        }
                        return null;
                      },
                    ),
                    if (_passwordController.text.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      ..._passwordRequirements.map((r) => Padding(
                            padding: const EdgeInsets.only(left: 4, bottom: 2),
                            child: Row(
                              children: [
                                Icon(
                                  r.satisfied ? Icons.check_circle : Icons.cancel,
                                  size: 16,
                                  color: r.satisfied
                                      ? AppColors.primaryTeal
                                      : Colors.grey,
                                ),
                                const SizedBox(width: 8),
                                Text(
                                  AppLocalizations.of(context)!.translate(r.l10nKey),
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: r.satisfied
                                        ? AppColors.primaryTeal
                                        : Colors.grey,
                                  ),
                                ),
                              ],
                            ),
                          )),
                      const SizedBox(height: 8),
                    ],
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _confirmPasswordController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.confirmPassword,
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscureConfirmPassword ? Icons.visibility_off : Icons.visibility,
                          ),
                          onPressed: () => setState(() => _obscureConfirmPassword = !_obscureConfirmPassword),
                        ),
                      ),
                      obscureText: _obscureConfirmPassword,
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) {
                          return AppLocalizations.of(context)!.translate('pleaseConfirmPassword');
                        }
                        if (v.trim() != _passwordController.text.trim()) {
                          return AppLocalizations.of(context)!.passwordsDoNotMatch;
                        }
                        return null;
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: const [
                  _Dot(active: true),
                  SizedBox(width: 8),
                  _Dot(),
                  SizedBox(width: 8),
                  _Dot(),
                ],
              ),
              const SizedBox(height: 24),
              ShifaPrimaryButton(
                label: AppLocalizations.of(context)!.next,
                onPressed: _isCheckingExisting ? null : _next,
                isLoading: _isCheckingExisting,
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
