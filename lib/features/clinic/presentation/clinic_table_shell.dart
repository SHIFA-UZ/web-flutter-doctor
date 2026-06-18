// lib/features/clinic/presentation/clinic_table_shell.dart
//
// Shared spreadsheet-style scaffolding used by every clinic-workspace list
// (Doctors, Patients, Services, Treatment plans, all Finance sub-tabs).
//
// The clinic screens used to render their lists as `ListView` of `ListTile`
// cards which read more like a feed than a sheet of records. Doctors / clinic
// admins routinely need to sort, search and filter these lists, so this file
// gives every tab a single visual + behavioural baseline:
//
//   * A toolbar with a debounced search field, optional filter chips and
//     trailing actions (e.g. an "Add" button).
//   * A horizontally-scrollable Material 3 [DataTable] with consistent
//     density, sortable column headers, and a min-width that matches the
//     viewport so the table fills the tab even when the dataset is narrow.
//   * Unified loading / error / empty states.
//
// All clinic tabs MUST go through this file so the workspace stays uniform.

import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/layout/shifa_scroll_behavior.dart';

/// Layout shell for a clinic data-table screen.
///
/// Renders [toolbar] in a sticky strip at the top followed by the supplied
/// [body] (typically the table or an empty/loading/error state) in the
/// remaining space. Use [ClinicTableShell.async] when wrapping an
/// `AsyncValue`-style provider — it handles loading, error and empty states
/// out of the box and only calls [tableBuilder] for non-empty data.
class ClinicTableShell extends StatelessWidget {
  /// Top toolbar (search field, filter chips, optional CTA buttons). Built
  /// by the caller so each tab can mix in its own controls without coupling
  /// to this widget.
  final Widget toolbar;

  /// Main content area — usually the result of [clinicDataTable], but any
  /// widget works (used by error / empty states).
  final Widget body;

  const ClinicTableShell({
    super.key,
    required this.toolbar,
    required this.body,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
          child: toolbar,
        ),
        const Divider(height: 1),
        Expanded(child: body),
      ],
    );
  }
}

/// Compact, themed search field used by every clinic table toolbar.
///
/// Clears via the trailing "x" icon, debounced calling is the caller's
/// responsibility (most tabs do client-side filtering so no debounce is
/// needed; the few that hit the server wire their own [Timer]).
class ClinicTableSearchField extends StatelessWidget {
  final TextEditingController controller;
  final String hint;
  final ValueChanged<String> onChanged;

  const ClinicTableSearchField({
    super.key,
    required this.controller,
    required this.hint,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      onChanged: onChanged,
      decoration: InputDecoration(
        prefixIcon: const Icon(Icons.search, size: 20),
        hintText: hint,
        isDense: true,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 12,
          vertical: 10,
        ),
        suffixIcon: controller.text.isEmpty
            ? null
            : IconButton(
                icon: const Icon(Icons.close, size: 18),
                onPressed: () {
                  controller.clear();
                  onChanged('');
                },
              ),
      ),
    );
  }
}

/// Spreadsheet-style [DataTable] wrapped in nested scroll views.
///
/// Horizontal scroll is the **outer** axis so its scrollbar stays pinned to
/// the bottom of the visible table viewport. Vertical scroll is inner so
/// long lists do not push the horizontal bar below the fold.
Widget clinicDataTable({
  required BuildContext context,
  required int? sortColumnIndex,
  required bool sortAscending,
  required List<DataColumn> columns,
  required List<DataRow> rows,
  EdgeInsetsGeometry padding = const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
  double dataRowMinHeight = 52,
  double dataRowMaxHeight = 64,
}) {
  return _ClinicDataTable(
    sortColumnIndex: sortColumnIndex,
    sortAscending: sortAscending,
    columns: columns,
    rows: rows,
    padding: padding,
    dataRowMinHeight: dataRowMinHeight,
    dataRowMaxHeight: dataRowMaxHeight,
  );
}

class _ClinicDataTable extends StatefulWidget {
  final int? sortColumnIndex;
  final bool sortAscending;
  final List<DataColumn> columns;
  final List<DataRow> rows;
  final EdgeInsetsGeometry padding;
  final double dataRowMinHeight;
  final double dataRowMaxHeight;

  const _ClinicDataTable({
    required this.sortColumnIndex,
    required this.sortAscending,
    required this.columns,
    required this.rows,
    required this.padding,
    required this.dataRowMinHeight,
    required this.dataRowMaxHeight,
  });

  @override
  State<_ClinicDataTable> createState() => _ClinicDataTableState();
}

class _ClinicDataTableState extends State<_ClinicDataTable> {
  final ScrollController _h = ScrollController();
  final ScrollController _v = ScrollController();

  @override
  void dispose() {
    _h.dispose();
    _v.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (ctx, constraints) {
        // Use the parent's available width (not the full screen) so the
        // table renders correctly inside split-pane layouts. Subtracting
        // the horizontal padding from [widget.padding] is overkill since
        // the inner ConstrainedBox is sized to *minimum*, not exact width.
        final maxWidth = constraints.maxWidth;
        final available = maxWidth.isFinite && maxWidth > 16
            ? maxWidth - 16
            : MediaQuery.of(ctx).size.width - 16;
        return ScrollConfiguration(
          behavior: const ShifaScrollBehavior(),
          child: Scrollbar(
            controller: _h,
            thumbVisibility: true,
            // Horizontal bar is on the outer viewport so it stays at the
            // bottom of the visible table area (not below all rows).
            child: SingleChildScrollView(
              controller: _h,
              scrollDirection: Axis.horizontal,
              padding: widget.padding,
              child: ConstrainedBox(
                constraints: BoxConstraints(minWidth: available),
                child: Scrollbar(
                  controller: _v,
                  thumbVisibility: true,
                  child: SingleChildScrollView(
                    controller: _v,
                    child: DataTable(
                      sortColumnIndex: widget.sortColumnIndex,
                      sortAscending: widget.sortAscending,
                      headingRowHeight: 44,
                      dataRowMinHeight: widget.dataRowMinHeight,
                      dataRowMaxHeight: widget.dataRowMaxHeight,
                      columnSpacing: 24,
                      showCheckboxColumn: false,
                      columns: widget.columns,
                      rows: widget.rows,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Centered empty-state text used by every clinic table when filters return
/// nothing or the underlying provider yielded an empty list.
class ClinicTableEmpty extends StatelessWidget {
  final String text;
  const ClinicTableEmpty(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.grey.shade700),
        ),
      ),
    );
  }
}

/// Lightweight filter chip strip, exposed as a builder so each tab can mix
/// in its own option set without redefining the row layout.
class ClinicFilterChips<T> extends StatelessWidget {
  final List<({T value, String label})> options;
  final T selected;
  final ValueChanged<T> onSelected;

  const ClinicFilterChips({
    super.key,
    required this.options,
    required this.selected,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (final opt in options)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: ChoiceChip(
                label: Text(opt.label),
                selected: selected == opt.value,
                onSelected: (_) => onSelected(opt.value),
              ),
            ),
        ],
      ),
    );
  }
}
