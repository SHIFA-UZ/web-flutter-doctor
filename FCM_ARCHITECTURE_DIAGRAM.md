# FCM Architecture & Flow Diagrams

**Visual guide to how Firebase Cloud Messaging works in Shifa Doctor App**

---

## 🏗️ **System Architecture**

```
┌─────────────────────────────────────────────────────────────────┐
│                     SHIFA NOTIFICATION SYSTEM                    │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│  Flutter App     │         │  Backend API     │         │  Firebase FCM    │
│  (Doctor)        │         │  (Spring Boot)   │         │  (Google)        │
└────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
         │                            │                            │
         │ 1. App starts             │                            │
         │ 2. Gets FCM token         │                            │
         │────────────────────────────→                            │
         │    PUT /api/doctors/me/   │                            │
         │    fcm-token              │                            │
         │                           │                            │
         │                           │ 3. Saves token to DB       │
         │                           │    doctor.fcmToken = "..." │
         │                           │                            │
         │                           │ 4. Event occurs            │
         │                           │    (appointment booked)    │
         │                           │                            │
         │                           │ 5. Create notification     │
         │                           │    in database             │
         │                           │                            │
         │                           │ 6. Get doctor FCM token    │
         │                           │                            │
         │                           │ 7. Send FCM request        │
         │                           │────────────────────────────→
         │                           │    POST /v1/projects/...   │
         │                           │    {to: token, data: {...}}│
         │                           │                            │
         │                           │                            │ 8. Validates token
         │                           │                            │ 9. Delivers to device
         │ 10. Receives notification │                            │
         │←───────────────────────────────────────────────────────┤
         │    Push notification      │                            │
         │                           │                            │
         │ 11. Shows system notification                          │
         │ 12. User taps                                          │
         │ 13. Navigates (document/appointment/patient)           │
         │                           │                            │
         │ 14. Mark as read          │                            │
         │────────────────────────────→                            │
         │    PUT /api/notifications/│                            │
         │    123/read               │                            │
         │                           │                            │
```

---

## 🔄 **Complete Flow: Document Access Request**

```
┌─────────────────────────────────────────────────────────────────────┐
│ SCENARIO: Doctor B requests access to Doctor A's patient document  │
└─────────────────────────────────────────────────────────────────────┘

1. Doctor B (in app)
   Clicks "Request Access" on locked document
   ↓
2. Flutter App
   POST /api/patients/37/documents/42/request-access
   ↓
3. Backend API
   • Creates document_access_request (status: PENDING)
   • Creates notification for Doctor A
   • Saves to notifications table:
     {
       id: 123,
       userId: doctorA_id,
       type: "DOCUMENT_ACCESS_REQUEST",
       documentId: 42,
       patientId: 37,
       documentTitle: "X-Ray Report"
     }
   ↓
4. Backend (FCM Service)
   • Loads Doctor A's fcmToken from database
   • Builds FCM payload:
     {
       notification: {
         title: "Document Access Request",
         body: "Dr. Smith requested access..."
       },
       data: {
         notificationId: "123",
         type: "DOCUMENT_ACCESS_REQUEST",
         documentId: "42",
         patientId: "37",
         documentTitle: "X-Ray Report"
       }
     }
   • Calls Firebase API
   ↓
5. Firebase FCM
   • Validates token
   • Routes to correct device
   • Delivers notification
   ↓
6. Doctor A's Device
   • Receives push notification
   • System notification appears (iOS/Android notification tray)
   ↓
7. Doctor A taps notification
   ↓
8. Flutter App
   • Receives tap event
   • Parses data payload
   • LOG: "documentId: 42, patientId: 37"
   • LOG: "→ Opening specific document"
   • Opens DocumentViewerScreen
   ↓
9. Doctor A
   Sees the EXACT X-Ray Report document
   Can approve/reject the access request
```

---

## 🔀 **Navigation Decision Tree**

```
Notification Tapped
    |
    |── Has documentId + patientId?
    |   |
    |   ├─ YES → Open DocumentViewerScreen(documentId, patientId)
    |   |         User sees: SPECIFIC DOCUMENT
    |   |
    |   └─ NO ↓
    |
    |── Has appointmentId?
    |   |
    |   ├─ YES → Switch to Calendar tab (index 2)
    |   |         User sees: CALENDAR with appointment
    |   |
    |   └─ NO ↓
    |
    |── Has patientId?
    |   |
    |   ├─ YES → Navigate to Patients screen (patient selected)
    |   |         User sees: SPECIFIC PATIENT details
    |   |
    |   └─ NO ↓
    |
    └── Default → Open Notifications screen
                  User sees: ALL NOTIFICATIONS
```

