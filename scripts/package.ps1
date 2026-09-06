param(
    [switch]$Offline,
    [switch]$SkipVerification,
    [switch]$Installer,
    [ValidateRange(1, 16)][int]$MaxWorkers = 2
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'gradlew.bat'
$releaseRoot = Join-Path $root 'build\release'
$gradleMode = @('--project-dir', $root)
if ($Offline) { $gradleMode += '--offline' }

if (-not $SkipVerification) {
    & (Join-Path $PSScriptRoot 'verify.ps1') -Offline:$Offline -MaxWorkers $MaxWorkers
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$tasks = @(':app:assembleRelease', ':app:assembleDebug', ':pc:packageUberJarForCurrentOS', ':pc:createDistributable')
if ($Installer) { $tasks += ':pc:packageMsi' }
& $gradle @tasks @gradleMode --no-daemon "--max-workers=$MaxWorkers" --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

function Get-ApkArtifact {
    param([string]$Variant)

    $variantDirectory = Join-Path $root "app\build\outputs\apk\$Variant"
    $metadataPath = Join-Path $variantDirectory 'output-metadata.json'
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
        throw "Falta el metadata del APK: $Variant ($metadataPath)"
    }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $elements = @($metadata.elements)
    if ($elements.Count -eq 0) { throw "El metadata del APK $Variant no contiene artefactos." }
    $allowedPrefix = [System.IO.Path]::GetFullPath($variantDirectory).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    foreach ($element in $elements) {
        $outputFile = [string]$element.outputFile
        $versionName = [string]$element.versionName
        if ([string]::IsNullOrWhiteSpace($outputFile) -or $versionName -notmatch '^[0-9A-Za-z][0-9A-Za-z.+-]*$') {
            throw "Metadata incompleto o version invalida en $metadataPath"
        }
        $artifactPath = [System.IO.Path]::GetFullPath((Join-Path $variantDirectory $outputFile))
        if (-not $artifactPath.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            [System.IO.Path]::GetExtension($artifactPath) -ne '.apk') {
            throw "El metadata apunta fuera de la carpeta de APK: $outputFile"
        }
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            throw "Falta el APK declarado en metadata: $artifactPath"
        }
        [PSCustomObject]@{ Path = $artifactPath; Version = $versionName }
    }
}

$apkArtifacts = @('release', 'debug' | ForEach-Object { Get-ApkArtifact -Variant $_ })
$versions = @($apkArtifacts | ForEach-Object { $_.Version } | Sort-Object -Unique)
if ($versions.Count -ne 1) { throw 'Los APK debug y release tienen versiones diferentes. Vuelve a construir ambos.' }
$version = $versions[0]
$jarDirectory = Join-Path $root 'pc\build\compose\jars'
$jarPattern = '^Qetara-windows-[A-Za-z0-9_]+-' + [regex]::Escape($version) + '\.jar$'
$jarArtifacts = @(Get-ChildItem -LiteralPath $jarDirectory -File | Where-Object { $_.Name -match $jarPattern })
if ($jarArtifacts.Count -ne 1) {
    throw "Se esperaba un JAR Windows de Qetara $version y se encontraron $($jarArtifacts.Count). Revisa $jarDirectory"
}
$sources = @($apkArtifacts | ForEach-Object { Get-Item -LiteralPath $_.Path })
$sources += $jarArtifacts[0]
if ($Installer) {
    $msi = Join-Path $root "pc\build\compose\binaries\main\msi\Qetara-$version.msi"
    if (-not (Test-Path -LiteralPath $msi -PathType Leaf)) { throw "Falta el instalador de esta version: $msi" }
    $sources += Get-Item -LiteralPath $msi
}
if (@($sources | Group-Object Name | Where-Object { $_.Count -gt 1 }).Count -gt 0) {
    throw 'Dos artefactos tienen el mismo nombre. No se publicaran sobrescribiendo otro archivo.'
}
$portableApp = Join-Path $root 'pc\build\compose\binaries\main\app\Qetara'
if (-not (Test-Path -LiteralPath (Join-Path $portableApp 'Qetara.exe') -PathType Leaf)) {
    throw "Falta la aplicacion Windows portable: $portableApp"
}

# Every run has its own directory. Previous releases are kept intact.
$runName = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$releaseDir = Join-Path $releaseRoot $runName
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
foreach ($artifact in $sources) {
    Copy-Item -LiteralPath $artifact.FullName -Destination (Join-Path $releaseDir $artifact.Name)
}
Compress-Archive -LiteralPath $portableApp -DestinationPath (Join-Path $releaseDir 'Qetara-windows-portable.zip')

foreach ($notice in @('LICENSE', 'THIRD_PARTY_NOTICES.md', 'licenses')) {
    $noticeSource = Join-Path $root $notice
    if (Test-Path -LiteralPath $noticeSource) {
        Copy-Item -LiteralPath $noticeSource -Destination (Join-Path $releaseDir $notice) -Recurse
    }
}
$checksumLines = foreach ($artifact in (Get-ChildItem -LiteralPath $releaseDir -File -Recurse -Force | Sort-Object FullName)) {
    $hash = Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256
    $relativePath = $artifact.FullName.Substring($releaseDir.Length + 1).Replace('\', '/')
    '{0}  {1}' -f $hash.Hash.ToLowerInvariant(), $relativePath
}
[System.IO.File]::WriteAllLines(
    (Join-Path $releaseDir 'SHA256SUMS.txt'),
    [string[]]$checksumLines,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "Paquetes Qetara $version disponibles en $releaseDir"
Write-Output 'El APK release requiere firma si no se configuro una clave; el APK debug es solo para pruebas.'
