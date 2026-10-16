# Document Vault v2.1 — Editor Black

Monochrome code-editor-inspired workspace; appearance and import navigation only.
The existing offline vault, OCR, scan, PDF tools, local JSON index, ZIP backups,
and interface PIN remain unchanged. Files and ZIP backups are not encrypted.

## Changes
- Graphite/OLED black backgrounds, white text, subtle cyan metadata accents,
  bordered panels, monospaced field labels and a compact dark dock.
- System/Graphite/OLED choices stay dark; OLED black is the default.
- The dock Scan (+) opens the Scan hub, instead of immediately launching camera.
- Scan hub has four visible commands: Camera, Gallery photos (multiple),
  Files/PDF picker, and Create manual information card.
- Card → Add supports Camera / Gallery / Files for additional pages.
- Full Details is styled as a details.json editor with line numbers, readable
  key/value syntax, per-field Copy and Edit, field selection and ordering,
  and a fixed Copy Selected / Copy All bar.
- Quick Copy in Document Detail shares the same syntax-style presentation.

## Manual acceptance checks
1. Open Scan from the floating + dock button; verify Gallery and Files/PDF.
2. Import two photos via Gallery and one PDF via Files; originals are kept.
3. Open card → Add; import a second page from Gallery without replacing first.
4. In Full Details, add fields, copy single value, Copy Selected, Copy All,
   rearrange and verify selection still references correct fields.
5. Switch between Graphite and OLED; ensure no cream, orange or purple UI.
6. Retest scanning/crop, PDFs, OCR, share, backup and PIN.
7. CI: testDebugUnitTest, assembleDebug, lintDebug and offline manifest check.

No new dependencies and no INTERNET permission.

---

# Document Vault v2.0 — Phases 1–4 integration

**Status:** Built using GitHub Actions. On-device camera/scan accuracy still requires user testing.

The original fast v1.2 plaintext, local-only architecture is retained. The app
has no INTERNET permission and no account or sync server. **The UI PIN does not
encrypt documents, the JSON index, or ZIP backups.**

## Phase 1 — Document wallet design

- Warm cream / orange light theme, warm charcoal dark theme and system default.
- Material 3, rounded cards, floating four-tab navigation and separate Scan action.
- Home vault summary, categories, expiry attention and recent cards.
- Cards, Search, Full Details and Settings remain part of the same navigation.

## Phase 2 — Offline scanner

- Bundled MakeACopy DocQuadNet-256 model through ONNX Runtime with DocuScan
  OpenCV border-detection fallback. No network or cloud inference.
- Auto-edge detection, crop-corner review, perspective correction, rotate,
  and photo enhancements including document-friendly presets.
- Multi-page capture with add-page flow and user choice to keep the original.
- Enhance/Crop available for existing gallery image attachments, preserving
  the imported original.
- See THIRD_PARTY_SCAN_NOTICES.md for Apache 2.0 / MIT attributions.

## Phase 3 — Smart extraction

- Offline recognition of Passport MRZ, Aadhaar (Verhoeff), PAN, driving
  licences and Seafarer CDC.
- Passport ICAO MRZ checks, strict ISO date conversion, meaningful field names
  and confidence indicators.
- Full Details exposes editable/copyable OCR fields (copy one/selected/all).
- Validation and OCR extraction remain heuristics, **not identity verification**.

## Phase 4 — Reliability / version tools

- **Manage individual attachments** from a card: rename, move up/down, remove.
  Files shared with other cards are preserved.
- OCR runs in WorkManager with per-page progress and Cancel. It can continue
  after navigation. Extracted fields are added as suggestions; **review before use**.
- Search queries, filters and screen state use rememberSaveable and a screen
  state holder.
- Restores clean obsolete blobs only after committing the new vault index.
  A rolling vault.json.bak retains the previous good index.
- Settings → About shows installed app version and offline privacy information.

## Install, transfer and verification

The GitHub Actions **v2.0 debug APK** installs alongside previous test editions
with suffix .v2; it does not automatically share data with them. Create a ZIP
backup in your old app, then restore the ZIP inside v2.0 after first backing up
any v2.0 data.

Run the GitHub Actions Android APK workflow. It executes
testDebugUnitTest, assembleDebug, lintDebug, plus a merged-manifest assertion
that INTERNET permission is absent.

**On-device checklist:** auto-crop a photographed notebook with a side shadow;
rotate a portrait ID; check enhancing a gallery import keeps its original;
scan and reorder several pages; OCR a multi-page PDF and cancel midway; verify
text extraction and expiry; rename/reorder/delete an individual attachment;
restart while WorkManager is running; try Copy All/Selected; and test ZIP backup.

**Known limitations:** OCR field candidates must be reviewed, scanner corners
can require manual adjustment, WorkManager cancellation may wait for an
in-flight ML Kit recognition call, and native ONNX/OpenCV libraries enlarge
the APK. Never treat parsed identity fields as proof of authenticity.

---


## v1.3 Premium Navy UI + Full Details

This release continues to use the **fast local plaintext** storage, sharing and offline OCR engine introduced in v1.2. All files remain on device unless you explicitly share or export.

