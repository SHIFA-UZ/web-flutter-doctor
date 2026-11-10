package shifa.doctor.data.tracking

/**
 * For iosApp/iosApp/AppDelegate.swift (`TrackingBridgeKt`): read before `PostHogSDK.setup`, so a launch that must not
 * track never opts the SDK in, not even for the events setup itself sends. `Tracking.start` then applies the same rule.
 */
fun trackingStartsOff(): Boolean = !trackingStatus(Tracking.environment(), Tracking.settings.optedOut, null).on
