# Extended profile (biography, service tags, certificates, social links)

| | |
|---|---|
| Area | Profile, settings, reports & legal |
| Who sees it | Doctor only. No tier gate. |
| Reached from | Profile root → "Extended profile" row; completeness chip "Add services" (opens Services & pricing instead — see §13) |
| Screen type | Full screen (pushed) + file/photo picker |
| Current Flutter source | `profile_screen.dart` (panel `extended`, ~1956–2194), `lib/state/profile/profile_actions.dart` (`uploadCertificate`) |
| Spec | `docs/kmp-spec/08-profile-settings-reports-legal.md` §1.10, ticket PROF-08 |

## 1. Purpose
The public-facing "about me" that patients read in the patient app: a biography, free-text service tags (what the doctor treats), diplomas/certificates as images or PDFs, and Telegram/Instagram handles. Filled once, touched a few times a year.

## 2. Entry points
- Profile root → row → pushed.
- Nothing else deep-links here today.

## 3. Layout, top to bottom (phone portrait)
1. **App bar** — back, title "Extended Profile", right action **Save** (for biography and social handles; tags and certificates save on their own, see §4).
2. **Biography** — multiline field (5 visible lines, grows to 10, 2 000-char counter appearing at 1 800 — limit UNVERIFIED), helper "Shown to patients on your profile" **NEW**.
3. **Services (tags)** — title "Services"; input row: text field (placeholder "Add Service") + **Add** button; below, chips in a wrap, each with a remove (×). Today: a list of cards with a red delete icon. Helper: "Short keywords patients search for, e.g. 'Braces', 'Root canal'. Prices live in Services & pricing." **NEW**
4. **Certificates** — title; **Upload certificate** secondary button; list of rows: thumbnail (image) or PDF icon, file name (last path segment), upload date if known (UNVERIFIED — not in the data today), overflow with View / Delete. Tapping a row opens the document viewer (area 06 component).
5. **Social media** — Telegram field (prefix "@", hint "@username or URL"), Instagram field (same). Helper: "Patients can open these from your profile" **NEW**.

Scrolls under the pinned app bar.

## 4. Data and formats
| Field | Source → saved | Format rule |
|---|---|---|
| Biography | profile.biography ← `PATCH /api/doctors/me/profile {"biography"}` | plain text, line breaks kept; today debounced 500 ms auto-save → target: Save button (one request), with the same per-field debounce acceptable only if Save is dropped |
| Service tags | profile.services — **a JSON-encoded string of a string array** ← `PATCH {"services": "[\"Braces\",\"Root canal\"]"}` | each tag trimmed, 1–40 chars, duplicates (case-insensitive) rejected, max 30 tags (NEW limits); saves immediately on add/remove with optimistic UI |
| Certificates | profile.certificates — JSON string of URLs ← `POST /api/doctors/me/certificate` (multipart `file`, response key `photoUrl`), delete via `PATCH {"certificates": "<json>"}` | images (JPEG/PNG/HEIC → JPEG, downsized ≤ 2 000 px) and PDF (≤ 10 MB, UNVERIFIED); display name = file name; URL normalised like other media |
| Telegram / Instagram | profile.telegram / profile.instagram ← PATCH | stored as typed; display strips a leading "https://t.me/" / "https://instagram.com/" to "@handle" (NEW) |

## 5. Primary flow (happy path)
1. Open → fields filled from the cached profile; tags as chips; certificates as rows.
2. Doctor writes a biography, adds two tags (each chip appears instantly; a tiny ring on the chip until the PATCH returns), uploads a PDF certificate (row appears with a progress bar, then thumbnail/name), types "@drkarimov" in Telegram.
3. Tap Save → PATCH with biography + telegram → banner "Extended profile saved" → pop.

