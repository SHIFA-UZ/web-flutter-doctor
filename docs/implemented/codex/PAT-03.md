- **[P2] Refresh can leave an invalid page.** [PatientsScreen.kt:177](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L177) replaces the directory without clamping `page`. Refreshing page 3 after the directory shrinks to 15 patients leaves no rows and displays “Showing 21 to 15 of 15”.

- **[P2] Opening or dismissing filters resets pagination.** [PatientsScreen.kt:217](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L217) resets the page for every `ui` action, including opening/closing the status sheet and dismissing an error banner. Those actions should preserve the page unless a filter actually changes.

- **[P2] Page changes do not scroll to the top.** [PatientsScreen.kt:286](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L286) retains its scroll state without reacting to page changes. Selecting Next at the footer leaves the doctor at the bottom of the replacement rows, contrary to flow §7.

- **[P2] Pager controls do not fit narrow screens.** [PatientsScreen.kt:474](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L474) puts Previous, five padded page chips, and Next in one non-wrapping, non-scrolling row. With at least five pages, later controls are squeezed/clipped on phone widths; increased font sizes worsen this.

- **[P2] Boundary navigation remains accessible as enabled.** [PatientsScreen.kt:478](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L478) and line 484 merely make boundary clicks no-ops. Previous on the first page and Next on the last remain enabled selectable controls for screen readers instead of exposing disabled semantics.

- **[P2] Persisted favourites can remain invisible after startup.** [PatientsScreen.kt:178](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L178) reads favourites only during initialization. Opening Patients before the asynchronous profile fetch finishes yields a null doctor ID and an empty favourites set; subsequent profile arrival never reloads the stored IDs.

- **[P2] Mixed-text searches produce unrelated phone matches.** [PatientsScreen.kt:128](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L128) strips every non-digit before phone matching. Searching `Alice 123` therefore includes any patient whose phone contains `123`, even when neither their name nor ID matches the query.

- **[P2] Required tablet layout and selection are absent.** [MainScreen.kt:322](composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt#L322) always pushes a patient route, while [PatientsScreen.kt:282](composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt#L282) always renders one full-width column. PAT-03 explicitly requires the ≥720dp 7:13 list/detail layout and selected-row highlighting; neither is implemented.
---
**Answers (round 1):**
1. *Refresh leaves an invalid page:* fixed; the page shown is kept in range (`currentPage`) when the list shrinks. Tested.
2. *Opening sheets resets the page:* fixed; only a change of what is shown (segment, status, order, search) resets it (`filter`); opening the status sheet or dismissing an error doesn't (`ui`). Tested.
3. *No scroll to top on a page change:* fixed; a new page scrolls to the top.
4. *Pager doesn't fit:* fixed; it wraps (FlowRow) on narrow screens and large text.
5. *Boundary controls stay enabled:* fixed; Previous isn't shown on the first page nor Next on the last, so a screen reader never meets a dead control.
6. *Favourites invisible after startup:* fixed; they load when the signed-in doctor arrives (the profile flow), not only at creation.
7. *Words match phones:* fixed; phone digits are compared only for a number-like query (no letters). Tested with "Alice 90".
8. *Tablet two-pane:* not in this ticket. The tablet layout is the same open call as CAL-5 (a tablet pass, the user's call per the master plan); on phones the detail is a pushed screen. Recorded under "Not built".
