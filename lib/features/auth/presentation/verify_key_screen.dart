import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/constants/assets.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/auth/registration_state.dart';

class VerifyKeyScreen extends ConsumerStatefulWidget {
  const VerifyKeyScreen({super.key});

  @override
  ConsumerState<VerifyKeyScreen> createState() => _VerifyKeyScreenState();
}

class _VerifyKeyScreenState extends ConsumerState<VerifyKeyScreen> {
  final TextEditingController _keyController = TextEditingController();
  bool _isLoading = false;

  Future<void> _onNext() async {
    final key = _keyController.text.trim();
    if (key.isEmpty) {
      _snack(AppLocalizations.of(context)!.pleaseEnterOneTimeKey);
      return;
    }

    setState(() => _isLoading = true);
    try {
      // Verify key against backend (returns Future<void>)
      await ref.read(registrationProvider.notifier).verifyKey(key);

      if (!mounted) return;
      final purpose = ref.read(registrationProvider).invitePurpose;
      final nextRoute =
          purpose == RegistrationData.clinicReceptionistInvitePurpose
              ? AppRoutes.receptionistCreateAccount
              : AppRoutes.createAccount;
      Navigator.pushReplacementNamed(context, nextRoute);
    } catch (e) {
      _snack(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.cardboard,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.maybePop(context),
        ),
        title: Text(
          AppLocalizations.of(context)!.verify,
          style: const TextStyle(color: Colors.black, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 12),
            child: Center(child: LanguageMiniToggle()),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SvgPicture.asset(
                    Assets.shifaLogo,
                    width: 120,
                    height: 120,
                    fit: BoxFit.contain,
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _keyController,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.oneTimeKey,
                    ),
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _onNext(),
                  ),
                  const SizedBox(height: 16),
                  ShifaPrimaryButton(
                    label: AppLocalizations.of(context)!.next,
                    onPressed: _isLoading ? null : _onNext,
                    isLoading: _isLoading,
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
