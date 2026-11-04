# Calendar timezone-mismatch notice

| | |
|---|---|
| Area | 04 Calendar & schedule |
| Who sees it | Doctor (Calendar tab). Clinic staff: not shown today (target: also on the clinic doctor schedule screen when phone TZ ≠ clinic TZ). No tier gate. |
| Reached from | Automatically at the top of [01-calendar-tab](01-calendar-tab.md) when the phone's UTC offset differs from the practice timezone's offset |
| Screen type | Inline banner (top of the tab content, scrolls with the page) |
| Current Flutter source | `lib/features/calendar/presentation/calendar_screen.dart` (`_shouldShowTimeZoneMismatchHint`, `_buildTimeZoneHint`) |
| Spec | `docs/kmp-spec/03-calendar-schedule.md` §1.11, §4; ticket TZ-1 |

## 1. Purpose
Tell a travelling doctor (or one whose phone clock is wrong) that every time in the calendar is the *practice* time, not the phone time, so a "10:00" slot is not misread. Rare in daily use; essential when it happens.

## 2. Entry points
- Computed on every build of the Calendar tab: `offset(practiceTz, now) != offset(phoneTz, now)`. No user action opens it.
- Appears again on every fresh app start (dismissal is session-only).
- Not shown on Home, Appointments, Chat or elsewhere (by convention — see 00-README).
- Re-evaluated when the app resumes from background (the phone may have changed zone on a flight) and after the profile is refreshed (the doctor may have changed the practice zone in Profile). Today both happen only because the whole screen rebuilds; target: explicit listeners.
- Clinic doctor schedule screen (staff): today only a static hint line is shown instead of this banner ([14](14-staff-calendar-switcher.md)); target: same banner comparing phone vs clinic zone.

## 3. Layout, top to bottom (phone portrait)
1. Amber-50 rounded container (8 dp radius), 12 dp horizontal / 8 dp vertical padding, 12 dp gap below.
2. Left: clock icon, amber-800, 20 dp.
3. Middle (expands): message in 13 pt amber-900, wraps to as many lines as needed (3–4 lines in Russian).
4. Right: text button **"Dismiss"**.
5. (Target) second text button **"Set timezone"** when the practice zone is missing or when the doctor taps the banner body; keeps Dismiss as the first action.

Target: use the app's standard top banner component (info/warning variant) so it matches banners elsewhere; keep it inline (not floating) so it does not cover the month header.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Practice timezone id | `profile.timeZone` (IANA, e.g. "Asia/Tashkent") | shown raw inside the message; "Unknown" when empty (then the banner is actually suppressed — see §8) |
| Phone timezone | system default | not shown today; **target:** show both, e.g. "Calendar: Asia/Tashkent (UTC+5) · Phone: Europe/Berlin (UTC+2)" |
| Comparison | UTC offset of each zone **at this instant** | offsets equal → no banner even if the ids differ (e.g. Europe/Berlin vs Europe/Rome). Offsets differ only during one zone's DST → banner appears for a few weeks a year. |

Time rules: this is the only place in the app where the phone clock is read on purpose. All times on the calendar stay in practice TZ whether or not the banner is dismissed.

Worked examples (practice TZ = Asia/Tashkent, UTC+5, no DST):

| Phone zone | Phone offset now | Banner? | What the doctor sees for a 10:00 Tashkent slot |
|---|---|---|---|
| Asia/Tashkent | +5 | no | 10:00 |
| Asia/Almaty (changed to +5 in 2024) | +5 | no (same offset, different id) | 10:00 |
| Europe/Berlin in summer | +2 | yes | 10:00 (phone shows 07:00 on its own clock) |
| America/New_York in winter | −5 | yes | 10:00 (it is 00:00 the previous night on the phone) — "today" in the calendar may be a day ahead of the phone's date |
| Asia/Kabul | +4:30 | yes | 10:00 |
| UTC (practice TZ missing) | any ≠ 0 | hidden today (should be the "not set" variant) | times are UTC and wrong by 5 h |

## 5. Primary flow (happy path)
1. Doctor lands in Tashkent-practice calendar while the phone is on Berlin time → banner slides in at the top with the message naming "Asia/Tashkent".
2. Doctor reads it, taps **Dismiss** → banner collapses (animated height, target 200 ms); layout below moves up.
3. Banner stays hidden until the next cold start.

## 6. Screen states
- **First load:** not shown until the profile (timezone) is loaded; then appears without animation. Target: slide-in.
- **Empty:** n/a.
- **Error on load (profile failed):** banner cannot be computed → hidden.
- **Offline:** shown from the cached profile; works offline.
- **Refreshing:** no change.
- **Partial data:** practice TZ missing → banner hidden. **Target:** different banner "Your practice timezone is not set — times are shown in UTC. Set it in Profile" with a "Set timezone" action.
- **Permission needed:** n/a.
- **Locked by tier:** n/a.
- **Role difference:** staff do not see the Calendar tab; the clinic doctor schedule screen shows a permanent small hint "Times use this clinic timezone" instead (see [14](14-staff-calendar-switcher.md)). Target: add the same mismatch banner there, comparing phone TZ with the *clinic* TZ.

