# Firebase Cloud Messaging (FCM) Integration - Complete Guide

**Purpose:** Enable real-time push notifications in the Shifa Doctor app
**Platform:** iOS, Android, Web (Flutter)
**Date:** March 4, 2026

---

## 📋 **Prerequisites**

Before starting, ensure you have:
- [ ] Google account (for Firebase Console)
- [ ] Flutter SDK installed
- [ ] FlutterFire CLI installed (`dart pub global activate flutterfire_cli`)
- [ ] Node.js installed (for Firebase CLI)
- [ ] Backend API that can send FCM notifications
- [ ] Admin access to your Flutter project

---

## 🚀 **Step 1: Create Firebase Project**

### **1.1 Go to Firebase Console**

Visit: https://console.firebase.google.com/

### **1.2 Create New Project**

1. Click **"Add project"**
2. **Project name:** `shifa-doctor-app` (or your preferred name)
3. Click **"Continue"**
4. **Google Analytics:** Enable (recommended) or skip
5. Click **"Create project"**
6. Wait for project creation (~30 seconds)
7. Click **"Continue"**

**Screenshot checkpoints:**
- You should see the Firebase Console dashboard
- Project name appears in top-left corner

---

## 🔧 **Step 2: Install FlutterFire CLI**

### **2.1 Install FlutterFire CLI**

```bash
dart pub global activate flutterfire_cli
```

### **2.2 Verify Installation**

```bash
flutterfire --version
```

**Expected output:** `FlutterFire CLI version X.X.X`

### **2.3 Login to Firebase**

```bash
firebase login
```

**This will:**
- Open browser for Google authentication
- Grant Firebase CLI access to your account
- Return to terminal with success message

---

## 📱 **Step 3: Configure Flutter Project with Firebase**

### **3.1 Navigate to Your Project**

```bash
cd /Users/sheroziy.saidkhodjaev/Projects/Private/shifa-doc-app-clean
```

### **3.2 Run FlutterFire Configure**

```bash
flutterfire configure
```

**Interactive prompts:**

**1. Select Firebase project:**
```
? Select a Firebase project to configure your Flutter application with
  > shifa-doctor-app (newly created)
```
Press Enter

**2. Select platforms:**
```
? Which platforms should your configuration support (leave blank to select all)?
  [x] android
  [x] ios
  [x] web
```
Use Space to select, Enter to confirm

**3. Confirm project ID:**
```
? Enter the project ID for your Firebase project: shifa-doctor-app
```
Press Enter

**What this does:**
- Creates `firebase_options.dart` in `lib/` directory
- Configures all platform-specific Firebase settings
- Registers your app with Firebase project

**Expected output:**
```
✓ Firebase configuration file lib/firebase_options.dart generated successfully
```

---

## 📦 **Step 4: Add Firebase Dependencies**

### **4.1 Check pubspec.yaml**

Your app already has these dependencies:
```yaml
dependencies:
  firebase_core: ^3.8.1
  firebase_auth: ^5.3.3
```

### **4.2 Add Firebase Messaging**

Edit `pubspec.yaml` and add:

```yaml
dependencies:
  firebase_core: ^3.8.1
  firebase_auth: ^5.3.3
  firebase_messaging: ^15.0.0  # ← Add this
  flutter_local_notifications: ^17.0.0  # ← Add this (for foreground notifications)
```

### **4.3 Install Dependencies**

```bash
flutter pub get
```

**Expected output:**
```
Running "flutter pub get" in shifa-doc-app-clean...
Got dependencies!
```

---

## 🔔 **Step 5: Platform-Specific Configuration**

### **5.1 Android Configuration**

#### **A. Enable FCM in Firebase Console**