### Updated screens
- Premium navy Home dashboard, actual document thumbnails, compact action buttons and validity indicators.
- Two-column Cards library with thumbnails, document types, people and expiry.
- Smarter Search matching document titles, saved fields and OCR text.
- Document Detail with attachment/page strip, add button, quick copy, View, OCR, Share and PDF Studio.
- **Full Details**, a dedicated per-card information page. Add custom fields, select individual text lines from a document's OCR output, edit/remove/reorder fields, and Copy Individual / Copy Selected / Copy All. Field records are saved with the card.
- Compact grouped navy settings with OCR language, lock, backup and family options.
- Scan hub with **offline OpenCV** border detection, four draggable crop corners and perspective correction after camera capture. Originals can be retained if the crop is unsuitable.

### Multi-page / combined PDF
Open a card → **Add** → choose **Scan new page** or **Import files**. Open its **PDF** action, select/reorder PDFs or scanned images, then **Create one PDF**. Select a destination in Android's file picker. The combined PDF is also imported back into the same card; originals remain unchanged.

### Installation & migration
The GitHub Actions v1.3 **debug APK uses a separate test package** ending in `.navy`. It installs alongside v1.2 to protect sample documents and avoid issues caused by changing GitHub debug signing keys. For v1.2 data transfer, create a ZIP backup from v1.2 → open v1.3 → Settings → Backup & restore → Restore ZIP. Only restore after backing up any v1.3 data, since restore replaces its current card index.

The UI is a **minimalist premium navy theme** with blue action accents rather than strongly pixelated 8-bit typography. The app remains a personal offline utility, not a government identity verification application.

### Remaining verification
- On-device camera/OCR/crop behavior and PDF combining should be manually tested with multi-page samples; CI only compiles and runs JVM unit tests.
- OpenCV increases APK size due to native libraries.
- Automatic crop is contour-based and can fail on low-contrast backgrounds; manual corner adjustment is always available.
- Full Details currently picks from OCR text lines. Direct finger-selected bounding-box extraction on the visible document is not yet implemented.


# Document Vault – Fast Plaintext Edition v1.2

A native Android (Kotlin + Jetpack Compose) offline document card manager.

## What changed in v1.2

- **No file or database encryption:** attachments are stored verbatim in app-private storage; card records are stored as a plain JSON index.
- **Instant sharing:** use Android's read-only FileProvider URI directly. No decrypt-and-copy job before opening WhatsApp or Android's share chooser; repeat taps are coalesced.
- **Fast viewer:** no temporary decrypted copy; reuses an in-app PDF renderer with a two-page render cache. PDF page rendering remains proportional to page complexity.
- **Fast import:** only copy and index the incoming file. Scanned document OCR remains available with a dedicated OCR button and is intentionally not performed automatically at import time.
- **Unencrypted ZIP backup and restore:** standard ZIP archive including index.json and original files, with no password. Do not upload or share the backup publicly.
- Plaintext edition has its own Android test package (`com.snehith.documentvault.fast`) to avoid modifying older encrypted test installations.

## Included features

Card grids, freeform fields, custom card templates, profiles, search and copy buttons, camera capture, incoming Android share, native PDF and image viewer, selectable text display, bundled offline ML Kit OCR scripts, optional externally supplied Tesseract traineddata language packs, manual OCR review and source text, expiry notifications, PDF merge/split/rotation/image-to-PDF, plain ZIP backups.

## Build

GitHub Actions → **Android APK** workflow → run or open latest successful job → download `DocumentVault-debug-apk`. CI runs JVM unit tests before `assembleDebug`.

Release builds support an optional stable signing setup from `VAULT_SIGNING_KEYSTORE_BASE64`, `VAULT_STORE_PASSWORD`, `VAULT_KEY_ALIAS` and `VAULT_KEY_PASSWORD` GitHub Actions secrets. Keep the signing key backed up outside GitHub.

## Privacy and migration

**Plaintext means exactly that.** An optional PIN/biometric app lock controls the app UI only; it is not encryption, and rooted devices or backups with filesystem access may read these files. Android still keeps app-private files inaccessible to ordinary other apps, and the FileProvider exposes only an intentionally shared document.

The v1.2 debug package **does not open or migrate data from the earlier encrypted debug/audit packages**, because Android isolates each app's private data. Preserve the earlier app if you want to keep its sample documents. An earlier password-protected `.dvault` backup is not supported by this plaintext ZIP restore path. You can separately share/export selected files from the old app and import them here.

The app's camera capture currently uses the Android camera intent, not advanced automatic perspective correction. DOC/DOCX are read as extracted text, not full-fidelity rendered Office pages. OCR recognition quality varies with resolution and language pack. Files and PDF page rendering can still take time on very large or complex documents; there are no measured hardware latency guarantees.

## Android file lifecycle

Import: SAF content URI → one copy into `files/blobs/<uuid>.dat` → SHA-256 duplicate check → atomic JSON index write.
View: direct local file from `files/blobs`; PDF renderer created once per active viewer.
Share: `FileProvider` URI for original file with the original display name; temporary read permission.
Backup: regular ZIP with `index.json` and `blobs/<uuid>`.

No network permission or sync service is intentionally declared by the application itself. Validate the final merged Android manifest in CI if you need strict assurance about dependency permissions.
