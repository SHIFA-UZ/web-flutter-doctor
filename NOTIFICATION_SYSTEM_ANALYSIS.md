# Notification System - Complete Analysis

**Date:** March 4, 2026
**Scope:** Doctor web app notification system

---

## System Overview

The app has **two separate notification/badge systems**:

1. **Doctor Notifications** - System notifications (appointments, document access)
2. **Chat Unread Count** - Unread message badge

These are independent systems with different data sources, refresh mechanisms, and display locations.

---

## 1. Doctor Notification System

### Architecture

**Backend-Driven:** Notifications are created by the backend (not the frontend) in response to events:
- Document access requests
- Document access approvals/rejections
- (Potentially) Appointment reminders
- (Potentially) Other system events

**Frontend Role:** Fetch, display, and mark as read.

### Data Model

**File:** `lib/features/notifications/domain/notification_model.dart`

```dart
class DoctorNotificationModel {
  final int id;
  final String title;
  final String message;
  final String type;              // Notification type (enum-like string)
  final int? appointmentId;       // Optional link to appointment
  final int? documentAccessRequestId;
  final String? documentAccessRequestStatus; // "pending" | "approved" | "rejected"
  final String? patientName;      // For document access notifications
  final String? documentTitle;    // For document access notifications
  final String? requestingDoctorName;
  final DateTime createdAt;       // When notification was created (UTC from backend)
  final DateTime? readAt;         // When marked as read (null if unread)
}
```

**Key Properties:**
- `isRead` - Computed: `readAt != null`
- `isDocumentAccessRequest` - Computed: type is request and has requestId
- `isDocumentAccessApproved` - Computed: type is approved
- `isDocumentAccessRejected` - Computed: type is rejected

### Notification Types

Based on code analysis, currently implemented types:

| Type | Description | Icon | Color | Actions |
|------|-------------|------|-------|---------|
| `DOCUMENT_ACCESS_REQUEST` | Doctor requesting access to patient document | lock_open | Teal | Approve/Reject |
| `DOCUMENT_ACCESS_APPROVED` | Your document access request was approved | check_circle | Green | None (info) |
| `DOCUMENT_ACCESS_REJECTED` | Your document access request was rejected | cancel | Red | None (info) |
| Generic (other) | Fallback for any other notification | notifications_outlined | Grey | Mark as read |

**Note:** Code structure suggests more types may be added in future (appointment reminders, etc.)

### API Endpoints

**File:** `lib/state/notifications/doctor_notification_actions.dart`

| Endpoint | Method | Purpose | Returns |
|----------|--------|---------|---------|
| `/api/notifications` | GET | Fetch all notifications for current doctor | `List<DoctorNotificationModel>` |
| `/api/notifications/unread/count` | GET | Get count of unread notifications | `{"count": number}` |
| `/api/notifications/{id}/read` | PUT | Mark single notification as read | Status only |
| `/api/notifications/read-all` | PUT | Mark all notifications as read | Status only |
| `/api/document-access-requests/{id}/approve` | POST | Approve document access request | Status only |
| `/api/document-access-requests/{id}/reject` | POST | Reject document access request | Status only |

### State Management

**File:** `lib/state/notifications/doctor_notifications_provider.dart`

#### Providers

**1. Auto-Refresh Provider:**
```dart
final notificationAutoRefreshProvider = Provider.autoDispose<void>((ref) {
  final timer = Timer.periodic(const Duration(seconds: 20), (_) {
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
  });
  ref.onDispose(() => timer.cancel());
});
```
- Runs a timer that refreshes notifications every 20 seconds
- Watched by MainShell to enable auto-refresh
- Auto-disposes timer when shell is disposed

**2. Notifications List Provider:**
```dart
final doctorNotificationsProvider =
    FutureProvider.autoDispose<List<DoctorNotificationModel>>((ref) async {
  final client = ref.watch(apiClientProvider);
  return fetchDoctorNotificationsWithClient(client: client);
});
```
- FutureProvider that fetches notifications list
- Auto-disposes when not watched
- Re-fetches when invalidated

