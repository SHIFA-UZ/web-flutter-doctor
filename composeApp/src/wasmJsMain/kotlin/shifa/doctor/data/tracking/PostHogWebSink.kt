package shifa.doctor.data.tracking

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// posthog-js from npm, bundled by webpack with the app (build.gradle.kts): no CDN script, nothing in index.html.
@JsModule("posthog-js")
external object PostHogJsModule : JsAny

/**
 * The web's [TrackingSink] over posthog-js. The SDK starts opted out and [Tracking.start] opts it in only where the
 * rule allows (doctor.shifa.uz on the production backend), so every other site sends nothing. It fetches nothing on
 * start either: no feature flags or remote config, no external scripts. Everything automatic is off (autocapture,
 * heatmaps, replay, surveys, page views and leaves, web vitals, exceptions): the page is one canvas, and its address
 * holds names and ids, so everything posthog-js derives from the URL, the referrer or the campaign is dropped from
 * every event and person (see `before_send`). Each event is sent at once (no batch), so an opt-out finds nothing
 * waiting; only a request that failed (offline) sits in posthog-js's retry queue, which it has no way to drop.
 */
class PostHogWebSink(
    apiKey: String,
) : TrackingSink {
    private val posthog: JsAny = initPostHog(PostHogJsModule, apiKey, POSTHOG_HOST)
    private var optedIn = false

    override fun capture(
        event: String,
        properties: Map<String, Any>,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    ) = phCapture(posthog, event, properties.json(), personProperties.json(), personPropertiesSetOnce.json())

    override fun screen(
        id: String,
        properties: Map<String, Any>,
    ) = phCapture(posthog, "\$screen", (properties + ("\$screen_name" to id)).json(), "{}", "{}")

    override fun identify(
        distinctId: String,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    ) = phIdentify(posthog, distinctId, personProperties.json(), personPropertiesSetOnce.json())

    override fun reset(dropQueue: Boolean) = phReset(posthog, optedIn)

    override fun register(superProperties: Map<String, Any>) = phRegister(posthog, superProperties.json())

    override fun optOut(out: Boolean) {
        optedIn = !out
        phOptIn(posthog, optedIn)
    }

    // Unbatched (`request_batching: false`): each event goes out when captured, so there is nothing to flush.
    override fun flush() = Unit
}

private fun Map<String, Any>.json(): String = JsonObject(mapValues { (_, value) -> value.json() }).toString()

// The values TrackingSink allows; anything else never reaches here (trackProperties drops it).
private fun Any.json(): JsonElement =
    when (this) {
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is List<*> -> JsonArray(mapNotNull { it?.json() })
        else -> JsonNull
    }

private fun initPostHog(
    module: JsAny,
    apiKey: String,
    host: String,
): JsAny =
    js(
        """(function () {
    var posthog = module.default || module;
    // posthog-js 1.438.24 adds these even with save_referrer / save_campaign_params off: the page's and the
    // referrer's URL, path and domain, the session-entry and initial copies of everything it reads from them (UTM and
    // click-id parameters, search engine and keyword), and Meta's _fbc / _fbp. The hostname property stays.
    var fromAddress = new RegExp('(url|pathname|referrer|referring_domain)$|^\\u0024(initial|session_entry)_|' +
        '^\\u0024(search_engine|fbc|fbp)$|^ph_keyword$|^utm_|^(gad_source|mc_cid|gclid|gclsrc|dclid|gbraid|wbraid|' +
        'fbclid|msclkid|twclid|li_fat_id|igshid|ttclid|rdt_cid|epik|qclid|sccid|oppref|bfcid|irclid|_kx)$');
    posthog.init(apiKey, {
        api_host: host,
        persistence: 'localStorage',
        opt_out_capturing_by_default: true,
        advanced_disable_flags: true,
        disable_external_dependency_loading: true,
        autocapture: false,
        rageclick: false,
        capture_dead_clicks: false,
        capture_heatmaps: false,
        capture_pageview: false,
        capture_pageleave: false,
        capture_performance: false,
        capture_exceptions: false,
        disable_session_recording: true,
        disable_surveys: true,
        save_referrer: false,
        save_campaign_params: false,
        request_batching: false,
        before_send: function (e) {
            [e.properties, e['\u0024set'], e['\u0024set_once']].forEach(function (props) {
                if (props) Object.keys(props).forEach(function (k) {
                    if (fromAddress.test(k)) delete props[k];
                });
            });
            return e;
        }
    });
    return posthog;
})()""",
    )

private fun phCapture(
    ph: JsAny,
    event: String,
    properties: String,
    set: String,
    setOnce: String,
) {
    js(
        """(function () {
    var options = {};
    var s = JSON.parse(set), so = JSON.parse(setOnce);
    if (Object.keys(s).length) options['\u0024set'] = s;
    if (Object.keys(so).length) options['\u0024set_once'] = so;
    ph.capture(event, JSON.parse(properties), options);
})()""",
    )
}

private fun phIdentify(
    ph: JsAny,
    distinctId: String,
    set: String,
    setOnce: String,
) {
    js("ph.identify(distinctId, JSON.parse(set), JSON.parse(setOnce))")
}

private fun phRegister(
    ph: JsAny,
    properties: String,
) {
    js("ph.register(JSON.parse(properties))")
}

// No opt-in event: `app` is not registered yet when Tracking.start opts in.
private fun phOptIn(
    ph: JsAny,
    optIn: Boolean,
) {
    js("optIn ? ph.opt_in_capturing({ captureEventName: false }) : ph.opt_out_capturing()")
}

/**
 * The identity goes. `dropQueue` can't be honoured: posthog-js has no public way to drop its retry queue (its fields
 * are renamed in the bundle), so a request that failed offline before an opt-out may still go out later, like on iOS.
 * Nothing else waits: events are not batched. Its reset also forgets the consent, which `opt_out_capturing_by_default`
 * turns into "off": opted in, it is opted in again (opted out first, so the reset doesn't warn about it).
 */
private fun phReset(
    ph: JsAny,
    optedIn: Boolean,
) {
    js(
        """(function () {
    ph.opt_out_capturing();
    ph.reset();
    if (optedIn) ph.opt_in_capturing({ captureEventName: false });
})()""",
    )
}
