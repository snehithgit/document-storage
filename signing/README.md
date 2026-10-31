# Persistent DocShelf release signing

Install the **DocShelf-signed-release-apk** artifact for normal use. Its package is `com.snehith.documentvault`. Test builds are separate apps and do not update the signed release.

The release key was created once for this project. Its public certificate is committed here; its private key and passwords are not in Git.

Certificate SHA-256: `85d878e13a25a9515c8f7b67bf21d8cbc02e11433aed555ffe1562df2860f7c2`.

On the project owner's Windows machine, the private keystore and build configuration are under `%USERPROFILE%\.android\keystores\docshelf\`, restricted to that Windows user and SYSTEM. Keep a private backup of that directory. Do not regenerate the key: updates must keep the package ID and signing certificate, and use a version code at least as new as the installed build.

Local build:

```powershell
./scripts/build-signed.ps1 -Gradle <path-to-gradle.bat>
```

The helper reads the private configuration, supplies signing credentials only to the build process, and restores its environment afterward. GitHub Actions uses the same key through the encrypted repository secrets `VAULT_SIGNING_KEYSTORE_BASE64`, `VAULT_STORE_PASSWORD`, `VAULT_KEY_ALIAS`, and `VAULT_KEY_PASSWORD`.

The first switch from the old debug/test installations to this signed release requires uninstalling the old apps once. Subsequent signed releases update this app in place. Signing identifies the app publisher; it does not encrypt stored documents.