```mermaid
flowchart TD
  A[Open] --> B{What changed?}
  B -- biography / socials --> C[Dirty → Save → PATCH]
  B -- add/remove tag --> D[Optimistic chip → PATCH services]
  B -- upload certificate --> E[Picker → compress → POST multipart]
  D -- fail --> D2[Revert chip, banner error]
  E -- 2xx --> F[Row appears, banner "Certificate uploaded"]
  E -- fail --> G[Row removed, banner "Upload failed"]
  C -- 200 --> H[Banner saved, pop]
```

## 6. Screen states
- **First load:** values from memory; cold open shows skeleton: text block, chip row (3 pills), two list rows, two fields.
- **Empty:** biography placeholder "Tell patients about your experience and approach" **NEW**; tags area shows only the input with helper; certificates area shows an inline empty row "No certificates yet" **NEW** + upload button; socials empty.
- **Error on load:** inherits root error.
- **Offline:** biography/socials editable, Save disabled (offline helper); Add tag disabled; Upload disabled; existing certificate thumbnails from cache; viewer may fail (area 06 handles).
- **Refreshing:** after each tag/certificate operation the profile is refetched silently; chips do not flicker (keep local order until the server list arrives; then reconcile).
- **Partial data:** `services` or `certificates` string is not valid JSON (corrupt) → treat as empty list and show a one-line warning "Couldn't read saved services — adding a new one will replace them" **NEW**; today the code silently falls back to a cached list.
- **Permission needed:** photos/camera for certificate images (same banners as `02`); file picker needs no permission.
- **Locked by tier:** n/a.
- **Role difference:** clinic staff: not reachable.

## 7. Interactions and secondary flows
- **Type biography** → dirty; counter near the limit.
- **Type tag + Return / Add** → validate, optimistic chip, PATCH; input cleared; focus stays in the input for rapid entry.
- **Tap chip ×** → chip removed immediately, PATCH; failure restores it.
- **Upload certificate** → action sheet: Take photo / Choose from gallery / Choose file (PDF). Progress bar in the new row; cancel (×) during upload aborts.
- **Tap certificate row** → viewer (image zoom or PDF).
- **Row overflow → Delete** → confirmation dialog "Delete certificate?" (destructive) → PATCH with the remaining list → row removed.
- **Type Telegram/Instagram** → dirty; paste of a full URL is accepted and shown as handle.
- **Save** → biography + socials in one PATCH; **Back with dirty** → unsaved-changes dialog (tags/certificates are already saved and are not part of "dirty").
- **Long-press chip** → n/a (no reorder).

## 8. Edge cases
- Biography with emoji, Cyrillic, Arabic script → stored as is; counter counts graphemes.
- Biography pasted 10 000 chars → truncated at the limit with the counter red; Save disabled until under the limit.
- Tag with trailing spaces or different case of an existing tag ("braces" vs "Braces") → rejected inline "Already added" **NEW**.
- Tag longer than 40 chars → blocked at input.
- 30 tags reached → input disabled with helper "Maximum 30 services" **NEW**.
- Two quick adds within 500 ms → each is its own request; the second request must send the list including the first (sequential queue, not the stale snapshot).
- Add fails with 409 (profile changed elsewhere) → refetch and show the server list; banner "Services were updated elsewhere — list refreshed" **NEW**.
- Certificate 25 MB PDF → rejected before upload "File is too large (max 10 MB)" **NEW**; 20 MB photo → downsized then uploaded.
- HEIC certificate photo → converted to JPEG.
- Upload returns 2xx but `photoUrl` missing → treat as failure, banner "Upload failed".
- Upload 401 → global logout.
- Certificate URL 404 (deleted on the server) → thumbnail falls back to a broken-file icon; Delete still works.
- Delete the last certificate → empty row returns.
- Telegram handle with "t.me/" prefix, "@" prefix, or spaces → normalised to "@handle"; invalid characters (anything but letters, digits, underscore, 5–32 chars) → inline "Enter a valid Telegram username" **NEW**; Instagram: letters, digits, dot, underscore, 1–30 chars.
- Slow network on Save > 3 s → ring; timeout 30 s → network banner.
- Double tap Save → ignored.
- Leaving while a certificate upload runs → dialog "Upload in progress. Leave and cancel the upload?" **NEW**.
- Rotation → all state kept, upload continues.
- Language switch → labels only.
- Font 200 % → chips wrap; Add button moves under the input; certificate rows two lines.
- Screen reader: chips announced "Braces, remove, button"; upload progress announced in 25 % steps; certificate rows "Diploma.pdf, document, button".

