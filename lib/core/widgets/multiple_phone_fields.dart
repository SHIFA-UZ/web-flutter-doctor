import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

class MultiplePhoneFields extends StatefulWidget {
  const MultiplePhoneFields({
    super.key,
    this.initialPhones = const [''],
    this.labelText,
  });

  final List<String> initialPhones;
  final String? labelText;

  @override
  MultiplePhoneFieldsState createState() => MultiplePhoneFieldsState();
}

class MultiplePhoneFieldsState extends State<MultiplePhoneFields> {
  final List<TextEditingController> _controllers = [];

  @override
  void initState() {
    super.initState();
    final seeds = widget.initialPhones.isEmpty ? [''] : widget.initialPhones;
    _controllers.addAll(seeds.map((seed) => TextEditingController(text: seed)));
  }

  @override
  void dispose() {
    for (final c in _controllers) {
      c.dispose();
    }
    super.dispose();
  }

  List<String> get values => _controllers
      .map((c) => c.text.trim())
      .where((phone) => phone.isNotEmpty)
      .toList();

  void _addField() {
    setState(() => _controllers.add(TextEditingController()));
  }

  void _removeField(int index) {
    if (_controllers.length <= 1) {
      _controllers.first.clear();
      setState(() {});
      return;
    }
    setState(() {
      _controllers.removeAt(index).dispose();
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final label = widget.labelText ?? '${l10n.phoneNumber} (${l10n.optional})';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < _controllers.length; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextField(
                  controller: _controllers[i],
                  keyboardType: TextInputType.phone,
                  decoration: InputDecoration(
                    labelText: i == 0 ? label : null,
                    hintText: l10n.phoneNumber,
                  ),
                ),
              ),
              if (_controllers.length > 1)
                IconButton(
                  tooltip: l10n.remove,
                  onPressed: () => _removeField(i),
                  icon: const Icon(Icons.remove_circle_outline),
                ),
            ],
          ),
        ],
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: _addField,
            icon: const Icon(Icons.add),
            label: Text(l10n.phoneNumber),
          ),
        ),
      ],
    );
  }
}
