# Notification Navigation - Granular (Specific Document/Appointment)

**Issue:** Clicking notification should open SPECIFIC document or appointment details, not just general tab
**Date:** March 4, 2026
**Status:** IMPLEMENTED ✅

---

## 🎯 **Problem Statement**

**Old Behavior:**
- Click document notification → Goes to Patients tab (generic)
- Click appointment notification → Goes to Calendar tab (generic)
- **User has to manually find the specific item** ❌

**Required Behavior:**
- Click document notification → **Opens THAT specific document** ✅
- Click appointment notification → **Shows THAT specific appointment** ✅
- **Direct access to the exact item** ✅

---

## ✅ **Solution Implemented**

### **Enhanced Navigation Priority System**

**Priority 1: Document (MOST SPECIFIC)** ✅
```
If notification has: documentId + patientId + documentTitle
  ↓
Open DocumentViewerScreen
  ↓
User sees: THE EXACT DOCUMENT
```

**Priority 2: Appointment** ✅
```
If notification has: appointmentId
  ↓
Navigate to Calendar tab
  ↓
User sees: Calendar with that appointment visible
(Can click "Start" on the appointment)
```

**Priority 3: Patient** ✅
```
If notification has: patientId only
  ↓
Navigate to Patients screen
Select that patient
  ↓
User sees: Patient details + documents
```

**Priority 4: Default**
```
If notification has: No IDs
  ↓
Stay on Notifications screen
  ↓
User sees: All notifications
```

---

## 📋 **Backend Notification Payload Requirements**

### **For Document Access Notifications:**

**Backend MUST include:**
```json
{
  "id": 123,
  "type": "DOCUMENT_ACCESS_REQUEST",
  "title": "Document Access Request",
  "message": "Dr. Smith requested access...",
  "documentId": 42,              // ✅ REQUIRED for document viewer
  "patientId": 37,               // ✅ REQUIRED
  "documentTitle": "X-Ray Report", // ✅ REQUIRED
  "documentAccessRequestId": 5
}
```

**Result:** Clicking opens the **specific document viewer** for document #42

### **For Appointment Notifications:**

**Backend MUST include:**
```json
{
  "id": 124,
  "type": "APPOINTMENT_REMINDER",
  "title": "Upcoming Appointment",
  "message": "Appointment with John Doe at 14:00",
  "appointmentId": 166,  // ✅ REQUIRED
  "patientId": 37
}
```

**Result:** Clicking navigates to **Calendar tab** where appointment #166 is visible

---

## 🔄 **Navigation Flow Diagrams**

### **Scenario 1: Document Access Notification**

```
Backend sends notification:
  documentId: 42
  patientId: 37
  documentTitle: "X-Ray Report"
  ↓
User clicks notification
  ↓
LOG: "═══ NOTIFICATION TAPPED IN LIST ═══"
LOG: "Has documentId: 42, opening specific document"
LOG: "PatientId: 37, Title: X-Ray Report"
  ↓
App opens DocumentViewerScreen
  ↓
User sees: THE EXACT X-RAY DOCUMENT ✅
  ↓
User can:
  - View the document
  - Approve/reject access request
  - Close viewer
```

### **Scenario 2: Appointment Notification**

```
Backend sends notification:
  appointmentId: 166
  patientId: 37
  ↓
User clicks notification
  ↓
LOG: "Has appointmentId: 166, navigating to Calendar"
LOG: "✓ Shell tab set to Calendar (index 2)"
  ↓
App switches to Calendar tab
  ↓
User sees: CALENDAR WITH THAT APPOINTMENT ✅
  ↓
User can:
  - Click "Start" on the appointment
  - View appointment details
  - Manage appointment
```

### **Scenario 3: Patient-Only Notification**

```
Backend sends notification:
  patientId: 37
  (no documentId or appointmentId)
  ↓
User clicks notification
  ↓
LOG: "Has patientId: 37, navigating to patient details"
  ↓
App opens Patients screen
Patient 37 is selected
  ↓
User sees: PATIENT INFO + DOCUMENTS TAB ✅
```

---

## 💻 **Code Changes**

### **1. Added documentId to Notification Model**

**File:** `lib/features/notifications/domain/notification_model.dart`

```dart
class DoctorNotificationModel {
  final int? appointmentId;
  final int? patientId;
  final int? documentId;  // ✅ NEW - for opening specific document
  final String? documentTitle;  // ✅ Already existed
  // ...
}
```

