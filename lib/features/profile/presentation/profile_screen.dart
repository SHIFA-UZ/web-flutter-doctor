import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/schedule/presentation/setup_schedule_screen.dart';

// Paste your original ProfileScreen, _SectionCard, and helpers here (unchanged).
// Only navigation to SetupScheduleScreen remains a direct MaterialPageRoute or use AppRoutes if preferred.

// ===================== Profile Screen =====================
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({Key? key}) : super(key: key);

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

enum _ProfilePanel { profile, contact, payment, settings, password }

class _ProfileScreenState extends State<ProfileScreen> {
  final _brand = const Color(0xFF17C3B2);

  // Which panel is open on the right
  _ProfilePanel _selected = _ProfilePanel.profile;

  // ---- Profile Information ----
  final _profileFormKey = GlobalKey<FormState>();
  final TextEditingController _nameCtrl = TextEditingController(
    text: 'Ulbek Karimov',
  );
  final TextEditingController _dobCtrl = TextEditingController(
    text: '19.09.1999',
  ); // dd.MM.yyyy
  final TextEditingController _addressCtrl = TextEditingController(
    text: 'Bird Street 17, 12437 Berlin',
  );

  // ---- Contact Details ----
  final _contactFormKey = GlobalKey<FormState>();
  final TextEditingController _phoneCtrl = TextEditingController(
    text: '+49 123456 4445',
  );
  final TextEditingController _emailCtrl = TextEditingController(
    text: 'doctor@clinic.com',
  );

  // ---- Payment & Invoicing ----
  final _paymentFormKey = GlobalKey<FormState>();
  final TextEditingController _billingNameCtrl = TextEditingController(
    text: 'Ulbek Karimov',
  );
  final TextEditingController _ibanCtrl = TextEditingController(
    text: 'DE12 3456 7890 1234 5678 90',
  );
  final TextEditingController _taxIdCtrl = TextEditingController(
    text: 'DE-123456789',
  );
  final TextEditingController _billingEmailCtrl = TextEditingController(
    text: 'billing@clinic.com',
  );

  // ---- Settings ----
  final _settingsFormKey = GlobalKey<FormState>();
  String _country = 'Germany';
  String _language = 'English';
  bool _twoFA = false;
  bool _encryptedDocs = true;

  // ---- Password ----
  final _passwordFormKey = GlobalKey<FormState>();
  final TextEditingController _currentPassCtrl = TextEditingController();
  final TextEditingController _newPassCtrl = TextEditingController();
  final TextEditingController _confirmPassCtrl = TextEditingController();

  @override
  void dispose() {
    // Profile info
    _nameCtrl.dispose();
    _dobCtrl.dispose();
    _addressCtrl.dispose();
    // Contact
    _phoneCtrl.dispose();
    _emailCtrl.dispose();
    // Payment
    _billingNameCtrl.dispose();
    _ibanCtrl.dispose();
    _taxIdCtrl.dispose();
    _billingEmailCtrl.dispose();
    // Password
    _currentPassCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmPassCtrl.dispose();
    super.dispose();
  }

