import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/constants/assets.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/verify_key_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/email_login/email_input_screen.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

// Use AuthController instead of talking to ApiClient directly
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
// Optional: to force a fresh profile fetch after login if your controller
// doesn't already invalidate it.
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

// ✅ NEW: to auto-apply optional extras after login
import 'package:shifa_doc_app_v1/state/auth/registration_state.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});
  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  bool _obscure = true;
  bool _isLoading = false;

  @override
  void dispose() {
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _onSignIn() async {
    final form = _formKey.currentState!;
    if (!form.validate()) return;
    setState(() => _isLoading = true);
    try {
      // 1) Login (authProvider should set JWT on ApiClient)
      await ref
          .read(authProvider.notifier)
          .login(_userCtrl.text.trim(), _passCtrl.text.trim());

      // 2) Ensure fresh profile load
      ref.invalidate(profileAllProvider);

      // 3) ✅ Auto-apply optional profile extras gathered during registration
      try {
        await ref
            .read(registrationProvider.notifier)
            .applyOptionalProfileExtrasAfterLogin();
        // Extras may have updated profile -> invalidate again to refresh data in shell
        ref.invalidate(profileAllProvider);
      } catch (_) {
        // Non-blocking: ignore if nothing to apply or if patch failed
      }

      if (!mounted) return;
      // 4) Navigate to main shell
      Navigator.pushReplacementNamed(context, AppRoutes.shell);
      return;
    } catch (e) {
      _showSnack(
        e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e',
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showSnack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 12),
            child: Center(child: LanguageMiniToggle()),
          ),
        ],
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Center(
                    child: SvgPicture.asset(
                      Assets.shifaLogo,
                      width: 80,
                      height: 80,
                      fit: BoxFit.contain,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    AppLocalizations.of(context)!.signIn,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 24),

                  // -------- Form --------
                  Form(
                    key: _formKey,
                    child: Column(
                      children: [
                        TextFormField(
                          controller: _userCtrl,
                          textInputAction: TextInputAction.next,
                          decoration: InputDecoration(
                            hintText: AppLocalizations.of(context)!.emailOrPhone,
                          ),
                          validator: (v) => (v == null || v.trim().isEmpty)
                              ? AppLocalizations.of(context)!.enterEmailOrPhone
                              : null,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: _passCtrl,
                          obscureText: _obscure,
                          onFieldSubmitted: (_) => _onSignIn(),
                          decoration: InputDecoration(
                            hintText: AppLocalizations.of(context)!.password,
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscure
                                    ? Icons.visibility_off
                                    : Icons.visibility,
                              ),
                              onPressed: () =>
                                  setState(() => _obscure = !_obscure),
                            ),
                          ),
                          validator: (v) => ((v ?? '').trim().length < 6)
                              ? AppLocalizations.of(context)!.minimum6Characters
                              : null,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: _isLoading
                          ? null
                          : () => Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => const EmailInputScreen(forForgotPassword: true),
                                ),
                              ),
                      child: Text(AppLocalizations.of(context)!.forgotPassword),
                    ),
                  ),
                  const SizedBox(height: 8),

                  // -------- Sign in --------
                  ShifaPrimaryButton(
                    label: AppLocalizations.of(context)!.signIn,
                    onPressed: _isLoading ? null : _onSignIn,
                    isLoading: _isLoading,
                    width: ButtonWidth.fill,
                  ),

                  const SizedBox(height: 16),

                  // -------- Sign in with Email OTP --------
                  ShifaSecondaryButton(
                    label: AppLocalizations.of(context)!.signInWithEmail,
                    icon: Icons.email_outlined,
                    onPressed: _isLoading
                        ? null
                        : () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const EmailInputScreen(forForgotPassword: false),
                              ),
                            ),
                    width: ButtonWidth.fill,
                  ),
                  const SizedBox(height: 16),

                  // -------- Create account --------
                  ShifaSecondaryButton(
                    label: AppLocalizations.of(context)!.createAccount,
                    onPressed: _isLoading
                        ? null
                        : () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => const VerifyKeyScreen(),
                            ),
                          ),
                    width: ButtonWidth.fill,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
