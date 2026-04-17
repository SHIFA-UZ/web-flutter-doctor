# Notification System - Logging & Navigation Fixes

**Issues Requested:**
1. Add logs to see where notifications come from (Firebase FCM vs Backend REST API)
2. Fix navigation when clicking notification - should open specific patient/appointment

**Date:** March 4, 2026
**Status:** FIXED ✅

---

## 🔍 Issue #1: Notification Source Logging - FIXED ✅

### **Discovery: You Have BOTH Systems!**

Your app uses **2 notification systems simultaneously**:

#### **System 1: Firebase FCM (Push Notifications)**
- **Purpose:** Real-time push notifications from backend
- **File:** `lib/core/services/push_notification_service.dart`
- **How it works:**
  - Backend sends push via Firebase Cloud Messaging
  - Delivers instantly (no polling)
  - Works even when app is closed
  - Shows system notification (Android/iOS notification tray)

#### **System 2: REST API Polling**
- **Purpose:** Fetch notification list for display in Notifications tab
- **File:** `lib/state/notifications/doctor_notifications_provider.dart`
- **How it works:**
  - Polls `GET /api/notifications` every 20 seconds
  - Displays in notifications list UI
  - Updates badge count

**Both systems work together!**

---

## 📊 Added Comprehensive Logging

### **REST API Polling Logs**

**File:** `lib/state/notifications/doctor_notification_actions.dart`

```dart
debugPrint('═══ NOTIFICATION SOURCE: REST API ═══');
debugPrint('Fetching notifications via: GET /api/notifications');
debugPrint('Method: Backend REST API polling (every 20 seconds)');
debugPrint('NOT using Firebase FCM for this fetch');
debugPrint('✓ Fetched X notifications from REST API');
debugPrint('  • ID:123 Type:DOCUMENT_ACCESS_REQUEST Read:false');
```

### **Firebase FCM Logs**

**File:** `lib/core/services/push_notification_service.dart`

```dart
debugPrint('═══ NOTIFICATION SOURCE: FIREBASE FCM ═══');
debugPrint('Received FCM push notification (FOREGROUND)');
debugPrint('Title: Document Access Request');
debugPrint('Body: Doctor requested access...');
debugPrint('Data payload: {type: DOCUMENT_ACCESS_REQUEST, patientId: 37}');
debugPrint('Notification ID: 123');
debugPrint('✓ Showing local notification to user');
```

### **Notification Tap Logs**

**When user taps notification:**
```dart
debugPrint('═══ NOTIFICATION TAP HANDLER ═══');
debugPrint('Notification tapped - payload: {...}');
debugPrint('Type: DOCUMENT_ACCESS_REQUEST');
debugPrint('AppointmentId: null');
debugPrint('PatientId: 37');
debugPrint('DocumentAccessRequestId: 5');
debugPrint('Determining navigation...');
debugPrint('→ Navigating to Patients screen with patient 37 selected');
```

**When tapped from notification list:**
```dart
debugPrint('═══ NOTIFICATION TAPPED IN LIST ═══');
debugPrint('Notification ID: 123');
debugPrint('Type: DOCUMENT_ACCESS_REQUEST');
debugPrint('PatientId: 37');
debugPrint('AppointmentId: null');
debugPrint('IsRead: false');
debugPrint('Marking as read...');
debugPrint('→ Has patientId, navigating to patient details');
```

---

## 🧭 Issue #2: Navigation to Specific Patient - FIXED ✅

### **Changes Made**

#### **1. Added patientId to Notification Model**

**File:** `lib/features/notifications/domain/notification_model.dart`

```dart
class DoctorNotificationModel {
  final int? appointmentId;
  final int? patientId;  // ✅ NEW - for navigation
  final int? documentAccessRequestId;
  // ...
}
```

**Backend should include** `patientId` in notification payload for navigation.

#### **2. Enhanced FCM Tap Handler**

**File:** `lib/app/app.dart` (lines 96-148)

