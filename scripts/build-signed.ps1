param(
    [string]$Gradle = "gradle",
    [string]$SigningConfig = (Join-Path $env:USERPROFILE '.android\keystores\docshelf\docshelf-signing.json')
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $SigningConfig -PathType Leaf)) { throw 'DocShelf private signing configuration is missing. Do not generate a replacement key for an existing installation.' }
$config = Get-Content -LiteralPath $SigningConfig -Raw | ConvertFrom-Json
if (!(Test-Path -LiteralPath $config.keystore -PathType Leaf)) { throw 'The persistent signing keystore is missing.' }
$values = @{
    VAULT_KEYSTORE_PATH = $config.keystore
    VAULT_STORE_PASSWORD = $config.storePassword
    VAULT_KEY_ALIAS = $config.keyAlias
    VAULT_KEY_PASSWORD = $config.keyPassword
}
$previous = @{}
foreach ($name in $values.Keys) {
    if ([string]::IsNullOrWhiteSpace($values[$name])) { throw "Missing signing value: $name" }
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    foreach ($name in $values.Keys) { [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process') }
    & $Gradle --no-daemon :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Signed release build failed.' }
} finally {
    foreach ($name in $values.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
