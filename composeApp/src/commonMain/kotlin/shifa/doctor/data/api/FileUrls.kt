package shifa.doctor.data.api

import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** The hosts that serve the backend's files: production signs its URLs with the Railway host. */
private val FILE_HOSTS = setOf("api.shifa.uz", "api-dev.shifa.uz", "shifa-doc-backend-mvp-production.up.railway.app")

/**
 * A file URL the backend sent, made absolute (07 §1.5): blank → null; `https://` on a backend host as it is (today's
 * signed URLs); anything relative (older rows) against the backend's host, `/api/` left out. Flutter applied it to
 * photos and images only, so a relative voice or document URL failed (bug 16); here every attachment goes through it.
 *
 * The sender of a chat message chooses its attachment URL, so anything else is null: another host (the app would
 * fetch or open a stranger's page), plain http, and `/api/` paths (the HTTP client adds the token to those).
 */
fun absoluteFileUrl(
    raw: String?,
    apiBase: String,
): String? {
    val url = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val apiHost = runCatching { Url(apiBase).host }.getOrNull()
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return null
        val known = parsed.host == apiHost || parsed.host in FILE_HOSTS
        return url.takeIf { parsed.protocol == URLProtocol.HTTPS && known && !isApiPath(parsed.encodedPath) }
    }
    val path = "/" + url.trimStart('/')
    if (isApiPath(path)) return null
    return apiBase.trimEnd('/').removeSuffix("/api") + path
}

private fun isApiPath(path: String) = path == "/api" || path.startsWith("/api/")

/**
 * What an image is cached under: its URL without the query. The backend signs a file's URL anew on every read
 * (`?exp=&sig=`), so the full URL would download and decode the same picture again on each poll.
 */
fun fileCacheKey(url: String): String = url.substringBefore('?')

/** A request that carries a file either way gets [ApiConfig.FILE_TIMEOUT_MS] instead of the 30 s of an API call. */
fun HttpRequestBuilder.fileTimeout() =
    timeout {
        requestTimeoutMillis = ApiConfig.FILE_TIMEOUT_MS
        socketTimeoutMillis = ApiConfig.FILE_TIMEOUT_MS
    }
