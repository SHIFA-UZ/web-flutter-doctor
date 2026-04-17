# FCM Backend Integration Guide

**Purpose:** Configure backend to send Firebase Cloud Messaging notifications
**Platform:** Spring Boot (Kotlin) - adjust for your stack
**Date:** March 4, 2026

---

## 📋 **Prerequisites**

- [ ] Firebase project created
- [ ] Firebase Admin SDK service account key downloaded
- [ ] Backend has access to doctor FCM tokens
- [ ] Backend can detect events that trigger notifications

---

## 🔧 **Step 1: Get Firebase Admin SDK Key**

### **1.1 Generate Service Account Key**

1. Go to **Firebase Console** → Your project
2. Click **⚙️ Settings** → **Project settings**
3. Go to **Service accounts** tab
4. Click **"Generate new private key"**
5. Click **"Generate key"**
6. **Save the JSON file** securely (e.g., `firebase-admin-key.json`)

⚠️ **IMPORTANT:** Keep this file secure! It has admin access to your Firebase project.

### **1.2 Add to Backend Environment**

**Option A: Environment Variable**
```bash
export FIREBASE_ADMIN_KEY_PATH=/path/to/firebase-admin-key.json
```

**Option B: Store JSON as String**
```bash
export FIREBASE_ADMIN_KEY='{"type":"service_account","project_id":"..."}'
```

---

## 📦 **Step 2: Add Firebase Admin SDK**

### **For Spring Boot (Maven):**

`pom.xml`:
```xml
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.2.0</version>
</dependency>
```

### **For Spring Boot (Gradle):**

`build.gradle.kts`:
```kotlin
implementation("com.google.firebase:firebase-admin:9.2.0")
```

Then:
```bash
./gradlew build
```

### **For Node.js:**

```bash
npm install firebase-admin
```

---

## ⚙️ **Step 3: Initialize Firebase Admin**

### **Spring Boot Example:**

**File:** `src/main/kotlin/config/FirebaseConfig.kt`

```kotlin
package com.shifa.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.FileInputStream
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun initializeFirebase() {
        try {
            val serviceAccountPath = System.getenv("FIREBASE_ADMIN_KEY_PATH")
                ?: "firebase-admin-key.json"  // Fallback to classpath

            val serviceAccount = try {
                FileInputStream(serviceAccountPath)
            } catch (e: Exception) {
                // Try classpath
                ClassPathResource(serviceAccountPath).inputStream
            }

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                println("✓ Firebase Admin SDK initialized successfully")
            }
        } catch (e: Exception) {
            println("✗ Firebase Admin SDK initialization failed: ${e.message}")
            // App will continue without FCM
        }
    }
}
```

---

## 🔔 **Step 4: Create Notification Service**

### **File:** `src/main/kotlin/service/FcmNotificationService.kt`

