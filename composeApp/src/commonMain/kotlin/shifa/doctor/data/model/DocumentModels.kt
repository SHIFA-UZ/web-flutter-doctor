package shifa.doctor.data.model

import io.ktor.http.decodeURLPart
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A patient's document as `GET /patients/{id}/documents` lists it (06 §4.3; checked against dev): [canView] false
 * means locked (another doctor's private document or the patient's own upload): no [url], and the download answers 404
 * until access is granted (request-access).
 */
@Serializable
data class PatientDocument(
    val id: Long = 0,
    val title: String = "",
    val date: String? = null,
    val url: String? = null,
    val canView: Boolean = true,
    val creatorLabel: String? = null,
    val creatorType: String? = null,
    val category: String? = null,
    val isSharedWithTeam: Boolean = false,
    val uploadedByPatient: Boolean = false,
    val appointmentId: Long? = null,
    // BE-21: the signed-in doctor uploaded it, so they may rename, recategorise or remove it.
    val canEdit: Boolean = false,
) {
    /** The document's date; a missing or bad one is null (Flutter turned it into "now", 06 §4.3). */
    val day: LocalDate? get() = date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    val kind: DocumentCategory? get() = DocumentCategory.of(category)

    val locked: Boolean get() = !canView

    /** Viewable and with a file to fetch (flow 06/06 §9: `canView && url`). */
    val openable: Boolean get() = canView && !url.isNullOrBlank()

    /** Editable here: the doctor's own upload, except a 025-2 form's PDF, which the form editor keeps (title, link). */
    val editable: Boolean get() = canEdit && kind != DocumentCategory.FORM_025_2
}

/**
 * The 27 categories (06 §4.1): medical results are shared with every doctor of the patient; private ones only with
 * their creator and whoever is granted access. `FORM_025_2` is set by the form screen only ([pickable] false), so the
 * upload sheet never offers it, but its documents get a chip now (Flutter showed none).
 */
enum class DocumentCategory(
    val medical: Boolean,
    val pickable: Boolean = true,
) {
    BLOOD_TEST(true),
    URINE_TEST(true),
    STOOL_TEST(true),
    LAB_RESULT(true),
    MRI(true),
    CT_SCAN(true),
    XRAY(true),
    ULTRASOUND(true),
    MAMMOGRAPHY(true),
    ECG(true),
    EEG(true),
    ENDOSCOPY(true),
    BIOPSY(true),
    PATHOLOGY(true),
    IMAGING_OTHER(true),
    PRESCRIPTION(true),
    VACCINATION_RECORD(true),
    DISCHARGE_SUMMARY(true),
    REFERRAL(true),
    HOSPITAL_REPORT(true),
    ALLERGY_REPORT(true),
    OTHER_MEDICAL(true),
    APPOINTMENT_NOTE(false),
    REMOTE_TASK_DOCUMENT(false),
    INTERNAL_NOTE(false),
    OTHER_PRIVATE(false),
    FORM_025_2(false, pickable = false),
    ;

    /** Lab results (blood, urine, stool, lab): the activity feed's "Lab result uploaded" (flow 06/11, by category only). */
    val lab: Boolean get() = this in setOf(BLOOD_TEST, URINE_TEST, STOOL_TEST, LAB_RESULT)

    companion object {
        fun of(code: String?): DocumentCategory? =
            code?.trim()?.uppercase()?.let { c -> entries.firstOrNull { it.name == c } }
    }
}

/** A downloaded document: its bytes, the file name the server gave, and what it really is ([mime]). */
class DocumentFile(
    val bytes: ByteArray,
    val fileName: String?,
    val mime: String,
) {
    val isPdf: Boolean get() = mime == PDF
    val isImage: Boolean get() = mime.startsWith("image/")

    companion object {
        const val PDF = "application/pdf"
        private const val OCTET = "application/octet-stream"

        /**
         * The name in a `Content-Disposition` header (06 §2.10): RFC 5987 `filename*=UTF-8''…` first (Cyrillic names),
         * then `filename="…"` or a bare `filename=…`; null when there is none.
         */
        fun fileName(header: String?): String? {
            if (header.isNullOrBlank()) return null
            val params = params(header)
            params["filename*"]?.let { value ->
                // charset'language'encoded (the language may be empty or set).
                val encoded = value.split('\'', limit = 3).let { if (it.size == 3) it[2] else value }
                runCatching { encoded.decodeURLPart() }.getOrNull()?.takeIf(String::isNotBlank)?.let { return it }
            }
            return params["filename"]?.takeIf(String::isNotBlank)
        }

        /** `key=value` parameters, split on `;` outside quotes; quoted values unquoted (`\"` kept as `"`). */
        private fun params(header: String): Map<String, String> {
            val parts = mutableListOf<String>()
            val part = StringBuilder()
            var quoted = false
            var escaped = false
            header.forEach { c ->
                when {
                    escaped -> part.append(c).also { escaped = false }
                    c == '\\' && quoted -> part.append(c).also { escaped = true }
                    c == '"' -> part.append(c).also { quoted = !quoted }
                    c == ';' && !quoted -> parts += part.toString().also { part.clear() }
                    else -> part.append(c)
                }
            }
            parts += part.toString()
            return parts
                .filter { '=' in it }
                .associate { p ->
                    val value = p.substringAfter('=').trim()
                    val unquoted =
                        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
                            value.substring(1, value.length - 1).replace("\\\"", "\"")
                        } else {
                            value
                        }
                    p.substringBefore('=').trim().lowercase() to unquoted
                }
        }

        /**
         * What the file is (06 §2.10 order): its first bytes (JPEG, PNG, GIF, WebP, PDF), else the server's content type,
         * else the name's extension; anything else is a binary the viewer can't show (Flutter sent it to the PDF view).
         */
        fun mime(
            bytes: ByteArray,
            contentType: String?,
            fileName: String?,
        ): String {
            fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }

            fun ascii(
                at: Int,
                text: String,
            ) = bytes.size >= at + text.length && text.indices.all { bytes[at + it] == text[it].code.toByte() }
            when {
                starts(0xFF, 0xD8, 0xFF) -> return "image/jpeg"
                starts(0x89, 0x50, 0x4E, 0x47) -> return "image/png"
                ascii(0, "GIF8") -> return "image/gif"
                ascii(0, "RIFF") && ascii(8, "WEBP") -> return "image/webp"
                ascii(0, "%PDF") -> return PDF
            }
            contentType?.substringBefore(
                ';',
            )?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != OCTET }?.let { return it }
            return when (fileName?.substringAfterLast('.', "")?.lowercase()) {
                "pdf" -> PDF
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> OCTET
            }
        }
    }
}
