# Document Vault — source audit and integrated redesign

**Baseline:** `main` at `e0af02ffdb6deca59f5e4e34965da04b6f207e4a`, application version **2.1.2** (versionCode 10).
**Branch:** `feature/premium-oled-ux-navigation`. This is a draft and MUST NOT be merged without explicit user approval.

## Existing implementation examined

- `MainActivity.kt`, `DocumentVaultApp.kt`, `Theme.kt`, `HubScreens.kt`, `NavyScreens.kt`, `VaultScreens.kt`, `FullDetailsPages.kt`, `MoreScreens.kt`, `PremiumSettings.kt`, `AttachmentManagerScreen.kt`
- `VaultRepository.kt`, `DocumentEngine.kt`, `DocumentCrop.kt`, `DocQuadModel.kt`, `ScanPrepare.kt`, `ScanOutputFiles.kt`, `ScanEnhance.kt`, `ScanControls.kt`, `VaultOcrWorker.kt`, `SmartDocuments.kt`, existing JUnit tests and `.github/workflows/android-apk.yml`.

### Verified from source (not physical device tests)

- Documents are indexed in local plaintext JSON; binary attachments are stored as local blobs. Rolling last-good index and ZIP backup paths exist. ZIP exports are **not encrypted**; optional PIN protects **UI only**.
- Offline OCR and extraction use existing `DocumentEngine` and `VaultOcrWorker`; WorkManager runs sequential jobs for **Get Details** across compatible attachments.
- Real document border detection with DocQuad model and OpenCV fallback, normalized corner drag, perspective crop, full-resolution image processing, scanner enhancement presets, and PDF tools are present. They are preserved.
- `ScanOutputFiles.newScan()` ensures the output parent exists before save; crop failure must not clear image/corners.
- The source manifest removes INTERNET permission, and CI checks the merged debug manifest.
- Source defects: code-like UI labels and all-monospace styling; Scan tab misplaced; route-state Back guesses; editor format with line numbers/JSON braces; small copy hit targets; unstructured file/search screens; imports could fall back to a new card when a specified attachment target was removed.

## Screen execution map

| Screen | Primary action | Navigation and code path |
|---|---|---|
| Home | Add Document | `HubHome` -> Add Document; real recent thumbnails and category links |
| Documents | Open document | `VaultNavCards` -> Detail; adaptable grid/list, categories, sorting |
| Add Document | Choose source | `NavyScanScreen`: camera/gallery/files/information card |
| Scan/Crop | Save processed page | `CropReviewScreen`; existing detector and enhancements |
| Document Detail | GET DETAILS | `VaultNavDetail`; full-attachment WorkManager route |
| Document Information | Copy / edit | `FullDetailsPage`; stable field IDs, copy one/selected/all |
| Select from Document | Save Field | `SelectDocumentTextPage`; OCR lines and named custom fields |
| Viewer | Read / share page | `DocumentViewerScreen`; native PDF and image rendering |
| PDF Tools | Combine into PDF | `PdfToolsScreen`; existing engine for combine, page extraction, rotate, searchable PDF |
| Search | Find matches | `VaultNavSearch`; 250-ms debounce, background search, matched excerpts |
| Settings | Adjust preferences | `PremiumSettings`; OLED/Graphite, PIN, OCR, family, reminders, backup |
| Backup & Restore | Export/restore | `BackupScreen` and repository; confirmation before destructive restore |
| Family Profiles | Add profile | `ProfilesScreen`; original profile IDs/data maintained |
| Expiry | Open document | `ExpiriesScreen`; expired, soon, valid, malformed dates |
| Document Editor | Save document | `CardEditorScreen`; original validation and fields remain |
| Attachments | View/manage file | `AttachmentManagerScreen`; real thumbnails, rename, reorder, confirmation |
| Lock & About | Unlock / disclosure | `UnlockScreen` and `VaultAboutScreen`; truthful privacy note |

All nested screens use the existing app scaffold with a saved caller stack and Back button; only four root tabs show bottom navigation. The centered Scan action opens the Add Document hub and **never immediately activates the camera**.

## Preservation and known limitations

- Existing storage serialization, existing imported files, attachment IDs, OCR engine, scanner model, native OpenCV fallback, PDF logic, profile/template records, WorkManager and backup protocol were not replaced.
- `ImportTargetPolicy` prevents importing into an unavailable destination card before reading new bytes.
- Scanner has an import-commit callback so failed saves preserve crop state; cache-parent fix already present.
- User must still confirm scanner, gallery/provider grant behavior, biometric unlock, OCR results, PDF text fidelity, restore of a real backup, orientation and large-font UX on a physical Android device. Static builds cannot prove hardware-dependent behavior.
- Some long-standing behaviors remain, including one scanned camera capture at a time with an add-page continuation, OCR worker queue constraints, and no true document encryption.
- More substantial state restoration and device coverage (especially external camera/process kill in mid-crop) remain QA risks. Do not claim these have been verified simply from CI.