```kotlin
package com.shifa.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FcmNotificationService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Send FCM notification to a doctor
     *
     * @param fcmToken Doctor's FCM device token
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data for navigation (documentId, appointmentId, patientId, etc.)
     */
    fun sendToDoctor(
        fcmToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        if (fcmToken.isBlank()) {
            logger.warn("Cannot send FCM: token is empty")
            return false
        }

        try {
            val message = Message.builder()
                .setToken(fcmToken)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("FCM sent successfully: $response")
            return true
        } catch (e: Exception) {
            logger.error("FCM send failed to token ${fcmToken.take(10)}...: ${e.message}")
            return false
        }
    }

    /**
     * Send document access request notification
     */
    fun sendDocumentAccessRequest(
        recipientFcmToken: String,
        documentId: Long,
        patientId: Long,
        documentTitle: String,
        requestingDoctorName: String,
        notificationId: Long
    ) {
        sendToDoctor(
            fcmToken = recipientFcmToken,
            title = "Document Access Request",
            body = "$requestingDoctorName requested access to \"$documentTitle\"",
            data = mapOf(
                "notificationId" to notificationId.toString(),
                "type" to "DOCUMENT_ACCESS_REQUEST",
                "documentId" to documentId.toString(),
                "patientId" to patientId.toString(),
                "documentTitle" to documentTitle
            )
        )
    }

    /**
     * Send appointment reminder notification
     */
    fun sendAppointmentReminder(
        doctorFcmToken: String,
        appointmentId: Long,
        patientName: String,
        appointmentTime: String,
        notificationId: Long
    ) {
        sendToDoctor(
            fcmToken = doctorFcmToken,
            title = "Upcoming Appointment",
            body = "Appointment with $patientName at $appointmentTime",
            data = mapOf(
                "notificationId" to notificationId.toString(),
                "type" to "APPOINTMENT_REMINDER",
                "appointmentId" to appointmentId.toString()
            )
        )
    }

    /**
     * Send appointment booked notification
     */
    fun sendAppointmentBooked(
        doctorFcmToken: String,
        appointmentId: Long,
        patientName: String,
        appointmentTime: String,
        notificationId: Long
    ) {
        sendToDoctor(
            fcmToken = doctorFcmToken,
            title = "New Appointment Booked",
            body = "$patientName booked an appointment at $appointmentTime",
            data = mapOf(
                "notificationId" to notificationId.toString(),
                "type" to "APPOINTMENT_BOOKED_BY_PATIENT",
                "appointmentId" to appointmentId.toString()
            )
        )
    }
}
```

---

## 💾 **Step 5: Store FCM Token**

### **5.1 Add fcmToken Column**

**Database migration:**

```sql
-- V1__add_fcm_token.sql
ALTER TABLE doctor_profiles
ADD COLUMN fcm_token VARCHAR(512);

CREATE INDEX idx_doctor_profiles_fcm_token
ON doctor_profiles(fcm_token);
```

### **5.2 Update Entity**

```kotlin
@Entity
@Table(name = "doctor_profiles")
class DoctorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    // ... existing fields ...

    @Column(name = "fcm_token", length = 512)
    var fcmToken: String? = null
}
```

### **5.3 Create/Update Endpoint**

```kotlin
@RestController
@RequestMapping("/api/doctors")
class DoctorController(
    private val doctorProfileRepository: DoctorProfileRepository
) {

    @PutMapping("/me/fcm-token")
    fun updateFcmToken(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<*> {
        val fcmToken = body["fcmToken"] ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "fcmToken required"))

        val doctorId = principal.id
        val doctor = doctorProfileRepository.findByUserId(doctorId)
            ?: return ResponseEntity.notFound().build<Any>()

        doctor.fcmToken = fcmToken
        doctorProfileRepository.save(doctor)

        return ResponseEntity.ok(mapOf("message" to "FCM token saved"))
    }
}
```

---

## 📤 **Step 6: Send Notifications on Events**

### **Example: Document Access Request**

```kotlin
@Service
class DocumentAccessService(
    private val doctorProfileRepository: DoctorProfileRepository,
    private val notificationRepository: NotificationRepository,
    private val fcmService: FcmNotificationService
) {

    fun requestDocumentAccess(
        requestingDoctorId: Long,
        documentId: Long,
        patientId: Long
    ) {
        // 1. Get document and owner
        val document = documentRepository.findById(documentId).orElseThrow()
        val ownerDoctorId = document.createdByDoctorId

        // 2. Create notification in database
        val notification = Notification(
            userId = ownerDoctorId,
            type = "DOCUMENT_ACCESS_REQUEST",
            title = "Document Access Request",
            message = "Dr. ${requestingDoctor.name} requested access",
            documentId = documentId,
            patientId = patientId,
            documentTitle = document.title
        )
        notificationRepository.save(notification)

        // 3. Get owner's FCM token
        val ownerDoctor = doctorProfileRepository.findById(ownerDoctorId).orElse(null)
        val fcmToken = ownerDoctor?.fcmToken

        // 4. Send FCM push (if token available)
        if (fcmToken != null && fcmToken.isNotBlank()) {
            fcmService.sendDocumentAccessRequest(
                recipientFcmToken = fcmToken,
                documentId = documentId,
                patientId = patientId,
                documentTitle = document.title,
                requestingDoctorName = requestingDoctor.name,
                notificationId = notification.id!!
            )
        }
    }
}
```

