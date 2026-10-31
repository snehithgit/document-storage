param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Gradle = "gradle",
    [string]$Adb = "adb"
)
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & $Gradle --no-daemon "-PvaultDebugSuffix=.uxcheck" :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Test APK build failed" }
    # This runner never clears or uninstalls any package. The test itself also
    # refuses other application IDs and uses unique disposable fixture directories.
    $manifest = Get-Content "app/build/outputs/apk/debug/output-metadata.json" -Raw | ConvertFrom-Json
    if ($manifest.applicationId -ne "com.snehith.documentvault.uxcheck") {
        throw "Refusing to install an APK without the isolated uxcheck ID"
    }
    $installed = & $Adb -s $Serial shell pm list packages com.snehith.documentvault.uxcheck
    if ($installed) {
        $saved = & $Adb -s $Serial shell run-as com.snehith.documentvault.uxcheck cat files/vault.json 2>$null
        if ($LASTEXITCODE -eq 0) {
            try { $index = ($saved -join "`n") | ConvertFrom-Json }
            catch { throw "Cannot verify the test app's data. Nothing was cleared or installed." }
            if ($null -eq $index.cards -or @($index.cards).Count -gt 0) {
                throw "The test app contains records. Use an empty disposable test installation; no data was cleared."
            }
        }
    }
    & $Adb -s $Serial install -r "app/build/outputs/apk/debug/app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw "App install failed" }
    & $Adb -s $Serial install -r "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if ($LASTEXITCODE -ne 0) { throw "Test install failed" }
    $results = "app/build/device-storage-results"
    New-Item -ItemType Directory -Force $results | Out-Null
    $output = & $Adb -s $Serial shell am instrument -w -r -e class com.snehith.documentvault.VaultStorageDeviceTest com.snehith.documentvault.uxcheck.test/androidx.test.runner.AndroidJUnitRunner
    $output | Set-Content "$results/instrumentation.txt"
    $output | Write-Output
    # Instrumentation may terminate its process before returning. UID filtering
    # still captures only the isolated app's logs after that process exits.
    $packages = & $Adb -s $Serial shell pm list packages -U com.snehith.documentvault.uxcheck
    $appUid = [regex]::Match(($packages -join "`n"), '(?m)^package:com\.snehith\.documentvault\.uxcheck uid:(\d+)\r?$')
    if ($appUid.Success) {
        & $Adb -s $Serial logcat -d "--uid=$($appUid.Groups[1].Value)" -t 500 | Set-Content "$results/logcat.txt"
    }
    if (($output -join "`n") -notmatch 'OK \(\d+ tests?\)') {
        throw "Device tests failed or did not complete; see $results/instrumentation.txt"
    }
} finally { Pop-Location }
