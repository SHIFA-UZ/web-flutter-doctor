package shifa.doctor.data.api

/**
 * A file URL the backend sent, made absolute (07 §1.5): blank → null; `http(s)://` as it is (today's signed URLs);
 * anything else (older rows) against the backend's host, `/api/` left out. Flutter applied it to photos and images
 * only, so a relative voice or document URL failed (bug 16); here every attachment goes through it.
 */
fun absoluteFileUrl(
    raw: String?,
    apiBase: String,
): String? {
    val url = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) return url
    val host = apiBase.trimEnd('/').removeSuffix("/api")
    return "$host/${url.trimStart('/')}"
}