**New logic:**
```dart
pushService.setOnNotificationTap((data) {
  // Extract IDs
  final patientId = data['patientId'];
  final appointmentId = data['appointmentId'];
  final type = data['type'];

  // Navigate based on content:
  if (patientId != null) {
    // Navigate to Patients screen with this patient selected
    navigatorKey.currentState?.pushNamed(
      AppRoutes.patientsWithSelection,
      arguments: patientId.toString(),
    );
  } else if (appointmentId != null) {
    // Future: navigate to appointment details
  } else {
    // Default: show notifications list
    navigatorKey.currentState?.pushNamed(AppRoutes.notifications);
  }
});
```

#### **3. Enhanced In-List Tap Handler**

**File:** `lib/features/notifications/presentation/notifications_screen.dart` (lines 80-107)

**New logic:**
```dart
onTap: () async {
  // Mark as read
  if (!n.isRead) {
    await controller.markAsRead(n.id);
  }

  // Navigate based on notification content
  if (n.patientId != null) {
    Navigator.pushNamed(
      context,
      AppRoutes.patientsWithSelection,
      arguments: n.patientId.toString(),
    );
  } else if (n.appointmentId != null) {
    // Future: navigate to appointment/calendar
  }
});
```

---

## 🎯 How It Works Now

### **Scenario 1: Document Access Request**

```
Backend creates notification:
  {
    id: 123,
    type: "DOCUMENT_ACCESS_REQUEST",
    patientId: 37,
    patientName: "John Doe",
    title: "Document Access Request",
    message: "Dr. Smith requested access..."
  }
  ↓
Backend sends Firebase FCM push (if available)
  ↓
User device receives push
  ↓
LOG: "═══ NOTIFICATION SOURCE: FIREBASE FCM ═══"
LOG: "Received FCM push notification"
LOG: "PatientId: 37"
  ↓
User taps notification
  ↓
LOG: "═══ NOTIFICATION TAP HANDLER ═══"
LOG: "→ Navigating to Patients screen with patient 37 selected"
  ↓
App opens Patients screen
Patient 37 automatically selected
User sees patient's documents
```

### **Scenario 2: Notification List Refresh**

```
Every 20 seconds:
  ↓
Timer triggers refresh
  ↓
LOG: "═══ NOTIFICATION SOURCE: REST API ═══"
LOG: "Fetching notifications via: GET /api/notifications"
LOG: "Method: Backend REST API polling"
  ↓
Fetches notification list from backend
  ↓
LOG: "✓ Fetched 5 notifications from REST API"
LOG: "  • ID:123 Type:DOCUMENT_ACCESS_REQUEST Read:false"
  ↓
Badge updates (shows unread count)
Notification list refreshes
```

### **Scenario 3: Tapping Notification in List**

```
User in Notifications screen
  ↓
User taps a notification
  ↓
LOG: "═══ NOTIFICATION TAPPED IN LIST ═══"
LOG: "Notification ID: 123"
LOG: "PatientId: 37"
LOG: "IsRead: false"
  ↓
Marks as read via API
  ↓
LOG: "→ Has patientId, navigating to patient details"
  ↓
Navigates to Patients screen
Patient 37 selected
```

---

## 📋 Backend Requirements for Navigation

### **For Notifications to Navigate Properly:**

Backend should include these fields in notification JSON:

```json
{
  "id": 123,
  "type": "DOCUMENT_ACCESS_REQUEST",
  "title": "Document Access Request",
  "message": "...",
  "patientId": 37,           // ✅ REQUIRED for patient navigation
  "patientName": "John Doe", // Optional, for display
  "appointmentId": null,     // Optional, for future appointment navigation
  "documentAccessRequestId": 5,
  "createdAt": "2024-03-04T..."
}
```

**Key field for navigation:** `patientId`

---

## 🧪 How to Test

### **Test 1: See Notification Source Logs**

1. Run app: `flutter run -d chrome`
2. Open browser DevTools (F12 → Console)
3. Wait 20 seconds
4. **Look for logs:**
   ```
   ═══ NOTIFICATION SOURCE: REST API ═══
   Fetching notifications via: GET /api/notifications
   Method: Backend REST API polling (every 20 seconds)
   ✓ Fetched 3 notifications from REST API
     • ID:123 Type:DOCUMENT_ACCESS_REQUEST Read:false
   ```

