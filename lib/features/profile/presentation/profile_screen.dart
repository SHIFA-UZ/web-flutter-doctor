/*// lib/features/profile/presentation/profile_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// NEW: backend-powered providers & actions (Step 4)
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_actions.dart';

// Existing schedule screen
import 'package:shifa_doc_app_v1/features/schedule/presentation/setup_schedule_screen.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({Key? key}) : super(key: key);
  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

enum _ProfilePanel { profile, contact, payment, settings, extended, password }

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  _ProfilePanel _selected = _ProfilePanel.profile;

  // Password fields remain local (not stored in provider)
  final _passwordFormKey = GlobalKey<FormState>();
  final TextEditingController _currentPassCtrl = TextEditingController();
  final TextEditingController _newPassCtrl = TextEditingController();
  final TextEditingController _confirmPassCtrl = TextEditingController();

  @override
  void dispose() {
    _currentPassCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmPassCtrl.dispose();
    super.dispose();
  }

  // ---------- Helpers ----------

  /// Formats an ISO date string (yyyy-MM-dd) to dd.MM.yyyy.
  String _fmtDobLabel(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final parts = iso.split('-'); // yyyy-MM-dd
    if (parts.length != 3) return '';
    return '${parts[2]}.${parts[1]}.${parts[0]}';
  }

  /// Shows a confirmation SnackBar when the user saves a section.
  void _saveCurrentSectionBackend({
    required Map<String, dynamic> profile,
    required Map<String, dynamic> contact,
    required Map<String, dynamic> billing,
    required Map<String, dynamic> settings,
  }) {
    // We already persist onChanged in each panel.
    // Keep an explicit Save button UX with a simple SnackBar.
    final l10n = AppLocalizations.of(context)!;
    switch (_selected) {
      case _ProfilePanel.profile:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.profileInformationSaved)),
        );
        break;
      case _ProfilePanel.contact:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.contactDetailsSaved)),
        );
        break;
      case _ProfilePanel.payment:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.paymentAndInvoicingSaved)),
        );
        break;
      case _ProfilePanel.settings:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.settingsSaved)),
        );
        break;
      case _ProfilePanel.password:
        if (_passwordFormKey.currentState?.validate() ?? false) {
          if (_newPassCtrl.text != _confirmPassCtrl.text) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(AppLocalizations.of(context)!.newPasswordConfirmationMismatchError),
                backgroundColor: Colors.red,
              ),
            );
            return;
          }
          // TODO: integrate backend password update
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text(AppLocalizations.of(context)!.passwordUpdated)));
          _currentPassCtrl.clear();
          _newPassCtrl.clear();
          _confirmPassCtrl.clear();
        }
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;

    // Fetch all profile sections (profile / contact / billing / settings) from backend
    final allAsync = ref.watch(profileAllProvider);

    return allAsync.when(
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (e, st) {
        debugPrint('Profile load error: $e $st');
        return Scaffold(body: Center(child: Text(AppLocalizations.of(context)!.somethingWentWrong)));
      },
      data: (all) {
        // Unpack maps from provider (all fields are dynamic maps)
        final profile = all.profile; // {firstName,lastName,dob,address,...}
        final contact = all.contact; // {phone,email}
        final billing = all.billing; // {billingName,billingEmail,iban,taxId}
        final settings = all.settings; // {country,language,twoFA,encryptedDocs}

        // Derive some labels for the left list
        final fullName = [
          profile['firstName'] ?? '',
          profile['lastName'] ?? '',
        ].where((s) => (s as String).isNotEmpty).join(' ').trim();
        final profession = (profile['profession'] as String?) ?? '';

        String? dobLabel;
        if (profile['dob'] != null && (profile['dob'] as String).isNotEmpty) {
          dobLabel = _fmtDobLabel(profile['dob'] as String);
        }

        return Scaffold(
          backgroundColor: const Color(0xFFF5F5F5),
          body: Padding(
            padding: const EdgeInsets.all(24),
            child: Row(
              children: [
                // ---------------- LEFT: Sections list ----------------
                Expanded(
                  flex: 3,
                  child: ListView(
                    children: [
                      Text(
                        AppLocalizations.of(context)!.profile,
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 16),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.profileInformation,
                        subtitleLines: [
                          fullName.isEmpty ? '—' : fullName,
                          dobLabel ?? '—',
                          profession.isNotEmpty ? profession : '—',
                          (profile['address'] as String?)?.isNotEmpty == true
                              ? profile['address'] as String
                              : '—',
                        ],
                        selected: _selected == _ProfilePanel.profile,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.profile),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.contactDetails,
                        subtitleLines: [
                          (contact['phone'] as String?)?.isNotEmpty == true
                              ? contact['phone'] as String
                              : '—',
                          (contact['email'] as String?)?.isNotEmpty == true
                              ? contact['email'] as String
                              : '—',
                        ],
                        selected: _selected == _ProfilePanel.contact,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.contact),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.paymentAndInvoicing,
                        subtitleLines: [
                          (billing['billingName'] as String?)?.isNotEmpty ==
                                  true
                              ? billing['billingName'] as String
                              : '—',
                          (billing['billingEmail'] as String?)?.isNotEmpty ==
                                  true
                              ? billing['billingEmail'] as String
                              : '—',
                          (billing['iban'] as String?)?.isNotEmpty == true
                              ? billing['iban'] as String
                              : '—',
                          (billing['taxId'] as String?)?.isNotEmpty == true
                              ? billing['taxId'] as String
                              : '—',
                        ],
                        selected: _selected == _ProfilePanel.payment,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.payment),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.settings,
                        subtitleLines: [
                          '${AppLocalizations.of(context)!.country}: ${settings['country'] ?? '—'}',
                          '${AppLocalizations.of(context)!.language}: ${settings['language'] ?? '—'}',
                          (settings['twoFA'] == true)
                              ? '${AppLocalizations.of(context)!.twoFactorAuthentication}: ${AppLocalizations.of(context)!.on}'
                              : '${AppLocalizations.of(context)!.twoFactorAuthentication}: ${AppLocalizations.of(context)!.off}',
                          (settings['encryptedDocs'] == false)
                              ? '${AppLocalizations.of(context)!.encryptedDocuments}: ${AppLocalizations.of(context)!.off}'
                              : '${AppLocalizations.of(context)!.encryptedDocuments}: ${AppLocalizations.of(context)!.on}',
                        ],
                        selected: _selected == _ProfilePanel.settings,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.settings),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.schedule,
                        subtitleLines: [AppLocalizations.of(context)!.updateOrChangeSchedule],
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const ScheduleScreen(),
                          ),
                        ),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.password,
                        subtitleLines: [
                          AppLocalizations.of(context)!.changeOrResetPassword,
                        ],
                        selected: _selected == _ProfilePanel.password,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.password),
                      ),
                    ],
                  ),
                ),

                const SizedBox(width: 24),

                // ---------------- RIGHT: Editable panel ----------------
                Expanded(
                  flex: 2,
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 250),
                    switchInCurve: Curves.easeOut,
                    switchOutCurve: Curves.easeIn,
                    child: _buildRightPanelBackend(
                      profile: profile,
                      contact: contact,
                      billing: billing,
                      settings: settings,
                      brand: brand,
                      key: ValueKey(_selected),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  // ------- Panel container with persistent Save button -------
  /// [onSaveAsync] If set, called when Save is pressed before showing SnackBar (e.g. contact panel sends current form values).
  Widget _panelWrapper({
    required Widget child,
    required Map<String, dynamic> profile,
    required Map<String, dynamic> contact,
    required Map<String, dynamic> billing,
    required Map<String, dynamic> settings,
    Future<void> Function()? onSaveAsync,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 12,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              child: child,
            ),
          ),
          const SizedBox(height: 12),
          ShifaPrimaryButton(
            width: ButtonWidth.fill,
            onPressed: () async {
              try {
                if (onSaveAsync != null) await onSaveAsync();
                await _saveCurrentSectionBackend(
                  profile: profile,
                  contact: contact,
                  billing: billing,
                  settings: settings,
                );
              } catch (e, _) {
                if (!context.mounted) return;
                final msg = e.toString().replaceFirst('Exception: ', '');
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(msg),
                    backgroundColor: Colors.red,
                    duration: const Duration(seconds: 5),
                  ),
                );
              }
            },
            label: AppLocalizations.of(context)!.save,
          ),
        ],
      ),
    );
  }

  // ------- Build the right-side editor panels using backend data -------
  Widget _buildRightPanelBackend({
    required Map<String, dynamic> profile,
    required Map<String, dynamic> contact,
    required Map<String, dynamic> billing,
    required Map<String, dynamic> settings,
    required Color brand,
    Key? key,
  }) {
    switch (_selected) {
      case _ProfilePanel.profile:
        {
          final nameCtrl = TextEditingController(
            text: [
              profile['firstName'] ?? '',
              profile['lastName'] ?? '',
            ].where((s) => (s as String).isNotEmpty).join(' ').trim(),
          );
          final dobCtrl = TextEditingController(
            text: _fmtDobLabel(profile['dob'] as String?),
          );
          final addressCtrl = TextEditingController(
            text: (profile['address'] ?? '') as String,
          );
          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _headerAvatarAndName(nameCtrl.text),
                const SizedBox(height: 12),
                TextFormField(
                  controller: nameCtrl,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.fullName),
                  onChanged: (v) {
                    final tokens = v.trim().split(RegExp(r'\s+'));
                    final first = tokens.isNotEmpty ? tokens.first : '';
                    final last = tokens.length > 1
                        ? tokens.sublist(1).join(' ')
                        : '';
                    patchProfile(ref, {'firstName': first, 'lastName': last});
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: dobCtrl,
                  readOnly: true,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.translate('birthDate'),
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.calendar_today),
                      onPressed: () async {
                        final initial =
                            _parseIsoDate(profile['dob'] as String?) ??
                            DateTime(1990, 1, 1);
                        final picked = await showDatePicker(
                          context: context,
                          initialDate: initial,
                          firstDate: DateTime(1900),
                          lastDate: DateTime.now(),
                          helpText: AppLocalizations.of(context)!.selectDateHint,
                        );
                        if (picked != null) {
                          final iso =
                              '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
                          dobCtrl.text = _fmtDobLabel(iso);
                          await patchProfile(ref, {'dob': iso});
                          setState(() {});
                        }
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: addressCtrl,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.address),
                  onChanged: (v) => patchProfile(ref, {'address': v}),
                ),
              ],
            ),
          );
        }

      case _ProfilePanel.contact:
        {
          final phoneCtrl = TextEditingController(
            text: (contact['phone'] ?? '') as String,
          );
          final emailCtrl = TextEditingController(
            text: (contact['email'] ?? '') as String,
          );

          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            onSaveAsync: () async {
              await patchContact(ref, {
                'phone': phoneCtrl.text.trim(),
                'email': emailCtrl.text.trim().isEmpty ? null : emailCtrl.text.trim(),
              });
            },
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle(AppLocalizations.of(context)!.contactDetails),
                const SizedBox(height: 12),
                TextFormField(
                  controller: phoneCtrl,
                  keyboardType: TextInputType.phone,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.phone),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: emailCtrl,
                  keyboardType: TextInputType.emailAddress,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.email),
                ),
              ],
            ),
          );
        }

      case _ProfilePanel.payment:
        {
          final billingNameCtrl = TextEditingController(
            text: (billing['billingName'] ?? '') as String,
          );
          final billingEmailCtrl = TextEditingController(
            text: (billing['billingEmail'] ?? '') as String,
          );
          final ibanCtrl = TextEditingController(
            text: (billing['iban'] ?? '') as String,
          );
          final taxIdCtrl = TextEditingController(
            text: (billing['taxId'] ?? '') as String,
          );

          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _panelTitle(AppLocalizations.of(context)!.paymentAndInvoicing),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: billingNameCtrl,
                    decoration: InputDecoration(hintText: AppLocalizations.of(context)!.billingName),
                    onChanged: (v) => patchBilling(ref, {
                      'billingName': v,
                      'billingEmail': billingEmailCtrl.text,
                      'iban': ibanCtrl.text,
                      'taxId': taxIdCtrl.text,
                    }),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: billingEmailCtrl,
                    keyboardType: TextInputType.emailAddress,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.billingEmail,
                    ),
                    onChanged: (v) => patchBilling(ref, {
                      'billingName': billingNameCtrl.text,
                      'billingEmail': v,
                      'iban': ibanCtrl.text,
                      'taxId': taxIdCtrl.text,
                    }),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: ibanCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.ibanAccountNumber,
                    ),
                    onChanged: (v) => patchBilling(ref, {
                      'billingName': billingNameCtrl.text,
                      'billingEmail': billingEmailCtrl.text,
                      'iban': v,
                      'taxId': taxIdCtrl.text,
                    }),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: taxIdCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.taxIdVatId,
                    ),
                    onChanged: (v) => patchBilling(ref, {
                      'billingName': billingNameCtrl.text,
                      'billingEmail': billingEmailCtrl.text,
                      'iban': ibanCtrl.text,
                      'taxId': v,
                    }),
                  ),
                ],
              ),
            ),
          );
        }

      case _ProfilePanel.settings:
        {
          // Provide default fallbacks to avoid null on first load
          final country = (settings['country'] as String?) ?? 'Uzbekistan';
          final language = (settings['language'] as String?) ?? 'English';
          final twoFA = (settings['twoFA'] == true);
          final encDocs = (settings['encryptedDocs'] != false);

          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle(AppLocalizations.of(context)!.settings),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: country,
                  items: const [
                    DropdownMenuItem(value: 'Germany', child: Text(AppLocalizations.of(context)!.germany)),
                    DropdownMenuItem(
                      value: 'Uzbekistan',
                      child: Text(AppLocalizations.of(context)!.uzbekistan),
                    ),
                    DropdownMenuItem(value: 'USA', child: Text(AppLocalizations.of(context)!.usa)),
                    DropdownMenuItem(value: 'Other', child: Text(AppLocalizations.of(context)!.otherCountry)),
                  ],
                  onChanged: (v) => patchSettings(ref, {
                    'country': v,
                    'language': language,
                    'twoFA': twoFA,
                    'encryptedDocs': encDocs,
                  }),
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.country),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: language,
                  items: [
                    DropdownMenuItem(
                      value: 'English',
                      child: Text(AppLocalizations.of(context)!.english),
                    ),
                    DropdownMenuItem(
                      value: 'Uzbek',
                      child: Text(AppLocalizations.of(context)!.uzbek),
                    ),
                    DropdownMenuItem(
                      value: 'Russian',
                      child: Text(AppLocalizations.of(context)!.russian),
                    ),
                    DropdownMenuItem(
                      value: 'German',
                      child: Text(AppLocalizations.of(context)!.translate('german')),
                    ),
                  ],
                  onChanged: (v) => patchSettings(ref, {
                    'country': country,
                    'language': v,
                    'twoFA': twoFA,
                    'encryptedDocs': encDocs,
                  }),
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.language),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(AppLocalizations.of(context)!.twoFactorAuthentication),
                  value: twoFA,
                  activeColor: brand,
                  onChanged: (v) => patchSettings(ref, {
                    'country': country,
                    'language': language,
                    'twoFA': v,
                    'encryptedDocs': encDocs,
                  }),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(AppLocalizations.of(context)!.encryptedDocuments),
                  value: encDocs,
                  activeColor: brand,
                  onChanged: (v) => patchSettings(ref, {
                    'country': country,
                    'language': language,
                    'twoFA': twoFA,
                    'encryptedDocs': v,
                  }),
                ),
              ],
            ),
          );
        }

      case _ProfilePanel.password:
        return _panelWrapper(
          profile: profile,
          contact: contact,
          billing: billing,
          settings: settings,
          child: Form(
            key: _passwordFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle('Password'),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _currentPassCtrl,
                  obscureText: true,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.currentPassword,
                  ),
                  validator: (v) => (v == null || v.isEmpty)
                      ? (AppLocalizations.of(context)!.currentPasswordIsRequired)
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _newPassCtrl,
                  obscureText: true,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.newPassword),
                  validator: (v) => (v == null || v.length < 6)
                      ? 'Minimum 6 characters'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _confirmPassCtrl,
                  obscureText: true,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.confirmNewPassword,
                  ),
                  validator: (v) => (v == null || v.isEmpty)
                      ? (AppLocalizations.of(context)!.pleaseConfirmNewPasswordError)
                      : null,
                ),
              ],
            ),
          ),
        );
    }
  }
}

// ---------- Small helper widgets (unchanged UI) ----------
Widget _panelTitle(String title) => Align(
  alignment: Alignment.centerLeft,
  child: Text(
    title,
    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
  ),
);

Widget _headerAvatarAndName(String name) {
  return Row(
    children: [
      CircleAvatar(
        radius: 28,
        backgroundColor: Colors.grey.shade300,
        child: const Icon(Icons.person, color: Colors.white, size: 28),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: Text(
          name.isEmpty ? 'Your Name' : name,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          overflow: TextOverflow.ellipsis,
        ),
      ),
    ],
  );
}

class _SectionCard extends StatelessWidget {
  final String title;
  final List<String> subtitleLines;
  final VoidCallback onTap;
  final bool selected;

  const _SectionCard({
    Key? key,
    required this.title,
    required this.subtitleLines,
    required this.onTap,
    this.selected = false,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    return Material(
      elevation: 0,
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected ? brand : Colors.transparent,
              width: selected ? 2 : 1,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 6),
                    ...subtitleLines.map(
                      (s) => Padding(
                        padding: const EdgeInsets.only(bottom: 2),
                        child: Text(
                          s,
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey.shade700,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: Colors.black45),
            ],
          ),
        ),
      ),
    );
  }
}

DateTime? _parseIsoDate(String? iso) {
  if (iso == null || iso.isEmpty) return null;
  final parts = iso.split('-'); // yyyy-MM-dd
  if (parts.length != 3) return null;
  return DateTime(
    int.parse(parts[0]),
    int.parse(parts[1]),
    int.parse(parts[2]),
  );
}

bool _listEquals<T>(List<T> a, List<T> b) {
  if (a.length != b.length) return false;
  for (int i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
*/

