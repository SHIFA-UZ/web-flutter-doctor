@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

// Flutter's test/intelligence_card_text_test.dart (the golden for the card-text localiser), against the strings JSON.
class CardTextTest {
    // Built from the generated resources the screen reads (not the JSON), with the same contract as rememberCardText:
    // templated keys resolved with their {name}s as arguments, plurals for the counts 1, 2 and 5.
    private fun resources(dir: String): Pair<Map<String, String>, Map<String, Map<String, String>>> {
        val base = "src/commonMain/composeResources/$dir"

        fun unescape(x: String) = x.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        val strings =
            Regex("""<string name="([^"]+)">([\s\S]*?)</string>""").findAll(File("$base/strings.xml").readText())
                .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        val plurals =
            Regex("""<plurals name="([^"]+)">([\s\S]*?)</plurals>""").findAll(File("$base/plurals.xml").readText())
                .associate { p ->
                    p.groupValues[1] to
                        Regex("""<item quantity="([a-z]+)">([\s\S]*?)</item>""").findAll(p.groupValues[2])
                            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
                }
        return strings to plurals
    }

    private fun withNames(
        template: String,
        names: List<String>,
    ) = Regex("""%(\d+)\$[ds]""").replace(template) { "{${names[it.groupValues[1].toInt() - 1]}}" }

    private fun cardText(
        lang: String,
        cyrillic: Boolean = false,
    ): CardText {
        val (strings, plurals) = resources(if (lang == "en") "values" else "values-$lang")
        val (enStrings, _) = resources("values")
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        fun string(key: String) = withNames(strings[key] ?: enStrings[key] ?: key, CardText.PLACEHOLDERS[key].orEmpty())

        // The resource's form for a sample count, as CMP picks it (uz has only `other`).
        fun form(
            forms: Map<String, String>,
            sample: Int,
        ): String {
            val category =
                if (lang == "ru") {
                    mapOf(1 to "one", 2 to "few", 5 to "many")[sample]
                } else if (sample == 1) {
                    "one"
                } else {
                    "other"
                }
            return forms[category] ?: forms.getValue("other")
        }
        return CardText(
            lang,
            ::string,
            { m -> strings["month${months[m - 1]}"] ?: enStrings.getValue("month${months[m - 1]}") },
            cyrillic,
        ) { key, count ->
            withNames(
                form(plurals.getValue(key), CardText.pluralSample(lang, count)),
                CardText.PLURAL_KEYS.getValue(key),
            )
        }
    }

    private val uz = cardText("uz")
    private val en = cardText("en")

    @Test
    fun `patient appointment card labels follow the app language`() {
        assertThat(uz.rangeLabel("this week")).isEqualTo("Shu hafta")
        assertThat(uz.dateLabel(isoDate = "2026-10-20", fallback = "20 Oct 2026")).isEqualTo("20 Okt 2026")
        assertThat(uz.statusLabel("COMPLETED")).isEqualTo("Yakunlangan")
        assertThat(uz.statusLabel("CONFIRMED")).isEqualTo("Tasdiqlangan")
        assertThat(uz.statusLabel("CANCELLED")).isEqualTo("Bekor qilingan")
        assertThat(en.rangeLabel("this week")).isEqualTo("This week")
        assertThat(en.dateLabel(fallback = "20 Oct 2026")).isEqualTo("20 Oct 2026")
    }

