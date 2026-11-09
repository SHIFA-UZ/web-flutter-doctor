package shifa.doctor.utils

/** True in debug builds: the debug menu, the development backend and `httpLogger` exist only there. */
expect val isDebugBuild: Boolean

/** True only for the web app opened at the development site (doctor-dev.shifa.uz): it talks to the development backend. */
expect val isDevelopmentSite: Boolean

/** The marketing version ("1.0.1"; debug builds may add "-debug"). */
expect val appVersion: String

/** The build number (Android versionCode, iOS CFBundleVersion), for support diagnostics. */
expect val appBuild: String

/** "android" or "ios": the key of per-platform values in the backend config (BE-04). */
expect val platformName: String

/** WEB-10: the page's path on the web ("/patients/12"); null on the phones. */
expect fun browserPath(): String?

/** WEB-10: shows [path] in the web's address bar, in place (Back stays the app's Back); nothing on the phones. */
expect fun showBrowserPath(path: String)