  // ------------- Actions -------------
  Future<void> _pickDob() async {
    // Parse current text -> initial date
    DateTime initial = DateTime(1999, 9, 19);
    try {
      final parts = _dobCtrl.text.split('.');
      if (parts.length == 3) {
        initial = DateTime(
          int.parse(parts[2]),
          int.parse(parts[1]),
          int.parse(parts[0]),
        );
      }
    } catch (_) {}

    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(1900),
      lastDate: DateTime.now(),
      helpText: 'Select Date of Birth',
    );
    if (picked != null) {
      String two(int n) => n.toString().padLeft(2, '0');
      _dobCtrl.text = '${two(picked.day)}.${two(picked.month)}.${picked.year}';
      setState(() {});
    }
  }

  void _saveCurrentSection() {
    switch (_selected) {
      case _ProfilePanel.profile:
        if (_profileFormKey.currentState!.validate()) {
          // TODO: Persist profile info
          _ok('Profile information saved');
        }
        break;
      case _ProfilePanel.contact:
        if (_contactFormKey.currentState!.validate()) {
          // TODO: Persist contact info
          _ok('Contact details saved');
        }
        break;
      case _ProfilePanel.payment:
        if (_paymentFormKey.currentState!.validate()) {
          // TODO: Persist payment/invoicing info
          _ok('Payment & invoicing saved');
        }
        break;
      case _ProfilePanel.settings:
        if (_settingsFormKey.currentState!.validate()) {
          // TODO: Persist settings
          _ok('Settings saved');
        }
        break;
      case _ProfilePanel.password:
        if (_passwordFormKey.currentState!.validate()) {
          if (_newPassCtrl.text != _confirmPassCtrl.text) {
            _err('New password and confirmation do not match');
            return;
          }
          // TODO: Call backend to change password
          _ok('Password updated');
          _currentPassCtrl.clear();
          _newPassCtrl.clear();
          _confirmPassCtrl.clear();
          setState(() {});
        }
        break;
    }
  }

  void _ok(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  void _err(String msg) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(msg), backgroundColor: Colors.red));

  // ------------- UI -------------
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            // -------- LEFT: sections list --------
            Expanded(
              flex: 3,
              child: ListView(
                children: [
                  const Text(
                    'Profile',
                    style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 16),

                  _SectionCard(
                    title: 'Profile Information',
                    subtitleLines: [
                      _nameCtrl.text.isEmpty ? '—' : _nameCtrl.text,
                      _dobCtrl.text.isEmpty ? '—' : _dobCtrl.text,
                      _addressCtrl.text.isEmpty ? '—' : _addressCtrl.text,
                    ],
                    selected: _selected == _ProfilePanel.profile,
                    onTap: () =>
                        setState(() => _selected = _ProfilePanel.profile),
                  ),
                  const SizedBox(height: 10),

                  _SectionCard(
                    title: 'Contact Details',
                    subtitleLines: [
                      _phoneCtrl.text.isEmpty ? '—' : _phoneCtrl.text,
                      _emailCtrl.text.isEmpty ? '—' : _emailCtrl.text,
                    ],
                    selected: _selected == _ProfilePanel.contact,
                    onTap: () =>
                        setState(() => _selected = _ProfilePanel.contact),
                  ),
                  const SizedBox(height: 10),

                  _SectionCard(
                    title: 'Payment and Invoicing',
                    subtitleLines: [
                      _billingNameCtrl.text.isEmpty
                          ? '—'
                          : _billingNameCtrl.text,
                      _billingEmailCtrl.text.isEmpty
                          ? '—'
                          : _billingEmailCtrl.text,
                      _ibanCtrl.text.isEmpty ? '—' : _ibanCtrl.text,
                      _taxIdCtrl.text.isEmpty ? '—' : _taxIdCtrl.text,
                    ],
                    selected: _selected == _ProfilePanel.payment,
                    onTap: () =>
                        setState(() => _selected = _ProfilePanel.payment),
                  ),
                  const SizedBox(height: 10),

                  _SectionCard(
                    title: 'Settings',
                    subtitleLines: [
                      'Country: $_country',
                      'Language: $_language',
                      if (_twoFA)
                        'Two-factor Authentication: On'
                      else
                        'Two-factor Authentication: Off',
                      if (_encryptedDocs)
                        'Encrypted Documents: On'
                      else
                        'Encrypted Documents: Off',
                    ],
                    selected: _selected == _ProfilePanel.settings,
                    onTap: () =>
                        setState(() => _selected = _ProfilePanel.settings),
                  ),
                  const SizedBox(height: 10),

                  _SectionCard(
                    title: 'Schedule',
                    subtitleLines: const ['Update or change your schedule'],
                    // Schedule opens a page (kept as in your spec)
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const SetupScheduleScreen(),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),

                  _SectionCard(
                    title: 'Password',
                    subtitleLines: const ['Change or reset your password here'],
                    selected: _selected == _ProfilePanel.password,
                    onTap: () =>
                        setState(() => _selected = _ProfilePanel.password),
                  ),
                ],
              ),
            ),

            const SizedBox(width: 24),

            // -------- RIGHT: editable panel --------
            Expanded(
              flex: 2,
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 250),
                switchInCurve: Curves.easeOut,
                switchOutCurve: Curves.easeIn,
                child: _buildRightPanel(key: ValueKey(_selected)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _panelWrapper({required Widget child}) {
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
          Expanded(child: child),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton(
              onPressed: _saveCurrentSection,
              style: ElevatedButton.styleFrom(
                backgroundColor: _brand,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text(
                'Save',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRightPanel({Key? key}) {
    switch (_selected) {
      case _ProfilePanel.profile:
        return _panelWrapper(
          child: Form(
            key: _profileFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _headerAvatarAndName(_nameCtrl.text),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _nameCtrl,
                  decoration: const InputDecoration(hintText: 'Full Name'),
                  validator: (v) => (v == null || v.trim().isEmpty)
                      ? 'Name is required'
                      : null,
                  onChanged: (_) => setState(() {}),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _dobCtrl,
                  readOnly: true,
                  decoration: InputDecoration(
                    hintText: 'Date of birth',
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.calendar_today),
                      onPressed: _pickDob,
                    ),
                  ),
                  onTap: _pickDob,
                  validator: (v) => (v == null || v.trim().isEmpty)
                      ? 'Date of birth is required'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _addressCtrl,
                  decoration: const InputDecoration(hintText: 'Address'),
                  validator: (v) => (v == null || v.trim().isEmpty)
                      ? 'Address is required'
                      : null,
                ),
              ],
            ),
          ),
        );

      case _ProfilePanel.contact:
        return _panelWrapper(
          child: Form(
            key: _contactFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle('Contact Details'),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _phoneCtrl,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(hintText: 'Phone Number'),
                  validator: (v) => (v == null || v.trim().isEmpty)
                      ? 'Phone number is required'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _emailCtrl,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(hintText: 'Email'),
                  validator: (v) {
                    if (v == null || v.trim().isEmpty)
                      return 'Email is required';
                    final ok = RegExp(
                      r'^[^@]+@[^@]+\.[^@]+$',
                    ).hasMatch(v.trim());
                    return ok ? null : 'Invalid email';
                  },
                ),
              ],
            ),
          ),
        );

      case _ProfilePanel.payment:
        return _panelWrapper(
          child: Form(
            key: _paymentFormKey,
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _panelTitle('Payment and Invoicing'),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _billingNameCtrl,
                    decoration: const InputDecoration(hintText: 'Billing Name'),
                    validator: (v) => (v == null || v.trim().isEmpty)
                        ? 'Billing name is required'
                        : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _billingEmailCtrl,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(
                      hintText: 'Billing Email',
                    ),
                    validator: (v) {
                      if (v == null || v.trim().isEmpty)
                        return 'Billing email is required';
                      final ok = RegExp(
                        r'^[^@]+@[^@]+\.[^@]+$',
                      ).hasMatch(v.trim());
                      return ok ? null : 'Invalid email';
                    },
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _ibanCtrl,
                    decoration: const InputDecoration(
                      hintText: 'IBAN / Account number',
                    ),
                    validator: (v) => (v == null || v.trim().isEmpty)
                        ? 'IBAN is required'
                        : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _taxIdCtrl,
                    decoration: const InputDecoration(
                      hintText: 'Tax ID / VAT ID',
                    ),
                  ),
                ],
              ),
            ),
          ),
        );

      case _ProfilePanel.settings:
        return _panelWrapper(
          child: Form(
            key: _settingsFormKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _panelTitle('Settings'),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: _country,
                  items: const [
                    DropdownMenuItem(value: 'Germany', child: Text('Germany')),
                    DropdownMenuItem(
                      value: 'Uzbekistan',
                      child: Text('Uzbekistan'),
                    ),
                    DropdownMenuItem(value: 'USA', child: Text('USA')),
                    DropdownMenuItem(value: 'Other', child: Text('Other')),
                  ],
                  onChanged: (v) => setState(() => _country = v ?? _country),
                  decoration: const InputDecoration(hintText: 'Country'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: _language,
                  items: const [
                    DropdownMenuItem(value: 'English', child: Text('English')),
                    DropdownMenuItem(value: 'Uzbek', child: Text('Uzbek')),
                    DropdownMenuItem(value: 'Russian', child: Text('Russian')),
                    DropdownMenuItem(value: 'German', child: Text('German')),
                  ],
                  onChanged: (v) => setState(() => _language = v ?? _language),
                  decoration: const InputDecoration(hintText: 'Language'),
                ),
                const SizedBox(height: 8),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Two-factor Authentication'),
                  value: _twoFA,
                  activeColor: _brand,
                  onChanged: (v) => setState(() => _twoFA = v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Encrypted Documents'),
                  value: _encryptedDocs,
                  activeColor: _brand,
                  onChanged: (v) => setState(() => _encryptedDocs = v),
                ),
              ],
            ),
          ),
        );

      case _ProfilePanel.password:
        return _panelWrapper(
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
                  decoration: const InputDecoration(
                    hintText: 'Current Password',
                  ),
                  validator: (v) => (v == null || v.isEmpty)
                      ? 'Current password is required'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _newPassCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(hintText: 'New Password'),
                  validator: (v) => (v == null || v.length < 6)
                      ? 'Minimum 6 characters'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _confirmPassCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    hintText: 'Confirm New Password',
                  ),
                  validator: (v) => (v == null || v.isEmpty)
                      ? 'Please confirm new password'
                      : null,
                ),
              ],
            ),
          ),
        );
    }
  }

  // Small helpers
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
            (name.isEmpty ? 'Your Name' : name),
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }
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
    final brand = const Color(0xFF17C3B2);
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
