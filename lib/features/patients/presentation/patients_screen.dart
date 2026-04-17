import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';

// Paste your original PatientsScreen and its private widgets here:
// _SearchField, _PatientsList, _PatientDetailsCard, _Avatar, _GeneralInfo, _CardBox.
// Change only one thing: fix the _selected getter to avoid casting null.

/// ---------------------- Screen ----------------------
class PatientsScreen extends StatefulWidget {
  const PatientsScreen({Key? key}) : super(key: key);

  @override
  State<PatientsScreen> createState() => _PatientsScreenState();
}

class _PatientsScreenState extends State<PatientsScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _query = '';
  String? _selectedId;

  late List<Patient> _allPatients;

  @override
  void initState() {
    super.initState();

    // --- Demo data (replace with your backend later) ---
    _allPatients = [
      Patient(
        id: 'p1',
        name: 'Jasur Karimov',
        general: PatientGeneral(
          birthDate: DateTime(1990, 3, 12),
          phone: '+998 90 123 45 67',
          email: 'jasur.k@example.com',
          address: 'Tashkent, Yunusabad',
          language: 'Uzbek, Russian',
        ),
        documents: [
          PatientDocument(
            id: 'd1',
            title: 'Blood Test',
            date: DateTime(2024, 09, 21),
          ),
          PatientDocument(
            id: 'd2',
            title: 'MRI Result',
            date: DateTime(2024, 08, 14),
          ),
        ],
      ),
      Patient(
        id: 'p2',
        name: 'Gulnora Yusupova',
        general: PatientGeneral(
          birthDate: DateTime(1985, 6, 1),
          phone: '+998 95 765 43 21',
          email: 'gulnora.y@example.com',
          address: 'Samarkand',
          language: 'Uzbek',
        ),
        documents: [
          PatientDocument(
            id: 'd3',
            title: 'X-Ray',
            date: DateTime(2024, 09, 10),
          ),
        ],
      ),
      Patient(
        id: 'p3',
        name: 'Ulugbek Tursunov',
        general: const PatientGeneral(
          birthDate: null,
          phone: '+998 97 555 00 11',
          address: 'Tashkent',
          language: 'Uzbek, English',
        ),
        documents: [
          PatientDocument(
            id: 'd4',
            title: 'Blood Test',
            date: DateTime(2024, 09, 21),
          ),
          PatientDocument(
            id: 'd5',
            title: 'Blood Pressure Log',
            date: DateTime(2024, 09, 05),
          ),
        ],
      ),
      Patient(
        id: 'p4',
        name: 'Dilshoda Rasulova',
        general: const PatientGeneral(phone: '+998 93 111 22 33'),
        documents: [
          PatientDocument(id: 'd6', title: 'ECG', date: DateTime(2024, 07, 12)),
        ],
      ),
      Patient(
        id: 'p5',
        name: 'Shavkat Nematov',
        general: const PatientGeneral(phone: '+998 99 444 55 66'),
        documents: [
          PatientDocument(
            id: 'd7',
            title: 'Prescription',
            date: DateTime(2024, 06, 20),
          ),
        ],
      ),
      Patient(
        id: 'p6',
        name: 'Nodira Akhmedova',
        general: const PatientGeneral(phone: '+998 90 321 00 77'),
        documents: [
          PatientDocument(
            id: 'd8',
            title: 'Lab Report',
            date: DateTime(2024, 09, 19),
          ),
        ],
      ),
      Patient(
        id: 'p7',
        name: 'Azamat Rakhimov',
        general: const PatientGeneral(phone: '+998 91 700 88 44'),
        documents: [],
      ),
    ];

    // Select the first patient by default (if available)
    if (_allPatients.isNotEmpty) _selectedId = _allPatients.first.id;

    _searchCtrl.addListener(() {
      setState(() => _query = _searchCtrl.text.trim());
    });
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  List<Patient> get _filtered {
    if (_query.isEmpty) return _allPatients;
    final q = _query.toLowerCase();
    return _allPatients.where((p) => p.name.toLowerCase().contains(q)).toList();
  }

  Patient? get _selected {
    if (_allPatients.isEmpty) return null;
    return _allPatients.firstWhere(
      (p) => p.id == _selectedId,
      orElse: () => _allPatients.first,
    );
  }

  String _formatDate(DateTime d) {
    // dd.MM.yyyy
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(d.day)}.${two(d.month)}.${d.year}';
    // If you prefer `intl`, use DateFormat('dd.MM.yyyy').format(d)
  }

  void _addDocument(Patient patient) async {
    // Minimal mock "upload" flow
    String tempTitle = '';
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(
            left: 16,
            right: 16,
            top: 16,
            bottom: 16 + MediaQuery.of(ctx).viewInsets.bottom,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Upload Document',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 12),
              TextField(
                decoration: const InputDecoration(hintText: 'Document title'),
                onChanged: (v) => tempTitle = v,
                autofocus: true,
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Add'),
                ),
              ),
            ],
          ),
        );
      },
    );

    if (tempTitle.trim().isEmpty) return;

    setState(() {
      final updatedDocs = List<PatientDocument>.from(patient.documents)
        ..add(
          PatientDocument(
            id: 'dx_${DateTime.now().millisecondsSinceEpoch}',
            title: tempTitle.trim(),
            date: DateTime.now(),
          ),
        );
      // Replace patient in list immutably
      _allPatients = _allPatients.map((p) {
        if (p.id == patient.id) {
          return p.copyWith(documents: updatedDocs);
        }
        return p;
      }).toList();
    });

    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Document "$tempTitle" uploaded')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final brand = const Color(0xFF17C3B2);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isNarrow =
                constraints.maxWidth < 980; // simple responsiveness

            final leftPane = Expanded(
              flex: isNarrow ? 0 : 2,
              child: SizedBox(
                width: isNarrow ? double.infinity : null,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Patients',
                      style: TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 12),
                    _SearchField(controller: _searchCtrl),
                    const SizedBox(height: 16),
                    Expanded(
                      child: _PatientsList(
                        patients: _filtered,
                        selectedId: _selectedId,
                        onSelect: (id) => setState(() => _selectedId = id),
                      ),
                    ),
                  ],
                ),
              ),
            );

            final rightPane = Expanded(
              flex: 3,
              child: _PatientDetailsCard(
                patient: _selected,
                brand: brand,
                onUpload: (p) => _addDocument(p),
                formatDate: _formatDate,
              ),
            );

            if (isNarrow) {
              // Stack vertically on narrow screens
              return Column(
                children: [leftPane, const SizedBox(height: 16), rightPane],
              );
            } else {
              return Row(
                children: [leftPane, const SizedBox(width: 24), rightPane],
              );
            }
          },
        ),
      ),
    );
  }
}

