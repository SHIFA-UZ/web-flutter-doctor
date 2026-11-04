package shifa.doctor.data.storage

import androidx.compose.ui.text.intl.Locale
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone

/**
 * The four UI languages (01 §8). [tag] is what is stored, [resourceCode] the resource folder (Cyrillic reads the
 * `uz` strings and transliterates them), [backendCode] what `PUT /doctors/me/language` takes. Names are not
 * translated.
 */
enum class AppLanguage(
    val tag: String,
    val resourceCode: String,
    val displayName: String,
    val cyrillic: Boolean = false,
) {
    English("en", "en", "English"),
    Uzbek("uz", "uz", "O'zbekcha"),
    UzbekCyrillic("uz-Cyrl", "uz", "Ўзбекча", cyrillic = true),
    Russian("ru", "ru", "Русский"),
    ;

    val backendCode: String get() = resourceCode

    companion object {
        /** Null for an unknown tag, so a stray value never forces a language. */
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag.equals(tag?.trim(), ignoreCase = true) }
    }
}

/**
 * The app language (CORE-04, 01 §4 and §8, LANG-02). A saved language always wins, whether the doctor chose it
 * ([set]) or a phone number set it ([applyRegionalDefault]): LANG-02's Fix drops Flutter's cold-start override of a
 * saved non-explicit value. With nothing saved, Uzbekistan (device language `uz`, region `UZ` or time zone
 * Asia/Tashkent) means Uzbek, else English. Never cleared on logout.
 */
class LanguageStorage(
    private val settings: Settings = Settings(),
    systemLanguage: String = Locale.current.language,
    systemRegion: String = Locale.current.region,
    timeZoneId: String = TimeZone.currentSystemDefault().id,
) {
    private val _language =
        MutableStateFlow(
            AppLanguage.fromTag(settings.getStringOrNull(KEY))
                ?: AppLanguage.Uzbek.takeIf {
                    systemLanguage == "uz" || systemRegion == "UZ" || timeZoneId == "Asia/Tashkent"
                }
                ?: AppLanguage.English,
        )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun set(language: AppLanguage) {
        settings[KEY] = language.tag
        settings[KEY_EXPLICIT] = true
        _language.value = language
    }

    /** Login and phone screens (`applyRegionalDefaultIfUnset`): a +998 number means Uzbek while nothing is saved. */
    fun applyRegionalDefault(phoneNumber: String?) {
        if (settings.hasKey(KEY) || phoneNumber?.filter(Char::isDigit)?.startsWith("998") != true) return
        settings[KEY] = AppLanguage.Uzbek.tag
        _language.value = AppLanguage.Uzbek
    }

    private companion object {
        const val KEY = "doctor_language"
        const val KEY_EXPLICIT = "doctor_language_explicit"
    }
}

val languageStorage by lazy { LanguageStorage() }
