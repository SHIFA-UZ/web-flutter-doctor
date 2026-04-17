# FCM Quick Start - 15 Minutes

**Fastest path to get Firebase Cloud Messaging working**

---

## ⚡ **Quick Steps**

### **1. Create Firebase Project (5 min)**

```bash
# Visit Firebase Console
open https://console.firebase.google.com/

# Create project:
# - Name: shifa-doctor-app
# - Analytics: Yes
# - Click "Create project"
```

### **2. Configure Flutter App (2 min)**

```bash
# Install FlutterFire CLI (one-time)
dart pub global activate flutterfire_cli

# Login to Firebase
firebase login

# Configure your project
cd /Users/sheroziy.saidkhodjaev/Projects/Private/shifa-doc-app-clean
flutterfire configure

# Select: shifa-doctor-app
# Platforms: android, ios, web (select all)
```

**This creates:** `lib/firebase_options.dart`

### **3. Add Dependencies (if missing) (1 min)**

Check `pubspec.yaml` for:
```yaml
firebase_messaging: ^15.0.0
flutter_local_notifications: ^17.0.0
```

If missing:
```bash
flutter pub add firebase_messaging flutter_local_notifications
flutter pub get
```

### **4. Platform Config - Android (2 min)**

Edit `android/app/src/main/AndroidManifest.xml`:

Add inside `<application>`:
```xml
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="shifa_doctor_channel" />
```

### **5. Platform Config - iOS (SKIP for now - web/android first)**

Required for iOS but can skip for initial testing.

### **6. Platform Config - Web (3 min)**

Create `web/firebase-messaging-sw.js`:

```javascript
importScripts('https://www.gstatic.com/firebasejs/10.7.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.0/firebase-messaging-compat.js');

// Copy config from lib/firebase_options.dart
firebase.initializeApp({
  apiKey: "YOUR_API_KEY",
  authDomain: "your-project.firebaseapp.com",
  projectId: "your-project-id",
  storageBucket: "your-project.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID"
});

const messaging = firebase.messaging();
```

### **7. Run and Test (2 min)**

```bash
flutter run -d chrome
```

**Check console for:**
```
Doctor FCM Token: eyJ...
Doctor FCM token uploaded to backend
```

**Copy the token!**

### **8. Send Test Notification (2 min)**

**Firebase Console:**
1. Go to **Messaging** → **New campaign**
2. Title: "Test"
3. Target: "Single device"
4. Paste your FCM token
5. Click **Publish**

**Expected:**
- Notification appears
- Console shows FCM logs
- Click works (navigates)

---

## ✅ **Done!**

**Time: ~15 minutes**

**What works:**
- ✅ Real-time push notifications
- ✅ Notification tapping navigation
- ✅ Granular routing (document/appointment/patient)

**For production:**
- Add iOS config (Step 5)
- Configure backend to send notifications
- Deploy to Firebase Hosting

**See FCM_INTEGRATION_GUIDE.md for complete details**