    @Test
    fun `cancellation cards use the app language`() {
        assertThat(
            uz.localizeHint(
                "Which visits should I cancel? Tell me the day, or say all for a period such as next week.",
            ),
        )
            .isEqualTo(
                "Qaysi qabullarni bekor qilishim kerak? Kunini ayting yoki kelasi hafta kabi ma’lum bir davr uchun hammasi deng.",
            )
        assertThat(
            uz.localizeHint(
                "I can cancel 1 upcoming visit: Botir 2 Muzrabov Friday 23 October at 16:00. Say yes to cancel them. Nothing has been cancelled yet.",
            ),
        ).isEqualTo(
            "Men yaqinlashib kelayotgan 1 ta qabulni bekor qila olaman: Botir 2 Muzrabov Juma 23-oktabr soat 16:00 da. Ularni bekor qilish uchun «ha» deng. Hali hech narsa bekor qilinmadi.",
        )
        assertThat(uz.localizeHint("Nothing was cancelled. Say yes if you want me to cancel the visits I just listed."))
            .isEqualTo(
                "Hech narsa bekor qilinmadi. Agar hozirgina sanab o‘tgan qabullarimni bekor qilishimni xohlasangiz, «ha» deng.",
            )
    }

    @Test
    fun `reschedule cards follow the app language`() {
        assertThat(uz.dateTimeLabel("20 Oct 2026 02:00")).isEqualTo("20 Okt 2026 02:00")
        assertThat(en.dateTimeLabel("20 Oct 2026 02:00")).isEqualTo("20 Oct 2026 02:00")
        val moved =
            uz.localizeHint(
                "Botir 2 Muzrabov is on Tuesday, 20 October at 02:00. Which day should I move that visit to?",
            )
        assertThat(moved).contains("Seshanba")
        assertThat(moved).contains("oktabr")
        assertThat(moved).contains("Botir 2 Muzrabov")
        assertThat(moved).contains("02:00")
        assertThat(moved).doesNotContain("Tuesday")
        assertThat(moved).doesNotContain("October")
        val english = "Botir 2 Muzrabov is on Tuesday, 20 October at 02:00. Which day should I move that visit to?"
        assertThat(en.localizeHint(english)).isEqualTo(english)
        assertThat(uz.localizeHint("I could not tell which visit to move. Say the patient and the day."))
            .isEqualTo("Qaysi tashrifni ko‘chirishni bilmayman. Bemor va kunni ayting.")
        assertThat(uz.patientLabel("Patient")).isEqualTo("Bemor")
        assertThat(uz.patientLabel(null)).isEqualTo("Bemor")
        assertThat(uz.patientLabel("Botir 2 Muzrabov")).isEqualTo("Botir 2 Muzrabov")
        assertThat(uz.documentLabel("Document")).isEqualTo("Hujjat")
        assertThat(uz.documentLabel("Chest X-ray")).isEqualTo("Chest X-ray")
    }

    @Test
    fun `screenshot search and booking sentences are Uzbek`() {
        val noMatchEn = "No patients matched \"Bruno nol\" in your roster."
        val bookedEn = "Booked Botir 2 Muzrabov at 11:00 on Friday, 23 October."
        assertThat(uz.localizeHint(noMatchEn)).isEqualTo("Ro‘yxatingizda “Bruno nol” bo‘yicha bemor topilmadi.")
        assertThat(
            uz.localizeHint("No patients matched “Bruno nol” in your roster."),
        ).isEqualTo("Ro‘yxatingizda “Bruno nol” bo‘yicha bemor topilmadi.")
        assertThat(en.localizeHint(noMatchEn)).isEqualTo(noMatchEn)
        val booked = uz.localizeHint(bookedEn)
        assertThat(booked).isEqualTo("Botir 2 Muzrabov 23-oktabr, Juma kuni, soat 11:00 ga band qilindi.")
        assertThat(en.localizeHint(bookedEn)).isEqualTo(bookedEn)
        val booking = uz.localizeHint("Booking Botir 2 Muzrabov at 11:00 on Friday, 23 October.")
        assertThat(booking).doesNotContain("Booking")
        assertThat(booking).doesNotContain("Friday")
        assertThat(booking).doesNotContain("October")
        assertThat(booking).contains("Juma")
        assertThat(booking).contains("oktabr")
        assertThat(uz.localizeHint("Please confirm booking Botir Muzrabov at 09:00 on 22-oktabr, Payshanba."))
            .isEqualTo("Iltimos, Botir Muzrabovning 22-oktabr, Payshanba kuni soat 09:00 dagi qabulini tasdiqlang.")
        assertThat(uz.localizeHint("Please confirm booking Botir Muzrabov at 09:00 on Thursday, 22 October."))
            .isEqualTo("Iltimos, Botir Muzrabovning 22-oktabr, Payshanba kuni soat 09:00 dagi qabulini tasdiqlang.")
        val confirmEn = "Please confirm booking Botir Muzrabov at 09:00 on Thursday, 22 October."
        assertThat(en.localizeHint(confirmEn)).isEqualTo(confirmEn)
        assertThat(uz.localizeHint("Booked Botir Muzrabov at 09:00 on 2026-10-22."))
            .isEqualTo("Botir Muzrabov 22-oktabr, Payshanba kuni, soat 09:00 ga band qilindi.")
    }

