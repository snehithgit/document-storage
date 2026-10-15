# Document Vault — Phase 0 audit and reliability repairs

Branch: `fix/phase0-reliability-audit`. Scope: Phase 0 only; no Phase 1 visual redesign.
Pinned AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 26.
The build-time JitPack repository stays because the existing Tesseract4Android dependency needs it.
The installed APK does not need internet. Vault files/index and ZIP backups remain **unencrypted**; PIN is a UI lock only.

## Item-by-item results

| Item | Initial code check | Status | Files |
|---|---|---|---|
| 0.1 EXIF orientation | EXIF applied in scaledBitmap but thumbnail had a separate decode path | Fixed: shared sampled EXIF-aware ImageDecode used by viewer, thumbnail, OCR, PDF and crop | ImageDecode.kt, DocumentEngine.kt, NavyScreens.kt, DocumentVaultApp.kt |
| 0.2 Gallery imports | Imported file bytes already preserved without entering the scan pipeline | Already fixed for Phase 0; Enhance/Crop action deliberately deferred to Phase 2 | VaultRepository.kt (readability only) |
| 0.3 Full-resolution crop | Crop used 2050px bitmap as its source | Fixed: full image decoded for perspective crop, downsample only preview/detection. Very large images over 32 megapixels intentionally rejected rather than crashing | DocumentVaultApp.kt, ImageDecode.kt, DocumentCrop.kt |
| 0.4 Bitmap OOM and lifecycle | Thumbnails used a different decoder; intermediate bitmaps not always scoped; OpenCV map disposal incomplete | Fixed: bounded sampling, image recycling, 16MB LruCache, PDF viewer session closure and native Mat cleanup | ImageDecode.kt, NavyScreens.kt, MoreScreens.kt, DocumentEngine.kt, DocumentCrop.kt, Cleanup.kt |
| 0.5 Searchable PDF | Whole OCR text repeated on all pages | Fixed: per-page text markers; if absent for multi-page scanned PDFs, require fresh OCR rather than inventing page mapping | DocumentLogic.kt, DocumentEngine.kt |
| 0.6 OCR engine per page | Previously created ML Kit or Tesseract repeatedly | Fixed: create OCR session once per extraction run; close in finally/use | DocumentEngine.kt |
| 0.7 OCR progress and cancel | Callback never reached screen | Fixed: actual per-page progress and cancel control. Blocking recognition of the current page may finish before cancel takes effect | DocumentEngine.kt, DocumentVaultApp.kt |
| 0.8 Image PDF geometry | PDF dimensions were raw bitmap pixels | Fixed: A4 PDF points, proportional fit, white background | DocumentLogic.kt, DocumentEngine.kt |
| 0.9 PDF renderer lifetime | A renderer was cached but closure/races needed hardening | Fixed: always close FD; prevent double close and late render; session closed on navigation | DocumentEngine.kt, MoreScreens.kt |
| 0.10 Restore orphan blobs | Old unreferenced blobs survived restore | Fixed: commit restored index first, then sweep blobs/.meta absent from new card references | VaultRepository.kt |
| 0.11 Restore input bounds | 25MB index and strict blob path regex already present, but oversized index read could consume memory first | Fixed: streaming 25MB limit and explanatory error, reject unsupported path entries; optional filenames listing is separately bounded | VaultRepository.kt |
| 0.12 Attachment CRUD | Per-file operations missing | Fixed: synchronized removeFile, renameFile, reorderFiles; index commit before physical delete. UI controls remain in Phase 4 | VaultRepository.kt |
| 0.13 Previous index recovery | No maintained `vault.json.bak` | Fixed: atomically staged rolling previous-good index and fallback load | VaultRepository.kt |
| 0.14 Stable field identity | Copy Selected stored row indexes | Fixed: stable field UUID persisted in JSON, migrated old cards on load; UI selection by ID | VaultRepository.kt, FullDetailsPages.kt, VaultScreens.kt |
| 0.15 OCR line selection | UUID-looking file chip and silent 350-result cutoff | Fixed: display title and explicit truncation/refine-search message | FullDetailsPages.kt, DocumentLogic.kt |
| 0.16 Dead Settings controls | Scanner quality and category/tag controls did nothing/redirected | Fixed: removed dead rows without changing the look of other settings | PremiumSettings.kt |
| 0.17 Human-readable file titles | Captured file UUID leaked into title/chips/sharing | Fixed: dated Scan title and display labels, plus readable ZIP backup `filenames.txt` | VaultRepository.kt, DocumentLogic.kt, MainActivity.kt, FullDetailsPages.kt, NavyScreens.kt |
| 0.18 State preservation | Some screen, editor and dialog values lost on recreation | Fixed: rememberSaveable and SaveableStateProvider; removed configChanges override. Crop preview is recreated from saved camera file | DocumentVaultApp.kt, VaultScreens.kt, FullDetailsPages.kt, NavyScreens.kt, MoreScreens.kt, DocumentCrop.kt, AndroidManifest.xml |
| 0.19 Search/thumbnails off main | OCR search snippets and thumbnail decode were on UI thread, no cache/debounce | Fixed: Dispatchers.Default search + 250ms debounce, Dispatchers.IO thumbnail decode, bounded LruCache | NavyScreens.kt |
| 0.20 Singular/plural | Hardcoded "1 files"/"1 documents" strings | Fixed: VaultCounts and Android plural resources, applied to card/search/reminder displays | DocumentLogic.kt, NavyScreens.kt, VaultScreens.kt, MoreScreens.kt, ExpiryWorker.kt, plurals.xml |
| 0.21 Reminders | System drawable; invalid dates silently skipped; retry unbounded | Fixed: monochrome notification icon, explicit Android 13+ permission request when saving reminders, invalid dates surfaced in expiry UI, bounded retries | ExpiryWorker.kt, DocumentVaultApp.kt, VaultScreens.kt, ic_notification.xml |
| 0.22 Manifest and app icon | No custom adaptive icon, backup disabled but no Android 12+ extraction rules | Fixed: adaptive/monochrome icon, disallow cloud/device transfer, remove merged INTERNET, assert in CI | AndroidManifest.xml, ic_launcher.xml, ic_vault_foreground.xml, launcher_colors.xml, data_extraction_rules.xml, android-apk.yml |
| 0.23 Build tooling | Test and assemble already ran; lint and manifest assertion absent. JitPack seemed redundant | Fixed lint and offline manifest checks. JitPack **retained** because Tesseract 4.9.0 could not resolve without it | settings.gradle.kts, android-apk.yml |
| 0.24 PIN | Cold-start PIN and 60s background relock existed, but failures unthrottled | Fixed PIN attempt throttling, correct-PIN removal and lock-screen explanation. Background grace remains 60 seconds to avoid interruptions while using file picker/camera | VaultRepository.kt, DocumentVaultApp.kt, MainActivity.kt |