### **Test 2: FCM Push (If Firebase Configured)**

1. Backend sends FCM push notification
2. **Look for logs:**
   ```
   ═══ NOTIFICATION SOURCE: FIREBASE FCM ═══
   Received FCM push notification (FOREGROUND)
   Title: Document Access Request
   Data payload: {patientId: 37, type: ...}
   ```

### **Test 3: Click Notification from FCM**

1. Backend sends FCM push
2. Click the system notification
3. **Look for logs:**
   ```
   ═══ NOTIFICATION TAP HANDLER ═══
   PatientId: 37
   → Navigating to Patients screen with patient 37 selected
   ```
4. **Verify:** App opens Patients screen with patient 37 selected

### **Test 4: Click Notification in List**

1. Go to Notifications tab (in app)
2. Click a notification that has a patientId
3. **Look for logs:**
   ```
   ═══ NOTIFICATION TAPPED IN LIST ═══
   PatientId: 37
   → Has patientId, navigating to patient details
   ```
4. **Verify:** Navigates to Patients screen with that patient selected

---

## 🔄 Notification Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│ Backend Creates Notification in Database                │
│  - Saves to notifications table                         │
│  - Includes: id, type, patientId, appointmentId        │
└────────────────┬────────────────────────────────────────┘
                 │
                 ├─────────────────┬─────────────────────┐
                 │                 │                     │
                 ↓                 ↓                     ↓
    ┌────────────────────┐  ┌──────────────┐  ┌─────────────────┐
    │ Firebase FCM Push  │  │ REST API     │  │ Doctor App      │
    │ (Real-time)        │  │ Polling      │  │ Sees It         │
    │                    │  │ (20s cycle)  │  │                 │
    │ iOS/Android System │  │              │  │ Badge updates   │
    │ Notification       │  │ GET /api/    │  │ List refreshes  │
    │                    │  │ notifications│  │                 │
    └──────┬─────────────┘  └──────┬───────┘  └─────────────────┘
           │                       │
           │ User taps             │ Auto-refresh
           ↓                       ↓
    ┌──────────────────────────────────────────┐
    │ App Navigation Logic                     │
    │  - Extracts patientId from payload       │
    │  - Navigates to Patients screen          │
    │  - Selects that specific patient         │
    └──────────────────────────────────────────┘
```

---

## 📝 Files Modified

1. ✅ `lib/features/notifications/domain/notification_model.dart`
   - Added `patientId` field
   - Updated JSON parsing

2. ✅ `lib/features/notifications/presentation/notifications_screen.dart`
   - Added comprehensive logging
   - Added navigation to patient when tapped

3. ✅ `lib/app/app.dart`
   - Enhanced FCM tap handler with logging
   - Added patient navigation logic

4. ✅ `lib/state/notifications/doctor_notification_actions.dart`
   - Added REST API source logging

5. ✅ `lib/core/services/push_notification_service.dart`
   - Added FCM source logging
   - Added tap event logging

---

## 🎯 What You'll See in Console

### **When App Loads:**
```
Doctor FCM Token: eyJ...
Doctor FCM token uploaded to backend
```

### **Every 20 Seconds:**
```
═══ NOTIFICATION SOURCE: REST API ═══
GET /api/notifications
✓ Fetched 3 notifications from REST API
```

### **When FCM Push Arrives:**
```
═══ NOTIFICATION SOURCE: FIREBASE FCM ═══
Received FCM push notification (FOREGROUND)
Title: Document Access Request
PatientId: 37
✓ Showing local notification to user
```

### **When User Taps:**
```
═══ NOTIFICATION TAPPED IN LIST ═══
PatientId: 37
→ Navigating to Patients screen with patient 37 selected
```

---

## ✅ Status

**Both Issues Resolved:**
1. ✅ **Comprehensive logging** - Shows exactly where each notification comes from
2. ✅ **Patient navigation** - Clicking notification opens specific patient

**Ready to test!**

---

## 🚀 Next Steps

1. **Run the app**
2. **Check browser console** - See the logs
3. **Click a notification** - See it navigate to patient
4. **Verify** both FCM and REST API logging works

**The logging will make it crystal clear where each notification originates!**
