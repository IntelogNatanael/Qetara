param(
    [switch]$Offline,
    [switch]$SkipVerification,
    [switch]$Installer,
    [string]$RuntimeSourceDirectory = $env:QETARA_RUNTIME_SOURCE_DIR,
    [ValidateRange(1, 16)][int]$MaxWorkers = 2
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'gradlew.bat'
$releaseRoot = Join-Path $root 'build\release'
$gradleMode = @('--project-dir', $root)
if ($Offline) { $gradleMode += '--offline' }

# Verifies the supplied bundle; it never downloads or changes its contents.
function Read-RuntimeSourceBundle {
    param([string]$Directory)
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        throw "Faltan los fuentes del runtime. Usa -RuntimeSourceDirectory <Qetara-third-party-source> o QETARA_RUNTIME_SOURCE_DIR con el paquete correspondiente al JDK de empaquetado."
    }
    $sourceRoot = (Get-Item -LiteralPath $Directory).FullName.TrimEnd('\', '/')
    $sourcePrefix = $sourceRoot + [System.IO.Path]::DirectorySeparatorChar
    $allItems = @(Get-Item -LiteralPath $sourceRoot) + @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Force)
    if (@($allItems | Where-Object { $_.Attributes -band [System.IO.FileAttributes]::ReparsePoint }).Count -gt 0) {
        throw 'El paquete de fuentes contiene enlaces o puntos de reanalisis. Usa una copia normal de sus archivos.'
    }
    $manifestPath = Join-Path $sourceRoot 'SHA256SUMS.txt'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Falta SHA256SUMS.txt en los fuentes del runtime.' }
    $entries = @{}
    foreach ($line in (Get-Content -LiteralPath $manifestPath)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^([0-9a-fA-F]{64})  (.+)$') { throw 'Formato invalido en SHA256SUMS.txt de los fuentes del runtime.' }
        $expectedHash = $Matches[1].ToLowerInvariant()
        $relative = $Matches[2]
        if ([System.IO.Path]::IsPathRooted($relative) -or $relative -match '[:\\]' -or
            @($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -gt 0 -or
            $entries.ContainsKey($relative) -or $relative -eq 'SHA256SUMS.txt') {
            throw "Ruta invalida o repetida en el manifiesto de fuentes: $relative"
        }
        $filePath = [System.IO.Path]::GetFullPath((Join-Path $sourceRoot $relative))
        if (-not $filePath.StartsWith($sourcePrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $filePath -PathType Leaf)) { throw "Falta un archivo declarado en los fuentes: $relative" }
        $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) { throw "SHA-256 no coincide en los fuentes del runtime: $relative. Recupera el paquete original antes de empaquetar." }
        $entries[$relative] = $expectedHash
    }
    foreach ($file in ($allItems | Where-Object { -not $_.PSIsContainer })) {
        $relative = $file.FullName.Substring($sourcePrefix.Length).Replace('\', '/')
        if ($relative -ne 'SHA256SUMS.txt' -and -not $entries.ContainsKey($relative)) {
            throw "Archivo no declarado en los fuentes del runtime: $relative. Usa un paquete sin archivos adicionales."
        }
    }
    foreach ($required in @('PROVENANCE.json', 'README.md', 'licenses/openjdk-LICENSE', 'licenses/openjdk-ASSEMBLY_EXCEPTION', 'licenses/temurin-build-LICENSE', 'licenses/temurin-build-NOTICE')) {
        if (-not $entries.ContainsKey($required)) { throw "Falta $required en el manifiesto de fuentes del runtime." }
    }
    $provenance = Get-Content -LiteralPath (Join-Path $sourceRoot 'PROVENANCE.json') -Raw | ConvertFrom-Json
    $selectedVersion = [string]$provenance.selected_version
    if ($selectedVersion -notmatch '^(?<java>[0-9]+(?:\.[0-9]+){0,3})\+[0-9]+(?:-LTS)?$') {
        throw 'PROVENANCE.json debe identificar selected_version con la version completa de Temurin, por ejemplo 21.0.12.1+1-LTS.'
    }
    $javaVersion = $Matches['java']
    $archive = [string]$provenance.source_archive.name
    if ([string]::IsNullOrWhiteSpace($archive) -or -not $entries.ContainsKey($archive) -or
        $entries[$archive] -ne [string]$provenance.source_archive.sha256 -or
        (Get-Item -LiteralPath (Join-Path $sourceRoot $archive)).Length -ne [long]$provenance.source_archive.bytes) {
        throw 'El archivo completo de fuentes no coincide con source_archive en PROVENANCE.json.'
    }
    $buildCommit = [string]$provenance.build_scripts_commit
    if ($buildCommit -notmatch '^[0-9a-fA-F]{40}$' -or -not $entries.ContainsKey("temurin-build-$buildCommit.tar.gz")) {
        throw 'Faltan los scripts de construccion del commit declarado en PROVENANCE.json.'
    }
    [PSCustomObject]@{ Directory = $sourceRoot; JavaVersion = $javaVersion; SelectedVersion = $selectedVersion; ManifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash }
}

function Assert-RuntimeSourceVersion {
    param([string]$RuntimeRelease, $SourceBundle)
    if (-not (Test-Path -LiteralPath $RuntimeRelease -PathType Leaf)) { throw "Falta runtime/release: $RuntimeRelease" }
    $versions = @(Get-Content -LiteralPath $RuntimeRelease | Where-Object { $_ -match '^JAVA_VERSION="[^"]+"$' })
    if ($versions.Count -ne 1 -or $versions[0] -ne ('JAVA_VERSION="' + $SourceBundle.JavaVersion + '"')) {
        throw "El runtime generado no coincide con los fuentes $($SourceBundle.SelectedVersion). Selecciona el JDK correspondiente mediante JAVA_HOME o aporta sus fuentes; vuelve a ejecutar package.ps1."
    }
}

if ([string]::IsNullOrWhiteSpace($RuntimeSourceDirectory)) {
    $RuntimeSourceDirectory = Join-Path (Split-Path -Parent $root) 'Qetara-third-party-source'
}
$runtimeSources = Read-RuntimeSourceBundle -Directory $RuntimeSourceDirectory
Write-Output "Fuentes del runtime verificadas: $($runtimeSources.SelectedVersion)"

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

Assert-RuntimeSourceVersion -RuntimeRelease (Join-Path $portableApp 'runtime\release') -SourceBundle $runtimeSources
# The build can take minutes. Recheck before copying a bundle that may have changed.
$currentSources = Read-RuntimeSourceBundle -Directory $runtimeSources.Directory
if ($currentSources.ManifestHash -ne $runtimeSources.ManifestHash) { throw 'El paquete de fuentes cambio durante la compilacion. Vuelve a ejecutar package.ps1.' }

# Every run has its own directory. Previous releases are kept intact.
$runName = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$releaseDir = Join-Path $releaseRoot $runName
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
foreach ($artifact in $sources) {
    Copy-Item -LiteralPath $artifact.FullName -Destination (Join-Path $releaseDir $artifact.Name)
}
Compress-Archive -LiteralPath $portableApp -DestinationPath (Join-Path $releaseDir 'Qetara-windows-portable.zip')
$sourceDestination = Join-Path $releaseDir 'Fuentes\Qetara-third-party-source'
New-Item -ItemType Directory -Path (Split-Path -Parent $sourceDestination) -Force | Out-Null
Copy-Item -LiteralPath $runtimeSources.Directory -Destination $sourceDestination -Recurse
$copiedSources = Read-RuntimeSourceBundle -Directory $sourceDestination
if ($copiedSources.ManifestHash -ne $runtimeSources.ManifestHash) { throw 'La copia de fuentes no conserva el manifiesto verificado.' }


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
