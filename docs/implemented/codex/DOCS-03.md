- **[P2] A second upload silently loses its draft.** [DocumentUpload.kt:241](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/documents/DocumentUpload.kt:241) clears the draft before `send()` rejects it when another upload is running (line 259). Upload remains accessible during that request. Enforce the documented one-at-a-time policy before picking or clearing another file.

- **[P2] Demo uploads appear in every patient’s record.** [DemoBackend.kt:320](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/debug/DemoBackend.kt:320) prepends the same global `uploads` collection to every patient’s documents. Uploading to patient 12 also adds the document to patient 14. Store and retrieve uploads by patient and clinic scope.

- **[P2] Opening a demo upload displays unrelated content.** [DemoBackend.kt:326](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/debug/DemoBackend.kt:326) stores only metadata; the download handler at lines 342–346 always returns `demoPdf()`. Uploaded photos and multi-page scans therefore open as the fixed sample PDF. Retain and serve the uploaded bytes and MIME type.

- **[P2] Unsupported files are accepted as PDFs.** [DocumentUpload.kt:160](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/documents/DocumentUpload.kt:160) unconditionally assigns PDF MIME; `offer()` checks only size and emptiness. A renamed DOCX or incorrectly typed provider result reaches upload as `application/pdf`, contrary to the flow’s explicit unsupported-file rejection and ticket’s MIME detection requirement.

- **[P2] The 120-character title limit is bypassed by default titles.** [DocumentUpload.kt:211](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/documents/DocumentUpload.kt:211) preserves the complete filename-derived title. Truncation happens only during text edits (line 351); an untouched long filename, or clearing the title to trigger `suggested`, uploads more than 120 characters. Apply the limit to the effective submitted title.

- **[P2] Add is enabled before a type is selected.** [DocumentUpload.kt:387](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/documents/DocumentUpload.kt:387) supplies no enabled condition. The handler rejects the click afterward, but flow §§3, 6 and 9 require a disabled action until selection, including an accessible disabled reason.
---
**Answers (round 1, all fixed; each small, so no second round):**
1. *A second upload loses its draft:* fixed; Upload is disabled while one runs, and Add with a running upload keeps the draft. Tested.
2. *Demo uploads in every record:* fixed; kept per patient. Tested.
3. *Demo uploads open the sample PDF:* fixed; the demo keeps the uploaded bytes and type (read from the raw multipart body) and serves them back. Tested.
4. *Any file goes as a PDF:* fixed; a picked file is typed by its first bytes; only PDFs and JPEG/PNG images go, anything else says "Only PDF and image files can be uploaded." (an empty or too large file says that first). Tested with a renamed DOCX.
5. *Long default titles pass 120:* fixed; the limit applies to the title that is sent, however it came. Tested.
6. *Add enabled without a type:* fixed; Add is disabled until a type is chosen, with "Select a document type" under the choices as the reason.