---

## 📊 **Data Flow: FCM Token Management**

```
┌─────────────────────────────────────────────────────────────┐
│                    FCM TOKEN LIFECYCLE                       │
└─────────────────────────────────────────────────────────────┘

App Installed/Opened
    ↓
Firebase SDK generates token
    ↓
Token: "eyJhbGciOiJSUzI1NiIsImtpZCI6ImY..."
    ↓
PUT /api/doctors/me/fcm-token
    ↓
Backend saves to doctor_profiles.fcm_token
    ↓
Token stored in database
    ↓
──────────────────────────────────────────────────
    ↓
Event occurs (appointment booked)
    ↓
Backend: SELECT fcm_token FROM doctor_profiles WHERE id = ?
    ↓
Token: "eyJhbGc..."
    ↓
Firebase.send(token, notification, data)
    ↓
Notification delivered to device
    ↓
──────────────────────────────────────────────────────
    ↓
Token expires/refreshes
    ↓
onTokenRefresh listener fires
    ↓
PUT /api/doctors/me/fcm-token (new token)
    ↓
Backend updates database
    ↓
New token stored
```

---

## 🔐 **Security Flow**

```
┌─────────────────────────────────────────────────────────────┐
│                    SECURITY MEASURES                         │
└─────────────────────────────────────────────────────────────┘

1. Token Storage
   ✓ Only doctor can update their OWN fcmToken
   ✓ JWT authentication required
   ✓ Token never exposed in GET requests
   ✓ Token stored encrypted (database-level encryption)

2. Notification Sending
   ✓ Backend validates doctor has permission to notify
   ✓ Firebase Admin SDK uses service account (not exposed)
   ✓ Server key kept secret (environment variable)
   ✓ Invalid tokens removed automatically

3. App Security
   ✓ Notification data payload validated
   ✓ Navigation checks permissions
   ✓ Documents require access control
   ✓ All API calls use JWT authentication
```

---

## 🎯 **Component Interaction Map**

```
┌────────────────────────────────────────────────────────────────────┐
│                        FLUTTER APP                                 │
│                                                                    │
│  ┌──────────────────────┐         ┌──────────────────────┐       │
│  │ PushNotificationService│────────│ app.dart             │       │
│  │ • Receives FCM        │         │ • Sets tap handler  │       │
│  │ • Shows local notif   │         │ • Navigation logic  │       │
│  └───────────┬───────────┘         └──────────┬───────────┘       │
│              │                                │                   │
│              │                                │                   │
│  ┌───────────▼────────────────────────────────▼──────────┐       │
│  │         Notification Tap Handler                       │       │
│  │  Priority 1: documentId → DocumentViewerScreen         │       │
│  │  Priority 2: appointmentId → Calendar tab              │       │
│  │  Priority 3: patientId → Patient details               │       │
│  └────────────────────────────────────────────────────────┘       │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                         BACKEND API                                │
│                                                                    │
│  ┌──────────────────────┐         ┌──────────────────────┐       │
│  │ NotificationService  │────────│ FcmService           │       │
│  │ • Creates notification│         │ • Sends FCM push     │       │
│  │ • Saves to DB        │         │ • Handles errors     │       │
│  └───────────┬───────────┘         └──────────┬───────────┘       │
│              │                                │                   │
│              │                                │                   │
│  ┌───────────▼────────────────────────────────▼──────────┐       │
│  │              Event Handlers                            │       │
│  │  • onDocumentAccessRequested()                         │       │
│  │  • onAppointmentBooked()                               │       │
│  │  • onAppointmentReminder()                             │       │
│  └────────────────────────────────────────────────────────┘       │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                       FIREBASE FCM                                 │
│                                                                    │
│  ┌──────────────────────────────────────────────────────┐         │
│  │  • Receives notification from backend                │         │
│  │  • Validates FCM token                               │         │
│  │  • Routes to correct device                          │         │
│  │  • Delivers (foreground/background/terminated)       │         │
│  └──────────────────────────────────────────────────────┘         │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## ✅ **Summary**

**Your Flutter app has:**
- ✅ Complete FCM implementation
- ✅ Notification handlers
- ✅ Granular navigation (document/appointment/patient)
- ✅ Comprehensive logging

**You need:**
1. Firebase project setup (15 min)
2. Backend FCM integration (1-2 hours)

**Follow:** `FCM_QUICK_START.md` to get started!