**3. Unread Count Provider:**
```dart
final doctorNotificationsUnreadCountProvider =
    FutureProvider.autoDispose<int>((ref) async {
  final client = ref.watch(apiClientProvider);
  return fetchDoctorNotificationsUnreadCountWithClient(client: client);
});
```
- FutureProvider that fetches unread count
- Used for badge on sidebar notification icon
- Auto-disposes when not watched

**4. Controller:**
```dart
class DoctorNotificationsController {
  Future<void> refresh();                        // Invalidate providers to refetch
  Future<void> markAsRead(int id);              // Mark single as read
  Future<void> markAllAsRead();                 // Mark all as read
  Future<void> approveDocumentAccessRequest();  // Approve and mark as read
  Future<void> rejectDocumentAccessRequest();   // Reject and mark as read
}
```

### UI Components

**File:** `lib/features/notifications/presentation/notifications_screen.dart`

#### NotificationsScreen
- Full-page screen accessed via sidebar tab
- Shows list of all notifications
- Pull-to-refresh support
- Auto-refreshes on screen init
- "Mark All as Read" button in header

#### _NotificationTile
- Individual notification card
- Unread: Blue background, blue dot indicator
- Read: White background, no indicator
- Time display: Relative (e.g., "2 minutes ago", "3h", "yesterday", "Feb 12")
- **Document Access Requests:** Show Approve/Reject buttons (only if status is "pending")
- **Approved/Rejected:** Show status badge (green/red chip)
- Tapping non-request notifications marks them as read

### Badge Display

**File:** `lib/features/shell/presentation/main_shell.dart`

**Location:** Sidebar notification icon (5th tab)

```dart
Widget _buildNotificationsNavItem(...) {
  final unreadAsync = ref.watch(doctorNotificationsUnreadCountProvider);

  return Stack(
    children: [
      Icon(Icons.notifications_outlined),
      unreadAsync.when(
        data: (count) {
          if (count > 0) {
            return Positioned(
              right: -4, top: -4,
              child: Container(
                // Red circle badge
                child: Text(count > 99 ? '99+' : count.toString()),
              ),
            );
          }
          return SizedBox.shrink();
        },
      ),
    ],
  );
}
```

**Appearance:**
- Red circle with white text
- Shows count (1-99, or "99+" if more)
- Updates automatically via 20-second polling
- Hidden when count is 0

### Auto-Refresh Behavior

**Triggers:**
1. **Periodic (20s):** When MainShell is active
2. **Manual:** Pull-to-refresh on notifications screen
3. **After Actions:** After approve/reject document access
4. **App Resume:** When app comes back to foreground

**MainShell Integration:**
```dart
@override
Widget build(BuildContext context) {
  // Activate 20s auto-refresh while shell is mounted
  ref.watch(notificationAutoRefreshProvider);
  ...
}

@override
void didChangeAppLifecycleState(AppLifecycleState state) {
  if (state == AppLifecycleState.resumed) {
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
  }
}
```

### How Notifications Are Created

**Frontend does NOT create notifications directly.** Backend creates them in response to:

1. **Document Access Request:**
   - Doctor A requests access to Doctor B's patient document
   - Frontend calls: `POST /api/patients/{id}/documents/{id}/request-access`
   - Backend creates notification for Doctor B
   - Notification type: `DOCUMENT_ACCESS_REQUEST`

2. **Document Access Approval:**
   - Doctor B approves request
   - Frontend calls: `POST /api/document-access-requests/{id}/approve`
   - Backend creates notification for Doctor A
   - Notification type: `DOCUMENT_ACCESS_APPROVED`

3. **Document Access Rejection:**
   - Doctor B rejects request
   - Frontend calls: `POST /api/document-access-requests/{id}/reject`
   - Backend creates notification for Doctor A
   - Notification type: `DOCUMENT_ACCESS_REJECTED`

4. **(Future) Other Events:**
   - Backend can create notifications for any event
   - Examples: appointment reminders, task assignments, system alerts

---

## 2. Chat Unread Count System

### Architecture

**Separate from doctor notifications** - different API endpoints, different data source.

### Data Source

**File:** `lib/state/chat/chat_providers.dart`

