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
