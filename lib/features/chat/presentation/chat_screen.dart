import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';
import 'package:shifa_doc_app_v1/state/chat/chat_actions.dart';
import 'package:shifa_doc_app_v1/state/chat/chat_providers.dart';
import 'package:characters/characters.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/widgets/text_message_bubble.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/widgets/image_message_bubble.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/widgets/voice_message_bubble.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/widgets/document_message_bubble.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/widgets/typing_indicator.dart';
import 'package:shifa_doc_app_v1/core/widgets/inline_voice_recorder_bar.dart';
import 'package:shifa_doc_app_v1/features/chat/services/image_compression_service.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:image_picker/image_picker.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'dart:io';
import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;

class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

/// Chat list filter/sort option for the dropdown next to search.
enum _ChatListFilter {
  newestFirst,
  oldestFirst,
  unreadNewestFirst,
  unreadOldestFirst,
}

class _ChatScreenState extends ConsumerState<ChatScreen> with WidgetsBindingObserver {
  int? _selectedConversationId;
  final TextEditingController _searchCtrl = TextEditingController();
  final TextEditingController _messageCtrl = TextEditingController();
  final ScrollController _chatScroll = ScrollController();
  bool _showSearchResults = false;
  _ChatListFilter _chatFilter = _ChatListFilter.newestFirst;
  bool _isTyping = false;
  String? _typingSenderRole;
  bool _chatVoiceCaptureOpen = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Refresh conversations when screen opens
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.invalidate(conversationsProvider);
    });
    // Start periodic refresh
    _startPeriodicRefresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _searchCtrl.dispose();
    _messageCtrl.dispose();
    _chatScroll.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    // Refresh when app comes to foreground
    if (state == AppLifecycleState.resumed) {
      ref.invalidate(conversationsProvider);
      if (_selectedConversationId != null) {
        ref.invalidate(conversationProvider(_selectedConversationId.toString()));
      }
    }
  }

  void _startPeriodicRefresh() {
    // Refresh conversations every 5 seconds
    Future.delayed(const Duration(seconds: 5), () {
      if (mounted) {
        ref.invalidate(conversationsProvider);
        if (_selectedConversationId != null) {
          ref.invalidate(conversationProvider(_selectedConversationId.toString()));
        }
        _startPeriodicRefresh(); // Schedule next refresh
      }
    });
  }

  void _selectConversation(String conversationId) {
    setState(() {
      _selectedConversationId = int.tryParse(conversationId);
      _showSearchResults = false;
    });
    // Mark as read
    final client = ref.read(apiClientProvider);
    markConversationAsReadWithClient(client: client, conversationId: conversationId).catchError((_) {});
    // Refresh conversations to update unread count
    ref.invalidate(conversationsProvider);
    // Refresh the selected conversation to get latest messages
    ref.invalidate(conversationProvider(conversationId));
  }

  Future<void> _startConversation(UserSearchResult user) async {
    setState(() {
      _showSearchResults = false;
      _searchCtrl.clear();
    });

    final client = ref.read(apiClientProvider);
    try {
      // First, check if a conversation already exists with this user
      final conversations = await fetchConversationsWithClient(client: client);
      final existingConversations = conversations.where(
        (conv) => conv.participantId == user.id,
      ).toList();

      if (existingConversations.isNotEmpty) {
        // Conversation exists, just select it
        final existingConversation = existingConversations.first;
        setState(() {
          _selectedConversationId = int.tryParse(existingConversation.id);
        });
        ref.invalidate(conversationProvider(existingConversation.id));
        return;
      }

      // No existing conversation: start one without sending an automatic message
      final newConversation = await startConversationWithClient(
        client: client,
        recipientDoctorId: user.isDoctor ? user.id : null,
        recipientPatientId: user.isDoctor ? null : user.id,
      );

      // Select the new conversation
      setState(() {
        _selectedConversationId = int.tryParse(newConversation.id);
      });

      ref.invalidate(conversationsProvider);
      ref.invalidate(conversationProvider(newConversation.id));
    } catch (e) {
      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${l10n.failedToStartConversation}: $e')),
        );
      }
    }
  }

  Future<void> _sendMessage({
    String? text,
    String? recipientDoctorId,
    String? recipientPatientId,
  }) async {
    final conversationId = _selectedConversationId?.toString();
    final messageText = text ?? _messageCtrl.text.trim();
    if (messageText.isEmpty && conversationId == null && recipientDoctorId == null && recipientPatientId == null) {
      return;
    }

    final client = ref.read(apiClientProvider);
    try {
      await sendMessageWithClient(
        client: client,
        conversationId: conversationId,
        recipientDoctorId: recipientDoctorId,
        recipientPatientId: recipientPatientId,
        text: messageText.isEmpty ? null : messageText,
      );
      _messageCtrl.clear();
      // Refresh conversations and current conversation
      ref.invalidate(conversationsProvider);
      if (conversationId != null) {
        ref.invalidate(conversationProvider(conversationId));
      }
      // Scroll to bottom
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_chatScroll.hasClients) {
          _chatScroll.animateTo(
            _chatScroll.position.maxScrollExtent + 80,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut,
          );
        }
      });
    } catch (e) {
      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${l10n.failedToSendMessage}: $e')),
        );
      }
    }
  }

  Future<void> _attachFile() async {
    final l10n = AppLocalizations.of(context)!;
    final result = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.image),
              title: Text(l10n.selectImage),
              onTap: () => Navigator.pop(ctx, 'image'),
            ),
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: Text(l10n.takePhoto),
              onTap: () => Navigator.pop(ctx, 'camera'),
            ),
            ListTile(
              leading: const Icon(Icons.insert_drive_file),
              title: Text(l10n.translate('selectDocument') ?? 'Select Document'),
              onTap: () => Navigator.pop(ctx, 'document'),
            ),
            // Voice recording - now supported on web too!
            ListTile(
              leading: const Icon(Icons.mic),
              title: Text(l10n.recordVoice),
              onTap: () => Navigator.pop(ctx, 'voice'),
            ),
          ],
        ),
      ),
    );

    if (result == null) return;

    switch (result) {
      case 'image':
        await _pickAndSendImage(ImageSource.gallery);
        break;
      case 'camera':
        await _pickAndSendImage(ImageSource.camera);
        break;
      case 'document':
        await _pickAndSendDocument();
        break;
      case 'voice':
        setState(() => _chatVoiceCaptureOpen = true);
        break;
    }
  }

  Future<void> _pickAndSendImage(ImageSource source) async {
    final l10n = AppLocalizations.of(context)!;
    try {
      final picker = ImagePicker();
      final image = await picker.pickImage(source: source, imageQuality: 100);
      if (image == null) return;

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.compressingImage)),
      );

      // On web, skip compression (uses dart:io); on mobile compress to save bandwidth
      XFile? toUpload = image;
      if (!kIsWeb) {
        final compressed = await ImageCompressionService.compressImage(image);
        if (compressed != null) toUpload = compressed;
      }

      // Get bytes from XFile (works on all platforms; avoids dart:io File / _Namespace on web)
      final bytes = await toUpload.readAsBytes();
      final fileSize = bytes.length;

      // Upload image
      if (!mounted) return;
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.uploadingFile)),
      );

      final client = ref.read(apiClientProvider);
      final fileUrl = await _uploadChatAttachmentBytes(
        client: client,
        fileBytes: bytes,
        fileName: toUpload.name,
      );

      if (fileUrl == null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.errorUploadingFile),
            backgroundColor: Colors.red,
          ),
        );
        return;
      }

      // Send message with image
      await sendMessageWithClient(
        client: client,
        conversationId: _selectedConversationId?.toString(),
        text: null,
        type: 'image',
        attachmentUrl: fileUrl,
        attachmentName: toUpload.name,
        fileSize: fileSize,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
      ref.invalidate(conversationsProvider);
      if (_selectedConversationId != null) {
        ref.invalidate(conversationProvider(_selectedConversationId.toString()));
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.errorUploadingFile}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _pickAndSendDocument() async {
    final l10n = AppLocalizations.of(context)!;
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png'],
        allowMultiple: false,
        withData: true, // Required for web: provides bytes when path is unavailable
      );

      if (result == null || result.files.isEmpty) return;

      final platformFile = result.files.single;
      final String fileName = platformFile.name;

      // Bytes-based: use PlatformFile.bytes on web; on mobile use path or bytes
      final Uint8List fileBytes;
      if (platformFile.bytes != null) {
        fileBytes = platformFile.bytes!;
      } else if (!kIsWeb && platformFile.path != null) {
        fileBytes = await File(platformFile.path!).readAsBytes();
      } else {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.errorUploadingFile),
            backgroundColor: Colors.red,
          ),
        );
        return;
      }
      final fileSize = fileBytes.length;

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.uploadingFile)),
      );

      final client = ref.read(apiClientProvider);
      final fileUrl = await _uploadChatAttachmentBytes(
        client: client,
        fileBytes: fileBytes,
        fileName: fileName,
      );

      if (fileUrl == null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.errorUploadingFile),
            backgroundColor: Colors.red,
          ),
        );
        return;
      }

      // Determine message type
      final ext = fileName.toLowerCase().split('.').last;
      final messageType = ['pdf', 'doc', 'docx'].contains(ext) ? 'document' : 'image';

      await sendMessageWithClient(
        client: client,
        conversationId: _selectedConversationId?.toString(),
        text: null,
        type: messageType,
        attachmentUrl: fileUrl,
        attachmentName: fileName,
        fileSize: fileSize,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
      ref.invalidate(conversationsProvider);
      if (_selectedConversationId != null) {
        ref.invalidate(conversationProvider(_selectedConversationId.toString()));
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.errorUploadingFile}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _sendVoiceMessage(String filePath, int durationSeconds) async {
    final l10n = AppLocalizations.of(context)!;
    try {
      Uint8List fileBytes;
      int fileSize;
      String fileName;

      if (kIsWeb) {
        // On web, filePath is a blob URL (e.g., "blob:http://localhost:8080/...")
        // We need to fetch it as bytes using HTTP
        if (filePath.startsWith('blob:')) {
          // Fetch the blob URL as bytes
          final response = await http.get(Uri.parse(filePath));
          if (response.statusCode != 200) {
            throw Exception('Failed to fetch blob URL: ${response.statusCode}');
          }
          fileBytes = response.bodyBytes;
        } else if (filePath.startsWith('data:')) {
          // Handle data URL (base64 encoded)
          final base64String = filePath.split(',')[1];
          fileBytes = base64Decode(base64String);
        } else {
          throw Exception('Web voice recording: Invalid file path format: $filePath');
        }
        fileSize = fileBytes.length;
        fileName = 'voice_${DateTime.now().millisecondsSinceEpoch}.wav';
      } else {
        // On mobile, use File API
        final file = File(filePath);
        if (!await file.exists()) return;
        fileBytes = await file.readAsBytes();
        fileSize = fileBytes.length;
        fileName = 'voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      }

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.uploadingFile)),
      );

      final client = ref.read(apiClientProvider);
      final fileUrl = await _uploadChatAttachmentBytes(
        client: client,
        fileBytes: fileBytes,
        fileName: fileName,
      );

      if (fileUrl == null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.errorUploadingFile),
            backgroundColor: Colors.red,
          ),
        );
        return;
      }

      await sendMessageWithClient(
        client: client,
        conversationId: _selectedConversationId?.toString(),
        text: null,
        type: 'voice',
        attachmentUrl: fileUrl,
        attachmentName: fileName,
        fileSize: fileSize,
        duration: durationSeconds,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
      ref.invalidate(conversationsProvider);
      if (_selectedConversationId != null) {
        ref.invalidate(conversationProvider(_selectedConversationId.toString()));
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.errorRecordingVoice}: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<String?> _uploadChatAttachmentBytes({
    required ApiClient client,
    required Uint8List fileBytes,
    required String fileName,
  }) async {
    try {
      final multipartFile = http.MultipartFile.fromBytes(
        'file',
        fileBytes,
        filename: fileName,
      );
      
      final streamedResponse = await client.postMultipart(
        '/api/messages/upload-attachment',
        files: [multipartFile],
      );
      
      final response = await http.Response.fromStream(streamedResponse);
      
      if (response.statusCode >= 200 && response.statusCode < 300) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return json['url'] as String?;
      }
      
      return null;
    } catch (e) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final conversationsAsync = ref.watch(conversationsProvider);
    final searchQuery = _searchCtrl.text.trim();
    final isMobile = Responsive.isMobile(context);
    final showConversation =
        !isMobile || _selectedConversationId != null;

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: Responsive.screenPadding(context),
        child: isMobile
            ? (showConversation
                ? _buildChatPane(context, brand)
                : _buildConversationsPane(
                    context,
                    l10n,
                    brand,
                    conversationsAsync,
                    searchQuery,
                  ))
            : Row(
                children: [
                  Expanded(
                    flex: 2,
                    child: _buildConversationsPane(
                      context,
                      l10n,
                      brand,
                      conversationsAsync,
                      searchQuery,
                    ),
                  ),
                  const SizedBox(width: 24),
                  Expanded(
                    flex: 3,
                    child: _buildChatPane(context, brand),
                  ),
                ],
              ),
      ),
    );
  }

  Widget _buildConversationsPane(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
    AsyncValue<List<ChatContact>> conversationsAsync,
    String searchQuery,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.chat,
          style: Responsive.pageTitleStyle(context),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _searchCtrl,
                onChanged: (_) {
                  setState(() {
                    _showSearchResults = _searchCtrl.text.trim().isNotEmpty;
                  });
                },
                onTap: () {
                  if (_searchCtrl.text.trim().isNotEmpty) {
                    setState(() => _showSearchResults = true);
                  }
                },
                decoration: InputDecoration(
                  hintText: l10n.translate('searchDoctorsAndPatients') ??
                      'Search doctors and patients',
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
                    borderRadius: const BorderRadius.all(Radius.circular(24)),
                    borderSide: BorderSide(color: brand, width: 2),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            PopupMenuButton<_ChatListFilter>(
              tooltip: l10n.translate('filterConversations') ??
                  'Filter conversations',
              icon: Icon(Icons.arrow_drop_down,
                  color: Colors.grey.shade700, size: 28),
              padding: EdgeInsets.zero,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              onSelected: (value) => setState(() => _chatFilter = value),
              itemBuilder: (context) => [
                PopupMenuItem(
                  value: _ChatListFilter.newestFirst,
                  child: Row(
                    children: [
                      Icon(
                        _chatFilter == _ChatListFilter.newestFirst
                            ? Icons.check
                            : Icons.circle_outlined,
                        size: 20,
                        color: brand,
                      ),
                      const SizedBox(width: 8),
                      Text(l10n.translate('newestFirst') ?? 'Newest first'),
                    ],
                  ),
                ),
                PopupMenuItem(
                  value: _ChatListFilter.oldestFirst,
                  child: Row(
                    children: [
                      Icon(
                        _chatFilter == _ChatListFilter.oldestFirst
                            ? Icons.check
                            : Icons.circle_outlined,
                        size: 20,
                        color: brand,
                      ),
                      const SizedBox(width: 8),
                      Text(l10n.translate('oldestFirst') ?? 'Oldest first'),
                    ],
                  ),
                ),
                const PopupMenuDivider(),
                PopupMenuItem(
                  value: _ChatListFilter.unreadNewestFirst,
                  child: Row(
                    children: [
                      Icon(
                        _chatFilter == _ChatListFilter.unreadNewestFirst
                            ? Icons.check
                            : Icons.circle_outlined,
                        size: 20,
                        color: brand,
                      ),
                      const SizedBox(width: 8),
                      Text(l10n.translate('unreadOnlyNewest') ??
                          'Unread only (newest first)'),
                    ],
                  ),
                ),
                PopupMenuItem(
                  value: _ChatListFilter.unreadOldestFirst,
                  child: Row(
                    children: [
                      Icon(
                        _chatFilter == _ChatListFilter.unreadOldestFirst
                            ? Icons.check
                            : Icons.circle_outlined,
                        size: 20,
                        color: brand,
                      ),
                      const SizedBox(width: 8),
                      Text(l10n.translate('unreadOnlyOldest') ??
                          'Unread only (oldest first)'),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 12),
        Expanded(
          child: _showSearchResults && searchQuery.isNotEmpty
              ? _buildSearchResults(searchQuery, brand)
              : _buildConversationsList(conversationsAsync, brand),
        ),
      ],
    );
  }

  Widget _buildChatPane(BuildContext context, Color brand) {
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
      child: _selectedConversationId == null
          ? Center(
              child: Text(
                AppLocalizations.of(context)!.translate('selectConversation') ??
                    'Select a conversation',
                style: TextStyle(color: Colors.grey.shade600),
              ),
            )
          : Column(
              children: [
                if (Responsive.isMobile(context))
                  Material(
                    color: Colors.white,
                    child: InkWell(
                      onTap: () => setState(() => _selectedConversationId = null),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.arrow_back, color: brand),
                            const SizedBox(width: 4),
                            Text(
                              AppLocalizations.of(context)!.chat,
                              style: TextStyle(
                                color: brand,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                Expanded(
                  child: _buildChatView(
                    _selectedConversationId.toString(),
                    brand,
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildSearchResults(String query, Color brand) {
    final searchAsync = ref.watch(userSearchProvider(query));

    return searchAsync.when(
      data: (results) {
        if (results.isEmpty) {
          return Center(
            child: Text(
              AppLocalizations.of(context)!.translate('noUsersFound') ?? 'No users found',
              style: TextStyle(color: Colors.grey.shade600),
            ),
          );
        }
        return ListView.separated(
          itemCount: results.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (context, i) {
            final user = results[i];
            return _UserSearchTile(
              user: user,
              brand: brand,
              onTap: () => _startConversation(user),
            );
          },
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => Center(
        child: Text(
          '${AppLocalizations.of(context)!.error}: $err',
          style: TextStyle(color: Colors.red.shade600),
        ),
      ),
    );
  }

  List<ChatContact> _applyFilterAndSort(List<ChatContact> conversations) {
    final unreadOnly = _chatFilter == _ChatListFilter.unreadNewestFirst || _chatFilter == _ChatListFilter.unreadOldestFirst;
    final newestFirst = _chatFilter == _ChatListFilter.newestFirst || _chatFilter == _ChatListFilter.unreadNewestFirst;
    var list = unreadOnly ? conversations.where((c) => c.unread > 0).toList() : List<ChatContact>.from(conversations);
    list.sort((a, b) {
      final aAt = a.lastActivity ?? DateTime(0);
      final bAt = b.lastActivity ?? DateTime(0);
      return newestFirst ? bAt.compareTo(aAt) : aAt.compareTo(bAt);
    });
    return list;
  }

  Widget _buildConversationsList(
    AsyncValue<List<ChatContact>> conversationsAsync,
    Color brand,
  ) {
    return conversationsAsync.when(
      data: (conversations) {
        final list = _applyFilterAndSort(conversations);
        if (list.isEmpty) {
          return Center(
            child: Text(
              _chatFilter == _ChatListFilter.unreadNewestFirst || _chatFilter == _ChatListFilter.unreadOldestFirst
                  ? (AppLocalizations.of(context)!.translate('noUnreadConversations') ?? 'No unread conversations')
                  : AppLocalizations.of(context)!.noConversations,
              style: TextStyle(color: Colors.grey.shade600),
            ),
          );
        }
        return ListView.separated(
          itemCount: list.length,
          separatorBuilder: (_, __) => const SizedBox(height: 12),
          itemBuilder: (context, i) {
            final conv = list[i];
            final selected = _selectedConversationId == int.tryParse(conv.id);
            return _ConversationTile(
              contact: conv,
              selected: selected,
              brand: brand,
              onTap: () => _selectConversation(conv.id),
            );
          },
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => Center(
        child: Text(
          '${AppLocalizations.of(context)!.error}: $err',
          style: TextStyle(color: Colors.red.shade600),
        ),
      ),
    );
  }

  Widget _buildChatView(String conversationId, Color brand) {
    final conversationAsync = ref.watch(conversationProvider(conversationId));

    return conversationAsync.when(
      data: (data) {
        final conv = data.conversation;
        return Column(
          children: [
            _ChatHeader(
              contact: conv,
              brand: brand,
              onTap: !conv.isDoctor
                  ? () => ShellScope.pushNamed(
                        context,
                        AppRoutes.patientsWithSelection,
                        arguments: conv.participantId,
                      )
                  : null,
            ),
            const Divider(height: 1),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: _MessageListView(
                  messages: data.messages,
                  controller: _chatScroll,
                  brand: brand,
                  showTypingIndicator: _isTyping,
                  typingSenderRole: _typingSenderRole,
                ),
              ),
            ),
            const Divider(height: 1),
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
                child: _chatVoiceCaptureOpen
                    ? InlineVoiceRecorderBar(
                        onRecordingComplete: (filePath, duration) async {
                          setState(() => _chatVoiceCaptureOpen = false);
                          await _sendVoiceMessage(filePath, duration);
                        },
                        onCancel: () {
                          setState(() => _chatVoiceCaptureOpen = false);
                        },
                      )
                    : Row(
                        children: [
                          IconButton(
                            icon: const Icon(Icons.attach_file),
                            color: brand,
                            onPressed: _attachFile,
                            tooltip: AppLocalizations.of(context)!.translate('attachFile') ?? 'Attach file',
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: TextField(
                              controller: _messageCtrl,
                              minLines: 1,
                              maxLines: 4,
                              textInputAction: TextInputAction.send,
                              onSubmitted: (_) => _sendMessage(),
                              decoration: InputDecoration(
                                hintText: AppLocalizations.of(context)!.typeMessage,
                                filled: true,
                                fillColor: Colors.grey.shade50,
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(24),
                                  borderSide: BorderSide(color: Colors.grey.shade300),
                                ),
                                focusedBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(24),
                                  borderSide: BorderSide(color: brand, width: 2),
                                ),
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 16,
                                  vertical: 12,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          ShifaPrimaryButton(
                            onPressed: () => _sendMessage(),
                            icon: Icons.send,
                            label: AppLocalizations.of(context)!.send,
                          ),
                        ],
                      ),
              ),
            ),
          ],
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => Center(
        child: Text(
          '${AppLocalizations.of(context)!.error}: $err',
          style: TextStyle(color: Colors.red.shade600),
        ),
      ),
    );
  }
}

// -------- User Search Tile --------
class _UserSearchTile extends StatelessWidget {
  final UserSearchResult user;
  final Color brand;
  final VoidCallback onTap;

  const _UserSearchTile({
    required this.user,
    required this.brand,
    required this.onTap,
  });

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.characters.first.toUpperCase();
    return (parts.first.characters.first + parts.last.characters.first).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.grey.shade300, width: 1),
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
              CircleAvatar(
                radius: 20,
                backgroundColor: Colors.grey.shade300,
                backgroundImage: user.photoUrl != null ? NetworkImage(user.photoUrl!) : null,
                child: user.photoUrl == null
                    ? Text(
                        _initials(user.name),
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          color: Colors.grey.shade700,
                        ),
                      )
                    : null,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user.name,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      user.isDoctor ? AppLocalizations.of(context)!.doctor : AppLocalizations.of(context)!.patient,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              ),
              Icon(Icons.chat_bubble_outline, color: brand, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}

// -------- Conversation Tile --------
class _ConversationTile extends StatelessWidget {
  final ChatContact contact;
  final bool selected;
  final Color brand;
  final VoidCallback onTap;

  const _ConversationTile({
    required this.contact,
    required this.selected,
    required this.brand,
    required this.onTap,
  });

  String _hhmm(DateTime d) =>
      '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.characters.first.toUpperCase();
    return (parts.first.characters.first + parts.last.characters.first).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
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
              CircleAvatar(
                radius: 20,
                backgroundColor: Colors.grey.shade300,
                backgroundImage: contact.photoUrl != null ? NetworkImage(contact.photoUrl!) : null,
                child: contact.photoUrl == null
                    ? Text(
                        _initials(contact.name),
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          color: Colors.grey.shade700,
                        ),
                      )
                    : null,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      contact.name,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      contact.lastMessage ?? AppLocalizations.of(context)!.noMessages,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  if (contact.lastActivity != null)
                    Text(
                      _hhmm(contact.lastActivity!.toLocal()),
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                    ),
                  if (contact.unread > 0) ...[
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: brand,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '${contact.unread}',
                        style: const TextStyle(color: Colors.white, fontSize: 12),
                      ),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// -------- Chat Header --------
class _ChatHeader extends StatelessWidget {
  final ChatContact contact;
  final Color brand;
  final VoidCallback? onTap;

  const _ChatHeader({required this.contact, required this.brand, this.onTap});

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.characters.first.toUpperCase();
    return (parts.first.characters.first + parts.last.characters.first).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final content = Container(
      height: 64,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Row(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: Colors.grey.shade300,
            backgroundImage: contact.photoUrl != null ? NetworkImage(contact.photoUrl!) : null,
            child: contact.photoUrl == null
                ? Text(
                    _initials(contact.name),
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      color: Colors.grey.shade700,
                    ),
                  )
                : null,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              contact.name,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: onTap != null ? brand : null,
                decoration: onTap != null ? TextDecoration.underline : null,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
    if (onTap != null) {
      return Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: content,
        ),
      );
    }
    return content;
  }
}

// -------- Messages List --------
class _MessageListView extends StatelessWidget {
  final List<ChatMessage> messages;
  final ScrollController controller;
  final Color brand;
  final bool showTypingIndicator;
  final String? typingSenderRole;

  const _MessageListView({
    required this.messages,
    required this.controller,
    required this.brand,
    this.showTypingIndicator = false,
    this.typingSenderRole,
  });

  Widget _buildMessageBubble(ChatMessage message) {
    switch (message.type) {
      case MessageType.text:
        return TextMessageBubble(
          message: message,
          brandColor: brand,
          isMine: message.isMine,
        );
      case MessageType.image:
        return ImageMessageBubble(
          message: message,
          brandColor: brand,
          isMine: message.isMine,
        );
      case MessageType.voice:
        return VoiceMessageBubble(
          message: message,
          brandColor: brand,
          isMine: message.isMine,
        );
      case MessageType.document:
        return DocumentMessageBubble(
          message: message,
          brandColor: brand,
          isMine: message.isMine,
        );
      case MessageType.system:
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: SelectableText(
            message.content.text ?? '',
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade600,
              fontStyle: FontStyle.italic,
            ),
            textAlign: TextAlign.center,
          ),
        );
    }
  }

  @override
  Widget build(BuildContext context) {
    return SelectionArea(
      child: ListView.builder(
        controller: controller,
        padding: const EdgeInsets.symmetric(vertical: 12),
        itemCount: messages.length + (showTypingIndicator ? 1 : 0),
        itemBuilder: (context, i) {
          // Show typing indicator at the end
          if (showTypingIndicator && i == messages.length) {
            return TypingIndicator(
              senderRole: typingSenderRole ?? 'doctor',
              brandColor: brand,
            );
          }

          final m = messages[i];
          // Key ensures each message (especially each image) has a stable identity so
          // Flutter correctly updates when the list changes and multiple images display correctly.
          return KeyedSubtree(
            key: ValueKey('msg_${m.id}_${m.sentAt.millisecondsSinceEpoch}'),
            child: _buildMessageBubble(m),
          );
        },
      ),
    );
  }
}
