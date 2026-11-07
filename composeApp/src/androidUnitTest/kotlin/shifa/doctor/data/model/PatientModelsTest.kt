@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.api.apiJson

class PatientModelsTest {
    // A dev row (2026-11-04), with its inline documents and the fields the spec lists.
    private val row =
        """{"id":18,"name":"Aziz Rahimov","phone":"+998900000105","phones":["+998900000105"],"email":null,"address":null,"birthDate":"2005-05-15","language":"uz","photoUrl":null,"chronicDisease":null,"hasAccount":false,"username":null,"documents":[{"id":1,"title":"X"}],"locationCountry":null,"locationRegion":null,"locationDistrict":null,"locationCity":"Tashkent","locationPostalCode":null,"locationStreetAddress":"Amir Temur 1","timeZone":"Asia/Tashkent","smsReminderEnabled":false,"smsReminderHoursBefore":24,"gender":"male","bloodGroup":null,"allergies":null,"clinicalStatus":"ACTIVE","atRisk":false,"followUpRequired":false}"""

    @Test
    fun `a dev row reads with its display id, age, status and location`() {
        val p = apiJson.decodeFromString(Patient.serializer(), row)
        assertThat(p.displayId).isEqualTo("P-00018")
        assertThat(p.age(LocalDate(2026, 5, 14))).isEqualTo(20)
        assertThat(p.age(LocalDate(2026, 5, 15))).isEqualTo(21)
        assertThat(p.status).isEqualTo(PatientStatus.Active)
        assertThat(p.allPhones).containsExactly("+998900000105")
        assertThat(p.formattedLocation).isEqualTo("Amir Temur 1, Tashkent")
    }

    @Test
    fun `lenient fields - single phone, odd SMS hours, unknown status, missing name and dates`() {
        val p =
            apiJson.decodeFromString(
                Patient.serializer(),
                """{"id":"7","phone":" +998901 ","phones":null,"smsReminderHoursBefore":6,"clinicalStatus":"archived","birthDate":"not a date","address":"Old street"}""",
            )
        assertThat(p.id).isEqualTo(7)
        assertThat(p.name).isEmpty()
        assertThat(p.allPhones).containsExactly("+998901")
        assertThat(p.smsHours).isEqualTo(24)
        assertThat(p.status).isEqualTo(PatientStatus.Unknown)
        assertThat(p.age(LocalDate(2026, 1, 1))).isNull()
        assertThat(p.formattedLocation).isEqualTo("Old street")
        assertThat(
            apiJson.decodeFromString(
                Patient.serializer(),
                """{"clinicalStatus":"at_risk","smsReminderHoursBefore":1}""",
            ).let {
                it.status to it.smsHours
            },
        )
            .isEqualTo(PatientStatus.AtRisk to 1)
    }

    @Test
    fun `stale single phone, structured location order, future birth, SMS hours that aren't numbers`() {
        val p =
            apiJson.decodeFromString(
                Patient.serializer(),
                """{"id":3,"phone":"old","phones":["new"],"locationCountry":"Uzbekistan","address":"Legacy 5","birthDate":"2026-12-01","smsReminderHoursBefore":"invalid"}""",
            )
        assertThat(p.allPhones).containsExactly("new")
        assertThat(p.formattedLocation).isEqualTo("Legacy 5") // a country alone doesn't hide the street
        assertThat(p.age(LocalDate(2026, 11, 4))).isNull()
        assertThat(p.smsHours).isEqualTo(24)
        val q =
            apiJson.decodeFromString(
                Patient.serializer(),
                """{"locationStreetAddress":"Amir Temur 1","locationDistrict":"Yunusobod","locationCity":"Tashkent","locationRegion":"Toshkent"}""",
            )
        assertThat(q.formattedLocation).isEqualTo("Amir Temur 1, Tashkent, Yunusobod, Toshkent")
    }
}
