import 'package:flutter_riverpod/flutter_riverpod.dart';

enum AskShifaAiPanelState { closed, open, minimized }

class AskShifaAiPanelNotifier extends StateNotifier<AskShifaAiPanelState> {
  AskShifaAiPanelNotifier() : super(AskShifaAiPanelState.closed);

  void open() => state = AskShifaAiPanelState.open;

  void minimize() => state = AskShifaAiPanelState.minimized;

  void close() => state = AskShifaAiPanelState.closed;

  void restore() => state = AskShifaAiPanelState.open;
}

final askShifaAiPanelProvider =
    StateNotifierProvider<AskShifaAiPanelNotifier, AskShifaAiPanelState>(
  (ref) => AskShifaAiPanelNotifier(),
);
