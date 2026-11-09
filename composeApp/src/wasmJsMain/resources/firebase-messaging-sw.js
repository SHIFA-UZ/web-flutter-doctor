// PUSH on the web (doctor.shifa.uz, doctor-dev.shifa.uz): shows a push while the doctor's tab is closed or in the
// background, and opens the app on a click. The page's bridge is in index.html (`shifaPush`); both pin the same SDK and
// the same Firebase web config (public by design; a service worker can't read the page's).
var SDK = 'https://www.gstatic.com/firebasejs/13.1.0/';
var CONFIG = {
    apiKey: 'AIzaSyB8q7D4Fp1a9rtHzNxTtPdhGlPG-0n56HM',
    authDomain: 'shifa-doctor-staging.firebaseapp.com',
    projectId: 'shifa-doctor-staging',
    storageBucket: 'shifa-doctor-staging.firebasestorage.app',
    messagingSenderId: '55173524197',
    appId: '1:55173524197:web:5db83dc4fe03cb3a92420c'
};

// The push's ids only (never names: they would sit in the address bar and the history).
function ids(data) {
    var out = {};
    Object.keys(data || {}).forEach(function (k) {
        if (['title', 'message', 'patientName', 'documentTitle'].indexOf(k) < 0) out[k] = String(data[k]);
    });
    return out;
}

// Registered before the SDK's own handler, which would only open a link the backend doesn't send.
self.addEventListener('notificationclick', function (event) {
    var d = event.notification.data || {};
    var data = d.shifaPushTap || (d.FCM_MSG && d.FCM_MSG.data) || {};
    event.notification.close();
    event.stopImmediatePropagation();
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (list) {
            var open = list.filter(function (c) { return new URL(c.url).origin === self.location.origin; })[0];
            if (open) {
                open.postMessage({ shifaPushTap: ids(data) });
                return open.focus();
            }
            return clients.openWindow('/#push=' + encodeURIComponent(JSON.stringify(ids(data))));
        })
    );
});

importScripts(SDK + 'firebase-app-compat.js', SDK + 'firebase-messaging-compat.js');
firebase.initializeApp(CONFIG);
// A push with a title and body is shown by the SDK itself (the backend's text, in the doctor's language).
firebase.messaging();
