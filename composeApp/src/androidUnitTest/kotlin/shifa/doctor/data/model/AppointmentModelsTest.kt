@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.api.apiJson

class AppointmentModelsTest {
    // dev, appointment 7 as seed.doctor1 reads it (2026-11-04), with nulls where the server sends them.
    private val dev =
        """{"id":7,"patientId":15,"patientName":"Gulnora Yusupova","doctorId":2,"startAt":"2026-10-30T07:00:00Z","endAt":"2026-10-30T07:30:00Z","location":"Video Consultation","status":"CONFIRMED","paymentStatus":"PAID","paymentAmountMinor":15000000,"paymentCurrency":"UZS","signatureRequested":false,"patientSignedAt":null,"patientSignatureImageBase64":null,"linkedPlanId":null,"linkedPlanTitle":null,"fulfilledLineIds":[],"briefingStatus":null,"attachmentCount":0,"alreadySigned":false,"documentIds":null,"notes":"Seed: confirmed","newField":1}"""

    private fun read(json: String) = apiJson.decodeFromString(AppointmentDetail.serializer(), json)

    @Test
    fun `dev's detail reads with its patient, times, payment and a null list as empty`() {
        val a = read(dev)
        assertThat(a.patientId to a.patientName).isEqualTo(15L to "Gulnora Yusupova")
        assertThat(a.start).isEqualTo(Instant.parse("2026-10-30T07:00:00Z"))
        assertThat(a.end).isEqualTo(Instant.parse("2026-10-30T07:30:00Z"))
        assertThat(a.paymentStatus to a.paymentAmountMinor).isEqualTo("PAID" to 15_000_000L)
        assertThat(a.documentIds).isEmpty()
        assertThat(a.kind).isEqualTo(VisitKind.Video)
        assertThat(a.signature).isEqualTo(SignatureState.None)
        assertThat(read("""{"id":3,"startAt":"soon"}""").start).isNull()
    }

    @Test
    fun `the kind is the backend's flag first, then the booked location`() {
        assertThat(VisitKind.of(null, "Shifa Dev Clinic")).isEqualTo(VisitKind.InPerson)
        assertThat(VisitKind.of(null, "video consultation")).isEqualTo(VisitKind.Video)
        assertThat(VisitKind.of(false, "Video Consultation")).isEqualTo(VisitKind.InPerson)
        assertThat(VisitKind.of(true, null)).isEqualTo(VisitKind.Video)
        assertThat(
            read("""{"id":1,"location":"Chilonzor","isVideoConsultation":true}""").kind,
        ).isEqualTo(VisitKind.Video)
    }

    @Test
    fun `the signature is waiting while requested and signed once a date or the flag says so`() {
        assertThat(read("""{"id":1,"signatureRequested":true}""").signature).isEqualTo(SignatureState.Waiting)
        val signed = read("""{"id":1,"signatureRequested":true,"patientSignedAt":"2026-10-30T07:40:00Z"}""")
        assertThat(signed.signature).isEqualTo(SignatureState.Signed)
        assertThat(signed.signedAt).isEqualTo(Instant.parse("2026-10-30T07:40:00Z"))
        assertThat(read("""{"id":1,"alreadySigned":true}""").signature).isEqualTo(SignatureState.Signed)
        assertThat(read("""{"id":1,"status":"completed"}""").completed).isTrue()
    }
}
