param(
    [ValidateNotNullOrEmpty()][string]$Ref = 'HEAD',
    [switch]$Offline,
    [ValidateRange(1, 16)][int]$MaxWorkers = 2
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Assert-NoSigningEnvironment {
    if (@(Get-ChildItem Env: | Where-Object { $_.Name -like 'QETARA_SIGNING_*' }).Count -ne 0) {
        throw 'La compilacion sin firma exige que todas las variables QETARA_SIGNING_* esten ausentes. No se han mostrado ni cambiado sus valores.'
    }
}

function Invoke-CheckedGit {
    param([string[]]$GitArguments)
    $output = & git @GitArguments
    $gitExitCode = $LASTEXITCODE
    if ($gitExitCode -ne 0) { throw "Git termino con codigo $gitExitCode." }
    $output
}

function Assert-OrdinaryPath {
    param([string]$Path, [string]$Boundary)
    $current = [System.IO.Path]::GetFullPath($Path)
    $limit = [System.IO.Path]::GetFullPath($Boundary).TrimEnd('\', '/')
    if (-not $current.StartsWith($limit + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'La ruta de salida no pertenece a la carpeta prevista.'
    }
    while ($current.Length -gt $limit.Length) {
        if ((Test-Path -LiteralPath $current) -and
            ((Get-Item -LiteralPath $current -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw 'La ruta de salida contiene un enlace o punto de reanalisis.'
        }
        $current = Split-Path -Parent $current
    }
}

function Assert-CleanCheckout {
    $actualCommit = [string](Invoke-CheckedGit @('-C', $checkout, 'rev-parse', '--verify', 'HEAD'))
    $changes = @(Invoke-CheckedGit @('-C', $checkout, 'status', '--porcelain=v1', '--untracked-files=all'))
    if ($actualCommit.Trim() -ne $commit -or $changes.Count -ne 0) {
        throw 'El clon ya no contiene exclusivamente el commit elegido y un arbol limpio.'
    }
}

Assert-NoSigningEnvironment
if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { throw 'Configura ANDROID_HOME con el SDK de Android ya instalado.' }
foreach ($sdkFile in @('platforms\android-36\android.jar', 'build-tools\36.0.0\aapt2.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME $sdkFile) -PathType Leaf)) {
        throw "Falta un componente del SDK configurado: $sdkFile. Este script no instala SDKs."
    }
}
$gitRoot = [string](Invoke-CheckedGit @('-C', $repoRoot, 'rev-parse', '--show-toplevel'))
if ([System.IO.Path]::GetFullPath($gitRoot.Trim()) -ne $repoRoot) { throw 'Ejecuta el script desde una copia Git completa de Qetara.' }
$commit = ([string](Invoke-CheckedGit @('-C', $repoRoot, 'rev-parse', '--verify', '--end-of-options', "$Ref^{commit}"))).Trim()
if ($commit -notmatch '^(?:[0-9a-f]{40}|[0-9a-f]{64})$') { throw 'Git no devolvio un identificador de commit valido.' }

# Only committed files enter this new local clone; it never reuses an existing build directory.
$runName = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N')
$runRoot = Join-Path $repoRoot "build\android-release\$runName"
Assert-OrdinaryPath -Path $runRoot -Boundary $repoRoot
if (Test-Path -LiteralPath $runRoot) { throw 'La carpeta exclusiva de esta ejecucion ya existe.' }
New-Item -ItemType Directory -Path $runRoot | Out-Null
$checkout = Join-Path $runRoot 'source'
$buildLog = Join-Path $runRoot 'build.log'
Invoke-CheckedGit @('clone', '--no-hardlinks', '--no-checkout', '--', $repoRoot, $checkout)
Invoke-CheckedGit @('-C', $checkout, 'checkout', '--detach', $commit)
Assert-CleanCheckout
if (Test-Path -LiteralPath (Join-Path $checkout 'local.properties')) {
    throw 'El commit contiene local.properties. Usa ANDROID_HOME y retira configuracion local del codigo versionado.'
}
Assert-NoSigningEnvironment
$gradleArguments = @(':app:assembleRelease', '--no-build-cache', '--no-daemon', "--max-workers=$MaxWorkers", '--console=plain')
if ($Offline) { $gradleArguments += '--offline' }
$buildExitCode = -1
Push-Location -LiteralPath $checkout
try {
    # Native stderr is logged even in Windows PowerShell 5; LASTEXITCODE decides build success.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $checkout 'gradlew.bat') @gradleArguments 2>&1 | Tee-Object -FilePath $buildLog -ErrorAction Stop
    $buildExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
    Pop-Location
}
Assert-CleanCheckout
if ($buildExitCode -ne 0) { throw "Gradle termino con codigo $buildExitCode. Se conserva el registro en $buildLog" }

$apkDirectory = Join-Path $checkout 'app\build\outputs\apk\release'
$metadataPath = Join-Path $apkDirectory 'output-metadata.json'
Assert-OrdinaryPath -Path $metadataPath -Boundary $checkout
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) { throw 'Falta output-metadata.json del APK release.' }
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$elements = @($metadata.elements)
if ($metadata.variantName -ne 'release' -or $metadata.artifactType.type -ne 'APK' -or
    $elements.Count -ne 1 -or $null -eq $elements[0] -or $elements[0].type -ne 'SINGLE' -or
    @($elements[0].filters).Count -ne 0) { throw 'Se esperaba exactamente un APK release SINGLE sin filtros.' }
$element = $elements[0]
$outputFile = $element.outputFile
if ($outputFile -isnot [string] -or [string]::IsNullOrWhiteSpace($outputFile) -or
    $outputFile.IndexOfAny([System.IO.Path]::GetInvalidFileNameChars()) -ge 0 -or $outputFile -match '[/\\:]' -or
    [System.IO.Path]::IsPathRooted($outputFile) -or [System.IO.Path]::GetFileName($outputFile) -ne $outputFile -or
    [System.IO.Path]::GetExtension($outputFile) -ne '.apk') { throw 'outputFile debe ser un nombre de archivo .apk sin directorios.' }
$version = [string]$element.versionName
$versionCode = 0
$applicationId = [string]$metadata.applicationId
if ($version -notmatch '^[0-9A-Za-z][0-9A-Za-z.+-]*$' -or
    -not [int]::TryParse([string]$element.versionCode, [ref]$versionCode) -or $versionCode -le 0 -or
    $applicationId -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') {
    throw 'El metadata no identifica una version, codigo e identificador Android validos.'
}
$apkPath = [System.IO.Path]::GetFullPath((Join-Path $apkDirectory $outputFile))
Assert-OrdinaryPath -Path $apkPath -Boundary $apkDirectory
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw 'Falta el APK declarado en output-metadata.json.' }
$destination = Join-Path $runRoot "Qetara-$version-unsigned.apk"
if (Test-Path -LiteralPath $destination) { throw 'Ya existe una salida APK en la carpeta exclusiva de esta ejecucion.' }
Copy-Item -LiteralPath $apkPath -Destination $destination
$hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()) { throw 'La copia del APK no conserva su SHA-256.' }
$provenance = [ordered]@{
    schema_version = 1
    created_utc = [DateTime]::UtcNow.ToString('o')
    commit = $commit
    version_name = $version
    version_code = $versionCode
    application_id = $applicationId
    variant = 'release'
    signing_environment = 'absent'
    source_tree_clean_before_and_after = $true
    gradle_arguments = $gradleArguments
    build_log = 'build.log'
    artifact = [ordered]@{ file = [System.IO.Path]::GetFileName($destination); bytes = (Get-Item -LiteralPath $destination).Length; sha256 = $hash }
}
[System.IO.File]::WriteAllText((Join-Path $runRoot 'provenance.json'), ($provenance | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
Write-Output "APK sin firma del commit $commit disponible en $destination"
Write-Output "SHA-256: $hash"
