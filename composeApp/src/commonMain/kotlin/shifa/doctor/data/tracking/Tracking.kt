package shifa.doctor.data.tracking

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.storage.tokenStorage
import shifa.doctor.data.tracking.events.TrackEvent
import shifa.doctor.utils.isDebugBuild

/** For ViewModels: constructor-injected with [Tracking] as the default, a recording fake in tests. */
interface Tracker {
    fun track(event: TrackEvent)

    /** A screen view. [properties] are fixed values the screen knows (a mode, a step), never anything from a route. */
    fun screen(
        screen: TrackScreen,
        properties: Map<String, Any?> = emptyMap(),
    )
}

/** Who is identified: the distinct id is `doctor_<userId>` or `staff_<userId>` (00-plan.md §2.3). Admins never are. */
enum class TrackingRole(
    val prefix: String,
) {
    Doctor("doctor"),
    Staff("staff"),
}

/**
 * The app's one way into product analytics (docs/posthog). Without a sink it sends nothing and reads nothing (a debug
 * build only keeps [recent] in memory): that is the state in tests, previews and builds without a key, so
 * `tracker: Tracker = Tracking` is safe as a default everywhere. With one, every call first applies the on/off rule ([trackingStatus]) to the app's state as it
 * is then, so nothing can be sent in a debug build, in demo mode, on the development backend, on a web site other
 * than production or after an opt-out, whoever forgot to call [refresh].
 *
 * Calls come from the main thread (ViewModels, composition, app start) and, through `apiCall`'s `api_error` /
 * `api_slow`, from whatever dispatcher a repository runs on (`PushRegistration` uses `Dispatchers.Default`): every
 * entry point holds the object's lock (reentrant; nested calls such as `track` → `refresh` are fine).
 */
object Tracking : SynchronizedObject(), Tracker {
    private var sink: TrackingSink? = null
    private var webHost: String? = null
    private var optOut = false

    // What the SDK was last told: null before the first evaluation.
    private var applied: Boolean? = null
    private val superProperties = mutableMapOf<String, Any>()
    private val _status = MutableStateFlow(TrackingStatus(TrackingOffReason.NoSink))

    /** On or off and why, as of the last call or [refresh]. For the debug menu, and to identify again once back on. */
    val status: StateFlow<TrackingStatus> get() = _status

    private val _recent = MutableStateFlow(emptyList<String>())

    /**
     * Debug builds only (always empty in a release build): the last [RECENT_MAX] events and screen views, newest first,
     * for the debug menu. Recorded before the on/off rule and without a sink, since a debug build never sends any.
     */
    val recent: StateFlow<List<String>> get() = _recent

    private fun recordRecent(line: String) {
        if (isDebugBuild) _recent.value = (listOf(line) + _recent.value).take(RECENT_MAX)
    }

    // Seams for tests; the real ones need a platform `Settings`.
    internal var environment: () -> TrackingEnvironment = ::appTrackingEnvironment
    internal var settings = TrackingSettings()
    internal var language: () -> String = { languageStorage.language.value.tag }
    internal var hasSavedSession: () -> Boolean = { runCatching { tokenStorage.token }.getOrNull() != null }

    /**
     * The platform's startup, right after its SDK is set up. The SDK is told on or off before anything else, so a
     * launch that must not track never opts it in for a moment; then the super properties `app` and `app_language`
     * are registered, before the SDK's first lifecycle event. [webHost] is the page's hostname (web only).
     */
    fun start(
        sink: TrackingSink,
        webHost: String? = null,
    ) {
        synchronized(this) {
            this.sink = sink
            this.webHost = webHost
            optOut = settings.optedOut
            applied = null
            superProperties["app"] = TRACKING_APP
            superProperties["app_language"] = language()
            refresh()
            sink.register(superProperties.toMap())
            // An identity held with no session saved: the process died between a sign-out and its reset. It goes now,
            // before the SDK's first lifecycle event could be sent under the previous doctor's id.
            if (settings.identified != null && !hasSavedSession()) reset()
        }
    }

