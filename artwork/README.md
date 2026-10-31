# DocShelf icon

DocShelf is the user-selected display name for the offline document organiser, replacing Document Vault. The package ID, document storage paths, preferences and backup format remain unchanged so existing installations can update without migration.

The new isometric artwork shows an open teal folder with paper documents, a card and an amber index tab. It intentionally contains no vault, lock, shield or encryption imagery.

- Master transparent artwork: `app/src/main/res/drawable-nodpi/ic_docshelf_isometric.png` (generated with the imagegen skill).
- Adaptive foreground: `ic_docshelf_foreground.xml`, with 16dp insets on the 108dp adaptive canvas.
- Background: `#112C3A`.
- Themed icon: a separate monochrome folder/document vector.
- `DocShelf-launcher-preview.png` previews the circular launcher mask at 512px. The launcher controls the final mask and optional themed tint.

Only the visible branding and icon resources change. Existing optional app-lock behavior and the explicit unencrypted-storage description in About remain accurate.
