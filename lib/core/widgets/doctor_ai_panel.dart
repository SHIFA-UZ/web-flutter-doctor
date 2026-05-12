import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/ai_api.dart';
import '../../core/api/ai_message.dart';
import '../../core/api/ai_api_provider.dart';
import '../../core/widgets/ai_response_text.dart';

class DoctorAiPanel extends ConsumerStatefulWidget {
  const DoctorAiPanel({super.key});

  @override
  ConsumerState<DoctorAiPanel> createState() => _DoctorAiPanelState();
}

class _DoctorAiPanelState extends ConsumerState<DoctorAiPanel> {
  final _controller = TextEditingController();
  final _scroll = ScrollController();

  StreamSubscription<AiStreamEvent>? _sub;
  bool _expanded = false;
  bool _loading = false;
  String _streamingText = '';
  String? _error;
  String? _draftId;
  List<AiMessage> _conversation = [];

  static const int _maxConversationMessages = 12;
  static const String _assistantSystemPrompt =
      'You are Shifa AI, a clinical decision support assistant for licensed doctors. Respond in clear English.\n'
      'Use a DIRECT concise answer for reference questions, coding/classification lists (e.g. ICD-10 examples), definitions, or general medical facts — no mandatory "Assessment / Causes / Red flags / Recommendations" headings.\n'
      'Use those four sections ONLY when the user asks for scenario-based clinical reasoning (symptoms, differentials, risk, next steps for a described case). '
      'If unsure, prefer a direct answer. Avoid inventing red-flag emergencies when risk is negligible. '
      'Do not diagnose definitively or give medication dosages; escalate true emergencies clearly.';

  List<AiMessage> _normalizedConversationFrom(List<AiMessage> source) {
    final trimmed = source.where((m) => m.content.trim().isNotEmpty).toList();
    if (trimmed.isEmpty || trimmed.first.role != 'system') {
      trimmed.insert(0, const AiMessage(role: 'system', content: _assistantSystemPrompt));
    } else {
      trimmed[0] = const AiMessage(role: 'system', content: _assistantSystemPrompt);
    }
    final tail = trimmed.skip(1).toList();
    final keptTail = tail.length > _maxConversationMessages
        ? tail.sublist(tail.length - _maxConversationMessages)
        : tail;
    return [trimmed.first, ...keptTail];
  }

  void _newSession() {
    setState(() {
      _conversation = [];
      _streamingText = '';
      _error = null;
      _draftId = null;
      _loading = false;
    });
  }

  void _ask() {
    final question = _controller.text.trim();
    if (question.isEmpty) return;

    final aiApi = ref.read(aiApiProvider);
    final nextConversation = [..._conversation, AiMessage(role: 'user', content: question)];
    final payloadMessages = _normalizedConversationFrom(nextConversation);

    setState(() {
      _conversation = nextConversation;
      _streamingText = '';
      _error = null;
      _draftId = null;
      _loading = true;
      _expanded = true;
    });
    _controller.clear();

    _sub?.cancel();
    _sub = aiApi
        .streamAi(messages: payloadMessages, question: question, language: 'EN', patientId: null)
        .listen(
          (event) {
            if (event is AiTokenEvent) {
              setState(() => _streamingText += event.token);
              _scroll.jumpTo(_scroll.position.maxScrollExtent + 40);
            } else if (event is AiDraftReadyEvent) {
              setState(() => _draftId = event.draft.draftId);
            }
          },
          onDone: () {
            setState(() {
              final answer = _streamingText.trimRight();
              debugPrint('[Ask Shifa AI][DoctorPanel] Raw response: ${answer.replaceAll('\n', r'\n')}');
              if (answer.isNotEmpty) {
                _conversation = [..._conversation, AiMessage(role: 'assistant', content: answer)];
                _conversation = _normalizedConversationFrom(_conversation);
              }
              _streamingText = '';
              _loading = false;
              _error = null;
            });
          },
          onError: (e) {
            setState(() {
              _loading = false;
              _error = e is AiStreamException ? e.message : e.toString();
            });
          },
        );
  }

  Future<void> _confirmDraft() async {
    if (_draftId == null) return;
    final aiApi = ref.read(aiApiProvider);
    try {
      await aiApi.confirmDraft(_draftId!);
      if (!mounted) return;
      setState(() => _draftId = null);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Draft saved as consultation note.')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Save failed: $e')),
      );
    }
  }

  Future<void> _discardDraft() async {
    if (_draftId == null) return;
    final aiApi = ref.read(aiApiProvider);
    try {
      await aiApi.discardDraft(_draftId!);
      if (!mounted) return;
      setState(() => _draftId = null);
    } catch (_) {}
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      width: _expanded ? 420 : 260,
      height: _expanded ? 420 : 180,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [BoxShadow(blurRadius: 8, color: Colors.black26)],
      ),
      child: Column(
        children: [
          Row(
            children: [
              const Text(
                'Ask Shifa AI',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const Spacer(),
              if (_expanded)
                TextButton(
                  onPressed: _loading ? null : _newSession,
                  child: const Text('New Session'),
                ),
              IconButton(
                icon: Icon(_expanded ? Icons.close : Icons.open_in_full),
                onPressed: () => setState(() => _expanded = !_expanded),
              ),
            ],
          ),
          if (_expanded)
            Expanded(
              child: SingleChildScrollView(
                controller: _scroll,
                child: _buildConversationView(context),
              ),
            ),
          if (_draftId != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(
                children: [
                  Text('AI Draft – Not Confirmed', style: TextStyle(fontSize: 11, color: Colors.grey.shade700)),
                  const Spacer(),
                  TextButton(onPressed: _confirmDraft, child: const Text('Save as Draft Note')),
                  const SizedBox(width: 4),
                  TextButton(onPressed: _discardDraft, child: const Text('Discard')),
                ],
              ),
            ),
          TextField(
            controller: _controller,
            decoration: InputDecoration(
              hintText: 'Ask a clinical question',
              suffixIcon: IconButton(
                icon: _loading
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send),
                onPressed: _loading ? null : _ask,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildConversationView(BuildContext context) {
    if (_error != null) {
      return Text(
        _error!,
        softWrap: true,
        textAlign: TextAlign.start,
        style: TextStyle(
          fontSize: 13,
          color: Theme.of(context).colorScheme.error,
        ),
      );
    }

    final visibleMessages = _conversation.where((m) => m.role != 'system').toList();
    if (visibleMessages.isEmpty && _streamingText.isEmpty) {
      return const Text(
        'Waiting for AI…',
        softWrap: true,
        textAlign: TextAlign.start,
        style: TextStyle(
          fontSize: 13,
          color: Colors.grey,
          fontStyle: FontStyle.italic,
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ...visibleMessages.map(
          (msg) => Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Align(
              alignment: msg.role == 'user' ? Alignment.centerRight : Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  color: msg.role == 'user' ? const Color(0xFFE8F7FA) : Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.grey.shade300),
                ),
                child: AiResponseText(
                  text: msg.content,
                  style: const TextStyle(fontSize: 13, height: 1.45),
                  maxWidth: 320,
                ),
              ),
            ),
          ),
        ),
        if (_streamingText.isNotEmpty)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: Colors.grey.shade300),
            ),
            child: AiResponseText(
              text: _streamingText,
              style: const TextStyle(fontSize: 13, height: 1.45),
              maxWidth: 320,
            ),
          ),
      ],
    );
  }
}
