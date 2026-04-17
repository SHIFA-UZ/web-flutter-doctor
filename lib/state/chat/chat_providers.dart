import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/chat/chat_actions.dart';

final conversationsProvider = FutureProvider<List<ChatContact>>((ref) async {
  // Depend on auth token so this provider resets when doctor logs in/out.
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) {
    throw StateError('Not authenticated');
  }
  final client = ref.read(apiClientProvider);
  return fetchConversationsWithClient(client: client);
});

final conversationProvider = FutureProvider.family<ConversationWithMessages, String>((ref, conversationId) async {
  final client = ref.read(apiClientProvider);
  return getConversationWithMessagesWithClient(client: client, conversationId: conversationId);
});

final unreadCountProvider = StreamProvider<int>((ref) async* {
  // Restart stream when auth token changes.
  ref.watch(authTokenProvider);

  while (true) {
    final token = ref.read(authTokenProvider);
    if (token == null || token.isEmpty) {
      yield 0;
      await Future.delayed(const Duration(seconds: 5));
      continue;
    }
    try {
      final client = ref.read(apiClientProvider);
      final count = await getUnreadCountWithClient(client: client);
      yield count;
      await Future.delayed(const Duration(seconds: 10));
    } catch (e) {
      yield 0;
      await Future.delayed(const Duration(seconds: 10));
    }
  }
});

final userSearchProvider =
    FutureProvider.family<List<UserSearchResult>, String>((ref, query) async {
  if (query.trim().isEmpty) return [];

  // Depend on token so search results reset on login/logout.
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) return [];

  final client = ref.read(apiClientProvider);
  return searchUsersWithClient(client: client, query: query);
});
