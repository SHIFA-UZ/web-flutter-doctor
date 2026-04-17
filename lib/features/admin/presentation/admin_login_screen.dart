// lib/features/admin/presentation/admin_login_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

class AdminLoginScreen extends ConsumerStatefulWidget {
  const AdminLoginScreen({super.key});

  @override
  ConsumerState<AdminLoginScreen> createState() => _AdminLoginScreenState();
}

class _AdminLoginScreenState extends ConsumerState<AdminLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _otpCtrl = TextEditingController();
  bool _obscure = true;
  bool _isLoading = false;
  bool _isCheckingSession = true;
  final FocusNode _passwordFocusNode = FocusNode();
  bool _otpStep = false;
  String? _emailHint;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _tryRestoreAdminSession());
  }

  /// Restore session from storage (e.g. after browser refresh). Uses admin-only token key to avoid using doctor token.
  Future<void> _tryRestoreAdminSession() async {
    final restored = await ref.read(authProvider.notifier).restoreSession(forAdmin: true);
    if (!mounted) return;
    setState(() => _isCheckingSession = false);
    if (!restored) {
      // Ensure we never use a doctor token in admin context (e.g. same-origin nav from doctor app)
      ref.read(adminApiClientProvider).clearToken();
      ref.read(authTokenProvider.notifier).state = null;
      return;
    }
    try {
      final api = ref.read(adminApiClientProvider);
      final res = await api.get('/api/admin/dashboard/stats');
      if (res.statusCode == 200 && mounted) {
        Navigator.pushReplacementNamed(context, AppRoutes.adminShell);
      }
    } catch (_) {
      // Token invalid or not admin; clear stale admin session and stay on login.
      ref.read(authProvider.notifier).logout(adminOnly: true);
    } finally {
      if (mounted) setState(() => _isCheckingSession = false);
    }
  }

  @override
  void dispose() {
    _userCtrl.dispose();
    _passCtrl.dispose();
    _otpCtrl.dispose();
    _passwordFocusNode.dispose();
    super.dispose();
  }

  Future<void> _onContinueToOtp() async {
    final form = _formKey.currentState!;
    if (!form.validate()) return;
    setState(() => _isLoading = true);
    try {
      final hint = await ref.read(authProvider.notifier).requestAdminLoginOtp(
            _userCtrl.text.trim(),
            _passCtrl.text.trim(),
          );
      if (!mounted) return;
      setState(() {
        _otpStep = true;
        _emailHint = hint ?? '';
      });
    } catch (e) {
      _showSnack(
        e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e',
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _onVerifyOtp() async {
    final code = _otpCtrl.text.trim();
    if (code.length < 6) {
      _showSnack(AppLocalizations.of(context)!.invalidOtp);
      return;
    }
    setState(() => _isLoading = true);
    try {
      await ref.read(authProvider.notifier).verifyAdminLoginOtp(
            _userCtrl.text.trim(),
            _passCtrl.text.trim(),
            code,
          );

      final api = ref.read(adminApiClientProvider);
      final adminRes = await api.get('/api/admin/dashboard/stats');
      if (adminRes.statusCode != 200) {
        ref.read(authProvider.notifier).logout(adminOnly: true);
        throw Exception('Access denied. Admin privileges required.');
      }

      if (!mounted) return;
      Navigator.pushReplacementNamed(context, AppRoutes.adminShell);
    } catch (e) {
      _showSnack(
        e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e',
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _onResendOtp() async {
    setState(() => _isLoading = true);
    try {
      final hint = await ref.read(authProvider.notifier).requestAdminLoginOtp(
            _userCtrl.text.trim(),
            _passCtrl.text.trim(),
          );
      if (!mounted) return;
      setState(() => _emailHint = hint ?? _emailHint);
      _showSnack(AppLocalizations.of(context)!.otpResent);
    } catch (e) {
      _showSnack(
        e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e',
      );
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _backToCredentials() {
    setState(() {
      _otpStep = false;
      _otpCtrl.clear();
      _emailHint = null;
    });
  }

  void _showSnack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: AppColors.destructiveRed),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_isCheckingSession) {
      return Scaffold(
        backgroundColor: Colors.white,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 16),
              Text(
                l10n.loading,
                style: TextStyle(color: Colors.grey.shade600),
              ),
            ],
          ),
        ),
      );
    }
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Image.asset(
                      'assets/branding/shifa_logo.png',
                      width: 120,
                      height: 120,
                    ),
                    const SizedBox(height: 32),
                    Text(
                      l10n.adminPanel,
                      style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.black87,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      l10n.signInToManageSystem,
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.grey.shade600,
                      ),
                    ),
                    const SizedBox(height: 48),
                    if (!_otpStep) ...[
                      TextFormField(
                        controller: _userCtrl,
                        textInputAction: TextInputAction.next,
                        onFieldSubmitted: (_) {
                          FocusScope.of(context).requestFocus(_passwordFocusNode);
                        },
                        decoration: InputDecoration(
                          labelText: l10n.emailOrPhone,
                          prefixIcon: const Icon(Icons.person_outline),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (v) => v?.isEmpty ?? true ? l10n.required : null,
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _passCtrl,
                        focusNode: _passwordFocusNode,
                        obscureText: _obscure,
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _onContinueToOtp(),
                        decoration: InputDecoration(
                          labelText: l10n.password,
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                            onPressed: () => setState(() => _obscure = !_obscure),
                          ),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (v) => v?.isEmpty ?? true ? l10n.required : null,
                      ),
                      const SizedBox(height: 32),
                      ShifaPrimaryButton(
                        label: l10n.continue_,
                        onPressed: _isLoading ? null : _onContinueToOtp,
                        width: ButtonWidth.fill,
                        isLoading: _isLoading,
                      ),
                      const SizedBox(height: 8),
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          onPressed: _isLoading
                              ? null
                              : () => Navigator.pushNamed(context, AppRoutes.adminForgotPassword),
                          child: Text(
                            l10n.forgotPassword,
                            style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                          ),
                        ),
                      ),
                    ] else ...[
                      Text(
                        l10n.adminEmailVerificationSent(_emailHint ?? 'your email'),
                        style: TextStyle(fontSize: 14, color: Colors.grey.shade700),
                      ),
                      const SizedBox(height: 24),
                      TextFormField(
                        controller: _otpCtrl,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.digitsOnly,
                          LengthLimitingTextInputFormatter(6),
                        ],
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _onVerifyOtp(),
                        decoration: InputDecoration(
                          labelText: l10n.adminEnterVerificationCode,
                          prefixIcon: const Icon(Icons.pin_outlined),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                      ),
                      const SizedBox(height: 24),
                      ShifaPrimaryButton(
                        label: l10n.adminVerifyAndSignIn,
                        onPressed: _isLoading ? null : _onVerifyOtp,
                        width: ButtonWidth.fill,
                        isLoading: _isLoading,
                      ),
                      const SizedBox(height: 12),
                      TextButton(
                        onPressed: _isLoading ? null : _onResendOtp,
                        child: Text(l10n.adminResendVerificationCode),
                      ),
                      TextButton(
                        onPressed: _isLoading ? null : _backToCredentials,
                        child: Text(l10n.adminChangeAccount),
                      ),
                    ],
                    const SizedBox(height: 24),
                    TextButton(
                      onPressed: _isLoading
                          ? null
                          : () => Navigator.pushReplacementNamed(context, AppRoutes.login),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.arrow_back, size: 18),
                          const SizedBox(width: 8),
                          Text(
                            l10n.goToDoctorLogin,
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey.shade700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
