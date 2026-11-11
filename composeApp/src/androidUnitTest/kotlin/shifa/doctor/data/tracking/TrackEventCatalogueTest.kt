package shifa.doctor.data.tracking

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import shifa.doctor.data.tracking.events.ApiEvents
import shifa.doctor.data.tracking.events.SessionTrackEvents
import shifa.doctor.data.tracking.events.ShellEvents
import shifa.doctor.data.tracking.events.TrackEvent
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.jar.JarFile

/**
 * The whole catalogue, found by reflection: every [TrackEvent] subclass in the events package is built with sample
 * values and checked, so the usual privacy slips fail before review and no list has to be kept by hand. An event
 * whose constructor takes anything but the allowed types fails here too.
 */
class TrackEventCatalogueTest {
    private val catalogue: List<TrackEvent> by lazy { eventClasses().map(::sample) }

    @Test
    fun `the walk finds the events`() {
        assertThat(catalogue.map { it::class.java })
            .containsAtLeast(
                ApiEvents.ApiError::class.java,
                SessionTrackEvents.SessionStarted::class.java,
                SessionTrackEvents.SessionEnded::class.java,
                ShellEvents.TabSelected::class.java,
                ShellEvents.MoreOpened::class.java,
                ShellEvents.PushOpened::class.java,
            )
    }

    @Test
    fun `event names are snake_case and unique`() {
        catalogue.forEach { assertWithMessage(it::class.java.name).that(it.name).matches(SNAKE) }
        assertThat(catalogue.map { it.name }).containsNoDuplicates()
    }

    @Test
    fun `no event carries personal data, an id or an exact amount`() {
        catalogue.forEach { event ->
            val all = event.properties + event.personProperties + event.personPropertiesSetOnce
            // The one exception to the blocked key `code`: `api_error.code` is the backend's error code
            // (`APPOINTMENT_LOCKED_24H`, shape-checked in apiCall), not a one-time code or PIN, and keeps the patient
            // app's name so the shared alert chart reads both apps.
            val checked = if (event is ApiEvents.ApiError) all - "code" else all
            assertWithMessage("${event::class.java.name} (${event.name})").that(propertyProblems(checked)).isEmpty()
        }
    }

    @Test
    fun `screen ids are snake_case and unique`() {
        TrackScreen.entries.forEach { assertThat(it.id).matches(SNAKE) }
        assertThat(TrackScreen.entries.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `the check itself catches each kind of slip`() {
        fun problems(vararg pairs: Pair<String, Any>) = propertyProblems(mapOf(*pairs))

        assertThat(problems("kind" to "pdf", "page_count" to 3, "has_email" to true, "query_length" to 4)).isEmpty()
        assertThat(problems("reason" to "network", "amount_bucket" to "<100k", "tags" to listOf("a"))).isEmpty()
        listOf("name", "email", "phone", "text", "note", "message", "title", "token", "password", "query", "prompt")
            .forEach {
                assertThat(problems(it to "x")).hasSize(1)
                assertThat(problems("patient_$it" to "x")).hasSize(1)
            }
        assertThat(problems("patient_id" to 1)).hasSize(1)
        assertThat(problems("id" to 1)).hasSize(1)
        assertThat(problems("patientId" to 1)).isNotEmpty()
        assertThat(problems("Kind" to "x")).hasSize(1)
        assertThat(problems("amount" to 5_000)).hasSize(1)
        assertThat(problems("price" to "<100k")).hasSize(1)
        assertThat(problems("paid_sum_bucket" to 3)).hasSize(1)
        assertThat(problems("when" to java.util.Date())).hasSize(1)
        assertThat(problems("list" to listOf(mapOf("a" to 1)))).hasSize(1)
    }

    private fun eventClasses(): List<Class<out TrackEvent>> {
        val base = TrackEvent::class.java
        val folder = base.`package`!!.name.replace('.', '/')
        val root = File(base.protectionDomain!!.codeSource.location.toURI())
        val files =
            if (root.isDirectory) {
                File(root, folder).walkTopDown().map { it.relativeTo(root).invariantSeparatorsPath }.toList()
            } else {
                JarFile(root).use { jar -> jar.entries().toList().map { it.name }.filter { it.startsWith("$folder/") } }
            }
        return files
            .filter { it.endsWith(".class") }
            .map { Class.forName(it.removeSuffix(".class").replace('/', '.')) }
            .filter { base.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
            .map { it.asSubclass(base) }
    }

    /** A `data object` is its own instance; a `data class` is built from a sample value per parameter. */
    private fun sample(type: Class<out TrackEvent>): TrackEvent {
        type.declaredFields.firstOrNull { it.name == "INSTANCE" }?.let { return it.get(null) as TrackEvent }
        val constructor = type.declaredConstructors.filterNot { it.isSynthetic }.maxBy { it.parameterCount }
        constructor.isAccessible = true
        return constructor.newInstance(*constructor.genericParameterTypes.map { sampleValue(it, type) }.toTypedArray())
            as TrackEvent
    }

    private fun sampleValue(
        type: Type,
        owner: Class<*>,
    ): Any {
        if (type is ParameterizedType && type.rawType == List::class.java) {
            val item = type.actualTypeArguments.single()
            return listOf(sampleValue(item, owner))
        }
        if (type is Class<*> && type.isEnum) return type.enumConstants!!.first()
        return when (type) {
            String::class.java -> "x"
            Int::class.java, Int::class.javaObjectType -> 1
            Long::class.java, Long::class.javaObjectType -> 1L
            Double::class.java, Double::class.javaObjectType -> 1.0
            Boolean::class.java, Boolean::class.javaObjectType -> true
            else -> throw AssertionError(
                "${owner.name}: a parameter of type $type; events take String, Int, Long, Double, Boolean, " +
                    "an enum or a List of those",
            )
        }
    }

    private companion object {
        const val SNAKE = "[a-z][a-z0-9]*(_[a-z0-9]+)*"

        // Identity, free text and secrets: blocked as the whole key or as its last word (`patient_name`), unless the
        // value is a Boolean (`has_email` says a fact, not the address). A length or count has its own last word.
        val BLOCKED =
            setOf(
                "name", "email", "phone", "text", "note", "notes", "comment", "message", "title", "token", "password",
                "query", "prompt", "value", "answer", "url", "address", "birth_date", "diagnosis", "code", "pin",
            )

        // D2: money only as a range, a String under a key ending in `_bucket`.
        val MONEY = Regex("(^|_)(amount|price|sum|cost|fee|revenue|debt|balance|discount|salary|paid)(_|$)")

        fun allowed(value: Any): Boolean =
            when (value) {
                is String, is Int, is Long, is Double, is Boolean -> true
                is List<*> -> value.all { it is String || it is Int || it is Long || it is Double || it is Boolean }
                else -> false
            }

        fun propertyProblems(properties: Map<String, Any>): List<String> =
            buildList {
                properties.forEach { (key, value) ->
                    if (!key.matches(Regex(SNAKE))) add("$key: not snake_case")
                    if (key == "id" || key.endsWith("_id") || key.endsWith("Id")) add("$key: an id (D1)")
                    if (value !is Boolean && BLOCKED.any { key == it || key.endsWith("_$it") }) {
                        add("$key: personal data, free text or a secret")
                    }
                    if (MONEY.containsMatchIn(key) && !(key.endsWith("_bucket") && value is String)) {
                        add("$key: money must be a range (amountBucket) under a key ending in _bucket (D2)")
                    }
                    if (!allowed(value)) add("$key: a ${value::class.simpleName} is not a property value")
                }
            }
    }
}
