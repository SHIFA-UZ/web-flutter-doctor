import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';

// Paste your original ChatScreen and helpers here:
// _ConversationTile, _ChatHeader, _MessageListView (no logic change).

// ===================== Chat Screen =====================
class ChatScreen extends StatefulWidget {
  const ChatScreen({Key? key}) : super(key: key);

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  static const Color brand = Color(0xFF17C3B2);

  // --- Demo conversations (replace with backend later) ---
  late List<ChatContact> _contacts;
  int? _selectedIndex;

  // Chat input
  final TextEditingController _searchCtrl = TextEditingController();
  final TextEditingController _messageCtrl = TextEditingController();
  final ScrollController _chatScroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _contacts = _seedConversations();
    if (_contacts.isNotEmpty) _selectedIndex = 0;
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _messageCtrl.dispose();
    _chatScroll.dispose();
    super.dispose();
  }

  // Filters left list by search query
  List<ChatContact> get _filteredContacts {
    final q = _searchCtrl.text.trim().toLowerCase();
    if (q.isEmpty) return _contacts;
    return _contacts.where((c) => c.name.toLowerCase().contains(q)).toList();
  }

  // Send a text message
  void _sendMessage() {
    final idx = _selectedIndex;
    if (idx == null) return;

    final text = _messageCtrl.text.trim();
    if (text.isEmpty) return;

    final now = DateTime.now();
    setState(() {
      final msg = ChatMessage(
        id: 'm_${now.millisecondsSinceEpoch}',
        text: text,
        sentAt: now,
        isMine: true,
      );
      _contacts[idx].messages.add(msg);
      _contacts[idx] = _contacts[idx].copyWith(
        lastMessage: text,
        lastActivity: now,
      );
      _messageCtrl.clear();
    });

    // Scroll to bottom after a short delay to let ListView rebuild
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_chatScroll.hasClients) {
        _chatScroll.animateTo(
          _chatScroll.position.maxScrollExtent + 80,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  // Attach button handler (placeholder)
  void _attach() {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Add attachment — TBD')));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            // -------- LEFT: conversations list --------
            Expanded(
              flex: 2,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Chat',
                    style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _searchCtrl,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      hintText: 'Search patients',
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
                      focusedBorder: const OutlineInputBorder(
                        borderRadius: BorderRadius.all(Radius.circular(24)),
                        borderSide: BorderSide(color: brand, width: 2),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: _filteredContacts.isEmpty
                        ? Center(
                            child: Text(
                              'No conversations',
                              style: TextStyle(color: Colors.grey.shade600),
                            ),
                          )
                        : ListView.separated(
                            itemCount: _filteredContacts.length,
                            separatorBuilder: (_, __) =>
                                const SizedBox(height: 12),
                            itemBuilder: (context, i) {
                              final item = _filteredContacts[i];
                              // Map filtered index back to real index
                              final int realIndex = _contacts.indexWhere(
                                (c) => c.id == item.id,
                              );
                              final selected = _selectedIndex == realIndex;
                              return _ConversationTile(
                                contact: item,
                                selected: selected,
                                onTap: () =>
                                    setState(() => _selectedIndex = realIndex),
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),

            const SizedBox(width: 24),

            // -------- RIGHT: chat view --------
            Expanded(
              flex: 3,
              child: Container(
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
                child: _selectedIndex == null
                    ? Center(
                        child: Text(
                          'Select a conversation',
                          style: TextStyle(color: Colors.grey.shade600),
                        ),
                      )
                    : Column(
                        children: [
                          // Header
                          _ChatHeader(contact: _contacts[_selectedIndex!]),
                          const Divider(height: 1),
                          // Messages
                          Expanded(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                              ),
                              child: _MessageListView(
                                messages: _contacts[_selectedIndex!].messages,
                                controller: _chatScroll,
                              ),
                            ),
                          ),
                          const Divider(height: 1),
                          // Composer row
                          SafeArea(
                            top: false,
                            child: Padding(
                              padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
                              child: Row(
                                children: [
                                  // Add / attachments
                                  IconButton(
                                    icon: const Icon(Icons.add),
                                    color: brand,
                                    onPressed: _attach,
                                    tooltip: 'Add file',
                                  ),
                                  const SizedBox(width: 4),
                                  // Input
                                  Expanded(
                                    child: TextField(
                                      controller: _messageCtrl,
                                      minLines: 1,
                                      maxLines: 4,
                                      textInputAction: TextInputAction.send,
                                      onSubmitted: (_) => _sendMessage(),
                                      decoration: InputDecoration(
                                        hintText: 'Type a message',
                                        filled: true,
                                        fillColor: Colors.grey.shade50,
                                        border: OutlineInputBorder(
                                          borderRadius: BorderRadius.circular(
                                            24,
                                          ),
                                          borderSide: BorderSide(
                                            color: Colors.grey.shade300,
                                          ),
                                        ),
                                        focusedBorder: OutlineInputBorder(
                                          borderRadius: BorderRadius.circular(
                                            24,
                                          ),
                                          borderSide: const BorderSide(
                                            color: brand,
                                            width: 2,
                                          ),
                                        ),
                                        contentPadding:
                                            const EdgeInsets.symmetric(
                                              horizontal: 16,
                                              vertical: 12,
                                            ),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  // Send
                                  SizedBox(
                                    height: 44,
                                    child: ElevatedButton.icon(
                                      onPressed: _sendMessage,
                                      icon: const Icon(Icons.send, size: 18),
                                      label: const Text('Send'),
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: brand,
                                        foregroundColor: Colors.white,
                                        shape: RoundedRectangleBorder(
                                          borderRadius: BorderRadius.circular(
                                            24,
                                          ),
                                        ),
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 16,
                                          vertical: 10,
                                        ),
                                        elevation: 0,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Seed demo chats
  List<ChatContact> _seedConversations() {
    DateTime t(int h, int m) => DateTime(
      DateTime.now().year,
      DateTime.now().month,
      DateTime.now().day,
      h,
      m,
    );
    return [
      ChatContact(
        id: 'c1',
        name: 'Jasur Karimov',
        lastMessage: 'Thank you, doctor!',
        lastActivity: t(9, 42),
        unread: 0,
        messages: [
          ChatMessage(
            id: 'm1',
            text: 'Assalomu alaykum, Jasur.',
            sentAt: t(9, 00),
            isMine: true,
          ),
          ChatMessage(
            id: 'm2',
            text: 'Please take the medication twice a day.',
            sentAt: t(9, 01),
            isMine: true,
          ),
          ChatMessage(
            id: 'm3',
            text: 'Thank you, doctor!',
            sentAt: t(9, 42),
            isMine: false,
          ),
        ],
      ),
      ChatContact(
        id: 'c2',
        name: 'Gulnora Yusupova',
        lastMessage: 'When is my next appointment?',
        lastActivity: t(10, 25),
        unread: 2,
        messages: [
          ChatMessage(
            id: 'm1',
            text: 'Hi Gulnora, your test looks good.',
            sentAt: t(10, 10),
            isMine: true,
          ),
          ChatMessage(
            id: 'm2',
            text: 'When is my next appointment?',
            sentAt: t(10, 25),
            isMine: false,
          ),
        ],
      ),
      ChatContact(
        id: 'c3',
        name: 'Ulugbek Tursunov',
        lastMessage: 'I will join via video.',
        lastActivity: t(12, 05),
        unread: 0,
        messages: [
          ChatMessage(
            id: 'm1',
            text: 'Tomorrow 12:30 is fine?',
            sentAt: t(12, 00),
            isMine: true,
          ),
          ChatMessage(
            id: 'm2',
            text: 'Yes, I will join via video.',
            sentAt: t(12, 05),
            isMine: false,
          ),
        ],
      ),
    ];
  }
}

// -------- Left list tile --------
class _ConversationTile extends StatelessWidget {
  const _ConversationTile({
    Key? key,
    required this.contact,
    required this.selected,
    required this.onTap,
  }) : super(key: key);

  final ChatContact contact;
  final bool selected;
  final VoidCallback onTap;

  String _hhmm(DateTime d) =>
      '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.characters.first.toUpperCase();
    return (parts.first.characters.first + parts.last.characters.first)
        .toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    const brand = Color(0xFF17C3B2);
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
                child: Text(
                  _initials(contact.name),
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    color: Colors.grey.shade700,
                  ),
                ),
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
                      contact.lastMessage,
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
                  Text(
                    _hhmm(contact.lastActivity),
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                  if (contact.unread > 0) ...[
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: brand,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '${contact.unread}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                        ),
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

// -------- Chat header --------
class _ChatHeader extends StatelessWidget {
  const _ChatHeader({Key? key, required this.contact}) : super(key: key);
  final ChatContact contact;

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts.first.characters.first.toUpperCase();
    return (parts.first.characters.first + parts.last.characters.first)
        .toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 64,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Row(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: Colors.grey.shade300,
            child: Text(
              _initials(contact.name),
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: Colors.grey.shade700,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              contact.name,
              style: const TextStyle(fontWeight: FontWeight.w700),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

// -------- Messages list with bubbles --------
class _MessageListView extends StatelessWidget {
  const _MessageListView({
    Key? key,
    required this.messages,
    required this.controller,
  }) : super(key: key);

  final List<ChatMessage> messages;
  final ScrollController controller;

  String _hhmm(DateTime d) =>
      '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    // We render in chronological order and keep the list scrolled to bottom.
    return ListView.builder(
      controller: controller,
      padding: const EdgeInsets.symmetric(vertical: 12),
      itemCount: messages.length,
      itemBuilder: (context, i) {
        final m = messages[i];
        final align = m.isMine
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start;
        final rowAlign = m.isMine
            ? MainAxisAlignment.end
            : MainAxisAlignment.start;
        final bubbleColor = m.isMine
            ? const Color(0xFF17C3B2)
            : Colors.grey.shade200;
        final textColor = m.isMine ? Colors.white : Colors.black87;

        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Column(
            crossAxisAlignment: align,
            children: [
              Row(
                mainAxisAlignment: rowAlign,
                children: [
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 520),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: bubbleColor,
                        borderRadius: BorderRadius.only(
                          topLeft: const Radius.circular(16),
                          topRight: const Radius.circular(16),
                          bottomLeft: Radius.circular(m.isMine ? 16 : 4),
                          bottomRight: Radius.circular(m.isMine ? 4 : 16),
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                        child: Text(m.text, style: TextStyle(color: textColor)),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                _hhmm(m.sentAt),
                style: TextStyle(fontSize: 10, color: Colors.grey.shade600),
              ),
            ],
          ),
        );
      },
    );
  }
}
