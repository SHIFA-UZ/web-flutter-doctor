import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_providers.dart';
import 'ai_api.dart';

final aiApiProvider = Provider<AiApi>((ref) {
  final apiClient = ref.read(apiClientProvider);
  return AiApi(apiClient);
});
