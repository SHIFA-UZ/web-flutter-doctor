import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/constants/assets.dart';

class VerifyKeyScreen extends StatefulWidget {
  const VerifyKeyScreen({super.key});

  @override
  State<VerifyKeyScreen> createState() => _VerifyKeyScreenState();
}

class _VerifyKeyScreenState extends State<VerifyKeyScreen> {
  final TextEditingController _keyController = TextEditingController();
  bool _isLoading = false;

  // Mock verification
  Future<bool> _verifyKey(String key) async {
    await Future.delayed(const Duration(milliseconds: 800));
    const allowedKeys = {'SHIFA-2025', 'ABC-123', 'TEST-KEY', 'BEKZOD'};
    return allowedKeys.contains(key.trim());
  }

  Future<void> _onNext() async {
    final key = _keyController.text.trim();
    if (key.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter your one-time key.')),
      );
      return;
    }
    setState(() => _isLoading = true);
    final ok = await _verifyKey(key);
    setState(() => _isLoading = false);
    if (!mounted) return;

    if (ok) {
      Navigator.pushReplacementNamed(context, AppRoutes.login);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Invalid key. Please check and try again.'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.maybePop(context),
        ),
        title: const Text(
          'Verify',
          style: TextStyle(color: Colors.black, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
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
                    decoration: const InputDecoration(hintText: 'One time Key'),
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _onNext(),
                  ),
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: _isLoading ? null : _onNext,
                      child: _isLoading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(
                                  Colors.white,
                                ),
                              ),
                            )
                          : const Text(
                              'Next',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                    ),
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
