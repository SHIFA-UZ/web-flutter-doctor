package shifa.doctor.data.tracking

import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.debug.DemoMode
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.platformName

/** Why nothing is sent, most basic cause first. [id] is for the debug menu's row. */
enum class TrackingOffReason(
    val id: String,
) {
    /** No platform SDK was attached: tests, previews, a build without a key. */
    NoSink("no_sink"),
    DebugBuild("debug_build"),
    DemoMode("demo_mode"),
    DevelopmentBackend("development_backend"),

    /** The web app opened anywhere but [PRODUCTION_WEB_HOST]. */
    NotProductionSite("not_production_site"),
    OptedOut("opted_out"),
}

/** Whether this build tracks right now, and why not ([reason] is null when [on]). */
data class TrackingStatus(
    val reason: TrackingOffReason?,
) {
    val on: Boolean get() = reason == null
}

/** What the on/off rule reads from the app. [Tracking.environment] supplies it; tests set their own. */
data class TrackingEnvironment(
    val releaseBuild: Boolean,
    val demo: Boolean,
    val productionBackend: Boolean,
    val web: Boolean,
)

/**
 * The one on/off rule (docs/posthog/00-plan.md §3, decided): a release build, demo data off, the production backend,
 * the doctor not opted out, and on the web the production site ([webHost] is the page's hostname; unknown counts as
 * not production).
 */
fun trackingStatus(
    environment: TrackingEnvironment,
    optedOut: Boolean,
    webHost: String?,
): TrackingStatus =
    TrackingStatus(
        when {
            !environment.releaseBuild -> TrackingOffReason.DebugBuild
            environment.demo -> TrackingOffReason.DemoMode
            !environment.productionBackend -> TrackingOffReason.DevelopmentBackend
            environment.web && webHost != PRODUCTION_WEB_HOST -> TrackingOffReason.NotProductionSite
            optedOut -> TrackingOffReason.OptedOut
            else -> null
        },
    )

/** The app's real state. Read only once a sink is attached: `DemoMode` and `BackendChoice` need a platform `Settings`. */
internal fun appTrackingEnvironment() =
    TrackingEnvironment(
        releaseBuild = !isDebugBuild,
        demo = DemoMode.enabled.value,
        productionBackend = BackendChoice.current.value == BackendChoice.Backend.Production,
        web = platformName == "web",
    )
