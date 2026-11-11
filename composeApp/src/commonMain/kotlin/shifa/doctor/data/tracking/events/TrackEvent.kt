package shifa.doctor.data.tracking.events

import shifa.doctor.data.tracking.trackProperties

/**
 * The base of every event, and with the group files beside it the whole catalogue: nothing else is ever sent. Each
 * group owns one file in this package (`AuthEvents.kt`, `CalendarEvents.kt`, …) holding an `object` with one
 * `data class` (with properties) or `data object` (without) per event. The package is shared because a sealed class
 * takes subclasses only from its own package.
 *
 * Names are `snake_case`, `object_action`, past tense for outcomes. Constructor parameters are `String`, `Int`,
 * `Long`, `Double`, `Boolean`, an enum (sent as its `trackName()`) or a `List` of those, nullable where unknown (a
 * null is left out). Values are our own fixed words, counts and ranges: no names, phone numbers, e-mail addresses,
 * free text, titles, tokens, ids (D1) or exact money (D2). `TrackEventCatalogueTest` walks every subclass.
 */
sealed class TrackEvent(
    val name: String,
    vararg props: Pair<String, Any?>,
    personProperties: Map<String, Any?> = emptyMap(),
    personPropertiesSetOnce: Map<String, Any?> = emptyMap(),
) {
    val properties: Map<String, Any> = trackProperties(props.toList())

    /** Person properties set with the event: usage facts such as `has_booked = true`. */
    val personProperties: Map<String, Any> = trackProperties(personProperties.toList())

    /** Person properties set only the first time. */
    val personPropertiesSetOnce: Map<String, Any> = trackProperties(personPropertiesSetOnce.toList())

    /** For the debug menu's list. (Not toString: data objects generate their own.) */
    val label: String get() = if (properties.isEmpty()) name else "$name $properties"
}
