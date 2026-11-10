package shifa.doctor.data.tracking.events

/**
 * Stage A (A6): the shell (`ui/navigation`). Screen views are not events here: they go through
 * `Tracker.screen(TrackScreen.X)`. Nothing from a route, a web path or a push's data except what is listed.
 */
object ShellEvents {
    /** A tab chosen by the doctor. [tab]: a `ShellTab` key (`home`, `calendar`, …); [source]: `bar` / `more` / `sidebar`. */
    data class TabSelected(
        val tab: String,
        val source: String,
    ) : TrackEvent("tab_selected", "tab" to tab, "source" to source)

    data object MoreOpened : TrackEvent("more_opened")

    /** A tab the plan or the clinic membership does not allow (it opens Home instead). [tab]: `tasks` / `clinic`. */
    data class TabRefused(
        val tab: String,
    ) : TrackEvent("tab_refused", "tab" to tab)

    /**
     * A tapped push, once its target is found. [type]: the push's `type` as the backend spells it (`unknown` without
     * one), and nothing else from its data (it names patients); [target]: `appointment` / `patient` / `document` /
     * `chat` / `partnership` / `task` / `inbox`, or `home` when its appointment is gone.
     */
    data class PushOpened(
        val type: String,
        val target: String,
    ) : TrackEvent("push_opened", "type" to type, "target" to target)
}
