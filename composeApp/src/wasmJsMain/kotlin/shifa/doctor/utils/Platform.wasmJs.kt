package shifa.doctor.utils

// The debug menu (demo data, development backend) and logging: a development build served from this machine only.
actual val isDebugBuild: Boolean = WEB_DEBUG && isLocalHost()

// The same bundle serves both sites (web_cpanel_deploy.yml); the address decides the backend.
actual val isDevelopmentSite: Boolean = js("window.location.hostname === 'doctor-dev.shifa.uz'")

actual val appVersion: String = WEB_VERSION

actual val appBuild: String = WEB_BUILD

actual val platformName: String = "web"

actual fun browserPath(): String? = js("location.pathname")

// Kept for main.kt's Back bridge, which re-arms its history entry with it.
actual fun showBrowserPath(path: String) {
    js("window.shifaPath = path; if (location.pathname !== path) history.replaceState(history.state, '', path)")
}

private fun isLocalHost(): Boolean =
    js("window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'")