---

## 🧪 **Step 7: Test**

### **7.1 Get FCM Token from App**

Run Flutter app:
```bash
flutter run -d chrome
```

Console shows:
```
Doctor FCM Token: eyJhbGc...
```

**Copy this token!**

### **7.2 Test with cURL**

```bash
# Get your Firebase Server Key from Console
# Go to: Project Settings → Cloud Messaging → Server key

curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=YOUR_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "PASTE_FCM_TOKEN_HERE",
    "notification": {
      "title": "Test Appointment",
      "body": "You have an appointment at 14:00"
    },
    "data": {
      "notificationId": "999",
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
  "failure": 0,
  "results": [{"message_id": "0:..."}]
}
```

**In Flutter app:**
- System notification appears
- Click it → Navigates to Calendar
- Console shows FCM logs

---

## 📊 **Step 8: Implement for All Events**

### **Events That Should Send FCM:**

| Event | Notification Type | Required Data |
|-------|------------------|---------------|
| Document access requested | DOCUMENT_ACCESS_REQUEST | documentId, patientId, documentTitle |
| Document access approved | DOCUMENT_ACCESS_APPROVED | documentId, patientId, documentTitle |
| Document access rejected | DOCUMENT_ACCESS_REJECTED | documentId, patientId, documentTitle |
| Appointment booked | APPOINTMENT_BOOKED_BY_PATIENT | appointmentId, patientId |
| Appointment reminder | APPOINTMENT_REMINDER | appointmentId, patientId |
| Appointment cancelled | APPOINTMENT_CANCELLED | appointmentId, patientId |
| Task assigned | TASK_ASSIGNED | taskId, patientId |
| Task completed | TASK_COMPLETED | taskId, patientId |
| Message received | CHAT_MESSAGE | conversationId, senderId |

### **Implementation Pattern:**

```kotlin
// In your event handler/service
fun onEventOccurred(...) {
    // 1. Create notification in database (for history)
    val notification = createNotification(...)
    notificationRepository.save(notification)

    // 2. Get recipient's FCM token
    val fcmToken = getDoctorFcmToken(recipientDoctorId)

    // 3. Send FCM push (if token available)
    if (fcmToken != null) {
        fcmService.sendToDoctor(
            fcmToken = fcmToken,
            title = notification.title,
            body = notification.message,
            data = buildDataPayload(notification)
        )
    }
}

private fun buildDataPayload(notification: Notification): Map<String, String> {
    return buildMap {
        put("notificationId", notification.id.toString())
        put("type", notification.type)
        notification.appointmentId?.let { put("appointmentId", it.toString()) }
        notification.patientId?.let { put("patientId", it.toString()) }
        notification.documentId?.let { put("documentId", it.toString()) }
        notification.documentTitle?.let { put("documentTitle", it) }
    }
}
```

---

## 🔐 **Step 9: Security Considerations**

### **9.1 Validate FCM Token**

```kotlin
fun saveFcmToken(doctorId: Long, fcmToken: String) {
    // Validate token format (basic check)
    if (fcmToken.length < 100) {
        throw IllegalArgumentException("Invalid FCM token format")
    }

    // Save to database
    val doctor = doctorProfileRepository.findByUserId(doctorId)
    doctor.fcmToken = fcmToken
    doctorProfileRepository.save(doctor)
}
```

### **9.2 Handle Token Errors**

