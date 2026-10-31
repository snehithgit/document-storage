# Release candidate: phases 1–7

Base: `main` at `212e675` (v2.2.0, versionCode 11). The phases are stacked
branches: #20 (Phases 1–2 and the backup fix) → Phase 3 → 4 → 5 → 6 → 7.
Version numbers are unchanged; bump them when this candidate is approved.

## What changed, by phase

| Phase | Branch | Main fixes |
|---|---|---|
| 1 | `fix/phase-01-storage-integrity` | Atomic index with inline OCR, checksummed and preflighted backups, Storage Health; export no longer refuses legacy or recovered attachments with blank metadata |
| 2 | `fix/phase-02-import-lifecycle` (merged into Phase 1) | Validated, all-or-nothing multi-file imports, duplicate handling, safe orphan recovery |
| 3 | `fix/phase-03-ocr-preservation` | Empty OCR runs keep saved text; manual edits leave review and are never overwritten; blank fields keep their IDs |
| 4 | `fix/phase-04-document-visibility` | Person and category filters can no longer hide every document |
| 5 | `fix/phase-05-scanner-performance` | PDF pages render in proportion; rotated scans crop without a second full-size bitmap |
| 6 | `fix/phase-06-navigation-usability` | Plain-language errors; accurate restore note; labelled avatar buttons |
| 7 | `release/phase-07-regression-rc` | Compose UI workflow tests, release checks, this report |

## Validation recorded on 31 October 2026

- Windows JVM (Robolectric): 216 tests, 215 passed, 1 skipped (PDF rendering is
  device-only), 0 failed. Lint: 0 errors, 21 warnings (dependency-version notices
  and pre-existing items). Debug and release APKs assembled; the release APK is
  unsigned locally because no signing keys are on this machine.
- Merged release manifest: no `INTERNET`; `allowBackup=false`. WorkManager adds
  `ACCESS_NETWORK_STATE`, which cannot transmit data without `INTERNET`.
- Physical phone (TECNO LJ8, Android 16, arm64, 256 MB app heap), isolated
  `.storagecheck` package, Wi-Fi ADB: 62/62 instrumentation tests passed in one
  full run.

## Workflow coverage

| # | Workflow | Where verified |
|---|---|---|
| 1 | Create → save → reopen | Device UI (open, recreate) + device storage; creation through the editor UI not driven |
| 2, 6 | Owner change, including under a person filter | Device UI |
| 3, 5 | Category and field edits survive restart | Device UI |
| 4 | Rename keeps attachments | JVM |
| 7 | Delete a person, documents remain | Device repository |
| 8–11 | Multi-image, PDF/DOCX, add page, duplicates | Device import |
| 12 | Re-edit a scanned page without an extra page | Device repository (crop screen UI not driven) |
| 13 | Rotate / crop / enhance | Device engine (OpenCV), not the crop screen |
| 14, 15 | OCR saved; manual fields preserved | Device, real OCR worker |
| 16, 17 | Corrupt backup rejected; valid restore | Device storage |
| 18 | Delete one attachment, keep shared bytes | Device import |
| 19 | Search after update | JVM |
| 20 | Back and activity recreation | Device UI (rotation exercised as recreation) |
| 21 | Cancellation | Device import; OCR cancel not tested |
| 22 | Share filename and MIME | Device |
| 23 | Offline, no INTERNET | Device + release manifest |

## Compatibility

Old indexes (no `formatVersion`, OCR sidecars, missing hashes and sizes) and old
backups (no manifest, blob-only manifest) are covered by the storage contract on
both JVM and device. Each phase was installed over the previous one in the
`.storagecheck` package, keeping its data. An upgrade from the production
v2.2.0 install itself was not run, because the production app must not be touched.

## Known risks

- Twice, an instrumentation run on Wi-Fi ADB stalled with the test process idle
  (zero CPU): once before any test started and once at test 47. Neither stall
  recurred in the next run, and no test failed. Cause not determined; root
  would be needed for stack dumps.
- Not tested: physical power loss, the camera and system file picker, the crop
  screen UI, OCR in scripts other than Latin, and actual peak memory use.
- The release APK must be signed with the existing release key, or devices with
  v2.2.0 installed will refuse the upgrade.