    /**
     * Applies the on/off rule now. Every call below does it too; call it when demo mode or the backend changes so
     * the switch happens at once. Turning off opts the SDK out and resets it: the identity and the queue go, so
     * nothing collected before is sent later or joins the next user.
     */
    fun refresh(): TrackingStatus {
        synchronized(this) {
            val sink = sink ?: return _status.value
            val status = trackingStatus(environment(), optOut, webHost)
            if (status.on != applied) {
                sink.optOut(!status.on)
                if (applied == true) reset(dropQueue = true)
                applied = status.on
            }
            _status.value = status
            return status
        }
    }

    private fun active(): TrackingSink? = sink?.takeIf { refresh().on }

    override fun track(event: TrackEvent) {
        synchronized(this) {
            recordRecent(event.label)
            active()?.capture(event.name, event.properties, event.personProperties, event.personPropertiesSetOnce)
        }
    }

    override fun screen(
        screen: TrackScreen,
        properties: Map<String, Any?>,
    ) {
        synchronized(this) {
            val props = trackProperties(properties.toList())
            recordRecent(if (props.isEmpty()) "screen ${screen.id}" else "screen ${screen.id} $props")
            active()?.screen(screen.id, props)
        }
    }

    /**
     * After sign-in or a restored session, and again whenever the person properties change. [userId] is the JWT
     * subject. [personProperties] are usage facts only (role, tier, time zone, language, `has_clinic`), never a name,
     * phone or e-mail address.
     *
     * The SDKs ignore an identify with a new id once identified, so a different user still held is reset first.
     */
    fun identify(
        role: TrackingRole,
        userId: String,
        personProperties: Map<String, Any?> = emptyMap(),
        personPropertiesSetOnce: Map<String, Any?> = emptyMap(),
    ) {
        synchronized(this) {
            val id = userId.trim().ifEmpty { return }
            val sink = active() ?: return
            val distinctId = "${role.prefix}_$id"
            val held = settings.identified
            if (held != null && held != distinctId) reset()
            sink.identify(
                distinctId,
                trackProperties(listOf("app" to TRACKING_APP) + personProperties.toList()),
                trackProperties(personPropertiesSetOnce.toList()),
            )
            settings.identified = distinctId
        }
    }

    /**
     * Every session end. Reaches the SDK even while tracking is off: the identity must go, or it would be left for
     * the next user. The SDK's reset drops the super properties too, so they are put back.
     */
    fun reset(dropQueue: Boolean = false) {
        synchronized(this) {
            val sink = sink ?: return
            sink.reset(dropQueue)
            settings.identified = null
            sink.register(superProperties.toMap())
        }
    }

    /** A super property on every event. Registered even while off, so it is there once tracking is back on. */
    fun register(
        key: String,
        value: Any,
    ) {
        synchronized(this) {
            superProperties[key] = value
            sink?.register(mapOf(key to value))
        }
    }

    /** Whether the doctor switched tracking off (Settings, D3). Reads the device's storage. */
    val optedOut: Boolean get() = settings.optedOut

    /** Settings → the usage-data switch. Saved on the device; off resets the identity, on needs a new [identify]. */
    fun setOptOut(out: Boolean) {
        synchronized(this) {
            settings.optedOut = out
            optOut = out
            refresh()
        }
    }

    fun flush() {
        synchronized(this) {
            active()?.flush()
        }
    }

    /** Tests only: the object is a singleton, so each test starts from nothing. */
    internal fun clearForTests() {
        sink = null
        webHost = null
        optOut = false
        applied = null
        superProperties.clear()
        _status.value = TrackingStatus(TrackingOffReason.NoSink)
        _recent.value = emptyList()
        environment = ::appTrackingEnvironment
        settings = TrackingSettings()
        language = { languageStorage.language.value.tag }
        hasSavedSession = { runCatching { tokenStorage.token }.getOrNull() != null }
    }
}

private const val RECENT_MAX = 50