```kotlin
fun sendNotification(fcmToken: String, ...) {
    try {
        FirebaseMessaging.getInstance().send(message)
    } catch (e: FirebaseMessagingException) {
        when (e.messagingErrorCode) {
            MessagingErrorCode.INVALID_ARGUMENT -> {
                // Token is invalid - remove from database
                removeInvalidToken(fcmToken)
            }
            MessagingErrorCode.UNREGISTERED -> {
                // App uninstalled - remove token
                removeInvalidToken(fcmToken)
            }
            else -> {
                logger.error("FCM send failed", e)
            }
        }
    }
}
```

### **9.3 Don't Expose Tokens**

❌ **DON'T:**
```kotlin
@GetMapping("/doctors/{id}")
fun getDoctor(@PathVariable id: Long): DoctorDTO {
    val doctor = repository.findById(id)
    return DoctorDTO(
        id = doctor.id,
        name = doctor.name,
        fcmToken = doctor.fcmToken  // ❌ DON'T expose!
    )
}
```

✅ **DO:**
```kotlin
// Only allow doctors to update their OWN token
@PutMapping("/me/fcm-token")
fun updateMyFcmToken(@AuthenticationPrincipal principal: UserPrincipal, ...)
```

---

## 🧪 **Step 10: Testing Backend Integration**

### **10.1 Test Token Storage**

```bash
# 1. Flutter app sends token
PUT /api/doctors/me/fcm-token
Authorization: Bearer <jwt>
Body: {"fcmToken": "eyJhbGc..."}

# 2. Check database
SELECT id, fcm_token FROM doctor_profiles WHERE id = 31;

# Should show: fcm_token = "eyJhbGc..."
```

### **10.2 Test Notification Sending**

**Create test endpoint:**

```kotlin
@PostMapping("/test/send-fcm/{doctorId}")
fun testSendFcm(@PathVariable doctorId: Long): ResponseEntity<*> {
    val doctor = doctorProfileRepository.findById(doctorId).orElse(null)
        ?: return ResponseEntity.notFound().build<Any>()

    val fcmToken = doctor.fcmToken
        ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "Doctor has no FCM token"))

    val success = fcmService.sendToDoctor(
        fcmToken = fcmToken,
        title = "Test Notification",
        body = "This is a test from backend",
        data = mapOf(
            "type" to "TEST",
            "appointmentId" to "166"
        )
    )

    return if (success) {
        ResponseEntity.ok(mapOf("message" to "FCM sent successfully"))
    } else {
        ResponseEntity.status(500).body(mapOf("error" to "FCM send failed"))
    }
}
```

**Test:**
```bash
curl -X POST http://localhost:8080/api/test/send-fcm/31 \
  -H "Authorization: Bearer YOUR_JWT"
```

**Expected:**
- Flutter app receives notification
- Console shows FCM logs
- Notification appears in system tray

---

## 📋 **Step 11: Notification Payload Structure**

### **Required Fields:**

```kotlin
data class FcmPayload(
    val notificationId: String,  // For marking as read
    val type: String,             // Notification type
    val appointmentId: String? = null,  // For appointment navigation
    val patientId: String? = null,      // For patient navigation
    val documentId: String? = null,     // For document navigation
    val documentTitle: String? = null   // Document name
)
```

### **Example Payloads:**

**Document Access:**
```kotlin
mapOf(
    "notificationId" to "123",
    "type" to "DOCUMENT_ACCESS_REQUEST",
    "documentId" to "42",
    "patientId" to "37",
    "documentTitle" to "X-Ray Report"
)
```

**Appointment:**
```kotlin
mapOf(
    "notificationId" to "124",
    "type" to "APPOINTMENT_REMINDER",
    "appointmentId" to "166",
    "patientId" to "37"
)
```

---

## 🔄 **Step 12: Integration with Existing Notification System**

### **Your Current Flow:**

```kotlin
// When event occurs (e.g., document access requested)
fun onDocumentAccessRequested(...) {
    // 1. Create notification in database
    val notification = Notification(...)
    notificationRepository.save(notification)

    // 2. Send FCM push (NEW!)
    val fcmToken = getDoctorFcmToken(recipientDoctorId)
    if (fcmToken != null) {
        fcmService.sendDocumentAccessRequest(
            recipientFcmToken = fcmToken,
            documentId = documentId,
            patientId = patientId,
            documentTitle = documentTitle,
            requestingDoctorName = requestingDoctor.name,
            notificationId = notification.id!!
        )
    }
}
```

