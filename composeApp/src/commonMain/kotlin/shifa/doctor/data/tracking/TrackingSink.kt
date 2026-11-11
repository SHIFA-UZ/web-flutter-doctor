package shifa.doctor.data.tracking

/** PostHog Cloud EU's ingestion host (docs/posthog/00-plan.md §1). Not a secret; the project key is. */
const val POSTHOG_HOST = "https://eu.i.posthog.com"

/** On every event and person: tells this app's data from the patient app's in the shared project. */
const val TRACKING_APP = "doctor"

/** The only web site that reports (00-plan.md §3): `doctor-dev.shifa.uz` and every other host never do. */
const val PRODUCTION_WEB_HOST = "doctor.shifa.uz"

/**
 * What a platform SDK does (Android: posthog-android, iOS: a Swift class over posthog-ios, web: posthog-js). The app
 * never calls a sink directly: [Tracking] owns it and decides whether anything is sent.
 *
 * Values are `String`, `Int`, `Long`, `Double`, `Boolean` or a `List` of those, never nested maps (iOS must turn every
 * `KotlinBoolean` into a Swift `Bool`, also inside lists, or it arrives as 1/0).
 */
interface TrackingSink {
    fun capture(
        event: String,
        properties: Map<String, Any>,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    )

    fun screen(
        id: String,
        properties: Map<String, Any>,
    )

    fun identify(
        distinctId: String,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    )

    /**
     * Forgets the identity. The SDK drops its super properties too; [Tracking] puts them back. [dropQueue] when tracking
     * turns off (nothing collected before may be sent later); a session end keeps the queue, so its `session_ended`,
     * captured under the old identity, still goes out.
     */
    fun reset(dropQueue: Boolean)

    /** Super properties: on every later event, the SDK's own (lifecycle, exceptions) included. */
    fun register(superProperties: Map<String, Any>)

    /** The SDK's own switch: opted out it sends nothing, its automatic lifecycle and exception events included. */
    fun optOut(out: Boolean)

    fun flush()
}