## 7. Interactions and secondary flows
- **Tap Dismiss** → hides for the session; no persistence.
- **Tap message** → nothing today. Target: opens Profile → Timezone (practice TZ editing) so a doctor who actually moved can fix the setting.
- **Phone timezone changes while the app is open** (e.g. plane lands): current app re-evaluates on next rebuild; target: listen to system timezone change and re-evaluate.
- **Tap on the "{scheduleTimeZone}" text** (target): opens a small explainer sheet with both zones, the current time in each, and the "Set timezone" action — useful when the doctor is not sure which one is wrong.
- **Banner and the "Update schedule" card both visible** (new doctor abroad): banner first, then header, month, card; the banner must not push the card below the fold — keep the banner to 2 lines in English.
- **Pull-to-refresh** (target) re-evaluates the banner together with the data.

## 8. Edge cases
- **Same offset, different ids** (Berlin vs Rome): hidden — correct, times read the same.
- **Half-hour / 45-minute zones** (Asia/Kabul +4:30): offsets differ → shown; message still only names the practice zone.
- **DST transition day:** at 02:00 the offset of one zone changes; banner can appear/disappear mid-session without user action; acceptable, but the collapse should animate.
- **Practice TZ empty or invalid id:** comparison throws → banner hidden; calendar silently uses UTC. Must become an explicit state (see §6 Partial data).
- **Dismissed, then app resumed after hours:** stays dismissed (session flag). Target: re-show after a cold start only.
- **Very long id** ("America/Argentina/ComodRivadavia"): message wraps; no truncation.
- **200 % font:** 6+ lines; Dismiss button wraps under the text (Row → should become a Column at large text).
- **Language switch:** message re-renders; Uzbek and Russian are ~35 % longer.
- **Screen reader:** announce as "Warning: " + message; Dismiss is a button. Should be the first focusable element on the tab.
- **Deep link opens the calendar with a sheet already up:** banner is behind the sheet; visible when the sheet closes.
- **Staff calendars visible with practice TZ ≠ clinic TZ:** the banner names the practice zone while colleague sections use the clinic zone — two different "schedule timezones" on one screen; the banner should name the zone each section uses, or (target) the whole screen should use one zone.
- **Rotation:** banner re-lays out; text wraps differently; no state change.
- **Slow network:** banner depends only on the profile, which is cached, so it appears before the day loads.
- **Offline for days (DST switched meanwhile):** the comparison uses the phone's current offset and the practice zone's rules from the bundled tz database — still correct.
- **Phone date wrong by a year:** offsets are evaluated at the phone's "now"; DST rules for that year apply; still a correct offset comparison, but the calendar's "today" is wrong — out of scope here.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (English) | Where shown |
|---|---|---|---|---|
| Show banner | practice offset ≠ phone offset at now | every build / on resume | `calendarTimezoneMismatchHint` | inline banner |
| Practice TZ missing | id empty | on profile load | **NEW** "Your practice timezone is not set. Times are shown in UTC." | inline banner |
| Unknown TZ id (profile holds a value the tz database does not know) | lookup fails | on profile load | **NEW** "Unknown timezone '{id}' — times are shown in UTC." + "Fix in Profile" | inline banner |
| Clinic TZ ≠ phone TZ (target, clinic schedule screen) | offsets differ | on open | **NEW** "Times are shown in the clinic's timezone ({clinicTimeZone})." | inline banner |

## 10. Navigation out
- Dismiss → stays on calendar. Target "Set timezone" → Profile timezone editor; on return re-evaluate and reload the calendar in the new zone (all cached days must be re-converted or cleared).

## 11. Copy
- `calendarTimezoneMismatchHint`: "Your current device timezone does not match the calendar schedule timezone ({scheduleTimeZone}). Your slots were defined in one timezone, but you are currently in another. Please keep this in mind while managing appointments." — **recommend shortening** to: "Times are shown in your practice timezone ({scheduleTimeZone}), which differs from your phone's." (**NEW**)
- `dismiss` "Dismiss".
- **NEW:** "Set timezone"; "Your practice timezone is not set. Times are shown in UTC."; (target) "Phone: {deviceTimeZone}".

## 12. Known problems in the current app (do not carry over)
- 60-word message; names only the practice zone, not the phone zone or the offset difference.
- Hidden instead of explained when the practice timezone is missing.
- No link to fix the setting.
- Comparison runs on every rebuild and logs to the console each time.

## 13. Open questions for design
- Inline (pushes content) or overlay (floats over the month header)?
- Show the offset difference ("+3 h") — helps or clutters?
- Should the banner also appear on Home's "Today's schedule" card? Convention says no; confirm.
- Persist dismissal per (practice zone, phone zone) pair instead of per session, so a doctor who permanently lives in another zone is not nagged on every launch?
- Is the amber warning style right, or should this be a neutral info banner (nothing is broken — it is an explanation)?
- Should the time in the phone's zone be shown next to each appointment time ("10:00 · 07:00 your time") while the mismatch persists? Probably too noisy; confirm.
