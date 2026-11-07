package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    private val g = ' '
    private val n = ' '

    @Test
    fun `UZS is whole sums, grouped, tiyin never shown`() {
        assertThat(Money.format(125_000_000, "UZS")).isEqualTo("1${g}250${g}000${n}UZS")
        assertThat(Money.format(15_000_000, "uzs")).isEqualTo("150${g}000${n}UZS") // dev's consultationPriceMinor
        assertThat(Money.format(0, "UZS")).isEqualTo("0${n}UZS")
        assertThat(Money.format(-45_000_000, "UZS")).isEqualTo("−450${g}000${n}UZS")
        assertThat(Money.format(149, "UZS")).isEqualTo("1${n}UZS")
        assertThat(Money.format(Long.MAX_VALUE, "UZS")).doesNotContain("\u2212") // no overflow into a sign
        assertThat(
            Money.format(Long.MIN_VALUE, "USD"),
        ).isEqualTo("\u221292${g}233${g}720${g}368${g}547${g}758.08${n}USD")
    }

    @Test
    fun `other currencies show two decimals`() {
        assertThat(Money.format(125_050, "USD")).isEqualTo("1${g}250.50${n}USD")
        assertThat(Money.format(5, "EUR")).isEqualTo("0.05${n}EUR")
    }

    @Test
    fun `typed amounts parse to minor units`() {
        assertThat(Money.parse("1 250 000", "UZS")).isEqualTo(125_000_000)
        assertThat(Money.parse("1250000", "UZS")).isEqualTo(125_000_000)
        assertThat(Money.parse("1250.50", "USD")).isEqualTo(125_050)
        assertThat(Money.parse("1250,5", "USD")).isEqualTo(125_050)
        listOf("", ".", ",", "12a", "1.2.3", "1.234", "92233720368547758.08").forEach {
            assertThat(Money.parse(it, "USD")).isNull()
        }
        assertThat(Money.parse("1250.50", "UZS")).isNull() // no tiyin
    }
}
