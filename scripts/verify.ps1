param(
    [switch]$Offline,
    [switch]$DesktopOnly,
    [ValidateRange(1, 16)][int]$MaxWorkers = 2
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root "gradlew.bat"
$selfTestOut = Join-Path $root "pc\build\qetara-selftest"
$gradleMode = @('--project-dir', $root)

if ($Offline) {
    $gradleMode += "--offline"
}
if ($DesktopOnly) {
    $gradleMode += '-PqetaraDesktopOnly=true'
}

New-Item -ItemType Directory -Force -Path $selfTestOut | Out-Null

$tasks = @(':protocol:test', ':pc:test', ':pc:classes')
if (-not $DesktopOnly) {
    $tasks += @(':app:assembleDebug', ':app:lintDebug', ':app:testDebugUnitTest')
}

& $gradle @tasks @gradleMode --no-daemon "--max-workers=$MaxWorkers" --console=plain
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $gradle :pc:run @gradleMode --no-daemon "--max-workers=$MaxWorkers" --console=plain --args='--no-gui --token ABCD1234 --pin 123456 --self-test --no-receiver --no-interactive --out build/qetara-selftest'
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Output 'Qetara: compilacion, pruebas y transferencia local verificadas.'