1. Go to Firebase Console → Your project
2. Click **⚙️ Settings** → **Project settings**
3. Go to **Cloud Messaging** tab
4. Under **Cloud Messaging API (Legacy)**, click **Enable**
5. Note your **Server Key** (you'll need this for backend)

#### **B. Configure Android Manifest**

File: `android/app/src/main/AndroidManifest.xml`

Add inside `<application>` tag:

```xml
<application>
    <!-- ... existing config ... -->

    <!-- FCM -->
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_channel_id"
        android:value="shifa_doctor_channel" />

    <!-- Notification icon -->
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_icon"
        android:resource="@mipmap/ic_launcher" />

</application>
```

#### **C. Request Notification Permission**

File: `android/app/src/main/AndroidManifest.xml`

Add before `<application>` tag:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

---

### **5.2 iOS Configuration**

#### **A. Enable Push Notifications in Xcode**

1. Open `ios/Runner.xcworkspace` in Xcode
2. Select **Runner** project in left sidebar
3. Select **Runner** target
4. Go to **Signing & Capabilities** tab
5. Click **+ Capability**
6. Add **Push Notifications**
7. Add **Background Modes**
8. Check **Remote notifications**

#### **B. Upload APNs Key to Firebase**

1. Go to https://developer.apple.com/account/
2. Go to **Certificates, Identifiers & Profiles**
3. Go to **Keys** section
4. Click **+** to create new key
5. **Key Name:** `Shifa FCM Key`
6. Check **Apple Push Notifications service (APNs)**
7. Click **Continue** → **Register**
8. **Download the .p8 key file** (save it securely!)
9. Note your **Key ID** and **Team ID**

10. Go to **Firebase Console** → Project Settings → Cloud Messaging
11. Under **Apple app configuration**, click **Upload**
12. Upload your .p8 key file
13. Enter Key ID and Team ID
14. Click **Upload**

#### **C. Update Info.plist**

File: `ios/Runner/Info.plist`

Add before `</dict>`:

```xml
<key>FirebaseAppDelegateProxyEnabled</key>
<false/>
```

---

### **5.3 Web Configuration**

#### **A. Generate Web Push Certificate**

1. Go to **Firebase Console** → Project Settings
2. Go to **Cloud Messaging** tab
3. Scroll to **Web configuration**
4. Under **Web Push certificates**, click **Generate key pair**
5. Copy the **Key pair** value (looks like: `BIx...`)

#### **B. Create Firebase Messaging Service Worker**

Create file: `web/firebase-messaging-sw.js`

```javascript
importScripts('https://www.gstatic.com/firebasejs/10.7.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.0/firebase-messaging-compat.js');

// Your Firebase config (copy from firebase_options.dart)
firebase.initializeApp({
  apiKey: "YOUR_API_KEY",
  authDomain: "shifa-doctor-app.firebaseapp.com",
  projectId: "shifa-doctor-app",
  storageBucket: "shifa-doctor-app.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID",
  measurementId: "YOUR_MEASUREMENT_ID"
});

const messaging = firebase.messaging();

// Background message handler
messaging.onBackgroundMessage(function(payload) {
  console.log('[firebase-messaging-sw.js] Received background message', payload);

  const notificationTitle = payload.notification?.title || 'New Notification';
  const notificationOptions = {
    body: payload.notification?.body || '',
    icon: '/icons/Icon-192.png',
    badge: '/icons/Icon-192.png',
    data: payload.data
  };

  return self.registration.showNotification(notificationTitle, notificationOptions);
});
```

**Important:** Replace `YOUR_API_KEY`, `YOUR_SENDER_ID`, etc. with actual values from `lib/firebase_options.dart`

#### **C. Update index.html**

File: `web/index.html`

Add before closing `</body>` tag:

```html
  <!-- Firebase Messaging Service Worker -->
  <script>
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/firebase-messaging-sw.js')
        .then(function(registration) {
          console.log('Service Worker registered:', registration);
        })
        .catch(function(err) {
          console.log('Service Worker registration failed:', err);
        });
    }
  </script>
</body>
```

---

## 💻 **Step 6: Update Flutter Code**

### **6.1 Your Code is Already Set Up!**

Your app already has:
- ✅ `lib/core/services/push_notification_service.dart` - FCM service
- ✅ `lib/main.dart` - Initializes Firebase and FCM
- ✅ `lib/app/app.dart` - Sets up notification tap handlers
- ✅ All necessary logic implemented

### **6.2 Verify main.dart Configuration**

File: `lib/main.dart`

Should have:
```dart
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:timezone/data/latest.dart' as tz;
import 'firebase_options.dart';
import 'core/services/push_notification_service.dart';

// Background message handler (must be top-level function)
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  debugPrint('Background FCM message: ${message.messageId}');
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  tz.initializeTimeZones();

  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

  final pushService = PushNotificationService();
  await pushService.initialize();

  runApp(const ProviderScope(child: ShifaDoctorApp()));
}
```

**Your app already has this!** ✅

---

## 🔐 **Step 7: Backend Integration**

### **7.1 Get FCM Server Key**

1. Go to **Firebase Console** → Project Settings
2. Go to **Cloud Messaging** tab
3. Under **Cloud Messaging API (Legacy)**, copy **Server key**

**Keep this secure!** This key allows sending notifications.

### **7.2 Backend API Endpoints**

Your backend needs these endpoints:

#### **A. Save FCM Token (Already Implemented)**

```
PUT /api/doctors/me/fcm-token
Body: { "fcmToken": "device_token_here" }
```

**Purpose:** When app starts, it sends FCM token to backend
**Your app already does this!** (in `app.dart`)

#### **B. Send FCM Notification**

**Backend pseudocode:**

```kotlin
// Kotlin/Spring Boot example
fun sendNotificationToDoctor(doctorId: Long, notification: Notification) {
    // 1. Get doctor's FCM token from database
    val fcmToken = doctorProfileRepository.findById(doctorId)
        .map { it.fcmToken }
        .orElse(null)

    if (fcmToken == null) return  // Doctor hasn't enabled notifications

    // 2. Create FCM payload
    val message = Message.builder()
        .setToken(fcmToken)
        .setNotification(
            com.google.firebase.messaging.Notification.builder()
                .setTitle(notification.title)
                .setBody(notification.message)
                .build()
        )
        .putAllData(mapOf(
            "notificationId" to notification.id.toString(),
            "type" to notification.type,
            "appointmentId" to notification.appointmentId?.toString() ?: "",
            "patientId" to notification.patientId?.toString() ?: "",
            "documentId" to notification.documentId?.toString() ?: "",
            "documentTitle" to (notification.documentTitle ?: "")
        ))
        .build()

    // 3. Send via Firebase Admin SDK
    FirebaseMessaging.getInstance().send(message)
}
```

**Required data fields for navigation:**
- `notificationId` - For marking as read
- `type` - Notification type
- `appointmentId` - Opens Calendar (optional)
- `patientId` - Opens patient page (optional)
- `documentId` - Opens document viewer (optional)
- `documentTitle` - Document name (optional)

---

## 🧪 **Step 8: Testing FCM**

### **8.1 Get FCM Token from App**

1. Run your app:
   ```bash
   flutter run -d chrome
   ```

2. Check browser console (F12 → Console)
3. Look for:
   ```
   Doctor FCM Token: eyJ...
   Doctor FCM token uploaded to backend
   ```

4. Copy this token - you'll use it for testing

### **8.2 Test with Firebase Console (Quick Test)**

1. Go to **Firebase Console** → Your project
2. Click **Engage** → **Messaging** (left sidebar)
3. Click **"Create your first campaign"** or **"New campaign"**
4. Select **"Firebase Notification messages"**
5. **Notification title:** "Test Notification"
6. **Notification text:** "This is a test"
7. Click **"Next"**
8. **Target:** Select "A single device"
9. **FCM registration token:** Paste the token you copied
10. Click **"Next"** → **"Next"** → **"Publish"**

**Expected result:**
- App receives notification
- Console shows: "═══ NOTIFICATION SOURCE: FIREBASE FCM ═══"
- System notification appears

### **8.3 Test with cURL (Advanced)**

```bash
# Replace YOUR_SERVER_KEY and DEVICE_TOKEN
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=YOUR_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "DEVICE_TOKEN",
    "notification": {
      "title": "Test Appointment",
      "body": "Appointment at 14:00"
    },
    "data": {
      "notificationId": "123",
      "type": "APPOINTMENT_REMINDER",
      "appointmentId": "166",
      "patientId": "37"
    }
  }'
```

**Expected response:**
```json
{
  "multicast_id": 123456789,
  "success": 1,
  "failure": 0
}
```

---

## 🔍 **Step 9: Verify Everything Works**

### **9.1 Check App Initialization**

**Console should show:**
```
Doctor FCM Token: eyJ...
Doctor FCM token uploaded to backend
```

**If you see:**
```
Firebase/FCM init skipped: ...
```
This is normal on web during development (service worker issue on localhost)

### **9.2 Test Foreground Notification**

**When app is OPEN:**

1. Send test notification from Firebase Console
2. **Console should show:**
   ```
   ═══ NOTIFICATION SOURCE: FIREBASE FCM ═══
   Received FCM push notification (FOREGROUND)
   Title: Test Notification
   Data payload: {...}
   ✓ Showing local notification to user
   ```

3. **System notification appears** (Chrome/OS notification)
4. Click it → **App handles navigation**

### **9.3 Test Background Notification**

**When app is CLOSED:**

1. Close your Flutter app completely
2. Send test notification from Firebase Console
3. **System notification appears** (even though app is closed!)
4. Click notification → **App opens and navigates**

### **9.4 Test Navigation**

**Test document notification:**
```json
{
  "data": {
    "documentId": "42",
    "patientId": "37",
    "documentTitle": "X-Ray Report"
  }
}
```
**Expected:** Opens specific document viewer ✅

**Test appointment notification:**
```json
{
  "data": {
    "appointmentId": "166",
    "patientId": "37"
  }
}
```
**Expected:** Opens Calendar tab with appointment ✅

---

## 🌐 **Step 10: Production Deployment**

### **10.1 Web Deployment (Firebase Hosting)**

**Update firebase.json:**

```json
{
  "hosting": {
    "public": "build/web",
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
    "rewrites": [{
      "source": "**",
      "destination": "/index.html"
    }],
    "headers": [{
      "source": "/firebase-messaging-sw.js",
      "headers": [{
        "key": "Content-Type",
        "value": "application/javascript"
      }, {
        "key": "Service-Worker-Allowed",
        "value": "/"
      }]
    }]
  }
}
```

**Deploy:**
```bash
flutter build web --release
firebase deploy --only hosting
```

**FCM will work on production** (service worker issue is localhost-only)

### **10.2 Android Deployment**

```bash
flutter build apk --release
# or
flutter build appbundle --release
```

**FCM works automatically** on Android (no extra config needed)

### **10.3 iOS Deployment**

```bash
flutter build ios --release
```

**Important:** APNs certificate must be uploaded to Firebase (Step 5.2.B)

---

## 🐛 **Step 11: Troubleshooting**

### **Issue 1: "FCM token is null"**

**Cause:** Firebase not initialized or permissions denied

**Fix:**
```dart
// Check permissions
final settings = await FirebaseMessaging.instance.requestPermission();
if (settings.authorizationStatus == AuthorizationStatus.authorized) {
  final token = await FirebaseMessaging.instance.getToken();
  print('FCM Token: $token');
}
```

### **Issue 2: "Service worker registration failed" (Web)**

**Cause:** Common on localhost

**Fix:**
- This is expected on localhost
- Works fine in production (HTTPS)
- For local testing, use `flutter run -d chrome --web-hostname=localhost --web-port=8080`

### **Issue 3: Notifications not appearing**

**Check:**
1. FCM token uploaded to backend? → Check backend logs
2. Backend sending to correct token? → Check FCM Server response
3. Notification permissions granted? → Check app permissions
4. Correct FCM server key? → Verify in Firebase Console

### **Issue 4: Clicking notification does nothing**

**Check:**
1. `setOnNotificationTap` called in app.dart? ✅ (your app has this)
2. Payload includes required IDs? → Check notification data
3. Routes exist? → Verify AppRoutes

**Console should show:**
```
═══ NOTIFICATION TAP HANDLER ═══
DocumentId: 42
→ Opening specific document
```

If no logs, tap handler isn't firing.

---

## 📊 **Step 12: Backend Implementation Checklist**

### **Required Backend Changes:**

- [ ] **Add fcmToken field** to doctor_profiles table
- [ ] **API endpoint:** `PUT /api/doctors/me/fcm-token` (already implemented in your backend)
- [ ] **Install Firebase Admin SDK** in backend
- [ ] **Send FCM notifications** when events occur:
  - Document access requested
  - Document access approved/rejected
  - Appointment booked by patient
  - Appointment reminder (15 min before)
  - Task assigned
  - Message received

### **Example Backend Code (Spring Boot):**

```kotlin
@Service
class NotificationService(
    private val doctorProfileRepository: DoctorProfileRepository
) {

    fun sendDocumentAccessNotification(
        recipientDoctorId: Long,
        documentId: Long,
        patientId: Long,
        documentTitle: String,
        requestingDoctorName: String
    ) {
        val doctor = doctorProfileRepository.findById(recipientDoctorId).orElse(null)
        val fcmToken = doctor?.fcmToken ?: return

        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(
                Notification.builder()
                    .setTitle("Document Access Request")
                    .setBody("$requestingDoctorName requested access to $documentTitle")
                    .build()
            )
            .putAllData(mapOf(
                "type" to "DOCUMENT_ACCESS_REQUEST",
                "documentId" to documentId.toString(),
                "patientId" to patientId.toString(),
                "documentTitle" to documentTitle
            ))
            .build()

        try {
            FirebaseMessaging.getInstance().send(message)
            logger.info("FCM sent to doctor $recipientDoctorId")
        } catch (e: Exception) {
            logger.error("FCM send failed", e)
        }
    }
}
```

---

## 📝 **Step 13: Final Checklist**

### **Flutter App:**
- [ ] `firebase_options.dart` generated
- [ ] Dependencies added to pubspec.yaml
- [ ] Android manifest configured
- [ ] iOS capabilities enabled
- [ ] APNs key uploaded to Firebase
- [ ] Web service worker created
- [ ] Notification handlers implemented (already done!)
- [ ] Navigation logic implemented (already done!)

### **Backend:**
- [ ] FCM token storage in database
- [ ] FCM token save endpoint working
- [ ] Firebase Admin SDK installed
- [ ] FCM server key configured
- [ ] Notification sending implemented
- [ ] Correct payload structure (with all IDs)

### **Firebase Console:**
- [ ] Project created
- [ ] Cloud Messaging enabled
- [ ] APNs key uploaded (iOS)
- [ ] Web push certificate generated
- [ ] Test notification sent successfully

---

## 🎯 **Expected Behavior After Setup**

### **App Launch:**
```
1. Firebase initializes
2. FCM token generated
3. Token uploaded to backend via PUT /api/doctors/me/fcm-token
4. Console: "Doctor FCM Token: eyJ..."
5. Console: "Doctor FCM token uploaded to backend"
```

### **When Notification Arrives:**
```
1. Backend sends FCM notification
2. Firebase delivers to device
3. Console: "═══ NOTIFICATION SOURCE: FIREBASE FCM ═══"
4. System notification appears
5. Badge count updates
```

### **When User Taps:**
```
1. Console: "═══ NOTIFICATION TAP HANDLER ═══"
2. Extracts documentId/appointmentId/patientId
3. Navigates to specific screen
4. User sees exact document/appointment/patient
```

---

## 🚀 **Quick Start Commands**

```bash
# 1. Install FlutterFire CLI
dart pub global activate flutterfire_cli

# 2. Login to Firebase
firebase login

# 3. Configure project
cd /path/to/your/project
flutterfire configure

# 4. Add dependencies (if not already added)
# Edit pubspec.yaml, then:
flutter pub get

# 5. Run and test
flutter run -d chrome

# 6. Check console for FCM token
# Copy token for testing

# 7. Send test from Firebase Console
# Go to Messaging → New campaign

# 8. Deploy to production
flutter build web --release
firebase deploy --only hosting
```

---

## 📚 **Additional Resources**

### **Official Documentation:**
- Firebase FCM: https://firebase.google.com/docs/cloud-messaging
- FlutterFire: https://firebase.flutter.dev/docs/messaging/overview
- Firebase Admin SDK: https://firebase.google.com/docs/admin/setup

### **Your App's Files:**
- `lib/core/services/push_notification_service.dart` - FCM service implementation
- `lib/main.dart` - Firebase initialization
- `lib/app/app.dart` - Notification tap handlers
- `lib/features/notifications/` - Notifications UI

### **Documentation Created:**
- `NOTIFICATION_SYSTEM_ANALYSIS.md` - System overview
- `NOTIFICATION_SYSTEM_FIXES.md` - Recent fixes
- `NOTIFICATION_GRANULAR_NAVIGATION.md` - Navigation details
- `REST_API_NOTIFICATIONS_DISABLED.md` - REST API removal
- **THIS FILE** - Complete FCM setup guide

---

## ✅ **Summary**

Your app **already has all the FCM code**! You just need to:

1. **Create Firebase project** (10 min)
2. **Run `flutterfire configure`** (2 min)
3. **Add dependencies** (if missing) (2 min)
4. **Platform-specific config** (20-30 min)
5. **Backend integration** (variable - depends on backend)
6. **Test!** (10 min)

**Total time: 1-2 hours**

**Your Flutter app is already 100% ready for FCM - just needs Firebase project configuration!** 🚀
