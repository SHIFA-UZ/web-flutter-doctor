- **[P1] Wide tooltips can crash drawing.** [AnalyticsCharts.kt:201](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsCharts.kt:201): When the measured tooltip plus padding exceeds the chart width, `coerceIn(0f, size.width - boxW)` receives a negative upper bound and throws. Large text, long translations or narrow screens can trigger this on tapping a point. Constrain/wrap the tooltip before positioning it.

- **[P2] The dynamic title falls back to the wrong period.** [AnalyticsCharts.kt:93](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsCharts.kt:93): A 30-day request returning an empty list or failure displays “Appointments (Last 7 Days)” because `span` becomes null. While loading a changed range, it retains the previous period. Pass the requested period into `TrendBlock` so the ticket’s dynamic-title fix also covers loading, empty and error states.

- **[P2] The final date label overlaps the preceding label.** [AnalyticsCharts.kt:189](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsCharts.kt:189): For 14 points, `labelEvery` selects indices 0, 2, …, 12, then this condition additionally selects 13. Those final labels are only one daily interval apart; clamping the last label inward further reduces separation. They overlap at phone widths. Check measured label bounds before adding the final label.

- **[P2] Large text still overflows the donut hole.** [AnalyticsCharts.kt:252](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsCharts.kt:252): The centre text remains inside a fixed 160-dp ring at every font scale. At 200%, its nominal line heights total 116 dp, exceeding the 104-dp hole even before wrapping. `FlowRow` only relocates the external legend; it cannot prevent centre text overlapping the arcs. Move the centre labels outside the ring at large font sizes, as required by flow §8.

---
**Answers (round 1, all fixed; one was a P1, so a second round follows):**
1. *A wide tooltip could crash:* fixed. The tooltip is measured no wider than the chart (it wraps), and every clamp
   has a non-negative upper bound, so large text or a long translation can't throw.
2. *The title fell back to "Last 7 Days":* fixed. `TrendBlock` gets the requested range: while loading, empty or
   failed the title names it; once loaded, the days drawn.
3. *The last date overlapped the one before:* fixed. Dates are placed by their measured width: one that would touch
   the date before it is left out, and the last always shows, in place of the one before it if they touch.
   `Analytics_largeText` (14 days at 200 %) shows 10/23, 10/27, 10/31, 11/05.
4. *Large text overflowed the ring:* fixed. From 130 % text the centre is left empty and the shares join the legend
   ("In-person · 17 · 68 %"); the space under the line also follows the dates' measured height, so the axis's 0 no
   longer meets them. `Analytics_largeText` shows both.

