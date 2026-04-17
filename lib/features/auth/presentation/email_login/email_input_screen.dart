import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/email_login/email_otp_screen.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

class EmailInputScreen extends ConsumerStatefulWidget {
  const EmailInputScreen({
    super.key,
    this.forForgotPassword = false,
  });
  final bool forForgotPassword;

  @override
  ConsumerState<EmailInputScreen> createState() => _EmailInputScreenState();
}

class _EmailInputScreenState extends ConsumerState<EmailInputScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailCtrl = TextEditingController();
  bool _isLoading = false;

  @override
  void dispose() {
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _onContinue() async {
    if (!_formKey.currentState!.validate()) return;
    final email = _emailCtrl.text.trim().toLowerCase();
    setState(() => _isLoading = true);
    try {
      final auth = ref.read(authProvider.notifier);
      if (widget.forForgotPassword) {
        await auth.sendForgotPasswordOtp(email);
      } else {
        await auth.sendLoginOtp(email);
      }
      if (!mounted) return;
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => EmailOtpScreen(
            email: email,
            forForgotPassword: widget.forForgotPassword,
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      final msg = e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
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
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      widget.forForgotPassword ? l10n.resetPassword : l10n.signInWithEmail,
                      style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      l10n.enterEmailForOtp,
                      style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
                    ),
                    const SizedBox(height: 24),
                    TextFormField(
                      controller: _emailCtrl,
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.email],
                      decoration: InputDecoration(
                        hintText: l10n.email,
                        prefixIcon: const Icon(Icons.email_outlined),
                        border: const OutlineInputBorder(),
                      ),
                      validator: (v) {
                        final val = (v ?? '').trim();
                        if (val.isEmpty) return l10n.enterEmail;
                        if (!val.contains('@') || !val.contains('.')) {
                          return l10n.enterEmail;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),
                    ShifaPrimaryButton(
                      label: l10n.continue_,
                      onPressed: _isLoading ? null : _onContinue,
                      isLoading: _isLoading,
                      width: ButtonWidth.fill,
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
