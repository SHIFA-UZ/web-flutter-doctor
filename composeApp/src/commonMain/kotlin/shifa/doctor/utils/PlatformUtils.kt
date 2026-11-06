package shifa.doctor.utils

/** True in debug builds: the debug menu, the development backend and `httpLogger` exist only there. */
expect val isDebugBuild: Boolean

/** The marketing version ("1.0.1"; debug builds may add "-debug"). */
expect val appVersion: String

/** The build number (Android versionCode, iOS CFBundleVersion), for support diagnostics. */
expect val appBuild: String

/** "android" or "ios": the key of per-platform values in the backend config (BE-04). */
expect val platformName: String