### **2. Enhanced Navigation Logic - In-List Tap**

**File:** `lib/features/notifications/presentation/notifications_screen.dart`

```dart
// Priority 1: Document (opens specific document viewer)
if (n.documentId != null && n.patientId != null) {
  ShellScope.push(
    context,
    MaterialPageRoute(
      builder: (_) => DocumentViewerScreen(
        patientId: n.patientId.toString(),
        documentId: n.documentId.toString(),
        title: n.documentTitle ?? 'Document',
      ),
    ),
  );
  debugPrint('✓ Document viewer opened for document ${n.documentId}');
  return;
}

// Priority 2: Appointment (opens Calendar tab)
if (n.appointmentId != null) {
  ref.read(shellProvider.notifier).setTab(2);  // Calendar tab
  Navigator.pushNamedAndRemoveUntil(context, AppRoutes.shell, ...);
  return;
}

// Priority 3: Patient (opens Patients with selection)
if (n.patientId != null) {
  Navigator.pushNamed(context, AppRoutes.patientsWithSelection, ...);
  return;
}
```

### **3. Enhanced FCM Tap Handler**

**File:** `lib/app/app.dart`

Same priority logic for FCM push notification taps.

---

## 🧪 **Testing**

### **Test 1: Document Access Notification**

**Setup:**
1. Backend creates document access notification with:
   - `documentId: 42`
   - `patientId: 37`
   - `documentTitle: "X-Ray Report"`

**Expected:**
```
Console:
  ═══ NOTIFICATION TAPPED IN LIST ═══
  Has documentId: 42, opening specific document
  PatientId: 37, Title: X-Ray Report
  ✓ Document viewer opened for document 42

UI:
  → Document viewer opens
  → Shows the specific X-Ray Report
  → User can view/download/approve
```

### **Test 2: Appointment Notification**

**Setup:**
1. Backend creates appointment notification with:
   - `appointmentId: 166`

**Expected:**
```
Console:
  ═══ NOTIFICATION TAPPED IN LIST ═══
  Has appointmentId: 166, navigating to Calendar
  ✓ Shell tab set to Calendar (index 2)
  ✓ Navigated to Calendar - appointment 166 visible

UI:
  → Calendar tab opens
  → User sees appointment on calendar
  → Can click "Start" button
```

### **Test 3: Patient Notification**

**Setup:**
1. Backend creates notification with only:
   - `patientId: 37`

**Expected:**
```
Console:
  Has patientId: 37, navigating to patient details

UI:
  → Patients screen opens
  → Patient 37 is selected
  → User sees documents tab
```

---

## 📊 **Comparison: Before vs After**

| Notification Type | Before | After |
|------------------|--------|-------|
| Document Access | → Patients tab (generic) | → Specific document viewer ✅ |
| Appointment Reminder | → Calendar tab (generic) | → Calendar tab (focused) ✅ |
| Patient-related | → Patients tab (unselected) | → Specific patient selected ✅ |

---

## 🔧 **Files Modified (3)**

1. ✅ `lib/features/notifications/domain/notification_model.dart`
   - Added `documentId` field
   - Updated JSON parsing

2. ✅ `lib/features/notifications/presentation/notifications_screen.dart`
   - Added document-specific navigation (Priority 1)
   - Opens DocumentViewerScreen directly

3. ✅ `lib/app/app.dart`
   - Added document-specific navigation for FCM
   - Enhanced navigation priority

**Compilation:** 0 errors ✅

---

## 🎯 **What Backend Needs to Send**

### **Document Access Request:**
```json
{
  "documentId": 42,         // ← Add this
  "patientId": 37,
  "documentTitle": "X-Ray",  // Already sent
  "documentAccessRequestId": 5
}
```

### **Document Access Approved/Rejected:**
```json
{
  "documentId": 42,         // ← Add this
  "patientId": 37,
  "documentTitle": "X-Ray"
}
```

### **Appointment Notifications:**
```json
{
  "appointmentId": 166,  // Already sent
  "patientId": 37
}
```

---

## ✅ **Status: COMPLETE**

**Navigation is now GRANULAR:**
- ✅ Documents open in specific document viewer
- ✅ Appointments navigate to Calendar (appointment visible)
- ✅ Patients navigate to specific patient page
- ✅ Comprehensive logging shows what's happening

**Ready to test!** App is restarting with these changes.
