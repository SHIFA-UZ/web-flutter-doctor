package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Golden words for the Latin → Cyrillic port (01 §8 "Done when": o', g', sh, ch, ng, placeholders preserved).
class UzbekCyrillicTest {
    private fun cyr(latin: String) = UzbekCyrillic.fromLatin(latin)

    @Test
    fun `digraphs and apostrophe letters`() {
        assertThat(cyr("O'zbekiston")).isEqualTo("Ўзбекистон")
        assertThat(cyr("g'alaba")).isEqualTo("ғалаба")
        assertThat(cyr("o‘qish")).isEqualTo("ўқиш") // curly apostrophe
        assertThat(cyr("Shifokor")).isEqualTo("Шифокор")
        assertThat(cyr("choy")).isEqualTo("чой")
        assertThat(cyr("tong")).isEqualTo("тонг")
        assertThat(cyr("mashg'ulot")).isEqualTo("машғулот")
    }

    @Test
    fun `iotated vowels, initial e and the separating apostrophe`() {
        assertThat(cyr("Yangi")).isEqualTo("Янги")
        assertThat(cyr("Yordam")).isEqualTo("Ёрдам")
        assertThat(cyr("yo'q")).isEqualTo("йўқ")
        assertThat(cyr("yuborish")).isEqualTo("юбориш")
        assertThat(cyr("yetarli")).isEqualTo("етарли")
        assertThat(cyr("Obyektiv")).isEqualTo("Объектив") // soapObjective
        assertThat(cyr("Subyektiv")).isEqualTo("Субъектив") // soapSubjective
        assertThat(cyr("eslatma")).isEqualTo("эслатма")
        assertThat(cyr("telefon")).isEqualTo("телефон")
        assertThat(cyr("ma'lumot")).isEqualTo("маълумот")
        assertThat(cyr("rahmat")).isEqualTo("раҳмат")
    }

    @Test
    fun `case, digits and punctuation`() {
        assertThat(cyr("SHIFA")).isEqualTo("ШИФА")
        assertThat(cyr("Bugun soat 10:30.")).isEqualTo("Бугун соат 10:30.")
    }

    @Test
    fun `placeholders pass through`() {
        assertThat(cyr("%1\$s ta bemor, %2\$d soat")).isEqualTo("%1\$s та бемор, %2\$d соат")
        assertThat(cyr("{count} ta, {{name}}")).isEqualTo("{count} та, {{name}}")
    }

    @Test
    fun `arguments go in after the template`() {
        assertThat("%2\$s, %1\$d".withArgs(arrayOf<Any>(3, "Sh"))).isEqualTo("Sh, 3")
        assertThat("100%".withArgs(emptyArray())).isEqualTo("100%")
    }
}
