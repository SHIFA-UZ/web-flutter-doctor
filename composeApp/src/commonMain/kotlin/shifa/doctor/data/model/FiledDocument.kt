package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** A PDF the backend rendered and filed as the patient's document (BE-01): its id and signed URL. */
@Serializable
data class FiledDocument(
    val documentId: Long,
    val url: String? = null,
)