// lib/features/profile/presentation/profile_screen.dart
import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

// Providers (you already use them)
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

// Existing schedule screen
import 'package:shifa_doc_app_v1/features/schedule/presentation/setup_schedule_screen.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';

import '../../../state/profile/profile_actions.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'location_picker_widget.dart';
import 'searchable_profession_dropdown.dart';
import 'searchable_timezone_dropdown.dart';
import 'services_pricing_screen.dart';
import 'package:url_launcher/url_launcher.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({Key? key}) : super(key: key);
  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

enum _ProfilePanel { profile, contact, payment, settings, servicesPricing, extended, password }

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  _ProfilePanel _selected = _ProfilePanel.profile;

  final _passwordFormKey = GlobalKey<FormState>();
  final TextEditingController _currentPassCtrl = TextEditingController();
  final TextEditingController _newPassCtrl = TextEditingController();
  final TextEditingController _confirmPassCtrl = TextEditingController();

  // Extended profile controllers (persist across rebuilds)
  final TextEditingController _biographyCtrl = TextEditingController();
  final TextEditingController _telegramCtrl = TextEditingController();
  final TextEditingController _instagramCtrl = TextEditingController();
  final TextEditingController _serviceCtrl = TextEditingController();

  bool _uploadingPhoto = false;
  bool _paymentActionLoading = false;
  String _selectedSubscriptionPlanCode = 'BASIC';
  Timer? _debounceTimer;
  _ProfilePanel? _lastSelectedPanel;
  List<String>? _cachedServices;
  List<String>? _cachedCertificates;

  @override
  void dispose() {
    _currentPassCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmPassCtrl.dispose();
    _biographyCtrl.dispose();
    _telegramCtrl.dispose();
    _instagramCtrl.dispose();
    _serviceCtrl.dispose();
    _debounceTimer?.cancel();
    super.dispose();
  }

  String _fmtDobLabel(String? iso) {
    if (iso == null || iso.isEmpty) return '';
    final parts = iso.split('-'); // yyyy-MM-dd
    if (parts.length != 3) return '';
    return '${parts[2]}.${parts[1]}.${parts[0]}';
  }

  Future<void> _saveCurrentSectionBackend({
    required Map<String, dynamic> profile,
    required Map<String, dynamic> contact,
    required Map<String, dynamic> billing,
    required Map<String, dynamic> settings,
  }) async {
    final l10n = AppLocalizations.of(context)!;
    switch (_selected) {
      case _ProfilePanel.profile:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.profileInformationSaved)),
        );
        break;
      case _ProfilePanel.contact:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.contactDetailsSaved)),
        );
        break;
      case _ProfilePanel.payment:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.paymentAndInvoicingSaved)),
        );
        break;
      case _ProfilePanel.settings:
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.settingsSaved)),
        );
        break;
      case _ProfilePanel.servicesPricing:
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Open Services & Pricing to manage entries')),
        );
        break;
      case _ProfilePanel.extended:
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(AppLocalizations.of(context)!.extendedProfileSaved)));
        break;
      case _ProfilePanel.password:
        if (_passwordFormKey.currentState?.validate() ?? false) {
          if (_newPassCtrl.text != _confirmPassCtrl.text) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(AppLocalizations.of(context)!.newPasswordConfirmationMismatchError),
                backgroundColor: Colors.red,
              ),
            );
            return;
          }
          try {
            await changePassword(
              ref,
              currentPassword: _currentPassCtrl.text.trim(),
              newPassword: _newPassCtrl.text,
            );
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(AppLocalizations.of(context)!.passwordUpdatedSuccessfully)),
              );
              _currentPassCtrl.clear();
              _newPassCtrl.clear();
              _confirmPassCtrl.clear();
            }
          } catch (e) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(e.toString().replaceFirst('Exception: ', '')),
                  backgroundColor: Colors.red,
                ),
              );
            }
          }
        }
        break;
    }
  }

  /// Pick from gallery and upload to backend; backend returns absolute photoUrl.
  /* Future<void> _pickAndUploadPhoto() async {
    final picker = ImagePicker();
    final XFile? file = await picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (file == null) return;

    setState(() => _uploadingPhoto = true);
    try {
      final bytes = await file.readAsBytes();

      // ✅ Now using our action that hits /api/doctors/me/photo with JWT
      final newUrl = await uploadDoctorPhoto(ref, bytes, file.name);

      if (newUrl != null && newUrl.isNotEmpty) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(AppLocalizations.of(context)!.photoUpdated)));
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(AppLocalizations.of(context)!.uploadFailed),
            backgroundColor: Colors.red,
          ),
        );
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${AppLocalizations.of(context)!.uploadFailed}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    } finally {
      setState(() => _uploadingPhoto = false);
    }
  } */

  Future<void> _pickAndUploadPhoto() async {
    final picker = ImagePicker();
    final XFile? file = await picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (file == null) return;
    setState(() => _uploadingPhoto = true);
    try {
      final bytes = await file.readAsBytes();
      final newUrl = await uploadDoctorPhoto(ref, bytes, file.name);

      if (newUrl != null && newUrl.isNotEmpty) {
        // profileAllProvider is already refreshed inside uploadDoctorPhoto; photoCacheBusterProvider
        // is bumped there so the avatar URL gets a new query param and the image refetches.
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(AppLocalizations.of(context)!.photoUpdated)));
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.uploadFailed),
            backgroundColor: Colors.red,
          ),
        );
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${AppLocalizations.of(context)!.uploadFailed}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    } finally {
      setState(() => _uploadingPhoto = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    final allAsync = ref.watch(profileAllProvider);

    return allAsync.when(
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (e, st) {
        debugPrint('Profile load error: $e $st');
        return Scaffold(body: Center(child: Text(AppLocalizations.of(context)!.somethingWentWrong)));
      },
      data: (all) {
        final profile =
            all.profile; // {firstName,lastName,dob,address,photoUrl?}
        final contact = all.contact; // {phone,email}
        final billing = all.billing; // {billingName,billingEmail,iban,taxId}
        final settings = all.settings; // {country,language,twoFA,encryptedDocs}

        final fullName = [
          profile['firstName'] ?? '',
          profile['lastName'] ?? '',
        ].where((s) => (s as String).isNotEmpty).join(' ').trim();

        String? dobLabel;
        if (profile['dob'] != null && (profile['dob'] as String).isNotEmpty) {
          dobLabel = _fmtDobLabel(profile['dob'] as String);
        }

        final rawPhotoUrl = (profile['photoUrl'] as String?);
        final cacheBuster = ref.watch(photoCacheBusterProvider);
        final photoUrl = (rawPhotoUrl != null && rawPhotoUrl.isNotEmpty)
            ? '$rawPhotoUrl${rawPhotoUrl.contains('?') ? '&' : '?'}t=$cacheBuster'
            : null;

        return Scaffold(
          backgroundColor: const Color(0xFFF5F5F5),
          body: Padding(
            padding: const EdgeInsets.all(24),
            child: Row(
              children: [
                // LEFT: Sections
                Expanded(
                  flex: 3,
                  child: ListView(
                    children: [
                      Text(
                        AppLocalizations.of(context)!.profile,
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 16),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.profileInformation,
                        subtitleLines: [
                          fullName.isEmpty ? '—' : fullName,
                          dobLabel ?? '—',
                          (profile['address'] as String?)?.isNotEmpty == true
                              ? profile['address'] as String
                              : '—',
                        ],
                        selected: _selected == _ProfilePanel.profile,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.profile),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.contactDetails,
                        subtitleLines: [
                          (contact['phone'] as String?)?.isNotEmpty == true
                              ? contact['phone'] as String
                              : '—',
                          (contact['email'] as String?)?.isNotEmpty == true
                              ? contact['email'] as String
                              : '—',
                        ],
                        selected: _selected == _ProfilePanel.contact,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.contact),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.paymentAndInvoicing,
                        subtitleLines: [
                          (billing['billingName'] as String?)?.isNotEmpty ==
                                  true
                              ? billing['billingName'] as String
                              : '—',
                          (billing['billingEmail'] as String?)?.isNotEmpty ==
                                  true
                              ? billing['billingEmail'] as String
                              : '—',
                          (billing['iban'] as String?)?.isNotEmpty == true
                              ? billing['iban'] as String
                              : '—',
                          (billing['taxId'] as String?)?.isNotEmpty == true
                              ? billing['taxId'] as String
                              : '—',
                        ],
                        selected: _selected == _ProfilePanel.payment,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.payment),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.settings,
                        subtitleLines: [
                          AppLocalizations.of(context)!.settingsSubtitle,
                        ],
                        selected: _selected == _ProfilePanel.settings,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.settings),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: 'Services & Pricing',
                        subtitleLines: const ['Manage service titles, prices, currencies and descriptions'],
                        selected: _selected == _ProfilePanel.servicesPricing,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.servicesPricing),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.extendedProfile,
                        subtitleLines: [
                          AppLocalizations.of(context)!.extendedProfileSubtitle,
                        ],
                        selected: _selected == _ProfilePanel.extended,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.extended),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.schedule,
                        subtitleLines: [AppLocalizations.of(context)!.updateOrChangeSchedule],
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const ScheduleScreen(),
                          ),
                        ),
                      ),
                      const SizedBox(height: 10),
                      _SectionCard(
                        title: AppLocalizations.of(context)!.password,
                        subtitleLines: [
                          AppLocalizations.of(context)!.changeOrResetPassword,
                        ],
                        selected: _selected == _ProfilePanel.password,
                        onTap: () =>
                            setState(() => _selected = _ProfilePanel.password),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 24),
                // RIGHT: Panel
                Expanded(
                  flex: 2,
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 250),
                    switchInCurve: Curves.easeOut,
                    switchOutCurve: Curves.easeIn,
                    child: _buildRightPanelBackend(
                      profile: profile,
                      contact: contact,
                      billing: billing,
                      settings: settings,
                      brand: brand,
                      photoUrl: photoUrl,
                      key: ValueKey(_selected),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _panelWrapper({
    required Widget child,
    required Map<String, dynamic> profile,
    required Map<String, dynamic> contact,
    required Map<String, dynamic> billing,
    required Map<String, dynamic> settings,
    Future<void> Function()? onSaveAsync,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 12,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              child: child,
            ),
          ),
          const SizedBox(height: 12),
          ShifaPrimaryButton(
            width: ButtonWidth.fill,
            onPressed: () async {
              try {
                if (onSaveAsync != null) await onSaveAsync();
                await _saveCurrentSectionBackend(
                  profile: profile,
                  contact: contact,
                  billing: billing,
                  settings: settings,
                );
              } catch (e, _) {
                if (!context.mounted) return;
                final msg = e.toString().replaceFirst('Exception: ', '');
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(msg),
                    backgroundColor: Colors.red,
                    duration: const Duration(seconds: 5),
                  ),
                );
              }
            },
            label: AppLocalizations.of(context)!.save,
          ),
        ],
      ),
    );
  }

  Widget _buildRightPanelBackend({
    required Map<String, dynamic> profile,
    required Map<String, dynamic> contact,
    required Map<String, dynamic> billing,
    required Map<String, dynamic> settings,
    required Color brand,
    required String? photoUrl,
    Key? key,
  }) {
    final profession = (profile['profession'] as String?) ?? '';
    switch (_selected) {
      case _ProfilePanel.profile:
        {
          final nameCtrl = TextEditingController(
            text: [
              profile['firstName'] ?? '',
              profile['lastName'] ?? '',
            ].where((s) => (s as String).isNotEmpty).join(' ').trim(),
          );
          final dobCtrl = TextEditingController(
            text: _fmtDobLabel(profile['dob'] as String?),
          );
          final addressCtrl = TextEditingController(
            text: (profile['address'] ?? '') as String,
          );
          final clinicCtrl = TextEditingController(
            text: (profile['clinic'] ?? '') as String,
          );

          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _headerAvatarAndName(
                  context,
                  nameCtrl.text,
                  photoUrl: photoUrl,
                  onUpload: _uploadingPhoto ? null : _pickAndUploadPhoto,
                  uploading: _uploadingPhoto,
                ),
                const SizedBox(height: 24),
                
                // ──────────── SECTION 1: Personal Information ────────────
                _sectionHeader(AppLocalizations.of(context)!.translate('personalInformation') ?? 'Personal Information'),
                const SizedBox(height: 12),
                TextFormField(
                  controller: nameCtrl,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.fullName,
                    border: const OutlineInputBorder(),
                  ),
                  onChanged: (v) {
                    // You already have patchProfile(...) in your providers; call it here if available.
                    // patchProfile(ref, {'firstName': first, 'lastName': last});
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: dobCtrl,
                  readOnly: true,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.translate('birthDate'),
                    border: const OutlineInputBorder(),
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.calendar_today),
                      onPressed: () async {
                        final initial =
                            _parseIsoDate(profile['dob'] as String?) ??
                            DateTime(1990, 1, 1);
                        final picked = await showDatePicker(
                          context: context,
                          initialDate: initial,
                          firstDate: DateTime(1900),
                          lastDate: DateTime.now(),
                          helpText: AppLocalizations.of(context)!.selectDateHint,
                        );
                        if (picked != null) {
                          final iso =
                              '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
                          dobCtrl.text = _fmtDobLabel(iso);
                          // await patchProfile(ref, {'dob': iso});
                          setState(() {});
                        }
                      },
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                SearchableProfessionDropdown(
                  value: profession.isNotEmpty ? profession : null,
                  hintText: AppLocalizations.of(context)!.translate('profession') ?? 'Profession',
                  labelText: AppLocalizations.of(context)!.translate('profession') ?? 'Profession',
                  onChanged: (v) {
                    if (v != null) {
                      patchProfile(ref, {'profession': v});
                      setState(() {});
                    }
                  },
                ),
                const SizedBox(height: 32),
                
                // ──────────── SECTION 2: Workplace Information ────────────
                _sectionHeader(AppLocalizations.of(context)!.translate('workplaceInformation') ?? 'Workplace Information'),
                const SizedBox(height: 12),
                TextFormField(
                  controller: clinicCtrl,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.translate('clinicOrWorkplaceName') ?? 'Clinic / Workplace Name',
                    border: const OutlineInputBorder(),
                    helperText: AppLocalizations.of(context)!.translate('enterClinicOrWorkplaceName') ?? 'Enter your clinic or workplace name',
                  ),
                  onChanged: (v) {
                    _debounceTimer?.cancel();
                    _debounceTimer = Timer(const Duration(milliseconds: 600), () {
                      patchProfile(ref, {'clinic': clinicCtrl.text.trim()});
                    });
                  },
                ),
                const SizedBox(height: 16),
                SearchableTimezoneDropdown(
                  value: profile['timeZone'] as String?,
                  hintText: 'Practice timezone (e.g. Europe/Berlin)',
                  labelText: 'Practice timezone',
                  onChanged: (v) {
                    if (v != null) {
                      patchProfile(ref, {'timeZone': v});
                      setState(() {});
                    }
                  },
                ),
                const SizedBox(height: 32),
                
                // ──────────── SECTION 3: Location ────────────
                LocationPickerSection(
                  latitude: (profile['latitude'] as num?)?.toDouble(),
                  longitude: (profile['longitude'] as num?)?.toDouble(),
                  locationCountry: profile['locationCountry'] as String?,
                  locationRegion: profile['locationRegion'] as String?,
                  locationDistrict: profile['locationDistrict'] as String?,
                  locationCity: profile['locationCity'] as String?,
                  locationPostalCode: profile['locationPostalCode'] as String?,
                  locationStreetAddress: profile['locationStreetAddress'] as String?,
                  onLocationSelected: (locationData) {
                    // Send all structured location data to backend
                    patchProfile(ref, locationData);
                  },
                ),
              ],
            ),
          );
        }
      case _ProfilePanel.contact:
        {
          final phoneCtrl = TextEditingController(
            text: (contact['phone'] ?? '') as String,
          );
          final emailCtrl = TextEditingController(
            text: (contact['email'] ?? '') as String,
          );
          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            onSaveAsync: () async {
              await patchContact(ref, {
                'phone': phoneCtrl.text.trim(),
                'email': emailCtrl.text.trim().isEmpty ? null : emailCtrl.text.trim(),
              });
            },
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle(AppLocalizations.of(context)!.contactDetails),
                const SizedBox(height: 12),
                TextFormField(
                  controller: phoneCtrl,
                  keyboardType: TextInputType.phone,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.phone),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: emailCtrl,
                  keyboardType: TextInputType.emailAddress,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.email),
                ),
              ],
            ),
          );
        }
      case _ProfilePanel.payment:
        {
          final billingNameCtrl = TextEditingController(
            text: (billing['billingName'] ?? '') as String,
          );
          final billingEmailCtrl = TextEditingController(
            text: (billing['billingEmail'] ?? '') as String,
          );
          final ibanCtrl = TextEditingController(
            text: (billing['iban'] ?? '') as String,
          );
          final taxIdCtrl = TextEditingController(
            text: (billing['taxId'] ?? '') as String,
          );
          final stripeConnectAccountId = (billing['stripeConnectAccountId'] as String?) ?? '';
          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            onSaveAsync: () async {
              await patchBilling(ref, {
                'billingName': billingNameCtrl.text.trim().isEmpty ? null : billingNameCtrl.text.trim(),
                'billingEmail': billingEmailCtrl.text.trim().isEmpty ? null : billingEmailCtrl.text.trim(),
                'iban': ibanCtrl.text.trim().isEmpty ? null : ibanCtrl.text.trim(),
                'taxId': taxIdCtrl.text.trim().isEmpty ? null : taxIdCtrl.text.trim(),
                'stripeConnectAccountId': stripeConnectAccountId.isEmpty ? null : stripeConnectAccountId,
                'clickMerchantId': billing['clickMerchantId'],
                'paymeMerchantId': billing['paymeMerchantId'],
              });
            },
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _panelTitle(AppLocalizations.of(context)!.paymentAndInvoicing),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: billingNameCtrl,
                    decoration: InputDecoration(hintText: AppLocalizations.of(context)!.billingName),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: billingEmailCtrl,
                    keyboardType: TextInputType.emailAddress,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.billingEmail,
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: ibanCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.ibanAccountNumber,
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: taxIdCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.taxIdVatId,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Stripe Connect payouts',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            stripeConnectAccountId.isEmpty
                                ? 'Not connected'
                                : 'Connected account: $stripeConnectAccountId',
                            style: TextStyle(color: Colors.grey.shade700),
                          ),
                          const SizedBox(height: 10),
                          ShifaPrimaryButton(
                            onPressed: _paymentActionLoading
                                ? null
                                : () async {
                                    setState(() => _paymentActionLoading = true);
                                    try {
                                      final result = await createStripeConnectOnboarding(ref);
                                      final url = result['onboardingUrl']?.toString();
                                      if (url != null && url.isNotEmpty) {
                                        final opened = await launchUrl(
                                          Uri.parse(url),
                                          mode: LaunchMode.externalApplication,
                                        );
                                        if (!opened && mounted) {
                                          ScaffoldMessenger.of(context).showSnackBar(
                                            const SnackBar(
                                              content: Text('Could not open Stripe onboarding page.'),
                                              backgroundColor: Colors.orange,
                                            ),
                                          );
                                        }
                                      } else if (mounted) {
                                        ScaffoldMessenger.of(context).showSnackBar(
                                          const SnackBar(
                                            content: Text('Stripe onboarding URL is missing.'),
                                            backgroundColor: Colors.red,
                                          ),
                                        );
                                      }
                                    } catch (e) {
                                      if (mounted) {
                                        ScaffoldMessenger.of(context).showSnackBar(
                                          SnackBar(
                                            content: Text(e.toString().replaceFirst('Exception: ', '')),
                                            backgroundColor: Colors.red,
                                          ),
                                        );
                                      }
                                    } finally {
                                      if (mounted) {
                                        setState(() => _paymentActionLoading = false);
                                      }
                                    }
                                  },
                            label: stripeConnectAccountId.isEmpty ? 'Connect Stripe' : 'Resume Stripe onboarding',
                            isLoading: _paymentActionLoading,
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Doctor monthly subscription',
                            style: TextStyle(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 8),
                          DropdownButtonFormField<String>(
                            value: _selectedSubscriptionPlanCode,
                            items: const [
                              DropdownMenuItem(value: 'BASIC', child: Text('BASIC')),
                              DropdownMenuItem(value: 'PRO', child: Text('PRO')),
                            ],
                            onChanged: (v) {
                              if (v != null) {
                                setState(() => _selectedSubscriptionPlanCode = v);
                              }
                            },
                            decoration: const InputDecoration(labelText: 'Plan'),
                          ),
                          const SizedBox(height: 10),
                          ShifaPrimaryButton(
                            onPressed: _paymentActionLoading
                                ? null
                                : () async {
                                    setState(() => _paymentActionLoading = true);
                                    try {
                                      final checkout = await createSubscriptionCheckout(
                                        ref,
                                        planCode: _selectedSubscriptionPlanCode,
                                      );
                                      final url = checkout['checkoutUrl']?.toString();
                                      if (url != null && url.isNotEmpty) {
                                        final opened = await launchUrl(
                                          Uri.parse(url),
                                          mode: LaunchMode.externalApplication,
                                        );
                                        if (!opened && mounted) {
                                          ScaffoldMessenger.of(context).showSnackBar(
                                            const SnackBar(
                                              content: Text('Could not open Stripe subscription checkout.'),
                                              backgroundColor: Colors.orange,
                                            ),
                                          );
                                        }
                                      } else if (mounted) {
                                        ScaffoldMessenger.of(context).showSnackBar(
                                          const SnackBar(
                                            content: Text('Stripe subscription checkout URL is missing.'),
                                            backgroundColor: Colors.red,
                                          ),
                                        );
                                      }
                                    } catch (e) {
                                      if (mounted) {
                                        ScaffoldMessenger.of(context).showSnackBar(
                                          SnackBar(
                                            content: Text(e.toString().replaceFirst('Exception: ', '')),
                                            backgroundColor: Colors.red,
                                          ),
                                        );
                                      }
                                    } finally {
                                      if (mounted) {
                                        setState(() => _paymentActionLoading = false);
                                      }
                                    }
                                  },
                            label: 'Start subscription checkout',
                            isLoading: _paymentActionLoading,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        }
      case _ProfilePanel.settings:
        {
          final l10n = AppLocalizations.of(context)!;
          final languageState = ref.watch(languageProvider);
          final currentLanguage = languageState.locale.languageCode;
          final country = (settings['country'] as String?) ?? 'Uzbekistan';
          final twoFA = (settings['twoFA'] == true);
          final encDocs = (settings['encryptedDocs'] != false);

          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle(l10n.settings),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: country,
                  items: [
                    DropdownMenuItem(
                      value: 'Germany',
                      child: Text(AppLocalizations.of(context)!.translate('germany') ?? 'Germany'),
                    ),
                    DropdownMenuItem(
                      value: 'Uzbekistan',
                      child: Text(AppLocalizations.of(context)!.translate('uzbekistan') ?? 'Uzbekistan'),
                    ),
                    DropdownMenuItem(
                      value: 'USA',
                      child: Text(AppLocalizations.of(context)!.translate('usa') ?? 'USA'),
                    ),
                    DropdownMenuItem(
                      value: 'Other',
                      child: Text(AppLocalizations.of(context)!.otherCountry),
                    ),
                  ],
                  onChanged: (v) {
                    patchSettings(ref, {'country': v, 'language': currentLanguage, 'twoFA': twoFA, 'encryptedDocs': encDocs});
                  },
                  decoration: InputDecoration(hintText: l10n.translate('country') ?? 'Country'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: currentLanguage,
                  items: [
                    DropdownMenuItem(
                      value: 'en',
                      child: Text(l10n.english),
                    ),
                    DropdownMenuItem(
                      value: 'uz',
                      child: Text(l10n.uzbek),
                    ),
                    DropdownMenuItem(
                      value: 'ru',
                      child: Text(l10n.russian),
                    ),
                  ],
                  onChanged: (v) async {
                    if (v != null) {
                      final newLocale = Locale(v);
                      await ref.read(languageProvider.notifier).setLanguage(newLocale);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(l10n.languageChanged)),
                      );
                    }
                  },
                  decoration: InputDecoration(hintText: l10n.language),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.translate('twoFactorAuthentication') ?? 'Two-factor Authentication'),
                  value: twoFA,
                  activeColor: brand,
                  onChanged: (v) {
                    patchSettings(ref, {'country': country, 'language': currentLanguage, 'twoFA': v, 'encryptedDocs': encDocs});
                  },
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.translate('encryptedDocuments') ?? 'Encrypted Documents'),
                  value: encDocs,
                  activeColor: brand,
                  onChanged: (v) {
                    patchSettings(ref, {'country': country, 'language': currentLanguage, 'twoFA': twoFA, 'encryptedDocs': v});
                  },
                ),
              ],
            ),
          );
        }
      case _ProfilePanel.extended:
        {
          final biography = (profile['biography'] as String?) ?? '';
          final servicesJson = (profile['services'] as String?) ?? '[]';
          final certificatesJson = (profile['certificates'] as String?) ?? '[]';
          final telegram = (profile['telegram'] as String?) ?? '';
          final instagram = (profile['instagram'] as String?) ?? '';

          // Initialize controllers when switching to this panel or when profile data changes
          if (_lastSelectedPanel != _ProfilePanel.extended) {
            _biographyCtrl.text = biography;
            _telegramCtrl.text = telegram;
            _instagramCtrl.text = instagram;
            _serviceCtrl.clear();
            _lastSelectedPanel = _ProfilePanel.extended;
            _cachedServices = null;
            _cachedCertificates = null;
          }

          // Parse services - always parse from current profile data to ensure we have latest
          List<String> services;
          try {
            final decoded = jsonDecode(servicesJson) as List;
            services = decoded.map((e) => e.toString()).toList();
            // Update cache if it's different (data changed from backend)
            if (_cachedServices == null || !_listEquals(_cachedServices!, services)) {
              _cachedServices = services;
            }
          } catch (e) {
            services = _cachedServices ?? [];
            if (_cachedServices == null) {
              _cachedServices = [];
            }
          }

          // Parse certificates - always parse from current profile data to ensure we have latest
          List<String> certificates;
          try {
            final decoded = jsonDecode(certificatesJson) as List;
            certificates = decoded.map((e) => e.toString()).toList();
            // Update cache if it's different (data changed from backend)
            if (_cachedCertificates == null || !_listEquals(_cachedCertificates!, certificates)) {
              _cachedCertificates = certificates;
            }
          } catch (e) {
            certificates = _cachedCertificates ?? [];
            if (_cachedCertificates == null) {
              _cachedCertificates = [];
            }
          }

          return _panelWrapper(
            profile: profile,
            contact: contact,
            billing: billing,
            settings: settings,
            child: SingleChildScrollView(
                child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _panelTitle(AppLocalizations.of(context)!.extendedProfile),
                  const SizedBox(height: 12),
                  // Biography
                  TextFormField(
                    controller: _biographyCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.biography,
                      labelText: AppLocalizations.of(context)!.biography,
                    ),
                    maxLines: 5,
                    onChanged: (v) {
                      _debounceTimer?.cancel();
                      _debounceTimer = Timer(const Duration(milliseconds: 500), () {
                        patchProfile(ref, {'biography': v});
                      });
                    },
                  ),
                  const SizedBox(height: 16),
                  // Services
                  Text(
                    AppLocalizations.of(context)!.services,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          controller: _serviceCtrl,
                          decoration: InputDecoration(
                            hintText: AppLocalizations.of(context)!.addService,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      ShifaPrimaryButton(
                        onPressed: () async {
                          if (_serviceCtrl.text.trim().isNotEmpty) {
                            final newService = _serviceCtrl.text.trim();
                            final updatedServices = List<String>.from(services)..add(newService);
                            _cachedServices = updatedServices;
                            _serviceCtrl.clear();
                            final servicesJson = jsonEncode(updatedServices);
                            await patchProfile(ref, {'services': servicesJson});
                            // Refresh to get updated data
                            ref.refresh(profileAllProvider);
                            setState(() {});
                          }
                        },
                        label: AppLocalizations.of(context)!.translate('add') ?? 'Add',
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  ...services.map((service) => Card(
                        margin: const EdgeInsets.only(bottom: 4),
                        child: ListTile(
                          title: Text(service),
                          trailing: IconButton(
                            icon: const Icon(Icons.delete, color: Colors.red),
                            onPressed: () async {
                              final updatedServices = List<String>.from(services)..remove(service);
                              _cachedServices = updatedServices;
                              final servicesJson = jsonEncode(updatedServices);
                              await patchProfile(ref, {'services': servicesJson});
                              // Refresh to get updated data
                              ref.refresh(profileAllProvider);
                              setState(() {});
                            },
                          ),
                        ),
                      )),
                  const SizedBox(height: 16),
                  // Certificates
                  Text(
                    AppLocalizations.of(context)!.certificates,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 8),
                  ShifaPrimaryButton(
                    onPressed: () async {
                      final picker = ImagePicker();
                      final file = await picker.pickImage(
                        source: ImageSource.gallery,
                      );
                      if (file != null) {
                        try {
                          final bytes = await file.readAsBytes();
                          final certUrl = await uploadCertificate(ref, bytes, file.name);
                          if (certUrl != null) {
                            _cachedCertificates = null; // Clear cache to force refresh
                            ref.refresh(profileAllProvider);
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(AppLocalizations.of(context)!.certificateUploaded)),
                            );
                            setState(() {});
                          }
                        } catch (e) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('${AppLocalizations.of(context)!.uploadFailed}: $e'),
                              backgroundColor: Colors.red,
                            ),
                          );
                        }
                      }
                    },
                    icon: Icons.upload,
                    label: AppLocalizations.of(context)!.uploadCertificate,
                  ),
                  const SizedBox(height: 8),
                  ...certificates.map((cert) => Card(
                        margin: const EdgeInsets.only(bottom: 4),
                        child: ListTile(
                          title: Text(cert.split('/').last),
                          subtitle: Text(cert),
                          trailing: IconButton(
                            icon: const Icon(Icons.delete, color: Colors.red),
                            onPressed: () async {
                              final updatedCerts = List<String>.from(certificates)..remove(cert);
                              _cachedCertificates = updatedCerts;
                              final certsJson = jsonEncode(updatedCerts);
                              await patchProfile(ref, {'certificates': certsJson});
                              // Refresh to get updated data
                              ref.refresh(profileAllProvider);
                              setState(() {});
                            },
                          ),
                        ),
                      )),
                  const SizedBox(height: 16),
                  // Telegram
                  TextFormField(
                    controller: _telegramCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.socialMediaHint,
                      labelText: AppLocalizations.of(context)!.telegram,
                      prefixIcon: const Icon(Icons.send),
                    ),
                    onChanged: (v) {
                      _debounceTimer?.cancel();
                      _debounceTimer = Timer(const Duration(milliseconds: 500), () {
                        patchProfile(ref, {'telegram': v});
                      });
                    },
                  ),
                  const SizedBox(height: 12),
                  // Instagram
                  TextFormField(
                    controller: _instagramCtrl,
                    decoration: InputDecoration(
                      hintText: AppLocalizations.of(context)!.socialMediaHint,
                      labelText: AppLocalizations.of(context)!.instagram,
                      prefixIcon: const Icon(Icons.camera_alt),
                    ),
                    onChanged: (v) {
                      _debounceTimer?.cancel();
                      _debounceTimer = Timer(const Duration(milliseconds: 500), () {
                        patchProfile(ref, {'instagram': v});
                      });
                    },
                  ),
                ],
              ),
            ),
          );
        }
      case _ProfilePanel.servicesPricing:
        return _panelWrapper(
          profile: profile,
          contact: contact,
          billing: billing,
          settings: settings,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _panelTitle('Services & Pricing'),
              const SizedBox(height: 12),
              const Text(
                'Define billable services with descriptions and multi-currency prices.',
              ),
              const SizedBox(height: 16),
              ShifaPrimaryButton(
                onPressed: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const ServicesPricingScreen()),
                  );
                },
                label: 'Open Services & Pricing',
                icon: Icons.medical_services_outlined,
                width: ButtonWidth.hug,
              ),
            ],
          ),
        );
      case _ProfilePanel.password:
        return _panelWrapper(
          profile: profile,
          contact: contact,
          billing: billing,
          settings: settings,
          child: Form(
            key: _passwordFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle('Password'),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _currentPassCtrl,
                  obscureText: true,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.currentPassword,
                  ),
                  validator: (v) => (v == null || v.isEmpty)
                      ? (AppLocalizations.of(context)!.currentPasswordIsRequired)
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _newPassCtrl,
                  obscureText: true,
                  decoration: InputDecoration(hintText: AppLocalizations.of(context)!.newPassword),
                  validator: (v) => (v == null || v.length < 6)
                      ? 'Minimum 6 characters'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _confirmPassCtrl,
                  obscureText: true,
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context)!.confirmNewPassword,
                  ),
                  validator: (v) => (v == null || v.isEmpty)
                      ? (AppLocalizations.of(context)!.pleaseConfirmNewPasswordError)
                      : null,
                ),
              ],
            ),
          ),
        );
    }
  }
}

