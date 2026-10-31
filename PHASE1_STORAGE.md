# Phase 1 storage integrity

The index is the commit record. New indexes include OCR text inline, so an OCR
update and its document metadata share one synced, atomic file replacement.
Legacy sidecars are read only when the inline field is absent, then migrated on
the next successful commit. They remain available to the previous index.
Temporary indexes are never loaded. A malformed current index falls back to the
previous readable index; an unreadable vault stays protected against saves.

The previous index is replaced only after validating its document, profile and
template data. Attachment cleanup retains files referenced by that previous
index. Restore retains unlinked files for explicit recovery rather than deleting
them automatically. This can consume additional storage.

Restore validates the complete archive before committing its index. New format-2
archives require checksums for blobs and OCR. Legacy archives without manifests,
with blob-only manifests, or without indexed hashes remain accepted. Missing
legacy hashes and sizes are populated from validated payloads. Optional absent
scan originals produce warnings while visible pages remain intact.

Restore writes new blobs through synced temporary files and never overwrites an
existing attachment. A different payload with an existing ID is rejected with a
conflict error; identical payloads can be restored repeatedly. Interrupted
restores can leave recoverable unlinked files, while the previous index remains
usable. Export stages its ZIP before opening the selected destination. A provider
failure during the final destination copy can still leave a partial external ZIP;
Android document providers do not offer a universal atomic replacement contract.

Storage Health in Settings is read-only. It reports document/attachment counts,
missing visible attachments, unlinked imports, index readability and availability
of the previous index. It cannot determine whether an external backup still exists.

## Validation commands

Use JDK 17, Gradle 8.10.2 and Android SDK 35, matching the existing CI workflow:

```powershell
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Shared deterministic fictional fixtures run under Robolectric on the JVM and
AndroidJUnit4 on a device. Robolectric is a test-only dependency. Device tests
require the dedicated `.storagecheck` package and unique fixture directories;
the normal `.v2` debug installation may contain personal data and is not used.

```powershell
./scripts/run-storage-device-tests.ps1 -Serial '<connected device serial>'
```

The runner builds and installs only the isolated package. It never clears or
uninstalls an application. Instrumentation output and app-process logcat remain
under the ignored `app/build/device-storage-results` directory. Do not publish
phone logs or screenshots to public artifacts. Tests simulate write interruptions
and staging failures; they do not establish physical power-loss durability.

PR #19 remains separate. Its initial emulator run failed before tests because
`sdkmanager` was not on PATH on the macOS runner. Phase 1 does not alter that PR.

The broader import, OCR worker, scanner, navigation and 23-workflow UI regression
roadmap remains behind the Phase 1 gate.

## Validation recorded on 31 October 2026

- Windows: 148 JVM tests passed, no failures or skipped tests.
- Lint: passed with 0 errors and 19 warnings.
- Debug application and instrumentation APKs: assembled successfully.
- Merged debug manifest: no INTERNET permission.
- Physical Android 16 ARM64 phone: 22 storage contract tests passed in the isolated
  package. Storage Health was also opened through ADB and its displayed values
  checked; this is a UI smoke check, not a Compose UI regression suite.
- No emulator was started. The broad UI/scanner/OCR/WorkManager workflows remain
  unexecuted in this phase. Physical power interruption was not tested.

Device output and screenshots are local ignored build artifacts, not committed
or uploaded. Main and the pre-existing debug installation were not modified.
