import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/services/timezone_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/utils/password_validation.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/searchable_timezone_dropdown.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/auth/registration_state.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

class ReceptionistCreateAccountScreen extends ConsumerStatefulWidget {
  const ReceptionistCreateAccountScreen({super.key});

  @override
  ConsumerState<ReceptionistCreateAccountScreen> createState() =>
      _ReceptionistCreateAccountScreenState();
}

class _ReceptionistCreateAccountScreenState
    extends ConsumerState<ReceptionistCreateAccountScreen> {
  final _firstCtrl = TextEditingController();
  final _lastCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  final _confirmPassCtrl = TextEditingController();

  final _formKey = GlobalKey<FormState>();
  bool _obscure = true;
  bool _confirmObscure = true;
  bool _busy = false;
  String _timeZone = 'UTC';

  List<PasswordRequirementResult> _requirements = const [];

  @override
  void initState() {
    super.initState();
    _passCtrl.addListener(_pwListener);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final tz = await getDetectedTimeZone();
      if (!mounted) return;
      final regTz = ref.read(registrationProvider).timeZone;
      final next =
          (tz != null && tz.trim().isNotEmpty) ? tz.trim() : (regTz ?? 'UTC');
      setState(() => _timeZone = next.trim().isEmpty ? 'UTC' : next.trim());
    });
  }

  void _pwListener() {
    setState(() {
      _requirements =
          PasswordValidation.getRequirementResults(_passCtrl.text);
    });
  }

  @override
  void dispose() {
    _passCtrl.removeListener(_pwListener);
    _firstCtrl.dispose();
    _lastCtrl.dispose();
    _passCtrl.dispose();
    _confirmPassCtrl.dispose();
    super.dispose();
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final l10n = AppLocalizations.of(context)!;

    final reg = ref.read(registrationProvider);
    if (reg.key == null || reg.key!.isEmpty) {
      _toast(l10n.pleaseVerifyInvitationKeyFirst);
      Navigator.pushNamed(context, AppRoutes.verify);
      return;
    }

    final emailLock = reg.inviteTargetEmail ?? reg.email;
    final emailTrimmed = emailLock?.trim();
    if (emailTrimmed == null ||
        emailTrimmed.isEmpty ||
        !emailTrimmed.contains('@')) {
      _toast(l10n.enterEmail);
      return;
    }

    setState(() => _busy = true);
    try {
      ref.read(registrationProvider.notifier).setClinicStaffSignup(
            firstName: _firstCtrl.text,
            lastName: _lastCtrl.text,
            password: _passCtrl.text,
            timeZone: _timeZone,
          );

      final token =
          await ref.read(registrationProvider.notifier).submitClinicStaffRegistration();
      if (token != null && token.isNotEmpty) {
        await ref.read(authProvider.notifier).setSessionFromToken(token);
      }

      ref.read(shellProvider.notifier).setTab(1);

      if (!mounted) return;
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.shell, (r) => false);
    } catch (e) {
      if (mounted) _toast(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String? _reqName(String v, BuildContext ctx, bool first) {
    final t = v.trim();
    if (t.isEmpty) {
      return first
          ? AppLocalizations.of(ctx)!.enterFirstName
          : AppLocalizations.of(ctx)!.enterLastName;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final reg = ref.watch(registrationProvider);
    final l10n = AppLocalizations.of(context)!;

    final email = (reg.inviteTargetEmail ?? reg.email)?.trim() ?? '';

    final headerLines = [
      reg.inviteClinicName?.trim(),
      reg.inviteTargetEmail ?? reg.email,
    ].whereType<String>().where((s) => s.trim().isNotEmpty).toList();

    return Scaffold(
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        title:
            Text(l10n.createAccount, style: const TextStyle(fontWeight: FontWeight.w700)),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 12),
            child: Center(child: LanguageMiniToggle()),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (headerLines.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Text(
                          headerLines.join('\n'),
                          style:
                              Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    color: Colors.black87,
                                  ),
                        ),
                      ),
                    Text(
                      l10n.enterEmail,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    InputDecorator(
                      decoration:
                          const InputDecoration(border: OutlineInputBorder()),
                      child: Text(
                        email.isNotEmpty ? email : '—',
                        style:
                            Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.w500,
                                ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _firstCtrl,
                      decoration:
                          InputDecoration(hintText: l10n.enterFirstName),
                      validator: (v) =>
                          _reqName(v ?? '', context, true),
                      textCapitalization: TextCapitalization.words,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _lastCtrl,
                      decoration: InputDecoration(hintText: l10n.enterLastName),
                      validator: (v) =>
                          _reqName(v ?? '', context, false),
                      textCapitalization: TextCapitalization.words,
                    ),
                    const SizedBox(height: 12),
                    SearchableTimezoneDropdown(
                      value: _timeZone,
                      onChanged: (v) {
                        final next = v?.trim();
                        if (next != null && next.isNotEmpty) {
                          setState(() => _timeZone = next);
                        }
                      },
                      hintText: l10n.translate('practiceTimezonePlaceholder'),
                      labelText: l10n.translate('practiceTimezone'),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passCtrl,
                      obscureText: _obscure,
                      decoration: InputDecoration(
                        hintText: l10n.password,
                        suffixIcon: IconButton(
                          onPressed: () => setState(() => _obscure = !_obscure),
                          icon: Icon(
                            _obscure ? Icons.visibility_off : Icons.visibility,
                          ),
                        ),
                      ),
                      validator: (v) {
                        final txt = v?.trim() ?? '';
                        if (txt.isEmpty) {
                          return l10n.translate('passwordRequired');
                        }
                        final errorKey = PasswordValidation.validate(txt);
                        if (errorKey != null) {
                          return l10n.translate(errorKey);
                        }
                        return null;
                      },
                    ),
                    if (_passCtrl.text.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      ..._requirements.map((r) => Padding(
                            padding:
                                const EdgeInsets.only(left: 4, bottom: 2),
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
                                Expanded(
                                  child: Text(
                                    l10n.translate(r.l10nKey),
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: r.satisfied
                                          ? AppColors.primaryTeal
                                          : Colors.grey,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          )),
                      const SizedBox(height: 8),
                    ],
                    TextFormField(
                      controller: _confirmPassCtrl,
                      obscureText: _confirmObscure,
                      decoration: InputDecoration(
                        hintText: l10n.confirmPassword,
                        suffixIcon: IconButton(
                          onPressed: () => setState(
                            () =>
                                _confirmObscure = !_confirmObscure,
                          ),
                          icon: Icon(
                            _confirmObscure
                                ? Icons.visibility_off
                                : Icons.visibility,
                          ),
                        ),
                      ),
                      validator: (v) {
                        if (v == null || v.trim().isEmpty) {
                          return l10n.translate('pleaseConfirmPassword');
                        }
                        if (v.trim() != _passCtrl.text.trim()) {
                          return l10n.passwordsDoNotMatch;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),
                    ShifaPrimaryButton(
                      label: l10n.createAccount,
                      onPressed: _busy ? null : _submit,
                      isLoading: _busy,
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
