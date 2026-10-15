# Document Vault for Android

Offline-first Android smart document cards and reusable information manager.

## Features

- **Material 3 card dashboard**: customizable fields, personal/family profiles, categories, tags, templates, search and copy-all.
- **Receive/Share**: import PDFs, DOC/DOCX, TXT, images or scans with the Android file picker or the Share menu (including CamScanner). Export selected originals to WhatsApp, email and other apps.
- **In-app reading**: page-by-page PDF, zoomable images, selectable extracted text for PDF/Office/text formats.
- **On-device text extraction**: digital PDF text via PDFBox, DOCX XML, legacy DOC via Apache POI, plain text; scanned PDFs/images via bundled ML Kit OCR models for Latin, Devanagari, Chinese, Japanese and Korean.
- **Offline extra languages**: install your own Tesseract `.traineddata` language file, e.g. `tel.traineddata` for Telugu, from Settings. Language data are not shipped by default.
- **OCR review**: extraction suggestions require review and confirmation; saved OCR text is searchable.
- **Documents and renewals**: multiple attachments per card, document expiry dates, renewal lineage, local reminders.
- **PDF tools**: merge, page range export, rotate, images to PDF, PDF with text layer for reviewed Latin OCR.
- **Secure storage**: encrypted original files and encrypted card database (Android Keystore AES-256-GCM), optional app PIN/biometric lock, encrypted password-backed portable archive.
- **No backend**: all app data stays on device unless user intentionally shares/exports it.

## Building

Go to **Actions > Android APK > Run workflow**, then download the `DocumentVault-debug-apk` artifact.

The workflow runs `gradle :app:assembleDebug`. Debug APK is installable for testing; not signed for Play Store distribution.

## Important constraints

- All PDF/image processing is local. ML Kit OCR models for its five bundled scripts are in the APK. Tesseract requires a separate trained language file to be added by the user.
- PDF/DOC/DOCX viewer provides readable extracted text; for Office files it is not a full-fidelity word processor rendering.
- The built-in camera capture is a starting point for scanned pages. Advanced automatic border detection/perspective correction is **not included yet**.
- Encrypted local data may be unrecoverable after uninstall or hardware-key loss without a password-protected exported backup. Back up first.
- Biometric/PIN is an app UI lock. File content is encrypted with Android Keystore; user PIN does not itself derive the file key.
- Legacy DOC extraction depends on Apache POI and some unusual files may fail. Preserve originals.
- Exporting a searchable PDF currently only embeds reviewed text in an invisible Latin text layer (no per-word coordinate alignment).
- Android may not guarantee the exact execution time of background expiry checks; notifications require notification permission on recent versions.
- This is a personal data utility, not a government-certified ID verification tool.

## Privacy

No internet permission, no cloud account, and no analytics SDK. Sharing produces a temporary decrypted file in app cache for Android's share sheet; cache is periodically cleaned. App screenshots are protected using FLAG_SECURE.

## Dependencies / licenses

AndroidX / Jetpack Compose, Google ML Kit, Tesseract4Android, Apache PDFBox-Android, Apache POI. Check each dependency's license before redistribution.