/// ---------------------- Left: search ----------------------
class _SearchField extends StatelessWidget {
  final TextEditingController controller;
  const _SearchField({Key? key, required this.controller}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 420),
      child: TextField(
        controller: controller,
        decoration: InputDecoration(
          hintText: 'Search',
          prefixIcon: const Icon(Icons.search),
          filled: true,
          fillColor: Colors.white,
          contentPadding: const EdgeInsets.symmetric(vertical: 0),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: BorderSide(color: Colors.grey.shade300),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: BorderSide(color: Colors.grey.shade300),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(24),
            borderSide: const BorderSide(color: Color(0xFF17C3B2), width: 2),
          ),
        ),
      ),
    );
  }
}

/// ---------------------- Left: list ----------------------
class _PatientsList extends StatelessWidget {
  final List<Patient> patients;
  final String? selectedId;
  final ValueChanged<String> onSelect;

  const _PatientsList({
    Key? key,
    required this.patients,
    required this.selectedId,
    required this.onSelect,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    if (patients.isEmpty) {
      return const Center(child: Text('No patients found'));
    }

    return ListView.separated(
      itemCount: patients.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final p = patients[index];
        final isSelected = selectedId == p.id;

        return InkWell(
          onTap: () => onSelect(p.id),
          borderRadius: BorderRadius.circular(12),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isSelected
                    ? const Color(0xFF17C3B2)
                    : Colors.transparent,
                width: 2,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Row(
              children: [
                _Avatar(name: p.name, image: p.avatar),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    p.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Icon(Icons.chevron_right, color: Colors.grey.shade600),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// ---------------------- Right: details ----------------------
class _PatientDetailsCard extends StatelessWidget {
  final Patient? patient;
  final Color brand;
  final void Function(Patient) onUpload;
  final String Function(DateTime) formatDate;

  const _PatientDetailsCard({
    Key? key,
    required this.patient,
    required this.brand,
    required this.onUpload,
    required this.formatDate,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    if (patient == null) {
      return const SizedBox(); // nothing selected yet
    }

    final p = patient!;
    final docs = List<PatientDocument>.from(p.documents)
      ..sort((a, b) => b.date.compareTo(a.date)); // newest first

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFF8F8F8),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 14,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        children: [
          // Header
          Row(
            children: [
              _Avatar(size: 44, name: p.name, image: p.avatar),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  p.name,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // General card
          _CardBox(child: _GeneralInfo(general: p.general)),
          const SizedBox(height: 12),

          // Document History title
          Align(
            alignment: Alignment.centerLeft,
            child: Text(
              'Document History',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: Colors.grey.shade800,
              ),
            ),
          ),
          const SizedBox(height: 8),

          // Scrollable list of documents inside the right panel
          Expanded(
            child: docs.isEmpty
                ? const Center(child: Text('No documents yet'))
                : ListView.separated(
                    itemCount: docs.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final d = docs[index];
                      return _CardBox(
                        child: Row(
                          children: [
                            // Doc info
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    d.title,
                                    style: const TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    formatDate(d.date),
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: Colors.grey.shade600,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            // Download button (mock)
                            IconButton.filledTonal(
                              style: ButtonStyle(
                                backgroundColor: WidgetStatePropertyAll(
                                  brand.withOpacity(0.15),
                                ),
                                foregroundColor: WidgetStatePropertyAll(brand),
                              ),
                              onPressed: () {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      'Downloading "${d.title}"...',
                                    ),
                                  ),
                                );
                              },
                              icon: const Icon(Icons.download, size: 20),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
          const SizedBox(height: 12),

          // Upload button
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () => onUpload(p),
              icon: Icon(Icons.upload, color: brand),
              label: Text('Upload Document', style: TextStyle(color: brand)),
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: brand),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
                backgroundColor: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// ---------------------- Bits & pieces ----------------------
class _Avatar extends StatelessWidget {
  final String? image;
  final String name;
  final double size;

  const _Avatar({Key? key, required this.name, this.image, this.size = 24})
    : super(key: key);

  String get initials {
    final parts = name.split(' ');
    if (parts.length == 1) {
      return parts.first.isNotEmpty
          ? parts.first.characters.first.toUpperCase()
          : '?';
    }
    return (parts[0].characters.first + parts[1].characters.first)
        .toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final bg = Colors.grey.shade300;

    if (image != null && image!.isNotEmpty) {
      return CircleAvatar(
        radius: size,
        backgroundImage: AssetImage(image!), // switch to NetworkImage if needed
        backgroundColor: bg,
      );
    }

    return CircleAvatar(
      radius: size,
      backgroundColor: bg,
      child: Text(
        initials,
        style: TextStyle(
          fontWeight: FontWeight.w700,
          color: Colors.grey.shade700,
          fontSize: size * 0.8,
        ),
      ),
    );
  }
}

class _GeneralInfo extends StatelessWidget {
  final PatientGeneral general;
  const _GeneralInfo({Key? key, required this.general}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    String two(int n) => n.toString().padLeft(2, '0');
    String? dob;
    if (general.birthDate != null) {
      final d = general.birthDate!;
      dob = '${two(d.day)}.${two(d.month)}.${d.year}';
    }

    final rows = <MapEntry<String, String>>[
      if (dob != null) MapEntry('Birth Date', dob),
      if (general.phone != null) MapEntry('Phone Number', general.phone!),
      if (general.email != null) MapEntry('Email', general.email!),
      if (general.address != null) MapEntry('Address', general.address!),
      if (general.language != null) MapEntry('Language', general.language!),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'General',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: Colors.grey.shade800,
          ),
        ),
        const SizedBox(height: 8),
        ...rows.map(
          (e) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 2),
            child: Row(
              children: [
                SizedBox(
                  width: 110,
                  child: Text(
                    e.key,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                ),
                Expanded(
                  child: Text(e.value, style: const TextStyle(fontSize: 12)),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _CardBox extends StatelessWidget {
  final Widget child;
  const _CardBox({Key? key, required this.child}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: child,
    );
  }
}
