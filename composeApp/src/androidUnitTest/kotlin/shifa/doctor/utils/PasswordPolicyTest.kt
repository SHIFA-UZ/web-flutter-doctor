package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.utils.PasswordPolicy.Rule

class PasswordPolicyTest {
    @Test
    fun `the backend's rule`() {
        assertThat(PasswordPolicy.isValid("Dev12345!")).isTrue() // the dev seed password
        assertThat(PasswordPolicy.missing("short1!")).containsExactly(Rule.Length, Rule.Upper)
        assertThat(PasswordPolicy.missing("alllowercase")).containsExactly(Rule.Upper, Rule.Digit, Rule.Special)
        assertThat(PasswordPolicy.missing("A".repeat(129) + "a1!")).containsExactly(Rule.Length)
        assertThat(PasswordPolicy.missing("Äbcdefg1!")).containsExactly(Rule.Upper) // ASCII only, as the server
        assertThat(PasswordPolicy.missing("Abcdefg1 ")).containsExactly(Rule.Special) // a space isn't special
    }
}
