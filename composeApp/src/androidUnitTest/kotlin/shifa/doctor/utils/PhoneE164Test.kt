package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneE164Test {
    @Test
    fun `Uzbek numbers in any common form`() {
        listOf("+998 90 123 45 67", "998901234567", "90 123-45-67", "(90) 123 45 67", "00998901234567")
            .forEach { assertThat(PhoneE164.normalize(it)).isEqualTo("+998901234567") }
    }

    @Test
    fun `other countries need their code, junk is null`() {
        assertThat(PhoneE164.normalize("+49 151 23456789")).isEqualTo("+4915123456789")
        listOf(
            null,
            "",
            "12345",
            "+998 90 123",
            "0151 23456789",
            "abc901234567",
            "++998901234567",
        ).forEach { assertThat(PhoneE164.normalize(it)).isNull() }
    }

    @Test
    fun `display groups Uzbek numbers only`() {
        assertThat(PhoneE164.display("+998901234567")).isEqualTo("+998 90 123 45 67")
        assertThat(PhoneE164.display("+4915123456789")).isEqualTo("+4915123456789")
        // Stored without the plus, already grouped, or not a number at all.
        assertThat(PhoneE164.display("998901234567")).isEqualTo("+998 90 123 45 67")
        assertThat(PhoneE164.display("+998 90 123 45 67")).isEqualTo("+998 90 123 45 67")
        assertThat(PhoneE164.display("ask reception")).isEqualTo("ask reception")
    }
}
