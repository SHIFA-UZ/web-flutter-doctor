import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

class IntelligenceChatState {
  const IntelligenceChatState({
    required this.sessions,
    required this.activeSessionId,
    this.loaded = false,
  });

  final List<IntelligenceChatSession> sessions;
  final String? activeSessionId;
  final bool loaded;

  IntelligenceChatSession? get active {
    if (activeSessionId == null) return null;
    for (final s in sessions) {
      if (s.id == activeSessionId) return s;
    }
    return null;
  }

  IntelligenceChatState copyWith({
    List<IntelligenceChatSession>? sessions,
    String? activeSessionId,
    bool? loaded,
    bool clearActive = false,
  }) {
    return IntelligenceChatState(
      sessions: sessions ?? this.sessions,
      activeSessionId:
          clearActive ? null : (activeSessionId ?? this.activeSessionId),
      loaded: loaded ?? this.loaded,
    );
  }
}

class IntelligenceChatController extends StateNotifier<IntelligenceChatState> {
  IntelligenceChatController(this._ref)
      : super(const IntelligenceChatState(sessions: [], activeSessionId: null));

  final Ref _ref;

  String _storageKey() {
    final profile = _ref.read(profileAllProvider).valueOrNull?.profile;
    final doctorId =
        (profile?['id'] ?? profile?['doctorId'] ?? 'anonymous').toString();
    return 'shifa_intelligence_chats_v1:$doctorId';
  }

  Future<void> ensureLoaded() async {
    if (state.loaded) return;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey());
    final sessions = raw == null || raw.isEmpty
        ? <IntelligenceChatSession>[]
        : IntelligenceChatSession.decodeList(raw);

    // Cold start: restore chat history for the drawer, but open the Intelligence
    // welcome (empty chat). Within this app session, activeSessionId stays in
    // memory so leaving and re-entering Intelligence resumes the same chat.
    IntelligenceChatSession? welcome;
    for (final s in sessions) {
      if (s.messages.isEmpty) {
        welcome = s;
        break;
      }
    }
    if (welcome == null) {
      welcome = _newSession();
      sessions.insert(0, welcome);
      await _persist(sessions);
    }

    state = IntelligenceChatState(
      sessions: sessions,
      activeSessionId: welcome.id,
      loaded: true,
    );
  }

  IntelligenceChatSession _newSession() {
    final now = DateTime.now();
    return IntelligenceChatSession(
      id: 'chat_${now.millisecondsSinceEpoch}',
      title: 'New chat',
      createdAt: now,
      updatedAt: now,
      messages: [],
    );
  }

  Future<void> _persist(List<IntelligenceChatSession> sessions) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _storageKey(),
      IntelligenceChatSession.encodeList(sessions),
    );
  }

  Future<void> createChat() async {
    await ensureLoaded();
    final created = _newSession();
    final next = [created, ...state.sessions];
    await _persist(next);
    state = state.copyWith(sessions: next, activeSessionId: created.id);
  }

  /// Open the Intelligence welcome (empty chat), without leaving Intelligence mode.
  Future<void> goToWelcome() async {
    await ensureLoaded();
    for (final s in state.sessions) {
      if (s.messages.isEmpty) {
        state = state.copyWith(activeSessionId: s.id);
        return;
      }
    }
    await createChat();
  }

  Future<void> selectChat(String id) async {
    await ensureLoaded();
    if (state.sessions.any((s) => s.id == id)) {
      state = state.copyWith(activeSessionId: id);
    }
  }

  Future<void> deleteChat(String id) async {
    await ensureLoaded();
    final next = state.sessions.where((s) => s.id != id).toList();
    if (next.isEmpty) {
      final created = _newSession();
      next.add(created);
      await _persist(next);
      state = state.copyWith(sessions: next, activeSessionId: created.id);
      return;
    }
    final active = state.activeSessionId == id ? next.first.id : state.activeSessionId;
    await _persist(next);
    state = state.copyWith(sessions: next, activeSessionId: active);
  }

  Future<void> replaceMessages(
    String sessionId,
    List<IntelligenceChatMessage> messages,
  ) async {
    await ensureLoaded();
    final next = state.sessions.map((s) {
      if (s.id != sessionId) return s;
      String title = 'New chat';
      if (messages.isNotEmpty) {
        for (final m in messages) {
          if (m.role == 'user' && m.content.trim().isNotEmpty) {
            title = m.content.trim();
            break;
          }
        }
      }
      return IntelligenceChatSession(
        id: s.id,
        title: title,
        createdAt: s.createdAt,
        updatedAt: DateTime.now(),
        messages: List.of(messages),
      );
    }).toList()
      ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    await _persist(next);
    state = state.copyWith(sessions: next, activeSessionId: sessionId);
  }
}

final intelligenceChatProvider =
    StateNotifierProvider<IntelligenceChatController, IntelligenceChatState>(
  (ref) => IntelligenceChatController(ref),
);