```dart
final unreadCountProvider = StreamProvider<int>((ref) async* {
  final client = ref.read(apiClientProvider);
  while (true) {
    try {
      final count = await getUnreadCountWithClient(client: client);
      yield count;
      await Future.delayed(const Duration(seconds: 10)); // Poll every 10 seconds
    } catch (e) {
      yield 0;
      await Future.delayed(const Duration(seconds: 10));
    }
  }
});
```

**Key Differences from Doctor Notifications:**
- StreamProvider (not FutureProvider) - continuous polling
- **10-second refresh** (faster than notifications' 20s)
- Returns int count only (not list of messages)
- Different API endpoint: `/api/chat/unread-count` (inferred)

### API Endpoint

**File:** `lib/state/chat/chat_actions.dart` (assumed - not read yet)

Likely: `GET /api/chat/unread-count` or similar

### Badge Display

**Location:** Sidebar chat icon (1st tab)

Same visual style as notification badge:
- Red circle with white text
- Count or "99+"
- Hidden when count is 0
- Updates every 10 seconds via StreamProvider

### How Unread Count Changes

**Increments when:**
- Another user (patient) sends a message to the doctor
- Backend increments unread count for the doctor

**Decrements when:**
- Doctor opens a conversation (marks messages as read)
- Frontend likely calls: `POST /api/chat/conversations/{id}/mark-read` or similar

---

## Complete Flow Diagrams

### Document Access Notification Flow

```
Doctor A requests access to Patient X's document (owned by Doctor B)
  ↓
Frontend: POST /api/patients/{X}/documents/{id}/request-access
  ↓
Backend:
  - Creates document_access_request record
  - Creates notification for Doctor B
  - Type: DOCUMENT_ACCESS_REQUEST
  ↓
Doctor B's app:
  - Auto-refresh (20s polling) fetches new notification
  - Badge count increases
  - Notification appears in list
  ↓
Doctor B clicks Approve/Reject
  ↓
Frontend: POST /api/document-access-requests/{id}/approve OR /reject
  ↓
Backend:
  - Updates request status
  - Creates notification for Doctor A
  - Type: DOCUMENT_ACCESS_APPROVED or DOCUMENT_ACCESS_REJECTED
  ↓
Doctor A's app:
  - Auto-refresh fetches new notification
  - Shows approval/rejection notification
```

### Notification Badge Update Flow

```
Backend creates new notification
  ↓
Every 20 seconds:
  ↓
Frontend: GET /api/notifications/unread/count
  ↓
Backend: Returns {"count": N}
  ↓
Frontend: Updates badge
  ↓
User sees red badge with count
  ↓
User clicks notification icon (opens notifications screen)
  ↓
Frontend: GET /api/notifications (full list)
  ↓
User taps notification (if not document access)
  ↓
Frontend: PUT /api/notifications/{id}/read
  ↓
Frontend: Invalidates providers
  ↓
Badge count decreases
```

---

## Refresh Mechanisms Comparison

| Feature | System | Mechanism | Interval | Provider Type |
|---------|--------|-----------|----------|---------------|
| Doctor Notifications | Notifications | Timer invalidation | 20s | FutureProvider |
| Notification Unread Count | Notifications | Timer invalidation | 20s | FutureProvider |
| Chat Unread Count | Chat | StreamProvider polling | 10s | StreamProvider |

**Why different approaches:**
- **Notifications:** Less critical, 20s is acceptable
- **Chat:** More real-time, 10s for faster updates
- **StreamProvider for chat:** Continuous stream pattern, natural for chat
- **FutureProvider for notifications:** One-time fetch pattern, uses invalidation

---

## Key Files Summary

### Domain (Models)
- `lib/features/notifications/domain/notification_model.dart` - DoctorNotificationModel class

### State Management
- `lib/state/notifications/doctor_notifications_provider.dart` - Providers and controller
- `lib/state/notifications/doctor_notification_actions.dart` - API calls

### Presentation
- `lib/features/notifications/presentation/notifications_screen.dart` - Full screen UI
- `lib/features/shell/presentation/main_shell.dart` - Badge display (lines 344-408)

### Related Systems
- `lib/state/chat/chat_providers.dart` - Chat unread count (separate system)
- `lib/state/patients/patient_actions.dart` - Document access request (triggers notifications)

---

## Notification Lifecycle

### 1. Creation (Backend)
- Backend creates notification in `notifications` table
- Fields: user_id (recipient), type, title, message, created_at, read_at (null)
- For document access: also stores request_id, patient_name, document_title, etc.

### 2. Polling (Frontend)
- MainShell watches `notificationAutoRefreshProvider`
- Timer invalidates providers every 20 seconds
- Providers refetch from backend

### 3. Display (Frontend)
- Badge shows unread count on sidebar
- Notifications screen shows full list with details
- Unread: blue background, blue dot
- Read: white background, no dot

### 4. Interaction (Frontend)
- **Regular notifications:** Tap to mark as read
- **Document access requests:** Approve/Reject buttons
  - Calls backend to approve/reject
  - Automatically marks notification as read
  - Refreshes list

### 5. Cleanup (Backend)
- Backend may archive/delete old notifications
- Not implemented in frontend (assumes backend handles)

---

## Technical Details

### Timezone Handling

**Timestamps:**
- `createdAt`: UTC from backend (ISO 8601)
- `readAt`: UTC from backend (ISO 8601)

**Display:**
- Uses **device local timezone** (via `DateTime.now()` comparison)
- Relative time display: "2m ago", "3h", "yesterday", "Feb 12"
- This is correct for notifications (user-centric, not doctor-practice-centric)

**File:** `notifications_screen.dart:129-148`

```dart
String _formatTime(DateTime dateTime) {
  final now = DateTime.now();
  final diff = now.difference(dateTime);
  // Returns: "just now", "2 minutes ago", "3h", "yesterday", "5d", "Feb 12"
}
```

**Rationale:** Notifications are about when something happened relative to the user viewing them, so device local time makes sense (unlike appointments which need doctor's practice timezone).

### Auto-Dispose Behavior

All notification providers use `autoDispose`:
- Cleans up resources when not watched
- Stops polling when user not on notifications screen
- Timer in `notificationAutoRefreshProvider` cancelled when MainShell disposed

**Trade-off:**
- ✅ Saves resources when app backgrounded
- ❌ Requires re-fetch when reopening notifications screen
- Decision: Acceptable trade-off for resource efficiency

### Error Handling

**Graceful Degradation:**
- If fetch fails: shows error with retry button
- If unread count fails: badge shows nothing (no error badge)
- If mark as read fails: shows error snackbar, doesn't update optimistically

**No Optimistic Updates:**
- Doesn't mark as read locally before backend confirms
- Ensures UI always matches backend state
- Trade-off: Slight delay but guaranteed consistency

---

## User Interactions

### Viewing Notifications

**Entry Points:**
1. Click notification icon in sidebar (tab 5)
2. Badge shows unread count
3. Opens NotificationsScreen

**Screen Features:**
- "Mark All as Read" button (top-right)
- Pull-to-refresh
- Auto-refresh on mount
- Scrollable list

### Marking as Read

**Automatic:**
- Tapping non-request notification marks it as read
- Approving/rejecting document request marks it as read

**Manual:**
- "Mark All as Read" button marks everything as read

**Backend Sync:**
- All mark-as-read actions call backend API
- Success: invalidates providers (refetch)
- Failure: shows error, UI stays unchanged

### Document Access Request Flow

**Receiving a Request (Doctor B):**
1. Notification appears with "pending" status
2. Shows Approve/Reject buttons
3. Clicking either:
   - Calls backend API
   - Marks notification as read
   - Refreshes notification list
   - Shows success/error snackbar

**After Responding:**
- Notification changes to approved/rejected status
- Buttons disappear, status badge appears
- Can no longer change decision (backend enforced)

---

## Integration Points

### MainShell Integration

**File:** `lib/features/shell/presentation/main_shell.dart`

**On Mount:**
```dart
ref.watch(notificationAutoRefreshProvider); // Starts 20s polling
```

**On App Resume:**
```dart
void didChangeAppLifecycleState(AppLifecycleState state) {
  if (state == AppLifecycleState.resumed) {
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
    // Also invalidates appointments, chat
  }
}
```

**Badge Display:**
- Sidebar icon (index 5)
- Red circle badge with count
- Only shows when count > 0

### Logout Integration

**File:** `lib/state/auth/auth_controller.dart` (inferred)

On logout, notifications are likely invalidated to clear data for next login.

---

## Performance Characteristics

### Polling Overhead

**Notifications:**
- 2 API calls every 20 seconds (list + count)
- Minimal data transfer (JSON array)
- Acceptable for always-on web app

**Chat:**
- 1 API call every 10 seconds (count only)
- Even less data (just number)

**Total:** 3 API calls per 10 seconds when app is active

**Optimization Opportunities:**
- Could use WebSockets for real-time updates
- Could batch the two notification calls into one
- Could increase polling interval (30s or 60s)

### Memory Usage

- Notifications list kept in memory (autoDispose helps)
- Typically 10-50 notifications max
- Negligible memory impact

---

## Localization

**Notification Titles:**
- `DOCUMENT_ACCESS_REQUEST` → `l10n.translate('documentAccessRequest')`
- `DOCUMENT_ACCESS_APPROVED` → `l10n.translate('documentAccessApproved')`
- `DOCUMENT_ACCESS_REJECTED` → `l10n.translate('documentAccessRejected')`
- Generic types: use backend `title` field

**Notification Details:**
- Template-based with placeholders
- Example: `"{doctorName} requests access to {documentTitle} for {patientName}"`
- Filled with backend data (doctorName, documentTitle, patientName)
- Falls back to backend `message` if structured data unavailable

**File:** `lib/core/localization/app_localizations.dart`

Supports: English, Uzbek, Russian (same as rest of app)

---

## Security

### Authentication
- All endpoints require JWT token (Bearer auth)
- 401/403 triggers logout via ApiClient
- No notification data leaked without auth

### Authorization
- Backend ensures doctor only sees their own notifications
- Document access approval/rejection validated server-side
- Frontend trusts backend authorization

### Data Exposure
- Notifications may contain patient names (for context)
- Document titles shown (necessary for approval decision)
- No sensitive medical data in notification text

---

## Extensibility

### Adding New Notification Types

**Backend:** Create notification with new type string
**Frontend:** Update switch statements in:
1. `_iconForType()` - Add icon mapping
2. `_colorForType()` - Add color mapping
3. `_displayTitle()` - Add localized title
4. `_buildDetailLine()` - Add structured detail template (optional)

**Example:**
```dart
case 'APPOINTMENT_REMINDER':
  return Icons.event;  // Icon
  return Colors.blue;  // Color
  return l10n.appointmentReminder;  // Title
```

### Adding Actions to Notifications

**Pattern:** Follow document access request example:
1. Add action buttons to `_NotificationTile`
2. Add controller method (calls API)
3. Refresh after success
4. Show snackbar feedback

---

## Comparison: Notifications vs Chat Unread

| Aspect | Doctor Notifications | Chat Unread Count |
|--------|---------------------|-------------------|
| **Purpose** | System notifications | Unread messages |
| **Data** | Full notification objects | Count only |
| **Refresh** | 20s timer (FutureProvider) | 10s polling (StreamProvider) |
| **Display** | Full screen + badge | Badge only |
| **Mark Read** | PUT /api/notifications/{id}/read | Open conversation |
| **Types** | Document access, alerts | Just count |
| **Actions** | Approve/Reject | None (badge only) |
| **Provider** | doctorNotificationsProvider | unreadCountProvider |
| **Badge Location** | Tab 5 (Notifications) | Tab 0 (Chat) |

---

## Common Patterns

### Provider Invalidation Pattern
```dart
// After any action that changes notifications:
ref.invalidate(doctorNotificationsProvider);
ref.invalidate(doctorNotificationsUnreadCountProvider);
```

### Error Handling Pattern
```dart
try {
  await controller.someAction();
  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(l10n.success)),
    );
  }
} catch (e) {
  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(l10n.error), backgroundColor: Colors.red),
    );
  }
}
```

### Async Action Pattern
```dart
// All controller methods are async
await controller.markAsRead(id);
// Then refresh happens automatically via invalidation
```

---

## Known Limitations

1. **No Real-Time Updates:**
   - Uses polling, not WebSockets
   - Max delay: 20 seconds for notifications, 10 seconds for chat
   - Trade-off: Simpler implementation, no WebSocket complexity

2. **No Push Notifications:**
   - Web app doesn't have native push notifications
   - Could add browser notifications API in future
   - Currently: requires app to be open

3. **No Infinite Scroll:**
   - Loads all notifications at once
   - Could be slow if doctor has 1000+ notifications
   - Consider pagination if becomes issue

4. **No Notification Filtering:**
   - Shows all notifications in one list
   - No filter by type, date, or read status
   - Could add tabs/filters if needed

5. **No Notification Sounds:**
   - Silent updates
   - Could add sound on new notification arrival

---

## Testing Considerations

### Testing Notification Flow

1. **Create Test Data:**
   - Need two doctor accounts
   - Need patient with documents
   - Doctor A requests access to Doctor B's patient doc

2. **Verify:**
   - Doctor B sees notification within 20 seconds
   - Badge count increases
   - Approve/Reject buttons work
   - Doctor A sees approval notification
   - Badge counts update correctly

3. **Edge Cases:**
   - Mark as read while list is loading
   - Approve/reject while offline
   - Many notifications (100+)
   - All marked as read at once

### Testing Auto-Refresh

1. Create notification in backend
2. Wait 20 seconds
3. Verify badge updates without manual refresh
4. Verify list updates when screen is open

---

## Possible Issues & Solutions

### Issue: Notifications Not Appearing

**Possible Causes:**
- Backend not creating notification
- JWT token expired (401 error)
- Auto-refresh not running (MainShell not mounted)
- Polling interval too long

**Debug:**
- Check browser console for API calls
- Check network tab: should see `/api/notifications` every 20s
- Check backend logs

### Issue: Badge Count Wrong

**Possible Causes:**
- Backend count calculation wrong
- Frontend caching stale data
- Race condition between mark-as-read and count fetch

**Debug:**
- Compare badge count to actual unread in list
- Check `/api/notifications/unread/count` response
- Check if `readAt` is null for "unread" notifications

### Issue: Performance Degradation

**Possible Causes:**
- Too many notifications (100+)
- Polling too aggressive
- Memory leak from timer not cancelled

**Solutions:**
- Implement pagination
- Increase polling interval
- Verify `autoDispose` working correctly

---

## Future Enhancements

### Potential Improvements

1. **Real-Time Updates:**
   - Add WebSocket connection
   - Push notifications instantly
   - Remove polling overhead

2. **Browser Push Notifications:**
   - Request notification permission
   - Use Web Notifications API
   - Show OS-level notifications

3. **Notification Grouping:**
   - Group by type or date
   - Collapsible sections
   - Better organization for many notifications

4. **Notification Actions:**
   - More inline actions (not just document access)
   - Quick reply from notification
   - Snooze/dismiss options

5. **Notification Preferences:**
   - User settings for notification types
   - Email notifications toggle
   - Sound preferences

6. **Notification History:**
   - Archive old notifications
   - Search notifications
   - Export notification log

---

## Dependencies

**Packages Used:**
- `flutter_riverpod` - State management
- `intl` - Date formatting (relative time)
- `http` - API calls (via ApiClient)

**No External Notification Libraries:**
- No `firebase_messaging` (no push notifications)
- No `flutter_local_notifications` (no local notifications)
- Pure REST API + polling approach

---

## Summary

### Strengths ✅
- Clear separation: notifications vs chat unread
- Well-structured: domain, state, presentation layers
- Type-safe: strongly typed models
- Localized: supports 3 languages
- Auto-refresh: polls automatically
- Actions: approve/reject workflow works
- Error handling: graceful degradation

### Weaknesses ⚠️
- Polling overhead (not real-time)
- No pagination (could be slow with many notifications)
- No filtering/search
- No push notifications
- Timer-based (not event-driven)

### Overall Assessment
**Good implementation for MVP/initial version.** Works reliably for typical usage (10-50 notifications). Consider WebSockets or push notifications for scale/real-time requirements.

---

## Ready for Your Question!

I've completed a comprehensive analysis of the notification system. What would you like to know or change?