    @Test
    fun `other tool hints follow the app language`() {
        assertThat(uz.localizeHint("You have 3 appointments today. The first is with Patient in about 5 minutes."))
            .isEqualTo("Bugun 3 ta uchrashuv bor. Birinchisi Bemor bilan taxminan 5 daqiqadan so‘ng.")
        assertThat(
            uz.localizeHint("No patients matched \"Botil\" in your roster."),
        ).isEqualTo("Ro‘yxatingizda “Botil” bo‘yicha bemor topilmadi.")
        assertThat(
            uz.localizeHint("No exact match for \"Botil\". Did you mean Botir 2 Muzrabov?"),
        ).contains("Botir 2 Muzrabov")
        assertThat(uz.localizeHint("You have 2 appointments scheduled last week.")).contains("2")
        assertThat(uz.localizeHint("You have 2 appointments scheduled last week.")).doesNotContain("scheduled")
    }

    @Test
    fun `resource placeholders and plurals come through (no %1$s on screen)`() {
        assertThat(en.localizeHint("That matches more than 20 visits. Narrow the period before I cancel anything."))
            .isEqualTo("That matches more than 20 visits. Narrow the period before I cancel anything.")
        assertThat(
            uz.localizeHint("That matches more than 20 visits. Narrow the period before I cancel anything."),
        ).contains("20")
        assertThat(
            uz.localizeHint("That matches more than 20 visits. Narrow the period before I cancel anything."),
        ).doesNotContain("%")
        val ru = cardText("ru")
        val two =
            ru.localizeHint(
                "I can cancel 2 upcoming visits: A, B. Say yes to cancel them. Nothing has been cancelled yet.",
            )
        assertThat(two).startsWith("Могу отменить 2 предстоящих визита: A, B.")
        val five =
            ru.localizeHint(
                "I can cancel 5 upcoming visits: A, B, C, D, E. Say yes to cancel them. Nothing has been cancelled yet.",
            )
        assertThat(five).startsWith("Могу отменить 5 предстоящих визитов")
        assertThat(
            en.localizeHint(
                "I can cancel 1 upcoming visit: A. Say yes to cancel them. Nothing has been cancelled yet.",
            ),
        )
            .isEqualTo("I can cancel 1 upcoming visit: A. Say yes to cancel it. Nothing has been cancelled yet.")
        assertThat(en.rangeLabel("upcoming (from 20 Oct 2026)")).isEqualTo("Upcoming (from 20 Oct 2026)")
        assertThat(CardText.pluralSample("ru", 21)).isEqualTo(1)
        assertThat(CardText.pluralSample("ru", 12)).isEqualTo(5)
        assertThat(CardText.pluralSample("ru", 34)).isEqualTo(2)
    }

    @Test
    fun `Cyrillic Uzbek turns this file's own templates, names stay as written`() {
        val cyr = cardText("uz", cyrillic = true)
        val booked = cyr.localizeHint("Booked Botir Muzrabov at 09:00 on 2026-10-22.")
        assertThat(booked).contains("Botir Muzrabov")
        assertThat(booked).contains("банд қилинди")
    }
}