## Deliberate deferrals / limitations

- The gallery **Enhance/Crop** command and CamScanner-style filtering belong to Phase 2, not Phase 0.
- Attachment rename/delete/reorder repository methods are implemented; interactive file-management controls belong to Phase 4.
- OCR cancellation is cooperative between pages. A synchronous ML Kit recognition call cannot always be interrupted mid-page.
- Cropping supports ordinary high-resolution photos with a 32MP safety cap; extremely large inputs show an error instead of risking out-of-memory.
- Reminder permission requires user approval on Android 13+. Reminders cannot show notifications if permission is denied.
- A PIN provides no cryptographic protection: documents, the JSON index and ZIP backups remain plaintext.
- CI validates compilation, JVM tests, lint, and manifest. Camera scan accuracy, rotation recreation, backup/restore and gallery sharing still need manual device testing.

## Test checklist

- Install as isolated `.phase0` debug app; keep the older test app installed and unchanged.
- Import an EXIF-rotated portrait JPEG. Check thumbnail, viewer, OCR and image-to-PDF all show upright content.
- Scan a portrait page; adjust corners, rotate, crop and verify the saved output retains normal high resolution.
- Import PDF, image and DOCX documents, reopen, share and verify original contents.
- Run OCR on multiple PDF pages: confirm progress, cancel and retry; export searchable PDF and check page-specific text only.
- Create two identical blank fields; select one in Full Details, reorder/delete rows and check Copy Selected remains correct.
- Rename/reorder/remove an attachment using repository methods and verify other cards do not lose shared blobs.
- Export an unencrypted ZIP backup; confirm `index.json`, `filenames.txt`, and `blobs/*`; restore over a different card set, restart and confirm obsolete blobs are removed.
- Interrupt a JSON save, simulate corrupt `vault.json`, and verify `vault.json.bak` recovery (test with sample data).
- Search many OCR fields while typing; confirm debounce and no main-thread freezes.
- Rotate while editing, searching, viewing and crop-reviewing. Check scroll, filters, selected card and unsaved fields survive.
- Set PIN, restart, fail five attempts to trigger delay, unlock correctly, background longer than 60 seconds, and remove PIN using the current PIN.
- Enter invalid ISO expiry; expect a correction message; check valid reminders with notification permission allowed and denied.
- Confirm installed app lacks INTERNET permission, no auto-backups, and screenshots behave per Settings privacy toggle.
- Run GitHub Actions `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` and merged-manifest permission assertion.

**Phase boundary:** Stop after this report and test APK. Do not implement Phase 1 until separately requested.