**Both systems work together:**
- Database notification: For history and when user checks notification tab
- FCM push: For real-time delivery and instant notification

---

## ⚠️ **Common Pitfalls**

### **1. Missing Data Fields**

❌ **Bad:**
```kotlin
data = mapOf("type" to "APPOINTMENT")  // No IDs!
```

✅ **Good:**
```kotlin
data = mapOf(
    "type" to "APPOINTMENT_REMINDER",
    "appointmentId" to "166",  // Required for navigation!
    "patientId" to "37"
)
```

### **2. Wrong Token**

```kotlin
// Make sure you're getting the RECIPIENT's token, not the sender's!
val recipientToken = doctorProfileRepository
    .findById(recipientDoctorId)  // ← Recipient!
    .map { it.fcmToken }
    .orElse(null)
```

### **3. Not Handling Failures**

```kotlin
try {
    fcmService.send(...)
} catch (e: Exception) {
    // Don't let FCM failure break your main flow!
    logger.error("FCM failed, but notification saved to DB", e)
}
```

---

## 📊 **Step 13: Monitoring & Logging**

### **Log Important Events:**

```kotlin
logger.info("FCM notification sent to doctor $doctorId (type: $type)")
logger.info("FCM token updated for doctor $doctorId")
logger.warn("FCM send failed for doctor $doctorId: ${e.message}")
logger.error("Invalid FCM token removed for doctor $doctorId")
```

### **Metrics to Track:**

- FCM tokens registered: Count of doctors with non-null fcmToken
- FCM send success rate: successful_sends / total_sends
- FCM send failures: Track error types
- Average notification delivery time

---

## ✅ **Checklist**

### **Backend Setup:**
- [ ] Firebase Admin SDK initialized
- [ ] Service account key configured
- [ ] FcmNotificationService created
- [ ] FCM token storage endpoint working
- [ ] Database column added
- [ ] Notification sending implemented
- [ ] Error handling added
- [ ] Logging added

### **Flutter App:**
- [ ] firebase_options.dart generated
- [ ] Dependencies installed
- [ ] Platform configs done
- [ ] FCM token uploads to backend
- [ ] Notification handlers work
- [ ] Navigation tested

### **Testing:**
- [ ] Token storage works
- [ ] Test endpoint sends FCM
- [ ] App receives notification
- [ ] Clicking navigates correctly
- [ ] All data fields present

---

## 🎯 **Expected Flow After Setup**

```
1. Doctor opens app
   ↓
2. FCM token generated
   ↓
3. PUT /api/doctors/me/fcm-token (token saved)
   ↓
4. Doctor closes app
   ↓
5. Patient books appointment
   ↓
6. Backend creates notification in DB
   ↓
7. Backend sends FCM to doctor's token
   ↓
8. Firebase delivers to doctor's device
   ↓
9. System notification appears (even though app closed!)
   ↓
10. Doctor taps notification
   ↓
11. App opens to Calendar tab
   ↓
12. Doctor sees the appointment
```

**Real-time, instant, works even when app is closed!** ✅

---

## 📚 **Additional Resources**

- **Firebase Admin SDK (Java):** https://firebase.google.com/docs/admin/setup#java
- **FCM HTTP v1 API:** https://firebase.google.com/docs/cloud-messaging/send-message
- **Your Flutter service:** `lib/core/services/push_notification_service.dart`

---

## 🚀 **Quick Commands**

```bash
# Backend (Spring Boot)
./gradlew build
./gradlew bootRun

# Flutter
flutterfire configure
flutter pub get
flutter run -d chrome

# Test notification
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=YOUR_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{"to":"TOKEN","notification":{"title":"Test"}}'
```

**Your app code is ready - just need Firebase project setup and backend integration!**
