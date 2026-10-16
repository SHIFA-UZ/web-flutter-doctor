import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Top-level product surface: clinic dashboard vs full-screen Shifa intelligence chat.
enum WorkspaceMode { clinic, intelligence }

final workspaceModeProvider =
    StateNotifierProvider<WorkspaceModeController, WorkspaceMode>(
  (ref) => WorkspaceModeController(),
);

class WorkspaceModeController extends StateNotifier<WorkspaceMode> {
  WorkspaceModeController() : super(WorkspaceMode.clinic);

  void showClinic() => state = WorkspaceMode.clinic;

  void showIntelligence() => state = WorkspaceMode.intelligence;

  void toggle() {
    state = state == WorkspaceMode.clinic
        ? WorkspaceMode.intelligence
        : WorkspaceMode.clinic;
  }
}