## Automated checks

GitHub Actions `Android APK` on the draft PR runs:
`gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug`,
`gradle --no-daemon :app:lintDebug`,
and a merged-manifest assertion that `android.permission.INTERNET` is absent.
The workflow uploads the debug APK and a ZIP of complete changed source files when successful.
Added JUnit tests cover category/sort stability and import destination validation.

## Physical test checklist

1. Upgrade over an existing v2.1.2 test build; check cards, field IDs, attachments, custom categories, profiles, backup compatibility. Note release-vs-debug package IDs differ (`.v2` suffix for debug).
2. Navigate Home -> category -> card -> information -> OCR line -> Back repeatedly. Repeat from Search results, Settings -> Backup, and PDF Tools; confirm prior search text/scroll survives.
3. Tap center Scan: four choices appear. Add single/multiple gallery photos, SAF PDF/files, information-only card, and Add Page from an existing card; verify destination.
4. Take real camera photo; verify detection handles align and drag, rotate/enhance, Add Page, original retention option, save; delete cache camera subdirectory mid-crop and retry save.
5. Put the phone in landscape and background/kill/reopen at critical navigation, import, crop, OCR, and export steps.
6. GET DETAILS on multipage PDF and multiple attachments; confirm all supported source pages are processed sequentially and no duplicate OCR started; edit/copy individual, selected, all fields and test blank copy.
7. Test PDF render/zoom/pages, combine/reorder/export selected pages/rotate/searchable PDF, direct share with FileProvider and cleanup.
8. Test all font sizes, TalkBack labels, 48dp touch targets, keyboard insets, long names/values, no overlaps.
9. Export ZIP and restore with confirmation in a disposable test vault; inspect that the backup is readable plaintext and that user-facing privacy labels are accurate.
10. Enable PIN/reminder/biometric where available; confirm lock throttling, permission handling, and offline behavior with airplane mode enabled.


## 16 October device-report fixes: scan geometry, thumbnails and navigation

These changes follow a real device report with three scanned attachments showing rotated/cropped previews and unintended extra pages.

- Re-edit a page from its retained raw original (when available) using normalized corners and original rotation saved in plaintext JSON. A re-edit **replaces the selected page** at its existing list position; it does not append a new attachment. The raw file is referenced but not displayed as another page. Older cards may already contain extra visible originals; those are not automatically deleted.
- Manual crop shows enlarged grab targets, a live zoomed corner lens and preserves corners through in-editor rotation. Detect is an explicit action, not automatically repeated when re-editing a processed image.
- Main preview uses high-quality FIT decoding; smaller cards also FIT instead of center-cropping. Generated numeric scanner IDs are hidden from filenames shown to ordinary users.
- Attachment and card deletions publish Compose state atomically. Related source blobs are removed only when no document, including preserved-original references, still uses them.
- Viewer and crop no longer recycle bitmaps that might be drawn or filtered by native code when system Back is pressed. PDF renderer close is performed on an IO coroutine and synchronizes with native render.
- Recovery excludes intentionally preserved originals. ZIP backup and restore include their hidden blobs for newly cropped scans.
- Pure tests cover crop rotations and persistence, shared-original reference checks, sorted browsing and import target guards.

### Focused retest

1. Capture a document; drag all four crop corners using the magnifier; save; reopen crop and confirm the same region, including orientation.
2. Choose an existing page, Enhance/Crop, adjust settings, save, and verify the number of attachments does **not** increase. Verify the old rendered image is replaced and other pages are unchanged.
3. Rotate the crop four times: geometry returns to its original normalized coordinates. Verify Detect only runs when pressed.
4. Verify front/back page thumbnails show the whole image, not clipped by center-crop, and opening the full viewer looks comparably sharp.
5. Delete the currently selected attachment from Manage Attachments, delete the last attachment, and navigate Back repeatedly. Re-test deleting a document.
6. From crop and PDF viewer, press system Back during processing, while rapidly switching pages, and after loading large images. Observe crash logs if any remain.
7. Backup and restore a newly saved scan with Keep Original enabled; confirm its raw source can still be reopened and edited. Then disable Keep Original and verify no extra visible raw file appears.
8. Enable rotation/process recreation and repeat crop, viewer and delete tests. Real device logs are required to rule out other hardware-specific native crashes.
