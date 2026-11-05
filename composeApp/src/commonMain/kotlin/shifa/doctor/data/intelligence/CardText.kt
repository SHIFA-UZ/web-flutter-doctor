@file:Suppress("ktlint:standard:max-line-length", "ktlint")

package shifa.doctor.data.intelligence

import kotlinx.datetime.LocalDate
import shifa.doctor.utils.UzbekCyrillic

/**
 * Tool-card text in the app's language (INTEL-10, 02 §0; Flutter `intelligence_card_text.dart`, ported with its golden
 * test): statuses, dates, period labels, placeholder names, and the backend's English hint sentences (cancellation,
 * booking, moving, search, schedule, money…), matched exactly or by pattern. A stop-gap: the backend is asked for codes
 * and ISO values instead of sentences (doc 15 §4); until then the patterns must match the Dart ones exactly, or uz/ru
 * cards regress. [lang] is `en`, `uz` or `ru`; [strings] resolves the app's string keys (the screen passes them in the
 * app's language, Cyrillic Uzbek included); [cyrillic] turns this file's own Uzbek templates to Cyrillic as `tr()` does.
 */
class CardText(
    private val lang: String,
    private val strings: (String) -> String,
    private val monthShort: (Int) -> String,
    private val cyrillic: Boolean = false,
    // A plural resource's text for a count ([PLURAL_KEYS]), with the same `{name}` placeholders.
    private val plural: (key: String, count: Int) -> String = { key, _ -> strings(key) },
) {
    private fun t(key: String): String = strings(key)

    private fun line(
        en: String,
        uz: String,
        ru: String,
    ): String =
        when (lang) {
            "uz" -> if (cyrillic) UzbekCyrillic.fromLatin(uz) else uz
            "ru" -> ru
            else -> en
        }

    private fun fill(
        template: String,
        vars: Map<String, String>,
    ): String = vars.entries.fold(template) { text, (k, v) -> text.replace("{$k}", v) }

    fun statusLabel(status: String): String =
        when (status.trim().uppercase()) {
            "COMPLETED" -> t("appointmentStatusCompleted")
            "CONFIRMED" -> t("appointmentStatusConfirmed")
            "CANCELLED", "CANCELED" -> t("appointmentStatusCancelled")
            "REQUESTED" -> t("appointmentStatusRequested")
            "IN_PROGRESS", "STARTED" -> t("appointmentStatusInProgress")
            "PENDING" -> t("pending")
            "SCHEDULED" -> t("scheduled")
            "NO_SHOW" -> t("appointmentStatusNoShow")
            else -> status
        }

    fun dateLabel(
        isoDate: String? = null,
        fallback: String? = null,
    ): String {
        parseIsoDate(isoDate)?.let { return "${it.dayOfMonth} ${monthShort(it.monthNumber)} ${it.year}" }
        parseEnglishDate(fallback)?.let { return "${it.dayOfMonth} ${monthShort(it.monthNumber)} ${it.year}" }
        return fallback ?: isoDate ?: ""
    }

    /** A card field that may be an ISO date, an English date, or a period name. */
    fun fieldLabel(
        isoDate: String? = null,
        fallback: String? = null,
    ): String {
        val raw = (fallback ?: "").trim()
        val dated = dateLabel(isoDate, raw.ifEmpty { null })
        if (dated.isEmpty()) return raw
        if (dated != raw) return dated
        return rangeLabel(dated)
    }

    /** "Patient" / "The patient" are placeholders, not a person's name. */
    fun patientLabel(raw: String?): String {
        val name = (raw ?: "").trim()
        return when (name.lowercase()) {
            "", "patient", "the patient", "this patient", "your next patient" -> t("intelPatient")
            else -> name
        }
    }

    /** "Document" is a placeholder title, not the file's real name. */
    fun documentLabel(raw: String?): String {
        val title = (raw ?: "").trim()
        return if (title.isEmpty() || title.lowercase() == "document") t("intelDocument") else title
    }

    /** Draft-fact category stored as an English enum. */
    fun factTypeLabel(raw: String): String =
        when (raw.trim().uppercase()) {
            "SYMPTOM" -> line("Symptom", "Simptom", "Симптом")
            "DIAGNOSIS" -> line("Diagnosis", "Tashxis", "Диагноз")
            "THERAPY" -> line("Therapy", "Terapiya", "Терапия")
            "MEDICATION" -> line("Medication", "Dori", "Лекарство")
            else -> raw
        }

    /** "20 Oct 2026" or "20 Oct 2026 02:00". */
    fun dateTimeLabel(raw: String): String {
        val text = raw.trim()
        val match = Regex("""^(\d{1,2}\s+[A-Za-z]{3,9}(?:\s+\d{4})?)(?:\s+(\d{2}:\d{2}))?$""").find(text)
            ?: return fieldLabel(fallback = text)
        val date = dateLabel(fallback = match.groupValues[1])
        val time = match.groups[2]?.value
        return if (time == null) date else "$date $time"
    }

    fun rangeLabel(raw: String): String {
        val text = raw.trim()
        val lower = text.lowercase()
        when (lower) {
            "this week" -> return t("intelRangeThisWeek")
            "next week" -> return t("intelRangeNextWeek")
            "today" -> return t("today")
            "tomorrow" -> return t("intelTomorrow")
            "yesterday" -> return t("yesterday")
            "upcoming" -> return t("upcomingAppointments")
            "last week", "this month", "last month", "next month", "this quarter", "last quarter", "next quarter",
            "this year", "last year", "next year", "coming up", "from tomorrow onwards", "friday of next week",
            "the upcoming year",
            -> return namedPeriod(lower)
        }
        Regex("""^the (next|last) (\d+) (day|days|week|weeks|month|months)$""").find(lower)?.let {
            return rollingPeriod(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        Regex("""^(on|since|before|after) (\d{1,2} [A-Za-z]+ \d{4})$""").find(text)?.let {
            return boundedDate(it.groupValues[1], it.groupValues[2])
        }
        Regex("""^from (\d{1,2} [A-Za-z]+ \d{4}) until (\d{1,2} [A-Za-z]+ \d{4})$""").find(text)?.let {
            return betweenDates(it.groupValues[1], it.groupValues[2])
        }
        Regex("""^(this week|next week)\s*\((.+)\)\s*$""", RegexOption.IGNORE_CASE).find(text)?.let {
            val label = if (it.groupValues[1].lowercase() == "next week") t("intelRangeNextWeek") else t("intelRangeThisWeek")
            return "$label (${localizeEnglishDates(it.groupValues[2])})"
        }
        Regex("""^upcoming\s*\(from\s+(.+)\)\s*$""", RegexOption.IGNORE_CASE).find(text)?.let {
            return t("intelRangeUpcomingFrom").replace("{date}", localizeEnglishDates(it.groupValues[1]))
        }
        return localizeEnglishDates(text)
    }

    /** The backend's known English hint sentences in the app's language; any other text is left as sent. */
    fun localizeHint(hint: String): String {
        val text = normalizeToolHint(hint)
        if (text.isEmpty()) return text
        val exact =
            mapOf(
                "Which visits should I cancel? Tell me the day, or say all for a period such as next week." to "intelCancelAskWhich",
                "Nothing was cancelled. Say yes if you want me to cancel the visits I just listed." to "intelCancelNothing",
                "No visits in that period can be cancelled. Nothing was changed." to "intelCancelNoneEligible",
                "I don't have a cancellation waiting for confirmation. Tell me which visits to cancel." to "intelCancelNotWaiting",
                "I could not find that visit." to "intelCancelNotFound",
            )
        exact[text]?.let { return t(it) }
        Regex("""^That matches more than (\d+) visits\. Narrow the period before I cancel anything\.$""").find(text)?.let {
            return t("intelCancelTooMany").replace("{count}", it.groupValues[1])
        }
        Regex(
            """^I can cancel (\d+) upcoming visit(?:s)?: (.+?)\.(?: (\d+) cannot be cancelled and will be left as they are\.)? Say yes to cancel them\. Nothing has been cancelled yet\.$""",
        ).find(text)?.let { preview ->
            val count = preview.groupValues[1]
            val names = localizeVisitList(preview.groupValues[2])
            // A plural resource (it replaced Flutter's string with {visitWord}): the count's own grammar in each language.
            var sentence = plural("intelCancelPreview", count.toInt()).replace("{count}", count).replace("{names}", names)
            preview.groups[3]?.value?.let { skipped -> sentence = "$sentence ${t("intelCancelSkipped").replace("{count}", skipped)}" }
            return sentence
        }
        if (lang == "en") return text
        matchToolSentence(text)?.let { return it }
        val parts = text.split(Regex("""(?<=\.)\s+(?=[A-ZА-ЯЁ])"""))
        if (parts.size > 1) {
            val joined = parts.joinToString(" ") { localizeHint(it.trim()) }
            if (joined != text) return joined
        }
        return localizeEmbeddedCalendar(text)
    }

    /** [localizeHint], or null when a uz/ru line came back unchanged (a sentence this file doesn't know: English). */
    fun localizedOrNull(hint: String): String? = localizeHint(hint).takeIf { lang == "en" || it != hint.trim() }

    private val exactTable: Map<String, String> by lazy { exactSentences() }

    private fun matchToolSentence(text: String): String? {
  val exact = exactTable[text];
  if (exact != null) return exact;

  val moveAsk = Regex(
    """^(.+?) is on ([A-Za-z]+), (\d{1,2}) ([A-Za-z]+) at (\d{2}:\d{2})\. Which day should I move that visit to\?$""",
  ).find(text);
  if (moveAsk != null) {
    return fill(
      line("{name} is on {when} at {time}. Which day should I move that visit to?",
        "{name} {when} kuni, soat {time} da. Ushbu tashrifni qaysi kunga o‘tkazishim kerak?",
        "{name} записан на {when}, {time}. На какой день перенести этот визит?",
      ),
      mapOf(
        "name" to patientLabel(moveAsk.groups[1]?.value),
        "when" to speakableDay(moveAsk.groupValues[2],
          moveAsk.groupValues[3],
          moveAsk.groupValues[4],
        ),
        "time" to moveAsk.groupValues[5],
      ),
    );
  }

  val passed = Regex(
    """^([A-Za-z]+, \d{1,2} [A-Za-z]+) has already passed\. Name a future day for (.+)\.$""",
  ).find(text);
  if (passed != null) {
    return fill(
      line("{when} has already passed. Name a future day for {name}.",
        "{when} o‘tib ketgan. {name} uchun kelajakdagi kunni ayting.",
        "{when} уже прошло. Назовите будущий день для {name}.",
      ),
      mapOf(
        "when" to localizeEmbeddedCalendar(passed.groupValues[1]),
        "name" to patientLabel(passed.groups[2]?.value),
      ),
    );
  }

  val patterns = listOf<Pair<Regex, (MatchResult) -> String>>(
    Regex(
      """^No free slots on (.+) to move (.+)\. Please choose another day\.$""",
    ) to { m -> fill(
      line("No free slots on {when} to move {name}. Please choose another day.",
        "{when} kuni {name}ni ko‘chirish uchun bo‘sh vaqt yo‘q. Boshqa kunni ayting.",
        "Нет свободного времени {when}, чтобы перенести {name}. Назовите другой день.",
      ),
      mapOf(
        "when" to localizeEmbeddedCalendar(m.groupValues[1]),
        "name" to patientLabel(m.groups[2]?.value),
      ),
    ) },
    Regex(
      """^(\d{2}:\d{2}) is not free on (.+)\. Here are other open times\.$""",
    ) to { m -> fill(
      line("{time} is not free on {when}. Here are other open times.",
        "{when} kuni soat {time} band. Mana boshqa bo‘sh vaqtlar.",
        "{time} {when} занято. Вот другие свободные окна.",
      ),
      mapOf(
        "time" to m.groupValues[1],
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
      ),
    ) },
    Regex("""^Moving (.+) to (\d{2}:\d{2}) on (.+)\.$""") to { m -> fill(
      line("Moving {name} to {time} on {when}.",
        "{name}ni {when} kuni soat {time} ga ko‘chiryapman.",
        "Переношу {name} на {when}, {time}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "time" to m.groupValues[2],
        "when" to localizeEmbeddedCalendar(m.groupValues[3]),
      ),
    ) },
    Regex("""^Moving (.+) to (.+)\. Which time\?$""") to { m -> fill(
      line("Moving {name} to {when}. Which time?",
        "{name}ni {when} ga ko‘chiryapman. Qaysi vaqt?",
        "Переношу {name} на {when}. Какое время?",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
      ),
    ) },
    Regex(
      """^No free slots on (.+) for (.+)\. Please choose another day\.$""",
    ) to { m -> fill(
      line("No free slots on {when} for {name}. Please choose another day.",
        "{when} kuni {name} uchun bo‘sh vaqt yo‘q. Boshqa kunni ayting.",
        "{when} нет свободного времени для {name}. Назовите другой день.",
      ),
      mapOf(
        "when" to localizeEmbeddedCalendar(m.groupValues[1]),
        "name" to patientLabel(m.groups[2]?.value),
      ),
    ) },
    Regex(
      """^(\d{2}:\d{2}) is not free for (.+) on (.+)\. Here are other open times\.$""",
    ) to { m -> fill(
      line("{time} is not free for {name} on {when}. Here are other open times.",
        "{name} uchun {when} kuni soat {time} band. Mana boshqa bo‘sh vaqtlar.",
        "{time} занято для {name} {when}. Вот другие свободные окна.",
      ),
      mapOf(
        "time" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[3]),
      ),
    ) },
    Regex("""^Booking (.+) at (\d{2}:\d{2}) on (.+)\.$""") to { m -> fill(
      line("Booking {name} at {time} on {when}.",
        "{name}ni {when} kuni soat {time} ga band qilyapman.",
        "Записываю {name} на {when}, {time}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "time" to m.groupValues[2],
        "when" to localizeEmbeddedCalendar(m.groupValues[3]),
      ),
    ) },
    Regex(
      """^I found (\d+) free slots on (.+) for (.+)\. Which time works\?$""",
    ) to { m -> fill(
      line("I found {count} free slots on {when} for {name}. Which time works?",
        "{name} uchun {when} kuni {count} ta bo‘sh vaqt topdim. Qaysi vaqt qulay?",
        "Нашла {count} свободных окон {when} для {name}. Какое время подходит?",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
        "name" to patientLabel(m.groups[3]?.value),
      ),
    ) },
    Regex(
      """^You have no free( morning| afternoon| evening)? slots on (.+)\.$""",
    ) to { m -> fill(
      line("You have no free{part} slots on {when}.",
        "{when} kuni{part} bo‘sh vaqt yo‘q.",
        "{when} нет свободных окон{part}.",
      ),
      mapOf(
        "part" to dayPart(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
      ),
    ) },
    Regex(
      """^You have 1 free( morning| afternoon| evening)? slot on (.+): (\d{2}:\d{2})\.$""",
    ) to { m -> fill(
      line("You have 1 free{part} slot on {when}: {time}.",
        "{when} kuni{part} 1 ta bo‘sh vaqt bor: {time}.",
        "{when} есть 1 свободное окно{part}: {time}.",
      ),
      mapOf(
        "part" to dayPart(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
        "time" to m.groupValues[3],
      ),
    ) },
    Regex(
      """^You have (\d+) free( morning| afternoon| evening)? slots on (.+)\. Which time works\?$""",
    ) to { m -> fill(
      line("You have {count} free{part} slots on {when}. Which time works?",
        "{when} kuni{part} {count} ta bo‘sh vaqt bor. Qaysi vaqt qulay?",
        "{when} есть {count} свободных окон{part}. Какое время подходит?",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "part" to dayPart(m.groups[2]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[3]),
      ),
    ) },
    Regex("""^Which of those visits should I cancel\? (.+)$""") to { m -> fill(
      line("Which of those visits should I cancel? {names}",
        "Shu tashriflardan qaysilarini bekor qilay? {names}",
        "Какие из этих визитов отменить? {names}",
      ),
      mapOf("names" to localizeVisitList(m.groupValues[1])),
    ) },
    Regex("""^Found (\d+) patients matching "(.+)"\. Which one\?$""") to { m ->
        fill(
          line("Found {count} patients matching \"{query}\". Which one?",
            "“{query}” bo‘yicha {count} ta bemor topildi. Qaysi biri?",
            "Найдено пациентов по запросу «{query}»: {count}. Кто именно?",
          ),
          mapOf("count" to m.groupValues[1], "query" to m.groupValues[2]),
        ) },
    Regex("""^Found (.+)\.$""") to { m -> fill(
      line("Found {name}.",
        "{name} topildi.",
        "Найден {name}.",
      ),
      mapOf("name" to patientLabel(m.groups[1]?.value)),
    ) },
    Regex(
      """^No patients matched ["“”«»](.+?)["“”«»] in your roster\.?$""",
    ) to { m -> fill(
      line("No patients matched \"{query}\" in your roster.",
        "Ro‘yxatingizda “{query}” bo‘yicha bemor topilmadi.",
        "В вашем списке нет пациентов по запросу «{query}».",
      ),
      mapOf("query" to m.groupValues[1]),
    ) },
    Regex("""^No exact match for "(.+)"\. Did you mean (.+)\?$""") to { m -> fill(
      line("No exact match for \"{query}\". Did you mean {name}?",
        "“{query}” bo‘yicha aniq moslik yo‘q. {name}ni nazarda tutdingizmi?",
        "Нет точного совпадения для «{query}». Вы имели в виду {name}?",
      ),
      mapOf("query" to m.groupValues[1], "name" to m.groupValues[2]),
    ) },
    Regex(
      """^No exact match for "(.+)"\. Closest matches: (.+)\. Which one did you mean\?$""",
    ) to { m -> fill(
      line("No exact match for \"{query}\". Closest matches: {names}. Which one did you mean?",
        "“{query}” bo‘yicha aniq moslik yo‘q. Eng yaqinlar: {names}. Qaysi birini nazarda tutdingiz?",
        "Нет точного совпадения для «{query}». Ближе всего: {names}. Кого вы имели в виду?",
      ),
      mapOf("query" to m.groupValues[1], "names" to m.groupValues[2]),
    ) },
    Regex("^You have no appointments with (.+) (${RANGE_TAIL})\\.\$") to { m ->
        fill(
          line("You have no appointments with {name} {when}.",
            "{name} bilan {when} uchrashuv yo‘q.",
            "Нет записей с {name} {when}.",
          ),
          mapOf(
            "name" to patientLabel(m.groups[1]?.value),
            "when" to rangeLabel(m.groupValues[2]),
          ),
        ) },
    Regex(
      "^You have (\\d+) appointments? with (.+) (${RANGE_TAIL}): (.+)\\.\$",
    ) to { m -> fill(
      line("You have {count} appointments with {name} {when}: {details}.",
        "{name} bilan {when} {count} ta uchrashuv bor: {details}.",
        "С {name} {when} записей: {count}. {details}.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
        "when" to rangeLabel(m.groupValues[3]),
        "details" to localizeEmbeddedCalendar(m.groupValues[4]),
      ),
    ) },
    Regex(
      """^Your most visited patient is (.+) with (\d+) visits\. Next: (.+)\.$""",
    ) to { m -> fill(
      line("Your most visited patient is {name} with {count} visits. Next: {rest}.",
        "Eng ko‘p tashrif buyurgan bemor — {name}, {count} ta tashrif. Keyingilar: {rest}.",
        "Чаще всего приходит {name}: {count} визитов. Далее: {rest}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "count" to m.groupValues[2],
        "rest" to m.groupValues[3],
      ),
    ) },
    Regex("""^Your most visited patient is (.+) with (\d+) visits\.$""") to { m ->
        fill(
          line("Your most visited patient is {name} with {count} visits.",
            "Eng ko‘p tashrif buyurgan bemor — {name}, {count} ta tashrif.",
            "Чаще всего приходит {name}: {count} визитов.",
          ),
          mapOf(
            "name" to patientLabel(m.groups[1]?.value),
            "count" to m.groupValues[2],
          ),
        ) },
    Regex("""^There are (\d+) draft facts\..+$""") to { m -> fill(
      line("There are {count} draft facts. Confirm to document them.",
        "{count} ta qoralama fakt bor. Hujjatlashtirish uchun tasdiqlang.",
        "Черновиков фактов: {count}. Подтвердите, чтобы задокументировать.",
      ),
      mapOf("count" to m.groupValues[1]),
    ) },
    Regex("""^There are no viewable documents on file for (.+) yet\.$""") to { m ->
        fill(
          line("There are no viewable documents on file for {name} yet.",
            "{name} uchun hali ko‘rish mumkin bo‘lgan hujjat yo‘q.",
            "У {name} пока нет документов, которые можно открыть.",
          ),
          mapOf("name" to patientLabel(m.groups[1]?.value)),
        ) },
    Regex(
      """^I found 1 document for (.+): (.+)\. Opening it in chat\.$""",
    ) to { m -> fill(
      line("I found 1 document for {name}: {title}. Opening it in chat.",
        "{name} uchun 1 ta hujjat topdim: {title}. Chatda ochyapman.",
        "Нашла 1 документ для {name}: {title}. Открываю в чате.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "title" to m.groupValues[2],
      ),
    ) },
    Regex(
      """^I found (\d+) documents for (.+)\. They are listed in the chat\.$""",
    ) to { m -> fill(
      line("I found {count} documents for {name}. They are listed in the chat.",
        "{name} uchun {count} ta hujjat topdim. Ular chatda ko‘rsatilgan.",
        "Нашла {count} документов для {name}. Они перечислены в чате.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
      ),
    ) },
    Regex("""^(.+) has no viewable documents yet\.$""") to { m -> fill(
      line("{name} has no viewable documents yet.",
        "{name}da hali ko‘rish mumkin bo‘lgan hujjat yo‘q.",
        "У {name} пока нет документов, которые можно открыть.",
      ),
      mapOf("name" to patientLabel(m.groups[1]?.value)),
    ) },
    Regex("""^Opening (.+) for (.+) in the chat\.$""") to { m -> fill(
      line("Opening {title} for {name} in the chat.",
        "{name} uchun {title} chatda ochilmoqda.",
        "Открываю {title} для {name} в чате.",
      ),
      mapOf(
        "title" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
      ),
    ) },
    Regex(
      """^Which document for (.+) should I open\? They are listed in the chat\.$""",
    ) to { m -> fill(
      line("Which document for {name} should I open? They are listed in the chat.",
        "{name}ning qaysi hujjatini ochay? Ular chatda ko‘rsatilgan.",
        "Какой документ {name} открыть? Они перечислены в чате.",
      ),
      mapOf("name" to patientLabel(m.groups[1]?.value)),
    ) },
    Regex(
      """^I could not match "(.+)"\. Here are (\d+) documents for (.+) — tap one to open in chat\.$""",
    ) to { m -> fill(
      line("I could not match \"{query}\". Here are {count} documents for {name} — tap one to open in chat.",
        "“{query}” ga mos hujjat topilmadi. {name}ning {count} ta hujjati bor — chatda ochish uchun birini bosing.",
        "Не нашла «{query}». Вот {count} документов {name} — нажмите, чтобы открыть в чате.",
      ),
      mapOf(
        "query" to m.groupValues[1],
        "count" to m.groupValues[2],
        "name" to patientLabel(m.groups[3]?.value),
      ),
    ) },
    Regex(
      """^Here is the dental diagram for (.+) from (.+) — (\d+) teeth have recorded services\.$""",
    ) to { m -> fill(
      line("Here is the dental diagram for {name} from {when} — {count} teeth have recorded services.",
        "{name}ning {when} dagi tish diagrammasi — {count} ta tishda xizmatlar yozilgan.",
        "Зубная диаграмма {name} от {when} — услуги записаны по {count} зубам.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
        "count" to m.groupValues[3],
      ),
    ) },
    Regex(
      """^Here is the dental diagram for (.+) from (.+) — 1 tooth has recorded services\.$""",
    ) to { m -> fill(
      line("Here is the dental diagram for {name} from {when} — 1 tooth has recorded services.",
        "{name}ning {when} dagi tish diagrammasi — 1 ta tishda xizmat yozilgan.",
        "Зубная диаграмма {name} от {when} — услуги записаны по 1 зубу.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
      ),
    ) },
    Regex("""^Here is the dental diagram for (.+) from (.+)\.$""") to { m -> fill(
      line("Here is the dental diagram for {name} from {when}.",
        "{name}ning {when} dagi tish diagrammasi.",
        "Зубная диаграмма {name} от {when}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
      ),
    ) },
    Regex(
      """^There is no dental diagram saved for (.+)'s latest visit yet\.$""",
    ) to { m -> fill(
      line("There is no dental diagram saved for {name}'s latest visit yet.",
        "{name}ning so‘nggi tashrifida hali tish diagrammasi yo‘q.",
        "Для последнего визита {name} зубная диаграмма ещё не сохранена.",
      ),
      mapOf("name" to patientLabel(m.groups[1]?.value)),
    ) },
    Regex(
      """^No visit dental chart yet\. Opening (.+) for (.+) in the chat\.$""",
    ) to { m -> fill(
      line("No visit dental chart yet. Opening {title} for {name} in the chat.",
        "Tashrif tish diagrammasi hali yo‘q. {name} uchun {title} chatda ochilmoqda.",
        "Диаграммы визита ещё нет. Открываю {title} для {name} в чате.",
      ),
      mapOf(
        "title" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
      ),
    ) },
    Regex(
      """^(.+) has no visits on your calendar yet, so there is no dental diagram\.$""",
    ) to { m -> fill(
      line("{name} has no visits on your calendar yet, so there is no dental diagram.",
        "{name}ning kalendaringizda hali tashrifi yo‘q, shuning uchun tish diagrammasi ham yo‘q.",
        "У {name} ещё нет визитов в вашем календаре, поэтому зубной диаграммы нет.",
      ),
      mapOf("name" to patientLabel(m.groups[1]?.value)),
    ) },
    Regex(
      """^Cancelled (\d+)\. The patients were notified in the app\.$""",
    ) to { m -> fill(
      line("Cancelled {count}. The patients were notified in the app.",
        "{count} ta qabul bekor qilindi. Bemorlarga ilovada xabar berildi.",
        "Отменено: {count}. Пациенты уведомлены в приложении.",
      ),
      mapOf("count" to m.groupValues[1]),
    ) },
    Regex(
      """^Cancelled (\d+)\. The patient was notified in the app\.$""",
    ) to { m -> fill(
      line("Cancelled {count}. The patient was notified in the app.",
        "{count} ta qabul bekor qilindi. Bemorga ilovada xabar berildi.",
        "Отменено: {count}. Пациент уведомлён в приложении.",
      ),
      mapOf("count" to m.groupValues[1]),
    ) },
    Regex("""^Cancelled (\d+)\. (\d+) could not be cancelled\.$""") to { m -> fill(
      line("Cancelled {count}. {failed} could not be cancelled.",
        "{count} ta qabul bekor qilindi. {failed} tasini bekor qilib bo‘lmadi.",
        "Отменено: {count}. Не удалось отменить: {failed}.",
      ),
      mapOf("count" to m.groupValues[1], "failed" to m.groupValues[2]),
    ) },
    Regex(
      """^I found (\d+) visits that can be cancelled\.(.*) Which one should I cancel\?$""",
    ) to { m -> fill(
      line("I found {count} visits that can be cancelled.{note} Which one should I cancel?",
        "Bekor qilish mumkin bo‘lgan {count} ta tashrif topdim.{note} Qaysi birini bekor qilay?",
        "Нашла {count} визитов, которые можно отменить.{note} Какой отменить?",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "note" to if (m.groupValues[2].trim().isEmpty()) "" else " ${localizeHint(m.groupValues[2].trim())}",
      ),
    ) },
    Regex(
      """^Moved (.+?) to (\d{1,2}:\d{2})(?: on ([^.]+))?\.?$""",
    ) to { m -> fill(
      line("Moved {name} to {time}{when}.",
        if (m.groups[3]?.value == null) "{name} soat {time} ga ko‘chirildi." else "{name} {when}kuni, soat {time} ga ko‘chirildi.",
        "{name} перенесён на {time}{when}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "time" to clock(m.groupValues[2]),
        "when" to if (m.groups[3]?.value == null) "" else line(" on ${m.groups[3]?.value}",
                "${localizeEmbeddedCalendar(m.groupValues[3].trim())} ",
                " ${localizeEmbeddedCalendar(m.groupValues[3].trim())}, ",
              ),
      ),
    ) },
    Regex(
      """^Please confirm booking (.+?) at (\d{1,2}:\d{2}) on ([^.]+)\.?$""",
    ) to { m -> fill(
      line("Please confirm booking {name} at {time} on {when}.",
        "Iltimos, {name}ning {when} kuni soat {time} dagi qabulini tasdiqlang.",
        "Подтвердите запись {name} на {time}, {when}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "time" to clock(m.groupValues[2]),
        "when" to localizeEmbeddedCalendar(m.groupValues[3].trim()),
      ),
    ) },
    Regex(
      """^I can move (.+?) to (\d{1,2}:\d{2}) on ([^.]+)\. Please confirm\.$""",
    ) to { m -> fill(
      line("I can move {name} to {time} on {when}. Please confirm.",
        "{name}ni soat {time} ga, {when} kuniga ko‘chira olaman. Tasdiqlang.",
        "Могу перенести {name} на {time}, {when}. Подтвердите.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "time" to clock(m.groupValues[2]),
        "when" to localizeEmbeddedCalendar(m.groupValues[3].trim()),
      ),
    ) },
    Regex("""^I abandoned that booking\.$""") to { _ -> line("I abandoned that booking.",
      "Bu band qilishni bekor qildim.",
      "Я отменила это бронирование.",
    ) },
    Regex("""^I did not book that appointment\.$""") to { _ -> line("I did not book that appointment.",
      "Bu uchrashuvni band qilmadim.",
      "Я не записала этот приём.",
    ) },
    Regex(
      """^Booked (.+?) at (\d{1,2}:\d{2})(?: on ([^.]+))?\.?$""",
    ) to { m -> fill(
      line("Booked {name} at {time}{when}.",
        if (m.groups[3]?.value == null) "{name} soat {time} ga band qilindi." else "{name} {when}kuni, soat {time} ga band qilindi.",
        "{name} записан на {time}{when}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "time" to clock(m.groupValues[2]),
        "when" to if (m.groups[3]?.value == null) "" else line(" on ${m.groupValues[3].trim()}",
                "${localizeEmbeddedCalendar(m.groupValues[3].trim())} ",
                " ${localizeEmbeddedCalendar(m.groupValues[3].trim())}, ",
              ),
      ),
    ) },
    Regex(
      """^You have (\d+) appointments? today\. The first is with (.+) (in about .+)\.$""",
    ) to { m -> fill(
      line("You have {count} appointments today. The first is with {name} {when}.",
        "Bugun {count} ta uchrashuv bor. Birinchisi {name} bilan {when}.",
        "Сегодня записей: {count}. Первая — {name}, {when}.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
        "when" to soonPhrase(m.groupValues[3]),
      ),
    ) },
    Regex(
      """^You have (\d+) appointments? today\. The next one with (.+) is starting now\.$""",
    ) to { m -> fill(
      line("You have {count} appointments today. The next one with {name} is starting now.",
        "Bugun {count} ta uchrashuv bor. Keyingisi {name} bilan hozir boshlanmoqda.",
        "Сегодня записей: {count}. Следующая, {name}, начинается сейчас.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
      ),
    ) },
    Regex(
      """^You (have|had) (\d+) (patient|patients) with appointments scheduled (.+)\.$""",
    ) to { m -> fill(
      line("You {verb} {count} patients with appointments scheduled {when}.",
        "{when} uchrashuvi bor bemorlar: {count}.",
        "{when} пациентов с записями: {count}.",
      ),
      mapOf(
        "verb" to m.groupValues[1],
        "count" to m.groupValues[2],
        "when" to rangeLabel(m.groupValues[4]),
      ),
    ) },
    Regex(
      """^You (have|had) no( cancelled)? appointments scheduled (.+?)(?: with (.+))?\.$""",
    ) to { m -> fill(
      line("You {verb} no{cancelled} appointments scheduled {when}{name}.",
        "{when}{name} rejalashtirilgan{cancelled} uchrashuv yo‘q.",
        "{when}{name} нет запланированных{cancelled} записей.",
      ),
      mapOf(
        "verb" to m.groupValues[1],
        "cancelled" to if (m.groups[2]?.value == null) "" else line(" cancelled",
                " bekor qilingan",
                " отменённых",
              ),
        "when" to rangeLabel(m.groupValues[3]),
        "name" to if (m.groups[4]?.value == null) "" else " ${patientLabel(m.groups[4]?.value)}",
      ),
    ) },
    Regex(
      """^You (have|had) (\d+) (appointment|appointments) scheduled ([^:.]+?)(?: with ([^:]+?))?(?:: (.+))?\.$""",
    ) to { m -> fill(
      line("You {verb} {count} appointments scheduled {when}{name}{details}.",
        "{when}{name} {count} ta uchrashuv rejalashtirilgan{details}.",
        "{when}{name} записей: {count}{details}.",
      ),
      mapOf(
        "verb" to m.groupValues[1],
        "count" to m.groupValues[2],
        "when" to rangeLabel(m.groupValues[4]),
        "name" to if (m.groups[5]?.value == null) "" else " ${patientLabel(m.groups[5]?.value)}",
        "details" to if (m.groups[6]?.value == null) "" else ": ${localizeEmbeddedCalendar(m.groupValues[6])}",
      ),
    ) },
    Regex(
      """^(\d+) (appointment|appointments) scheduled (.+?)(?: with (.+))? (?:is|are) currently cancelled\.$""",
    ) to { m -> fill(
      line("{count} appointments scheduled {when}{name} are currently cancelled.",
        "{when}{name} rejalashtirilgan {count} ta uchrashuv hozir bekor qilingan.",
        "{when}{name} сейчас отменено записей: {count}.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "when" to rangeLabel(m.groupValues[3]),
        "name" to if (m.groups[4]?.value == null) "" else " ${patientLabel(m.groups[4]?.value)}",
      ),
    ) },
    Regex(
      """^You (have|had) (\d+) (appointment|appointments) scheduled (.+?)(?: with (.+))?, of which (\d+) were confirmed and (\d+) are awaiting confirmation\.(?: Also (.+))?$""",
    ) to { m -> fill(
      line("You {verb} {count} appointments scheduled {when}{name}, of which {confirmed} were confirmed and {requested} are awaiting confirmation.{extra}",
        "{when}{name} {count} ta uchrashuv rejalashtirilgan, shundan {confirmed} tasi tasdiqlangan, {requested} tasi tasdiq kutmoqda.{extra}",
        "{when}{name} записей: {count}, из них подтверждено {confirmed}, ожидают подтверждения {requested}.{extra}",
      ),
      mapOf(
        "verb" to m.groupValues[1],
        "count" to m.groupValues[2],
        "when" to rangeLabel(m.groupValues[4]),
        "name" to if (m.groups[5]?.value == null) "" else " ${patientLabel(m.groups[5]?.value)}",
        "confirmed" to m.groupValues[6],
        "requested" to m.groupValues[7],
        "extra" to if (m.groups[8]?.value == null) "" else " ${localizeStatusBits(m.groupValues[8])}",
      ),
    ) },
    Regex("""^No appointments scheduled (.+)\.$""") to { m -> fill(
      line("No appointments scheduled {when}.",
        "{when} rejalashtirilgan uchrashuv yo‘q.",
        "{when} записей нет.",
      ),
      mapOf("when" to rangeLabel(m.groupValues[1])),
    ) },
    Regex("""^Scheduled (.+) by ([a-z_]+): (.+)\.$""") to { m -> fill(
      line("Scheduled {when} by {dimension}: {rows}.",
        "{when} bo‘yicha {dimension}: {rows}.",
        "{when} по {dimension}: {rows}.",
      ),
      mapOf(
        "when" to rangeLabel(m.groupValues[1]),
        "dimension" to dimensionLabel(m.groupValues[2]),
        "rows" to localizeStatusBits(m.groupValues[3]),
      ),
    ) },
    Regex("""^Compared with (.+), that was (\d+)\.$""") to { m -> fill(
      line("Compared with {when}, that was {count}.",
        "{when} bilan solishtirganda bu {count} edi.",
        "По сравнению с {when} тогда было {count}.",
      ),
      mapOf("when" to rangeLabel(m.groupValues[1]), "count" to m.groupValues[2]),
    ) },
    Regex("""^Showing (\d+) of (\d+)\.$""") to { m -> fill(
      line("Showing {shown} of {total}.",
        "{total} tadan {shown} tasi ko‘rsatilgan.",
        "Показано {shown} из {total}.",
      ),
      mapOf("shown" to m.groupValues[1], "total" to m.groupValues[2]),
    ) },
    Regex(
      """^There are no past visits scheduled (.+), so the cancellation rate is not available\.$""",
    ) to { m -> fill(
      line("There are no past visits scheduled {when}, so the cancellation rate is not available.",
        "{when} o‘tgan tashrif yo‘q, shuning uchun bekor qilish ulushi noma’lum.",
        "Прошедших визитов {when} нет, поэтому долю отмен посчитать нельзя.",
      ),
      mapOf("when" to rangeLabel(m.groupValues[1])),
    ) },
    Regex(
      """^Cancellation rate for visits scheduled (.+), excluding future visits, is ([0-9.]+) percent \((\d+) of (\d+)\)\. Visits that have not happened yet are left out\.$""",
    ) to { m -> fill(
      line("Cancellation rate for visits scheduled {when}, excluding future visits, is {percent} percent ({num} of {den}). Visits that have not happened yet are left out.",
        "{when} rejalashtirilgan tashriflarning bekor qilish ulushi, kelajakdagilar hisobga olinmagan holda, {percent} foiz ({num} / {den}). Hali bo‘lmagan tashriflar kiritilmagan.",
        "Доля отмен визитов, запланированных {when}, без будущих визитов — {percent} процентов ({num} из {den}). Ещё не состоявшиеся визиты не входят.",
      ),
      mapOf(
        "when" to rangeLabel(m.groupValues[1]),
        "percent" to m.groupValues[2],
        "num" to m.groupValues[3],
        "den" to m.groupValues[4],
      ),
    ) },
    Regex("""^For (.+) the rate is not available\.$""") to { m -> fill(
      line("For {when} the rate is not available.",
        "{when} uchun ulush noma’lum.",
        "Для {when} долю посчитать нельзя.",
      ),
      mapOf("when" to rangeLabel(m.groupValues[1])),
    ) },
    Regex("""^For (.+) it was ([0-9.]+) percent \((\d+) of (\d+)\)\.$""") to { m ->
        fill(
          line("For {when} it was {percent} percent ({num} of {den}).",
            "{when} uchun bu {percent} foiz edi ({num} / {den}).",
            "Для {when} было {percent} процентов ({num} из {den}).",
          ),
          mapOf(
            "when" to rangeLabel(m.groupValues[1]),
            "percent" to m.groupValues[2],
            "num" to m.groupValues[3],
            "den" to m.groupValues[4],
          ),
        ) },
    Regex("""^You collected nothing (.+)\.$""") to { m -> fill(
      line("You collected nothing {when}.",
        "{when} hech narsa tushum qilinmagan.",
        "{when} поступлений не было.",
      ),
      mapOf("when" to rangeLabel(m.groupValues[1])),
    ) },
    Regex("""^Collected (.+): (.+)\.$""") to { m -> fill(
      line("Collected {when}: {parts}.",
        "{when} tushum: {parts}.",
        "Поступления {when}: {parts}.",
      ),
      mapOf(
        "when" to rangeLabel(m.groupValues[1]),
        "parts" to moneyParts(m.groupValues[2]),
      ),
    ) },
    Regex(
      """^(Appointment prices|Pending amounts) scheduled (.+): nothing with a stored price\.$""",
    ) to { m -> fill(
      line("{lead} scheduled {when}: nothing with a stored price.",
        "{when} {lead}: saqlangan narx yo‘q.",
        "{lead} {when}: сохранённой цены нет.",
      ),
      mapOf(
        "lead" to moneyLead(m.groupValues[1]),
        "when" to rangeLabel(m.groupValues[2]),
      ),
    ) },
    Regex(
      """^(Appointment prices|Pending amounts) scheduled (.+): (.+)\.$""",
    ) to { m -> fill(
      line("{lead} scheduled {when}: {parts}.",
        "{when} {lead}: {parts}.",
        "{lead} {when}: {parts}.",
      ),
      mapOf(
        "lead" to moneyLead(m.groupValues[1]),
        "when" to rangeLabel(m.groupValues[2]),
        "parts" to moneyParts(m.groupValues[3]),
      ),
    ) },
    Regex(
      """^(.+?) · (.+?) · ([A-Z_]+)\. Opening the (appointment|documentation|patient record)\.$""",
    ) to { m -> fill(
      line("{name} · {when} · {status}. Opening the {where}.",
        "{name} · {when} · {status}. {where} ochilmoqda.",
        "{name} · {when} · {status}. Открываю {where}.",
      ),
      mapOf(
        "name" to patientLabel(m.groups[1]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
        "status" to statusLabel(m.groupValues[3]),
        "where" to openWhere(m.groupValues[4]),
      ),
    ) },
    Regex("""^The note says: (.+)$""") to { m -> fill(
      line("The note says: {text}",
        "Qayd matni: {text}",
        "В заметке сказано: {text}",
      ),
      mapOf("text" to m.groupValues[1]),
    ) },
    Regex(
      """^Last visit was on (.+) at (\d{2}:\d{2})\. Reason: (.+)\.$""",
    ) to { m -> fill(
      line("Last visit was on {when} at {time}. Reason: {reason}.",
        "So‘nggi tashrif {when}, soat {time}. Sabab: {reason}.",
        "Последний визит {when}, {time}. Причина: {reason}.",
      ),
      mapOf(
        "when" to localizeEmbeddedCalendar(m.groupValues[1]),
        "time" to m.groupValues[2],
        "reason" to reasonText(m.groupValues[3]),
      ),
    ) },
    Regex("""^Last visit (.+) at (\d{2}:\d{2})\.$""") to { m -> fill(
      line("Last visit {when} at {time}.",
        "So‘nggi tashrif {when}, soat {time}.",
        "Последний визит {when}, {time}.",
      ),
      mapOf(
        "when" to localizeEmbeddedCalendar(m.groupValues[1]),
        "time" to m.groupValues[2],
      ),
    ) },
    Regex("""^You have seen them (\d+) times\.$""") to { m -> fill(
      line("You have seen them {count} times.",
        "Siz ularni {count} marta ko‘rgansiz.",
        "Вы принимали их {count} раз.",
      ),
      mapOf("count" to m.groupValues[1]),
    ) },
    Regex("""^Chart notes include: (.+)\.$""") to { m -> fill(
      line("Chart notes include: {text}.",
        "Kartadagi qaydlar: {text}.",
        "В карте есть: {text}.",
      ),
      mapOf("text" to m.groupValues[1]),
    ) },
    Regex("""^Previously you planned: (.+)\.$""") to { m -> fill(
      line("Previously you planned: {text}.",
        "Avval rejalashtirganingiz: {text}.",
        "Ранее вы планировали: {text}.",
      ),
      mapOf("text" to m.groupValues[1]),
    ) },
    Regex(
      """^(\d+) (?:is|are) already cancelled, including (.+) ([A-Za-z]+ \d{1,2} [A-Za-z]+ at \d{2}:\d{2})\.$""",
    ) to { m -> fill(
      line("{count} are already cancelled, including {name} {when}.",
        "{count} tasi allaqachon bekor qilingan, jumladan {name} {when}.",
        "Уже отменено: {count}, в том числе {name} {when}.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "name" to patientLabel(m.groups[2]?.value),
        "when" to localizeEmbeddedCalendar(m.groupValues[3]),
      ),
    ) },
    Regex(
      """^Documented (\d+) facts(?: and drafted (\d+) medication tasks)?\. They are on the patient timeline\.$""",
    ) to { m -> fill(
      line("Documented {count} facts{tasks}. They are on the patient timeline.",
        "{count} ta fakt hujjatlashtirildi{tasks}. Ular bemor vaqt chizig‘ida.",
        "Задокументировано фактов: {count}{tasks}. Они на временной шкале пациента.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "tasks" to if (m.groups[2]?.value == null) "" else fill(
                line(" and drafted {n} medication tasks",
                  " va {n} ta dori vazifasi tayyorlandi",
                  " и создано задач по лекарствам: {n}",
                ),
                mapOf("n" to m.groupValues[2]),
              ),
      ),
    ) },
    Regex("""^Top patterns: (.+)\.$""") to { m -> fill(
      line("Top patterns: {rest}",
        "Asosiy naqshlar: {rest}",
        "Основные шаблоны: {rest}",
      ),
      mapOf("rest" to m.groupValues[1]),
    ) },
    Regex("""^You have (\d+) appointments today\.$""") to { m -> fill(
      line("You have {count} appointments today.",
        "Bugun {count} ta uchrashuv bor.",
        "Сегодня записей: {count}.",
      ),
      mapOf("count" to m.groupValues[1]),
    ) },
    Regex("""^You have 1 appointment today\.$""") to { m -> line("You have 1 appointment today.",
      "Bugun 1 ta uchrashuv bor.",
      "Сегодня 1 запись.",
    ) },
    Regex(
      """^You (?:have|had) (\d+) (?:appointment|appointments|patient|patients)(?: with .+)? scheduled (.+?)\.$""",
    ) to { m -> fill(
      line(text,
        "{count} ta yozuv {when} ga rejalashtirilgan.",
        "{count} записей запланировано {when}.",
      ),
      mapOf(
        "count" to m.groupValues[1],
        "when" to localizeEmbeddedCalendar(m.groupValues[2]),
      ),
    ) },
  );

  for ((pattern, render) in patterns) {
    pattern.find(text)?.let { return render(it) }
  }
  return null;
}

  private fun exactSentences(): Map<String, String> = mapOf(
    "I could not tell which visit to move. Say the patient and the day." to line(
      "I could not tell which visit to move. Say the patient and the day.",
      "Qaysi tashrifni ko‘chirishni bilmayman. Bemor va kunni ayting.",
      "Не поняла, какой визит переносить. Назовите пациента и день.",
    ),
    "Which patient should I check appointments for?" to line(
      "Which patient should I check appointments for?",
      "Qaysi bemorning uchrashuvlarini tekshiray?",
      "Для какого пациента проверить записи?",
    ),
    "I could not find that patient." to line(
      "I could not find that patient.",
      "Bu bemorni topa olmadim.",
      "Не нашла этого пациента.",
    ),
    "Please give a patient name to search." to line(
      "Please give a patient name to search.",
      "Qidirish uchun bemor ismini ayting.",
      "Назовите имя пациента для поиска.",
    ),
    "Which day should I check for free slots?" to line(
      "Which day should I check for free slots?",
      "Bo‘sh vaqtlarni qaysi kunga tekshiray?",
      "На какой день проверить свободное время?",
    ),
    "I need a patient id before I can propose a booking." to line(
      "I need a patient id before I can propose a booking.",
      "Band qilishdan oldin bemor kerak.",
      "Чтобы предложить запись, нужен пациент.",
    ),
    "I need an appointment to list draft visit facts." to line(
      "I need an appointment to list draft visit facts.",
      "Qoralama faktlar uchun uchrashuv kerak.",
      "Чтобы показать черновики фактов, нужна запись.",
    ),
    "That appointment was not found." to line(
      "That appointment was not found.",
      "Bu uchrashuv topilmadi.",
      "Эта запись не найдена.",
    ),
    "That appointment is not on your calendar." to line(
      "That appointment is not on your calendar.",
      "Bu uchrashuv sizning kalendaringizda yo‘q.",
      "Этой записи нет в вашем календаре.",
    ),
    "Which patient should I list documents for?" to line(
      "Which patient should I list documents for?",
      "Qaysi bemorning hujjatlarini ko‘rsatay?",
      "Для какого пациента показать документы?",
    ),
    "Which patient's document should I open?" to line(
      "Which patient's document should I open?",
      "Qaysi bemorning hujjatini ochay?",
      "Документ какого пациента открыть?",
    ),
    "Which patient's dental diagram should I open?" to line(
      "Which patient's dental diagram should I open?",
      "Qaysi bemorning tish diagrammasini ochay?",
      "Зубную диаграмму какого пациента открыть?",
    ),
    "Which visit should I complete?" to line(
      "Which visit should I complete?",
      "Qaysi tashrifni yakunlay?",
      "Какой визит завершить?",
    ),
    "Which visit should I request a signature for?" to line(
      "Which visit should I request a signature for?",
      "Qaysi tashrif uchun imzo so‘ray?",
      "Для какого визита запросить подпись?",
    ),
    "Which visit should I document? Open or start the appointment first." to line(
      "Which visit should I document? Open or start the appointment first.",
      "Qaysi tashrifni hujjatlashtiray? Avval uchrashuvni oching yoki boshlang.",
      "Какой визит документировать? Сначала откройте или начните приём.",
    ),
    "I could not save that note." to line(
      "I could not save that note.",
      "Bu qaydni saqlay olmadim.",
      "Не удалось сохранить эту запись.",
    ),
    "There is no consultation note for this visit yet." to line(
      "There is no consultation note for this visit yet.",
      "Bu tashrif uchun hali maslahat qaydi yo‘q.",
      "Для этого визита ещё нет заметки консультации.",
    ),
    "That is already in the consultation note." to line(
      "That is already in the consultation note.",
      "Bu allaqachon maslahat qaydida bor.",
      "Это уже есть в заметке консультации.",
    ),
    "I added that to the consultation note. Say when you want to request the patient's signature." to line(
      "I added that to the consultation note. Say when you want to request the patient's signature.",
      "Buni maslahat qaydida qo‘shdim. Bemor imzosini qachon so‘rashni ayting.",
      "Добавила это в заметку консультации. Скажите, когда запросить подпись пациента.",
    ),
    "I could not find a matching visit. Nothing was changed." to line(
      "I could not find a matching visit. Nothing was changed.",
      "Mos tashrif topilmadi. Hech narsa o‘zgartirilmadi.",
      "Подходящий визит не найден. Ничего не изменено.",
    ),
    "The schedule changed, so I did not cancel anything. Ask me again and I will show the updated list." to line(
      "The schedule changed, so I did not cancel anything. Ask me again and I will show the updated list.",
      "Jadval o‘zgardi, shuning uchun hech narsani bekor qilmadim. Qayta so‘rang, yangilangan ro‘yxatni ko‘rsataman.",
      "Расписание изменилось, поэтому я ничего не отменила. Спросите ещё раз, и я покажу обновлённый список.",
    ),
    "That visit was more than 24 hours ago and was never opened, so it is a no-show. I did not open the chart." to line(
      "That visit was more than 24 hours ago and was never opened, so it is a no-show. I did not open the chart.",
      "Bu tashrif 24 soatdan oldin bo‘lgan va ochilmagan, shuning uchun bu kelmagan holat. Kartani ochmadim.",
      "Этот визит был больше 24 часов назад и не открывался, поэтому это неявка. Карту я не открыла.",
    ),
    "Visit completed." to line(
      "Visit completed.",
      "Tashrif yakunlandi.",
      "Визит завершён.",
    ),
    "Signature requested." to line(
      "Signature requested.",
      "Imzo so‘raldi.",
      "Подпись запрошена.",
    ),
    "Nothing was cancelled." to line(
      "Nothing was cancelled.",
      "Hech narsa bekor qilinmadi.",
      "Ничего не отменено.",
    ),
    "No clinic patterns recorded yet." to line(
      "No clinic patterns recorded yet.",
      "Klinika naqshlari hali yozilmagan.",
      "Шаблоны клиники ещё не записаны.",
    ),
    "No patients found." to line(
      "No patients found.",
      "Bemor topilmadi.",
      "Пациенты не найдены.",
    ),
    "You have no appointments today." to line(
      "You have no appointments today.",
      "Bugun uchrashuv yo‘q.",
      "Сегодня записей нет.",
    ),
    "No prior chart notes available yet." to line(
      "No prior chart notes available yet.",
      "Hali oldingi qaydlar yo‘q.",
      "Предыдущих записей карты пока нет.",
    ),
    "There are no draft visit facts ready to document for this visit." to line(
      "There are no draft visit facts ready to document for this visit.",
      "Bu tashrif uchun hujjatlashtirishga tayyor qoralama fakt yo‘q.",
      "Для этого визита нет черновиков фактов, готовых к документированию.",
    ),
    "You do not have ranked visit history yet - no non-cancelled appointments were found in this period." to line(
      "You do not have ranked visit history yet - no non-cancelled appointments were found in this period.",
      "Hali tashrif reytingi yo‘q — bu davrda bekor qilinmagan uchrashuv topilmadi.",
      "Рейтинга визитов ещё нет — за этот период не найдено неотменённых записей.",
    ),
    "I found more than one patient. Which one did you mean?" to line(
      "I found more than one patient. Which one did you mean?",
      "Bir nechta bemor topildi. Qaysi birini nazarda tutdingiz?",
      "Нашлась больше чем одна пациентка или пациент. Кого вы имели в виду?",
    ),
    "No patients matched." to line(
      "No patients matched.",
      "Bemor topilmadi.",
      "Пациенты не найдены.",
    ),
    "Appointment booked." to line(
      "Appointment booked.",
      "Uchrashuv band qilindi.",
      "Приём записан.",
    ),
    "That slot could not be booked. Pick another time on the card." to line(
      "That slot could not be booked. Pick another time on the card.",
      "Bu vaqtni band qilib bo‘lmadi. Kartadan boshqa vaqtni tanlang.",
      "Это время занять не удалось. Выберите другое время на карточке.",
    ),
    "Did you mean…?" to line(
      "Did you mean…?",
      "Shuni nazarda tutdingizmi…?",
      "Вы имели в виду…?",
    ),
    "Top patterns:" to line(
      "Top patterns:",
      "Asosiy naqshlar:",
      "Основные шаблоны:",
    ),
    "This looks like a new patient for you. There is no prior visit history yet." to line(
      "This looks like a new patient for you. There is no prior visit history yet.",
      "Bu siz uchun yangi bemorga o‘xshaydi. Hali oldingi tashrif tarixi yo‘q.",
      "Похоже, для вас это новый пациент. Предыдущей истории визитов ещё нет.",
    ),
    "I have prior visits on file for this patient." to line(
      "I have prior visits on file for this patient.",
      "Bu bemorning oldingi tashriflari bor.",
      "По этому пациенту есть предыдущие визиты.",
    ),
    "That visit is still waiting for payment." to line(
      "That visit is still waiting for payment.",
      "Bu tashrif hali to‘lovni kutmoqda.",
      "Этот визит всё ещё ждёт оплаты.",
    ),
    "That visit expired before payment." to line(
      "That visit expired before payment.",
      "Bu tashrif to‘lovdan oldin muddati o‘tgan.",
      "Срок этого визита истёк до оплаты.",
    ),
    "Payment failed for that visit." to line(
      "Payment failed for that visit.",
      "Bu tashrif uchun to‘lov amalga oshmadi.",
      "Оплата этого визита не прошла.",
    ),
    "I can only start a non-video visit for today or tomorrow, or within 24 hours after its start." to line(
      "I can only start a non-video visit for today or tomorrow, or within 24 hours after its start.",
      "Video bo‘lmagan tashrifni faqat bugun, ertaga yoki boshlanishidan keyin 24 soat ichida boshlay olaman.",
      "Очный визит можно начать только сегодня, завтра или в течение 24 часов после начала.",
    ),
    "That visit is already in progress." to line(
      "That visit is already in progress.",
      "Bu tashrif allaqachon boshlangan.",
      "Этот визит уже идёт.",
    ),
    "That visit is already completed." to line(
      "That visit is already completed.",
      "Bu tashrif allaqachon yakunlangan.",
      "Этот визит уже завершён.",
    ),
    "I can only complete a visit that is in progress." to line(
      "I can only complete a visit that is in progress.",
      "Faqat boshlangan tashrifni yakunlay olaman.",
      "Завершить можно только визит, который уже идёт.",
    ),
    "I could not verify that the visit was completed." to line(
      "I could not verify that the visit was completed.",
      "Tashrif yakunlanganini tasdiqlay olmadim.",
      "Не удалось проверить, что визит завершён.",
    ),
    "That visit was cancelled." to line(
      "That visit was cancelled.",
      "Bu tashrif bekor qilingan.",
      "Этот визит был отменён.",
    ),
    "That visit was marked as a no-show." to line(
      "That visit was marked as a no-show.",
      "Bu tashrif kelmagan deb belgilangan.",
      "Этот визит отмечен как неявка.",
    ),
    "The patient has already signed." to line(
      "The patient has already signed.",
      "Bemor allaqachon imzo qo‘ygan.",
      "Пациент уже подписал.",
    ),
    "A signature was already requested. I did not send another notification." to line(
      "A signature was already requested. I did not send another notification.",
      "Imzo allaqachon so‘ralgan. Yana xabar yubormadim.",
      "Подпись уже запрашивали. Повторное уведомление я не отправила.",
    ),
    "Signature requested. The patient was notified in the app." to line(
      "Signature requested. The patient was notified in the app.",
      "Imzo so‘raldi. Bemorga ilovada xabar berildi.",
      "Подпись запрошена. Пациент уведомлён в приложении.",
    ),
    "I could not find that patient on your roster." to line(
      "I could not find that patient on your roster.",
      "Ro‘yxatingizda bu bemor topilmadi.",
      "Этого пациента нет в вашем списке.",
    ),
    "Which period should I use?" to line(
      "Which period should I use?",
      "Qaysi davrni olay?",
      "Какой период использовать?",
    ),
    "I could not retrieve that appointment information." to line(
      "I could not retrieve that appointment information.",
      "Uchrashuv ma’lumotini olib bo‘lmadi.",
      "Не удалось получить эти данные о записях.",
    ),
    "I could not tell which dates you mean." to line(
      "I could not tell which dates you mean.",
      "Qaysi sanalarni nazarda tutganingizni bilmayman.",
      "Не поняла, какие даты вы имеете в виду.",
    ),
    "That period is outside the supported range." to line(
      "That period is outside the supported range.",
      "Bu davr qo‘llab-quvvatlanadigan oralig‘dan tashqarida.",
      "Этот период вне поддерживаемого диапазона.",
    ),
    "The end date is before the start date." to line(
      "The end date is before the start date.",
      "Tugash sanasi boshlanish sanasidan oldin.",
      "Дата окончания раньше даты начала.",
    ),
    "That start date is too far in the future." to line(
      "That start date is too far in the future.",
      "Boshlanish sanasi juda uzoq kelajakda.",
      "Дата начала слишком далеко в будущем.",
    ),
    "That date is too far in the future." to line(
      "That date is too far in the future.",
      "Bu sana juda uzoq kelajakda.",
      "Эта дата слишком далеко в будущем.",
    ),
    "I could not read that number of days." to line(
      "I could not read that number of days.",
      "Kunlar sonini o‘qiy olmadim.",
      "Не удалось прочитать число дней.",
    ),
    "Shifa does not store a rescheduled status. A doctor reschedule changes the visit time. A patient reschedule cancels the old visit and books a new one. Those cannot be counted as reschedules." to line(
      "Shifa does not store a rescheduled status. A doctor reschedule changes the visit time. A patient reschedule cancels the old visit and books a new one. Those cannot be counted as reschedules.",
      "Shifa ko‘chirilgan holatni saqlamaydi. Shifokor ko‘chirsa, tashrif vaqti o‘zgaradi. Bemor ko‘chirsa, eski tashrif bekor qilinib yangisi yoziladi. Ularni ko‘chirishlar sifatida sanab bo‘lmaydi.",
      "Shifa не хранит статус переноса. Перенос врачом меняет время визита. Перенос пациентом отменяет старый визит и создаёт новый. Это нельзя посчитать как переносы.",
    ),
    "Consultation notes do not have a finalized or doctor-signed state. I can count visits with or without a consultation note, and whether the patient has signed." to line(
      "Consultation notes do not have a finalized or doctor-signed state. I can count visits with or without a consultation note, and whether the patient has signed.",
      "Maslahat qaydlarida yakuniy yoki shifokor imzolagan holat yo‘q. Qayd bor-yo‘qligini va bemor imzolaganini sanay olaman.",
      "У заметок консультации нет состояния «завершено» или «подписано врачом». Могу посчитать визиты с заметкой и без неё, и подписал ли пациент.",
    ),
    "Partial payments live on treatment plans, not as an appointment status. I can't count partially paid visits without mixing plan balances and visit prices." to line(
      "Partial payments live on treatment plans, not as an appointment status. I can't count partially paid visits without mixing plan balances and visit prices.",
      "Qisman to‘lovlar davolash rejalarida, uchrashuv holatida emas. Reja balansi va tashrif narxini aralashtirmasdan qisman to‘langan tashriflarni sanay olmayman.",
      "Частичные оплаты живут в планах лечения, а не в статусе записи. Не могу посчитать частично оплаченные визиты, не смешав балансы планов и цены визитов.",
    ),
    "An average is not defined for this. I can count appointments, or total collected money by currency." to line(
      "An average is not defined for this. I can count appointments, or total collected money by currency.",
      "Bu yerda o‘rtacha aniqlanmagan. Uchrashuvlarni yoki valyuta bo‘yicha tushumni sanay olaman.",
      "Среднее здесь не определено. Могу посчитать записи или собранные деньги по валютам.",
    ),
    "Refunds are not posted by the current payment flow, and they are not subtracted from the clinic ledger. I can't report a refund total." to line(
      "Refunds are not posted by the current payment flow, and they are not subtracted from the clinic ledger. I can't report a refund total.",
      "Hozirgi to‘lov oqimi qaytarishlarni yozmaydi va ular klinika daftarchasidan ayirilmaydi. Qaytarish yig‘indisini ayta olmayman.",
      "Текущий платёжный поток не проводит возвраты, и они не вычитаются из журнала клиники. Итог возвратов я сообщить не могу.",
    ),
    "Appointments do not store a booking channel." to line(
      "Appointments do not store a booking channel.",
      "Uchrashuvlarda band qilish kanali saqlanmaydi.",
      "В записях не хранится канал бронирования.",
    ),
    "I can only report appointments for the doctor who is signed in." to line(
      "I can only report appointments for the doctor who is signed in.",
      "Faqat tizimga kirgan shifokorning uchrashuvlari haqida ayta olaman.",
      "Могу сообщить только о записях врача, который вошёл в систему.",
    ),
    "Collected money is paid video-consultation checkouts plus clinic ledger payments for plans you attend, excluding waived amounts. A gateway treatment-plan payment is copied onto the ledger, so it is counted once. Appointment prices and receipt copies are not added. Currencies stay separate. The period is the payment time, not the visit time." to line(
      "Collected money is paid video-consultation checkouts plus clinic ledger payments for plans you attend, excluding waived amounts. A gateway treatment-plan payment is copied onto the ledger, so it is counted once. Appointment prices and receipt copies are not added. Currencies stay separate. The period is the payment time, not the visit time.",
      "Tushum — to‘langan video maslahatlar va siz qatnashgan rejalar bo‘yicha klinika daftarchasi to‘lovlari, kechirilgan summalarsiz. To‘lov shlyuzi orqali reja to‘lovi daftarchaga ko‘chiriladi va bir marta sanaladi. Uchrashuv narxi va chek nusxalari qo‘shilmaydi. Valyutalar alohida. Davr — to‘lov vaqti, tashrif vaqti emas.",
      "Поступления — оплаченные видеоконсультации плюс платежи журнала клиники по планам, которые вы ведёте, без списанных сумм. Платёж плана через шлюз копируется в журнал и считается один раз. Цены визитов и копии чеков не добавляются. Валюты отдельно. Период — время оплаты, не время визита.",
    ),
    "Appointment value is the price stored on the visit. It is not money collected." to line(
      "Appointment value is the price stored on the visit. It is not money collected.",
      "Uchrashuv qiymati — tashrifda saqlangan narx. Bu tushum emas.",
      "Стоимость записи — цена, сохранённая на визите. Это не поступившие деньги.",
    ),
    "Outstanding here is the price on visits marked pending. It does not include treatment-plan balances." to line(
      "Outstanding here is the price on visits marked pending. It does not include treatment-plan balances.",
      "Bu yerdagi qarzdorlik — kutilayotgan tashriflar narxi. Davolash rejasi balanslari kirmaydi.",
      "Задолженность здесь — цена визитов со статусом ожидания. Балансы планов лечения не входят.",
    ),
    "This uses the visit time, not the time the appointment was cancelled, because that time is not stored." to line(
      "This uses the visit time, not the time the appointment was cancelled, because that time is not stored.",
      "Bu tashrif vaqtidan foydalanadi, bekor qilingan vaqtdan emas, chunki u saqlanmaydi.",
      "Здесь используется время визита, а не время отмены, потому что оно не хранится.",
    ),
    "Counts use the scheduled start of the visit. Cancellation time is not stored." to line(
      "Counts use the scheduled start of the visit. Cancellation time is not stored.",
      "Hisob tashrifning rejalashtirilgan boshlanishiga qarab. Bekor qilish vaqti saqlanmaydi.",
      "Подсчёт идёт по запланированному началу визита. Время отмены не хранится.",
    ),
    "Collected money uses payment time for your practice. I can't combine that with a patient, status, or documentation filter without mixing different records." to line(
      "Collected money uses payment time for your practice. I can't combine that with a patient, status, or documentation filter without mixing different records.",
      "Tushum amaliyotingizdagi to‘lov vaqtiga qarab. Buni bemor, holat yoki hujjat filtri bilan aralashtirmasdan qo‘sha olmayman.",
      "Поступления считаются по времени оплаты вашей практики. Не могу совместить это с фильтром пациента, статуса или документации, не смешав разные записи.",
    ),
    "The cancellation rate already chooses which visits count. Ask for a count if you want one status only." to line(
      "The cancellation rate already chooses which visits count. Ask for a count if you want one status only.",
      "Bekor qilish ulushi qaysi tashriflar kirishini o‘zi tanlaydi. Faqat bitta holat kerak bo‘lsa, sonini so‘rang.",
      "Доля отмен уже сама выбирает, какие визиты входят. Если нужен один статус, попросите количество.",
    ),
    "Upcoming visits are requested, confirmed, or in progress." to line(
      "Upcoming visits are requested, confirmed, or in progress.",
      "Yaqinlashayotgan tashriflar — so‘ralgan, tasdiqlangan yoki jarayonda.",
      "Предстоящие визиты — запрошенные, подтверждённые или идущие.",
    ),
    "I can group appointment counts. I can't group that total." to line(
      "I can group appointment counts. I can't group that total.",
      "Uchrashuv sonlarini guruhlay olaman. Bu yig‘indini guruhlay olmayman.",
      "Могу группировать количество записей. Этот итог сгруппировать не могу.",
    ),
    "Outstanding amounts are visits marked pending. That conflicts with the payment filter." to
        line(
          "Outstanding amounts are visits marked pending. That conflicts with the payment filter.",
          "Qarzdorlik — kutilayotgan tashriflar. Bu to‘lov filtri bilan zid.",
          "Задолженность — визиты в ожидании. Это противоречит фильтру оплаты.",
        ),
    "Pending is not an appointment status. Unconfirmed visits are requested. Pending payment is a payment status." to line(
      "Pending is not an appointment status. Unconfirmed visits are requested. Pending payment is a payment status.",
      "Kutilmoqda uchrashuv holati emas. Tasdiqlanmagan tashriflar so‘ralgan. To‘lov kutilishi — to‘lov holati.",
      "«Ожидание» — не статус записи. Неподтверждённые визиты — запрошенные. Ожидание оплаты — статус платежа.",
    ),
  )

    private fun namedPeriod(lower: String): String =
        line(
            NAMED_EN[lower] ?: lower,
            NAMED_UZ[lower] ?: lower,
            NAMED_RU[lower] ?: lower,
        )

    private fun rollingPeriod(
        direction: String,
        count: String,
        unit: String,
    ): String {
        val next = direction == "next"
        val one = count == "1"
        val uzUnit =
            when (unit) {
                "day", "days" -> "kun"
                "week", "weeks" -> "hafta"
                else -> "oy"
            }
        val ruUnit =
            when (unit) {
                "day", "days" -> if (one) "день" else "дней"
                "week", "weeks" -> if (one) "неделю" else "недель"
                else -> if (one) "месяц" else "месяцев"
            }
        return line(
            "the $direction $count $unit",
            "${if (next) "keyingi" else "oxirgi"} $count $uzUnit",
            "${if (next) "следующие" else "последние"} $count $ruUnit",
        )
    }

    private fun boundedDate(
        kind: String,
        date: String,
    ): String {
        val whenText = localizeEnglishDates(date)
        return line(
            "$kind $date",
            when (kind.lowercase()) {
                "since" -> "$whenText dan beri"
                "before" -> "$whenText dan oldin"
                "after" -> "$whenText dan keyin"
                else -> whenText
            },
            when (kind.lowercase()) {
                "since" -> "с $whenText"
                "before" -> "до $whenText"
                "after" -> "после $whenText"
                else -> whenText
            },
        )
    }

    private fun betweenDates(
        start: String,
        end: String,
    ): String =
        line(
            "from $start until $end",
            "${localizeEnglishDates(start)} dan ${localizeEnglishDates(end)} gacha",
            "с ${localizeEnglishDates(start)} по ${localizeEnglishDates(end)}",
        )

    private fun soonPhrase(raw: String): String {
        Regex("""^in about (\d+) minutes$""").find(raw)?.let {
            return line(raw, "taxminan ${it.groupValues[1]} daqiqadan so‘ng", "примерно через ${it.groupValues[1]} мин.")
        }
        Regex("""^in about (\d+) hours$""").find(raw)?.let {
            return line(raw, "taxminan ${it.groupValues[1]} soatdan so‘ng", "примерно через ${it.groupValues[1]} ч.")
        }
        if (raw == "in about 1 minute") return line(raw, "taxminan 1 daqiqadan so‘ng", "примерно через 1 минуту")
        if (raw == "in about 1 hour") return line(raw, "taxminan 1 soatdan so‘ng", "примерно через 1 час")
        return raw
    }

    private fun andMore(): String = line(", and more", ", va yana", ", и ещё")

    private fun reasonText(raw: String): String =
        if (raw.trim().lowercase() == "not recorded") line("not recorded", "yozilmagan", "не указана") else raw

    private fun openWhere(raw: String): String =
        when (raw) {
            "documentation" -> line("documentation", "hujjatlar", "документацию")
            "patient record" -> line("patient record", "bemor kartasi", "карту пациента")
            else -> line("appointment", "uchrashuv", "запись")
        }

    private fun dimensionLabel(raw: String): String =
        when (raw) {
            "status" -> line("status", "holat", "статусу")
            "patient" -> line("patient", "bemor", "пациенту")
            "day" -> line("day", "kun", "дню")
            "modality" -> line("modality", "turi", "формату")
            else -> raw
        }

    private fun localizeStatusBits(raw: String): String =
        Regex(
            """\b(confirmed|requested|cancelled|canceled|completed|no_show|no-show|in_progress|pending|scheduled)\b""",
            RegexOption.IGNORE_CASE,
        ).replace(raw) { statusLabel(it.groupValues[1].replace("-", "_")) }

    private fun moneyLead(raw: String): String =
        if (raw.startsWith("Pending")) {
            line("Pending amounts", "kutilayotgan summalar", "Ожидаемые суммы")
        } else {
            line("Appointment prices", "uchrashuv narxlari", "Цены записей")
        }

    private fun moneyParts(raw: String): String {
        var text =
            raw.replace(
                "from video consultation checkouts",
                line("from video consultation checkouts", "video maslahat to‘lovlaridan", "из оплат видеоконсультаций"),
            )
        text =
            text.replace(
                "from clinic ledger payments",
                line("from clinic ledger payments", "klinika daftarchasi to‘lovlaridan", "из платежей журнала клиники"),
            )
        return Regex("""across (\d+) visits""").replace(text) {
            line("across ${it.groupValues[1]} visits", "${it.groupValues[1]} ta tashrif bo‘yicha", "по ${it.groupValues[1]} визитам")
        }
    }

    /** Straighten quotes and spaces so server hints match the templates. */
    private fun normalizeToolHint(hint: String): String =
        hint
            .replace('“', '"')
            .replace('”', '"')
            .replace('«', '"')
            .replace('»', '"')
            .replace(' ', ' ')
            .replace(' ', ' ')
            .trim()

    private fun clock(raw: String): String {
        val parts = raw.split(':')
        if (parts.size != 2) return raw
        val hour = parts[0].toIntOrNull() ?: return raw
        return "${hour.toString().padStart(2, '0')}:${parts[1]}"
    }

    private fun speakableDay(
        weekday: String,
        day: String,
        month: String,
    ): String {
        val dayName = t(weekday.lowercase())
        var monthName = t(fullMonthKey(month))
        if (lang == "uz") monthName = monthName.lowercase()
        if (lang == "uz") return "$day-$monthName, $dayName"
        if (lang == "ru") return "$dayName, $day $monthName"
        return "$weekday, $day $month"
    }

    private fun dayPart(raw: String?): String =
        when (raw?.trim()) {
            "morning" -> line(" morning", " ertalab", " утром")
            "afternoon" -> line(" afternoon", " tushdan keyin", " днём")
            "evening" -> line(" evening", " kechqurun", " вечером")
            else -> ""
        }

    private fun localizeEmbeddedCalendar(raw: String): String {
        var text =
            Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""").replace(raw) { m ->
                val date =
                    runCatching { LocalDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
                        ?: return@replace m.value
                speakableDay(WEEKDAYS[date.dayOfWeek.ordinal], "${date.dayOfMonth}", MONTHS[date.monthNumber - 1])
            }
        text =
            Regex("""\bfrom (\d{1,2} [A-Za-z]+ \d{4}) until (\d{1,2} [A-Za-z]+ \d{4})\b""").replace(text) {
                betweenDates(it.groupValues[1], it.groupValues[2])
            }
        text = Regex("""\b(on|since|before|after) (\d{1,2} [A-Za-z]+ \d{4})\b""").replace(text) { boundedDate(it.groupValues[1], it.groupValues[2]) }
        text =
            Regex(
                """\b(Friday of next week|from tomorrow onwards|the upcoming year|coming up|this quarter|last quarter|next quarter|this month|last month|next month|this year|last year|next year|last week|this week|next week|today|tomorrow|yesterday)\b""",
                RegexOption.IGNORE_CASE,
            ).replace(text) { rangeLabel(it.groupValues[1]) }
        text =
            Regex(
                """\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s*(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December)\b""",
            ).replace(text) { speakableDay(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        text = localizeVisitList(text)
        text =
            Regex("""\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b at (\d{2}:\d{2})""").replace(text) {
                val day = t(it.groupValues[1].lowercase())
                line("${it.groupValues[1]} at ${it.groupValues[2]}", "$day soat ${it.groupValues[2]}", "$day ${it.groupValues[2]}")
            }
        text = Regex("""\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""").replace(text) { t(it.groupValues[1].lowercase()) }
        text = localizeEnglishDates(text)
        text = text.replace(", and more", andMore())
        text =
            Regex("""\bthe (next|last) (\d+) (day|days|week|weeks|month|months)\b""").replace(text) {
                rollingPeriod(it.groupValues[1], it.groupValues[2], it.groupValues[3])
            }
        return text
    }

    private fun localizeVisitList(names: String): String {
        val withMore = Regex(""", and (\d+) more\b""").replace(names) { t("intelCancelAndMore").replace("{count}", it.groupValues[1]) }
        return WHEN_PATTERN.replace(withMore) { m ->
            val weekday = t(m.groupValues[1].lowercase())
            var month = t(fullMonthKey(m.groupValues[3]))
            if (lang == "uz") month = month.lowercase()
            t("intelWhenAt")
                .replace("{weekday}", weekday)
                .replace("{day}", m.groupValues[2])
                .replace("{month}", month)
                .replace("{time}", m.groupValues[4])
        }
    }

    private fun localizeEnglishDates(text: String): String {
        val full =
            Regex("""\b(January|February|March|April|May|June|July|August|September|October|November|December)\b""").replace(text) {
                t(fullMonthKey(it.groupValues[1]))
            }
        return Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\b""").replace(full) { m ->
            SHORT_MONTHS[m.groupValues[1].lowercase()]?.let { monthShort(it) } ?: m.value
        }
    }

    private fun parseIsoDate(raw: String?): LocalDate? {
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(raw?.trim() ?: return null) ?: return null
        return runCatching { LocalDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }

    private fun fullMonthKey(english: String): String {
        val lower = english.lowercase()
        return "month${lower[0].uppercase()}${lower.substring(1)}"
    }

    companion object {
        /** "6 Nov 2026" / "6 November 2026" (the backend's English date labels). */
        fun parseEnglishDate(raw: String?): LocalDate? {
            val m = Regex("""^(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})$""").find(raw?.trim() ?: return null) ?: return null
            val token = m.groupValues[2].lowercase()
            val month = FULL_MONTHS[token] ?: SHORT_MONTHS[token] ?: return null
            return runCatching { LocalDate(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
        }

        /** Every string key this class looks up; the screen resolves them in the app's language. */
        val KEYS: List<String> =
            listOf(
                "appointmentStatusCancelled", "appointmentStatusCompleted", "appointmentStatusConfirmed",
                "appointmentStatusInProgress", "appointmentStatusNoShow", "appointmentStatusRequested", "intelCancelSkipped",
                "intelDocument", "intelPatient", "intelRangeNextWeek",
                "intelRangeThisWeek", "intelTomorrow", "pending", "scheduled", "today", "upcomingAppointments", "yesterday",
                "intelRangeUpcomingFrom", "intelCancelTooMany", "intelCancelAndMore", "intelWhenAt",
                "intelCancelAskWhich", "intelCancelNothing", "intelCancelNoneEligible", "intelCancelNotWaiting",
                "intelCancelNotFound", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            ) + listOf(
                "January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
                "November", "December",
            ).map { "month$it" }

        /**
         * The placeholders of each templated key, in the order the generated resources number them (`%1$s`…: the
         * English order). The screen resolves these keys with the names as arguments, so the text comes back with
         * `{name}` placeholders again.
         */
        val PLACEHOLDERS: Map<String, List<String>> =
            mapOf(
                "intelCancelSkipped" to listOf("count"),
                "intelRangeUpcomingFrom" to listOf("date"),
                "intelCancelTooMany" to listOf("count"),
                "intelCancelAndMore" to listOf("count"),
                "intelWhenAt" to listOf("weekday", "day", "month", "time"),
            )

        /** Plural resources this class reads, with their placeholders. */
        val PLURAL_KEYS: Map<String, List<String>> = mapOf("intelCancelPreview" to listOf("count", "names"))

        /**
         * A count stood in for by 1, 2 or 5, which the screen resolves each plural with: the same CLDR form as [count]
         * in [lang] (ru: one / few / many; en and uz: one / other).
         */
        fun pluralSample(
            lang: String,
            count: Int,
        ): Int {
            if (lang != "ru") return if (count == 1) 1 else 2
            val ten = count % 10
            val hundred = count % 100
            return when {
                ten == 1 && hundred != 11 -> 1
                ten in 2..4 && hundred !in 12..14 -> 2
                else -> 5
            }
        }

        private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val MONTHS =
            listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

        private const val RANGE_TAIL =
            """Friday of next week|from tomorrow onwards|the upcoming year|coming up|from .+ onwards|the next \d+ (?:day|days|week|weeks|month|months)|the last \d+ (?:day|days|week|weeks|month|months)|on \d{1,2} [A-Za-z]+ \d{4}|since \d{1,2} [A-Za-z]+ \d{4}|before \d{1,2} [A-Za-z]+ \d{4}|after \d{1,2} [A-Za-z]+ \d{4}|from \d{1,2} [A-Za-z]+ \d{4} until \d{1,2} [A-Za-z]+ \d{4}|this quarter|last quarter|next quarter|this month|last month|next month|this year|last year|next year|last week|this week|next week|today|tomorrow|yesterday|[A-Za-z]+, \d{1,2} [A-Za-z]+(?: \d{4})?"""

        private val WHEN_PATTERN =
            Regex(
                """\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday) (\d{1,2}) (January|February|March|April|May|June|July|August|September|October|November|December) at (\d{2}:\d{2})\b""",
            )

        private val SHORT_MONTHS =
            mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
        private val FULL_MONTHS =
            mapOf(
                "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7, "august" to 8,
                "september" to 9, "october" to 10, "november" to 11, "december" to 12,
            )

        private val NAMED_EN =
            mapOf(
                "last week" to "last week", "this month" to "this month", "last month" to "last month", "next month" to "next month",
                "this quarter" to "this quarter", "last quarter" to "last quarter", "next quarter" to "next quarter",
                "this year" to "this year", "last year" to "last year", "next year" to "next year", "coming up" to "coming up",
                "from tomorrow onwards" to "from tomorrow onwards", "friday of next week" to "Friday of next week",
                "the upcoming year" to "the upcoming year",
            )
        private val NAMED_UZ =
            mapOf(
                "last week" to "o‘tgan hafta", "this month" to "shu oy", "last month" to "o‘tgan oy", "next month" to "keyingi oy",
                "this quarter" to "shu chorak", "last quarter" to "o‘tgan chorak", "next quarter" to "keyingi chorak",
                "this year" to "shu yil", "last year" to "o‘tgan yil", "next year" to "keyingi yil", "coming up" to "yaqinlashayotgan",
                "from tomorrow onwards" to "ertadan boshlab", "friday of next week" to "keyingi haftaning juma kuni",
                "the upcoming year" to "kelasi yil",
            )
        private val NAMED_RU =
            mapOf(
                "last week" to "на прошлой неделе", "this month" to "в этом месяце", "last month" to "в прошлом месяце",
                "next month" to "в следующем месяце", "this quarter" to "в этом квартале", "last quarter" to "в прошлом квартале",
                "next quarter" to "в следующем квартале", "this year" to "в этом году", "last year" to "в прошлом году",
                "next year" to "в следующем году", "coming up" to "предстоящие", "from tomorrow onwards" to "с завтрашнего дня",
                "friday of next week" to "пятница следующей недели", "the upcoming year" to "предстоящий год",
            )
    }
}
