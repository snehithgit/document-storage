# Document actions consistency

This change continues `release/ux-audit-improvements` without merging the branch stack into `main`.

## User-visible behavior

- Each opening of Edit details has its own saved form state. Switching documents cannot restore the previous document's form, and animated outgoing screens keep their own document identity.
- Enhancing a PDF replaces the selected attachment in the same document and keeps its other pages. Image enhancement also replaces in place. Saved fields and OCR text remain available; OCR is marked for review after an edit.
- Keep untouched scan original updates immediately and applies to enhancement as well as new scans. Missing retained originals fall back to the current image without applying the old crop twice. Turning the option off affects subsequent edits; the separate retained-originals action removes existing originals.
- Hide identity previews in Settings controls previews on Home, Cards (list/grid and wallet styles), and Search. Opening a document shows its content.
- Recovery offers individual removal and Clear all, both with confirmation. Permanent removal excludes active attachments and retained originals and checkpoints the current index before deleting bytes. This intentionally retires the older document-list recovery snapshot, as the confirmation explains.
- A document's More menu offers Merge documents and Split document. Both offer Copy and Move. Merge retains the destination's identity, owner, category and expiry, records source metadata in notes, and preserves profile field links on Move. Split creates one document per selected file or PDF page, keeps remaining pages in the source on Move, and removes the source only when none remain. When removing a source, profile links follow the first split document. Copied metadata should be reviewed for relevance to each page.

## Related protections

Document and profile-link changes commit together and roll back together if saving fails. Shared blobs remain protected while any document uses them. Stale selections and conflicting shared-attachment metadata are rejected. Merging earlier documents cannot create indirect renewal-history loops; the former link is retained in notes. Failed or cancelled splits remove generated outputs without changing source documents. PDF OCR page sections are remapped; legacy text with no page mapping is retained in notes rather than repeated as if it were page-specific. Splits are limited to 100 selected items and bounded metadata/index size.

## Validation

- Host regression suite: 256 tests, 255 passed, one existing device-only PDF test skipped; zero failures.
- New shared storage tests cover replacement, retained originals, permanent recovery removal, atomic merge/split, profile links, stale selections, cancellation and failed persistence.
- New Android PDF tests render generated red/green/blue pages to check selected-page enhancement and splitting with remaining pages.
- Expanded Compose tests cover editing different documents, activity recreation, immediate preferences, global preview visibility, merge Copy, split Move and recovery confirmation/refresh.
- Physical TECNO LJ8 phone: 93 tests passed across nine classes, each in a fresh instrumentation process: storage 29, release checks 3, native enhancement 3, scanner performance 2, imports 22, OCR 2, document actions 19, real PDF transformations 2, Compose workflows 11.
- Tests ran exclusively against `com.snehith.documentvault.uxcheck` using generated fictional fixtures. The global test document list contained zero records after cleanup. No emulator was used; `.v2` and `.storagecheck` were not installed over, cleared, uninstalled or edited.
- Two test-harness corrections were needed on-device: resolve FileProvider roots using the real application context while keeping repository storage scoped to the fixture, and scroll lazy lists to Settings/Recovery items before interacting with them. No production change was needed after the phone runs.

Local app/test APK builds passed. Lint reports zero errors and 21 warnings. The app-source revision `9b097eb` passed [CI](https://github.com/snehithgit/document-storage/actions/runs/41214753670), including APK upload. The follow-up test/report commit is validated by the same branch's Android APK workflow.
