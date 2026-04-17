importScripts('https://www.gstatic.com/firebasejs/10.7.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.0/firebase-messaging-compat.js');

// Your Firebase config (copied from firebase_options.dart)
firebase.initializeApp({
  apiKey: "AIzaSyB8q7D4Fp1a9rtHzNxTtPdhGlPG-0n56HM",
  authDomain: "shifa-doctor-staging.firebaseapp.com",
  projectId: "shifa-doctor-staging",
  storageBucket: "shifa-doctor-staging.firebasestorage.app",
  messagingSenderId: "55173524197",
  appId: "1:55173524197:web:964e0be5d1a5700892420c",
  measurementId: "G-W89E3NN4CG"
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
