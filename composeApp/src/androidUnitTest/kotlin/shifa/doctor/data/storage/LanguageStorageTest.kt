package shifa.doctor.data.storage

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import org.junit.Test

class LanguageStorageTest {
    private val settings = MapSettings()

    private fun storage(
        language: String = "en",
        region: String = "US",
        zone: String = "Europe/Berlin",
    ) = LanguageStorage(settings, language, region, zone)

    @Test
    fun `outside Uzbekistan with nothing saved it is English`() {
        assertThat(storage().language.value).isEqualTo(AppLanguage.English)
    }

    @Test
    fun `Uzbekistan by device language, region or time zone means Uzbek`() {
        assertThat(storage(language = "uz").language.value).isEqualTo(AppLanguage.Uzbek)
        assertThat(storage(region = "UZ").language.value).isEqualTo(AppLanguage.Uzbek)
        assertThat(storage(zone = "Asia/Tashkent").language.value).isEqualTo(AppLanguage.Uzbek)
    }

    @Test
    fun `an explicit choice wins over the region and survives a restart`() {
        storage().set(AppLanguage.UzbekCyrillic)

        assertThat(storage(region = "UZ").language.value).isEqualTo(AppLanguage.UzbekCyrillic)
    }

    @Test
    fun `a +998 phone sets Uzbek only while nothing is saved`() {
        val s = storage()
        s.applyRegionalDefault("+998 90 123 45 67")
        assertThat(s.language.value).isEqualTo(AppLanguage.Uzbek)
        assertThat(storage().language.value).isEqualTo(AppLanguage.Uzbek)

        s.set(AppLanguage.Russian)
        s.applyRegionalDefault("+998901234567")
        assertThat(s.language.value).isEqualTo(AppLanguage.Russian)
    }

    @Test
    fun `a saved non-explicit language is not overridden on a cold start in Uzbekistan (LANG-02 Fix)`() {
        settings.putString("doctor_language", "ru")

        assertThat(storage(zone = "Asia/Tashkent").language.value).isEqualTo(AppLanguage.Russian)
    }

    @Test
    fun `other numbers change nothing`() {
        val s = storage()
        s.applyRegionalDefault("+49 151 0000000")
        s.applyRegionalDefault(null)
        assertThat(s.language.value).isEqualTo(AppLanguage.English)
    }

    @Test
    fun `Cyrillic reads the uz resources and syncs as uz`() {
        assertThat(AppLanguage.UzbekCyrillic.resourceCode).isEqualTo("uz")
        assertThat(AppLanguage.UzbekCyrillic.backendCode).isEqualTo("uz")
        assertThat(AppLanguage.fromTag("UZ-CYRL")).isEqualTo(AppLanguage.UzbekCyrillic)
        assertThat(AppLanguage.fromTag("de")).isNull()
    }
}
