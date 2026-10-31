# UX audit improvements

Continuation of the handoff on `release/ux-audit-improvements`, based on
`fix/ux-enhance-and-paging`. This branch retains the existing phase stack; it does
not merge anything into `main`.

## Implemented

- Selected page text fills an equivalent field in place, retaining its ID and
  profile links. Empty fields and template names are suggested first. The picker
  includes page previews, likely values, search and page labels.
- OCR reports how many details were actually filled. Reading text without filling
  fields produces a dismissible explanation and a Select from page action.
  Manually typed and selected values remain protected by the existing OCR merge.
- Home counts pages, Settings uses actual PDF page totals for the chosen person,
  and profile/search counts consistently say documents. Category totals refresh
  after category edits. Everyone has a group avatar; people have stable colors
  and two-letter initials.
- Camera and numeric import names receive readable document titles. Identical
  titles get the first available numbered suffix. Attachment filenames and IDs
  are unchanged. The OCR title policy recognizes these new generic titles.
- Storage Health starts with a friendly summary and records the date of successful
  backup exports. Older backups with no recorded date are described as unknown,
  not nonexistent. Results scroll on smaller screens. File-read failures appear
  as an unreadable document list rather than crashing the check.
- The editor has an expiry picker with Clear, an existing-category menu with New,
  one heading and a fixed Save/Cancel bar that stays accessible with the keyboard.
- PDF Tools offers Merge, Pages and Rotate & more tasks, defaulting to Pages for a
  single attachment. Existing export callbacks keep their original behavior.
- Cards defaults to list view, offers Recent/Name/Expiry sorting, and gives the
  grid/list controls 48 dp targets. Home recent-document previews have uniform
  frames. The dark dock remains dark and selected segments have stronger contrast.
- Home links to Backup. Identity previews are blurred on Android 12+ and covered
  on earlier versions; tapping opens the document. This is visual privacy, not a
  new lock or encryption feature.
- Copy all in a profile asks for confirmation when sensitive fields are included,
  then uses the existing PIN/clipboard protection. Decorative progress bars were
  removed. Identity documents with no expiry offer Add expiry date, and the
  document screen no longer repeats attachment totals.
- The merged manifest removes the three DataTransport scheduling/discovery
  components. INTERNET remains absent; WorkManager's ACCESS_NETWORK_STATE remains.
- Release branches trigger the existing APK workflow. The artifact is still named
  `DocumentVault-debug-apk` and uses the normal `.v2` debug application ID.

## Validation

- Windows JVM suite: 233 tests, 232 passed, one device-only PDF test skipped,
  no failures. Added coverage for field selection, suggestions, likely values,
  OCR filled counts/messages, title generation/collisions, avatar initials and
  backup-date success/failure behavior.
- Lint: 0 errors, 21 warnings. Debug and instrumentation APKs built successfully.
- Physical phone, fresh `.uxcheck` installation: 66 tests passed across seven class-isolated instrumentation
  runs, including five Compose UI workflows. The optional audit-seeding test was
  not run. Editor tests cover the fixed Save bar, category creation and clearing
  an expiry date using the picker.
- A combined instrumentation run stalled. Its corrupted-ZIP test passed alone,
  and the entire storage class passed separately. The unchanged test methods were
  then run by class in fresh processes; none were removed or disabled. Combined
  runner stability remains a limitation.
- Final device verification requires a fresh `.uxcheck` package. The older
  `.storagecheck` installation contains copied personal records and existing OCR
  state, so it is no longer treated as disposable by the test guards or runner.
- Early checks in that older installation saw metadata changes in three existing
  records (including generated audit fixtures and an OCR-processed record). All
  referenced attachment bytes were unchanged; no pre-existing records or referenced attachments were
  deleted. The copy was stopped and retained without overwriting possible newer
  edits. Its record preservation cannot be certified. The installed `.v2` app was
  never edited, cleared or uninstalled.
- Phone logs, fingerprints and the original handoff patch remain local ignored
  artifacts. No real-document screenshots were taken or published.

Use `-PvaultDebugSuffix=.uxcheck` for device APKs. The phone runner refuses to
continue when an existing test installation contains records; it never clears
them. For the complete suite, run each of `VaultStorageDeviceTest`,
`ReleaseChecksDeviceTest`, `ScanEnhanceNativeTest`, `ScannerPerformanceDeviceTest`,
`VaultImportDeviceTest`, `VaultOcrDeviceTest`, and `VaultWorkflowUiTest` separately
with the AndroidJUnitRunner `-e class com.snehith.documentvault.<class>` argument.

## Deferred

Guided OCR review with text highlighting, automatic sideways-scan rotation, a
duplicate finder, moving pages between documents, and hide-with-PIN remain outside
this change. These were optional follow-ups in the handoff.
