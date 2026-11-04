package shifa.doctor.data.catalog

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

// The behavioural contract ported from Flutter's profession_catalog_test.dart and dental_documentation_professions.dart
// (08 §4), against the bundled catalogue the app falls back to.
class ProfessionCatalogTest {
    private val bundled =
        runBlocking {
            CatalogRepository(
                bundled = { File("src/commonMain/composeResources/files/catalog/professions.json").readText() },
            )
                .bundledProfessions()
        }

    @Test
    fun `the catalogue has the 138 professions with Urologist as Urolog`() {
        assertThat(bundled).hasSize(138)
        assertThat(bundled.first { it.english == "Urologist" }.uzbek).isEqualTo("Urolog")
    }

    @Test
    fun `the catalogue includes the physician specialties onboarding needs`() {
        assertThat(bundled.map { it.english }).containsAtLeast(
            "Urologist",
            "Andrologist",
            "Hospitalist",
            "Colorectal Surgeon (Proctologist)",
            "Radiation Oncologist",
            "Interventional Cardiologist",
            "Interventional Radiologist",
            "Pain Medicine Specialist",
            "Hepatologist",
            "Pediatric Urologist",
        )
    }

    @Test
    fun `every dental catalogue entry opens the dental template, in English and in Uzbek`() {
        val dental =
            listOf(
                "Dentist",
                "Orthodontist",
                "Oral Surgeon",
                "Periodontist",
                "Prosthodontist",
                "Pediatric Dentist",
                "Endodontist",
                "Implantologist",
                "Maxillofacial Surgeon",
            )
        dental.forEach { english ->
            assertThat(isDentalProfession(english)).isTrue()
            assertThat(isDentalProfession(bundled.first { it.english == english }.uzbek)).isTrue()
        }
    }

    @Test
    fun `free-text Russian and Uzbek variants and a list with one dental entry count too`() {
        listOf(
            "Стоматолог",
            "детский стоматолог",
            "Хирург-стоматолог",
            "Ортодонт",
            "tish shifokori",
            "Dental hygienist",
        )
            .forEach { assertThat(isDentalProfession(it)).isTrue() }
        assertThat(documentationTemplateFor("Cardiologist, Dentist")).isEqualTo(DocumentationTemplate.Dental)
    }

    @Test
    fun `everything else, and nothing at all, is general`() {
        bundled
            .filterNot {
                    p ->
                listOf("dent", "odont", "oral", "implant", "maxillofacial").any { it in p.english.lowercase() }
            }
            .forEach { assertThat(isDentalProfession(it.english)).isFalse() }
        listOf(null, "", "  ", "Cardiologist", "Pediatrician").forEach {
            assertThat(documentationTemplateFor(it)).isEqualTo(DocumentationTemplate.General)
        }
    }
}
