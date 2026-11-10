# Tracking: documents, forms and the clinical engine (B5)

Folders `ui/documents`, `ui/forms`; events in `data/tracking/events/DocumentsEvents.kt`. Every ViewModel that tracks
takes `tracker: Tracker = Tracking` as its last constructor parameter, except `ClinicalPanelViewModel`, where it comes
before `locale` because the tests pass the locale as a trailing lambda (developer guide §3).

The shared pieces are instrumented here once: the upload sheets (`DocumentUploadViewModel`) and the documents list
with its edit sheet (`PatientDocumentsViewModel`) send the same events from the patient record and from a visit.

## Events

`kind` is `pdf` / `image` / `other`, from the file's real type (`documentKind(mime)`); `category` is the
`DocumentCategory` as `trackName()` (`blood_test` … `form_025_2`); `form_type` is the form's `templateId` (`025-2`).

| Event | Properties | Fires in |
|---|---|---|
| `document_uploaded` | `kind`, `category`, `is_scan`, `pages_bucket` (scans only, `countBucket`), `is_retry` | `DocumentUploadViewModel.send`, on success |
| `document_upload_failed` | `kind`, `category` (null before the type is picked), `reason`: `empty_file` / `too_large` / `unsupported_type` / `scan_failed` / `toFailureReason()` | `pdf`, `finishScan`, `offer` (refused files), `send` (failed upload) |
| `document_upload_cancelled` | `kind`, `category` | `cancelUpload`, while an upload runs |
| `document_opened` | `kind` | `DocumentViewerViewModel.load`, file shown |
| `document_open_failed` | `reason`: `no_access` (locked) / `gone` / `empty_file` / `toFailureReason()` | `DocumentViewerViewModel.load` |
| `document_access_requested` | `from`: `list` / `viewer` | `PatientDocumentsViewModel.requestAccess`, `DocumentViewerViewModel.requestAccess` |
| `document_access_request_failed` | `from`, `reason`: `already_requested` (409) / `toFailureReason()` | same |
| `document_saved_to_device` / `document_save_to_device_failed` | `kind` | `DocumentViewerViewModel.save` |
| `document_edited` | `category` (the new one), `is_title_changed`, `is_category_changed` | `PatientDocumentsViewModel.saveEdit` |
| `document_edit_failed` | `reason` | same |
| `document_deleted` | `category` (also when it was already gone, 404) | `PatientDocumentsViewModel.delete` |
| `document_delete_failed` | `reason` | same |
| `form_saved` | `form_type`, `is_new` | `FormEditorViewModel`, end of the save pipeline |
| `form_save_failed` | `form_type`, `is_new`, `step` (`form` / `pdf` / `document` / `link`; none for `incomplete`), `reason`: `incomplete` / `pdf_not_made` / `toFailureReason()` | `FormEditorViewModel.save` and the pipeline's failed step |
| `form_signature_requested` | `form_type` | `FormEditorViewModel.requestSignature` |
| `form_signature_request_failed` | `form_type`, `reason`: `incomplete` / `toFailureReason()` (the silent save or the request) | same |
| `clinical_engine_applied` | `chips_bucket` (`countBucket` of the picked chips) | `ClinicalPanelViewModel.apply`, synthesis done |
| `clinical_engine_apply_failed` | `reason` | same |

No event uses `amountBucket`.

## Screens

None. `document` and `form` are the shell's routes; `patient_documents` and `patient_forms` are B4's
(`PatientDetailScreen`).

## Left out, and why

- **File size:** there is no shared size-range helper in `TrackProperties.kt`, so no size is sent. A refused file over
  25 MB is `document_upload_failed` with `reason = too_large`. A size range needs a shared function from the
  coordinator first (guide §3).
- **Source camera vs gallery:** both go through `DocumentUploadViewModel.photo`, so only `kind = image` and
  `is_scan` tell the sources apart. Splitting them would mean a new ViewModel entry point.
- **Share:** the documents and forms screens have no share action; saving to the device is tracked.
- **Never sent:** titles, file names, form answers, diagnoses, ICD-10 codes, disease ids, any record id.
- **No events for:** opening the sheets, the search box, ICD-10 search and pick, dental-chart edits, return visits,
  the discard question, Cancel on the upload sheet before an upload, and loading the lists (reads; their failures
  are `api_error`).
- An empty file is refused before its type is known, so its `kind` is `other`.

## Needs from the coordinator

- A shared file-size range (`sizeBucket`) in `TrackProperties.kt` if the owner wants upload sizes; then
  `document_uploaded` gets a `size_bucket`.
