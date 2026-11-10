package shifa.doctor.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentModelsTest {
    @Test
    fun `28 categories, the form and the booking ones not offered for upload, lab results by category`() {
        assertThat(DocumentCategory.entries).hasSize(28)
        assertThat(DocumentCategory.entries.count { it.medical }).isEqualTo(23)
        assertThat(
            DocumentCategory.entries.filterNot { it.pickable },
        ).containsExactly(DocumentCategory.PRE_APPOINTMENT, DocumentCategory.FORM_025_2)
        assertThat(DocumentCategory.of("PRE_APPOINTMENT")).isEqualTo(DocumentCategory.PRE_APPOINTMENT)
        assertThat(DocumentCategory.of(" blood_test ")).isEqualTo(DocumentCategory.BLOOD_TEST)
        assertThat(DocumentCategory.of("NEW_ONE")).isNull()
        assertThat(DocumentCategory.URINE_TEST.lab).isTrue()
        assertThat(DocumentCategory.MRI.lab).isFalse()
    }

    @Test
    fun `file names come from content disposition in every form`() {
        assertThat(DocumentFile.fileName("inline; filename=\"a b.pdf\"")).isEqualTo("a b.pdf")
        assertThat(DocumentFile.fileName("attachment; filename=report.pdf")).isEqualTo("report.pdf")
        assertThat(
            DocumentFile.fileName("attachment; filename*=UTF-8''%D0%90.pdf; filename=\"A.pdf\""),
        ).isEqualTo("А.pdf")
        assertThat(DocumentFile.fileName("inline; filename=\"scan; final.pdf\"")).isEqualTo("scan; final.pdf")
        assertThat(DocumentFile.fileName("inline; FILENAME=\"say \\\"hi\\\".pdf\"")).isEqualTo("say \"hi\".pdf")
        assertThat(DocumentFile.fileName("attachment; filename*=UTF-8'en'report%20one.pdf")).isEqualTo("report one.pdf")
        assertThat(DocumentFile.fileName("inline")).isNull()
        assertThat(DocumentFile.fileName(null)).isNull()
    }

    @Test
    fun `the type comes from the bytes first, then the header, then the extension`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8".encodeToByteArray()
        assertThat(DocumentFile.mime(png, "application/pdf", "x.pdf")).isEqualTo("image/png")
        assertThat(DocumentFile.mime(webp, null, null)).isEqualTo("image/webp")
        assertThat(DocumentFile.mime("%PDF-1.4".encodeToByteArray(), null, null)).isEqualTo(DocumentFile.PDF)
        assertThat(DocumentFile.mime(byteArrayOf(1, 2), "text/plain; charset=utf-8", null)).isEqualTo("text/plain")
        assertThat(DocumentFile.mime(byteArrayOf(1, 2), "application/octet-stream", "scan.JPG")).isEqualTo("image/jpeg")
        assertThat(DocumentFile.mime(byteArrayOf(1, 2), null, "notes.docx")).isEqualTo("application/octet-stream")
    }

    @Test
    fun `only a viewable document with a file opens`() {
        assertThat(PatientDocument(1, url = "/f").openable).isTrue()
        assertThat(PatientDocument(1, url = " ").openable).isFalse()
        assertThat(PatientDocument(1, url = null).openable).isFalse()
        assertThat(PatientDocument(1, url = "/f", canView = false).openable).isFalse()
    }
}
