package shifa.doctor.data.tracking

import android.app.Application
import com.posthog.PostHogEvent
import com.posthog.PostHogInterface
import com.posthog.PostHogStateless
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.internal.PostHogQueueInterface
import shifa.doctor.BuildConfig
import java.lang.reflect.Field

/**
 * docs/posthog (A2). Release builds with a key only: a debug build (its key is hard-wired empty) or a build without
 * the secret never sets up the SDK, so [Tracking] keeps no sink and sends nothing.
 */
fun startTracking(app: Application) {
    val key = BuildConfig.POSTHOG_API_KEY
    if (BuildConfig.DEBUG || key.isBlank()) return
    // Set up in the state the rule gives now: a launch that must not track is never opted in for a moment, and one
    // that may keeps the SDK's `Application Installed` / `Updated` (set up opted out, it records the version and
    // never sends them). Tracking.start then finds the SDK already in that state.
    val on = trackingStatus(Tracking.environment(), Tracking.optedOut, webHost = null).on
    val config =
        PostHogAndroidConfig(projectToken = key, host = POSTHOG_HOST).apply {
            captureApplicationLifecycleEvents = true // Application Installed / Updated / Opened / Backgrounded
            captureScreenViews = false // one Activity; screens are sent by hand
            captureDeepLinks = false
            captureElementInteractions = false
            capturePushNotificationSubscriptions = false
            capturePushNotificationOpened = false
            sessionReplay = false
            surveys = false
            preloadFeatureFlags = false // the app reads no flags
            errorTrackingConfig.autoCapture = true
            persistOptOut = false // TrackingSettings owns the opt-out; the SDK's stored copy must not outrank it
            optOut = !on
            // 00-plan.md §4 rule 1: `app` on every event, also the SDK's install event sent inside setup, before
            // Tracking.start can register it.
            addBeforeSend(::withApp)
        }
    Tracking.start(PostHogSink(PostHogAndroid.with(app, config)))
}

/**
 * `app` on every event, and an exception's messages dropped: a Ktor or serialization message can hold a URL with ids,
 * JSON input or the backend's text. Each `$exception_list` entry keeps its type, module, mechanism and stack frames.
 */
internal fun withApp(event: PostHogEvent): PostHogEvent {
    val props = event.properties.orEmpty().toMutableMap()
    props["app"] = TRACKING_APP
    props -= EXCEPTION_TEXT
    (props[EXCEPTION_LIST] as? List<*>)?.let { list ->
        props[EXCEPTION_LIST] = list.map { entry -> (entry as? Map<*, *>)?.filterKeys { it != "value" } ?: entry }
    }
    return event.copy(properties = props)
}

private const val EXCEPTION_LIST = "\$exception_list"

// posthog-android 3.75.1 sends neither (steps only from `addExceptionStep`, which the app never calls); dropped in case.
private val EXCEPTION_TEXT = setOf("\$exception_message", "\$exception_steps")

// ponytail: posthog-android has no public way to drop its queue (reset keeps it, close sends it), so the private
// `queue` field is read by reflection; PostHogSinkTest fails if an SDK update renames it.
internal val postHogQueueField: Field? =
    runCatching { PostHogStateless::class.java.getDeclaredField("queue").apply { isAccessible = true } }.getOrNull()

internal class PostHogSink(
    private val posthog: PostHogInterface,
) : TrackingSink {
    override fun capture(
        event: String,
        properties: Map<String, Any>,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    ) = posthog.capture(
        event = event,
        properties = properties,
        userProperties = personProperties.ifEmpty { null },
        userPropertiesSetOnce = personPropertiesSetOnce.ifEmpty { null },
    )

    override fun screen(
        id: String,
        properties: Map<String, Any>,
    ) = posthog.screen(id, properties)

    override fun identify(
        distinctId: String,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    ) = posthog.identify(distinctId, personProperties, personPropertiesSetOnce.ifEmpty { null })

    override fun reset(dropQueue: Boolean) {
        if (dropQueue) (postHogQueueField?.get(posthog) as? PostHogQueueInterface<*>)?.clear()
        posthog.reset()
    }

    override fun register(superProperties: Map<String, Any>) = superProperties.forEach(posthog::register)

    override fun optOut(out: Boolean) = if (out) posthog.optOut() else posthog.optIn()

    override fun flush() = posthog.flush()
}