Widget _sectionHeader(String title) {
  return Padding(
    padding: const EdgeInsets.only(bottom: 8.0),
    child: Row(
      children: [
        Expanded(
          child: Divider(
            thickness: 1,
            color: Colors.grey.shade300,
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12.0),
          child: Text(
            title,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Colors.grey.shade700,
              letterSpacing: 0.5,
            ),
          ),
        ),
        Expanded(
          child: Divider(
            thickness: 1,
            color: Colors.grey.shade300,
          ),
        ),
      ],
    ),
  );
}

Widget _panelTitle(String title) => Align(
  alignment: Alignment.centerLeft,
  child: Text(
    title,
    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
  ),
);

Widget _headerAvatarAndName(
  BuildContext context,
  String name, {
  required String? photoUrl,
  required Future<void> Function()? onUpload,
  required bool uploading,
}) {
  return Row(
    children: [
      CircleAvatar(
        radius: 28,
        backgroundColor: Colors.grey.shade300,
        backgroundImage: (photoUrl != null && photoUrl.isNotEmpty)
            ? NetworkImage(photoUrl)
            : null,
        // Only attach handler if backgroundImage is not null:
        onBackgroundImageError: (photoUrl != null && photoUrl.isNotEmpty)
            ? (_, __) {}
            : null,
        child: (photoUrl == null || photoUrl.isEmpty)
            ? const Icon(Icons.person, color: Colors.white, size: 28)
            : null,
      ),
      const SizedBox(width: 12),
      Expanded(
        child: Text(
          name.isEmpty ? AppLocalizations.of(context)!.translate('yourName') ?? 'Your Name' : name,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          overflow: TextOverflow.ellipsis,
        ),
      ),
      const SizedBox(width: 8),
      ShifaPrimaryButton(
        onPressed: onUpload,
        isLoading: uploading,
        icon: Icons.upload,
        label: uploading ? AppLocalizations.of(context)!.uploading : AppLocalizations.of(context)!.uploadPhoto,
      ),
    ],
  );
}

class _SectionCard extends StatelessWidget {
  final String title;
  final List<String> subtitleLines;
  final VoidCallback onTap;
  final bool selected;
  const _SectionCard({
    Key? key,
    required this.title,
    required this.subtitleLines,
    required this.onTap,
    this.selected = false,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    return Material(
      elevation: 0,
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected ? brand : Colors.transparent,
              width: selected ? 2 : 1,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 6),
                    ...subtitleLines.map(
                      (s) => Padding(
                        padding: const EdgeInsets.only(bottom: 2),
                        child: Text(
                          s,
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey.shade700,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: Colors.black45),
            ],
          ),
        ),
      ),
    );
  }
}

DateTime? _parseIsoDate(String? iso) {
  if (iso == null || iso.isEmpty) return null;
  final parts = iso.split('-'); // yyyy-MM-dd
  if (parts.length != 3) return null;
  return DateTime(
    int.parse(parts[0]),
    int.parse(parts[1]),
    int.parse(parts[2]),
  );
}

bool _listEquals<T>(List<T> a, List<T> b) {
  if (a.length != b.length) return false;
  for (int i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