## 9. Validation and messages
| Field/action | Rule | When | Message (EN) | Where |
|---|---|---|---|---|
| Biography | ≤ 2 000 chars (UNVERIFIED) | typing | counter; "Too long" **NEW** | inline |
| Tag | 1–40 chars, unique (case-insensitive), ≤ 30 tags | Add | "Enter service name" (`enterService`) / "Already added" **NEW** / "Maximum 30 services" **NEW** | inline under input |
| Tag save | 200 | after PATCH | revert + server `message` or `networkError` | top banner |
| Certificate | JPEG/PNG/HEIC/PDF, ≤ 10 MB after compression (UNVERIFIED) | after pick | "File is too large (max 10 MB)" **NEW** / "Upload failed" (`uploadFailed`) | top banner |
| Certificate | success | after 2xx | "Certificate uploaded" (`certificateUploaded`) | top banner |
| Certificate delete | confirm | tap Delete | "Delete certificate?" **NEW**, `delete` / `cancel` | dialog |
| Telegram | `@` + 5–32 `[A-Za-z0-9_]` | blur | "Enter a valid Telegram username" **NEW** | inline |
| Instagram | 1–30 `[A-Za-z0-9._]` | blur | "Enter a valid Instagram username" **NEW** | inline |
| Save | 200 | after PATCH | "Extended profile saved" (`extendedProfileSaved`) | top banner |
| Leave dirty | — | back | `unsavedChangesMessage` | dialog |

## 10. Navigation out
- Save → pop; root refreshes (row preview unchanged — static subtitle).
- Certificate viewer (push) → returns here.
- Services & pricing is a different screen (`08`); a text link under the tags section "Set prices in Services & pricing" pushes it.
- Back → dialog if biography/socials dirty.

## 11. Copy
- Title: `extendedProfile` ("Extended Profile"); subtitle on the root `extendedProfileSubtitle` ("Biography, Services, Certificates, Social Media").
- Fields: `biography` ("Biography"), `services` ("Services"), `addService` ("Add Service"), `add` ("Add"), `removeService` ("Remove Service"), `enterService` ("Enter service name"), `certificates` ("Certificates"), `uploadCertificate` ("Upload Certificate"), `certificateUploaded`, `socialMedia` ("Social Media"), `telegram`, `instagram`, `socialMediaHint` ("@username or URL").
- Pickers: `takePhoto`, `chooseFromGallery`, "Choose file" **NEW**.
- Empty/helpers: "Tell patients about your experience and approach" **NEW**, "No certificates yet" **NEW**, "Shown to patients on your profile" **NEW**, "Set prices in Services & pricing" **NEW**.
- Save: `save`, `extendedProfileSaved`; `uploadFailed`; `delete`, `cancel`.

## 12. Known problems in the current app (do not carry over)
- **One shared debounce timer** for biography, Telegram and Instagram: typing in a second field within 500 ms cancels the first field's pending save (spec quirk #4).
- Biography/socials auto-save silently while tags/certificates refetch the whole profile; the Save button only shows a "saved" snackbar.
- Services and certificates are JSON strings inside string fields; the UI parses them on every build and keeps a cache that can mask corrupt data.
- Certificate upload accepts images only (no PDF), no compression, response reuses the key `photoUrl`; the list shows the full URL as subtitle.
- Delete has no confirmation.
- Tags are full-width cards with a red bin icon rather than chips.

## 13. Open questions for design
- Keep "Services" tags here at all, now that Services & pricing exists? Proposal: rename to "Specialities / keywords" to avoid two things called Services.
- Certificates as a horizontal thumbnail strip vs. a vertical list?
- Should biography support basic formatting (paragraphs only is the proposal)?
