package shifa.doctor.data.tracking

import shifa.doctor.data.tracking.events.TrackEvent

/** For ViewModel tests: the events and screen views a ViewModel sent, in order. */
class RecordingTracker : Tracker {
    val events = mutableListOf<TrackEvent>()
    val screens = mutableListOf<Pair<TrackScreen, Map<String, Any>>>()

    override fun track(event: TrackEvent) {
        events += event
    }

    override fun screen(
        screen: TrackScreen,
        properties: Map<String, Any?>,
    ) {
        screens += screen to trackProperties(properties.toList())
    }
}

/** A platform SDK that records every call as one line, so tests assert order and arguments at once. */
class RecordingSink : TrackingSink {
    val calls = mutableListOf<String>()

    override fun capture(
        event: String,
        properties: Map<String, Any>,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    ) {
        calls += "capture $event $properties $personProperties $personPropertiesSetOnce"
    }

    override fun screen(
        id: String,
        properties: Map<String, Any>,
    ) {
        calls += "screen $id $properties"
    }

    override fun identify(
        distinctId: String,
        personProperties: Map<String, Any>,
        personPropertiesSetOnce: Map<String, Any>,
    ) {
        calls += "identify $distinctId $personProperties $personPropertiesSetOnce"
    }

    override fun reset(dropQueue: Boolean) {
        calls += if (dropQueue) "reset drop" else "reset"
    }

    override fun register(superProperties: Map<String, Any>) {
        calls += "register $superProperties"
    }

    override fun optOut(out: Boolean) {
        calls += "optOut $out"
    }

    override fun flush() {
        calls += "flush"
    }
}
