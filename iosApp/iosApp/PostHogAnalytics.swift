import ComposeApp
import Foundation
import PostHog

/// PostHog's iOS SDK behind the shared `TrackingSink` (docs/posthog/03-developer-guide.md §6). Set up and handed to
/// `Tracking` in AppDelegate; `Tracking` decides whether anything is sent.
final class PostHogAnalytics: NSObject, TrackingSink {
    func capture(
        event: String,
        properties: [String: Any],
        personProperties: [String: Any],
        personPropertiesSetOnce: [String: Any]
    ) {
        PostHogSDK.shared.capture(
            event,
            properties: properties.json,
            userProperties: personProperties.isEmpty ? nil : personProperties.json,
            userPropertiesSetOnce: personPropertiesSetOnce.isEmpty ? nil : personPropertiesSetOnce.json
        )
    }

    func screen(id: String, properties: [String: Any]) {
        PostHogSDK.shared.screen(id, properties: properties.json)
    }

    func identify(distinctId: String, personProperties: [String: Any], personPropertiesSetOnce: [String: Any]) {
        PostHogSDK.shared.identify(
            distinctId,
            userProperties: personProperties.json,
            userPropertiesSetOnce: personPropertiesSetOnce.isEmpty ? nil : personPropertiesSetOnce.json
        )
    }

    /// posthog-ios has no public way to drop its queue; AppDelegate sets `flushAt = 1`, so only events still in flight
    /// or held while offline can outlive this (each keeps the distinct id it was captured with).
    func reset(dropQueue: Bool) {
        PostHogSDK.shared.reset()
    }

    func register(superProperties: [String: Any]) {
        PostHogSDK.shared.register(superProperties.json)
    }

    func optOut(out: Bool) {
        if out { PostHogSDK.shared.optOut() } else { PostHogSDK.shared.optIn() }
    }

    func flush() {
        PostHogSDK.shared.flush()
    }
}

private extension Dictionary where Key == String, Value == Any {
    /// A Kotlin `Boolean` arrives as `KotlinBoolean`, which the SDK's JSONSerialization writes as `1` / `0` (Android
    /// sends `true` / `false`); a Swift `Bool` bridges to the CFBoolean it writes as `true` / `false`. Kotlin can't fix
    /// this: it turns even `kCFBooleanTrue` back into a `KotlinBoolean`. The patient app's fix, plus lists and maps.
    var json: [String: Any] { mapValues(jsonValue) }
}

private func jsonValue(_ value: Any) -> Any {
    switch value {
    case let flag as KotlinBoolean: return flag.boolValue
    case let list as [Any]: return list.map(jsonValue)
    case let map as [String: Any]: return map.json
    default: return value
    }
}
