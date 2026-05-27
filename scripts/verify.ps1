param(
    [switch]$Offline
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root "gradlew.bat"
$selfTestOut = Join-Path $root "build\qetara-selftest"
$gradleMode = @()

if ($Offline) {
    $gradleMode += "--offline"
}

New-Item -ItemType Directory -Force -Path $selfTestOut | Out-Null

& $gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :laptop:classes @gradleMode
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $gradle :laptop:run @gradleMode --args="--no-gui --token ABCD1234 --pin 123456 --self-test --no-receiver --no-interactive --out $selfTestOut"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
