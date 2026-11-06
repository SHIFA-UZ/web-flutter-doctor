package shifa.doctor.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The only JWT reader (01 §5.1): the app reads `exp`, `role` and `sub`, nothing else, and never verifies the signature
 * (the backend does). A token that doesn't parse has neither.
 */
object Jwt {
    fun expiresAtEpochSeconds(token: String?): Long? = claim(token, "exp") { it.jsonPrimitive.longOrNull }

    /** The account the token is for (`sub`): keys what the app remembers per user (the selected clinic). */
    fun subject(token: String?): String? = claim(token, "sub") { it.jsonPrimitive.content }

    /** `DOCTOR`, `CLINIC_STAFF` or `ADMIN`, upper-cased. */
    fun role(token: String?): String? = claim(token, "role") { it.jsonPrimitive.content.uppercase() }

    /**
     * 01 §3 / 15 §1, Flutter's `isJwtExpired`: expired once `exp + graceSeconds < now`, so a token keeps working for
     * [graceSeconds] past `exp` (clock skew). No readable `exp` counts as expired.
     */
    fun isExpired(
        token: String?,
        nowEpochSeconds: Long,
        graceSeconds: Long,
    ): Boolean = expiresAtEpochSeconds(token)?.let { it + graceSeconds < nowEpochSeconds } ?: true

    // A claim of the wrong type ("exp": {}) reads as missing, like an unparsable token.
    private fun <T> claim(
        token: String?,
        name: String,
        read: (JsonElement) -> T?,
    ): T? = runCatching { claims(token)?.get(name)?.let(read) }.getOrNull()

    @OptIn(ExperimentalEncodingApi::class)
    private fun claims(token: String?): JsonObject? =
        runCatching {
            val payload = token!!.split('.')[1]
            val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
            Json.parseToJsonElement(bytes.decodeToString()) as JsonObject
        }.getOrNull()
}
