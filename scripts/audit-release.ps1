param(
    [switch]$RequireCurrentRecipe
)

# Read-only inventory: this script never builds, installs, signs or contacts a remote.
# It validates the single-build recipe kept in this repository, not general F-Droid YAML.
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$recipePath = 'fdroid/metadata/io.github.intelognatanael.qetara.yml'

function Invoke-AuditGit {
    param([string[]]$GitArguments)
    $output = @(& git -C $repoRoot @GitArguments)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer el estado Git requerido por la auditoria.' }
    return ($output -join "`n")
}

function Read-UniqueValue {
    param([string]$Text, [string]$Pattern, [string]$Label)
    $matches = [regex]::Matches($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($matches.Count -ne 1) { throw "Se esperaba un unico valor de $Label; revisa el formato antes de continuar." }
    return $matches[0].Groups[1].Value.Trim()
}

function Read-ReleaseProperties {
    param([string]$Text)
    return [ordered]@{
        version = Read-UniqueValue $Text '^qetaraVersion=([0-9A-Za-z.+-]+)\r?$' 'qetaraVersion'
        android_version_code = [int](Read-UniqueValue $Text '^qetaraVersionCode=([0-9]+)\r?$' 'qetaraVersionCode')
    }
}

$head = Invoke-AuditGit @('rev-parse', '--verify', 'HEAD')
$properties = Get-Content -LiteralPath (Join-Path $repoRoot 'gradle.properties') -Raw
$sourceVersion = Read-ReleaseProperties $properties
$recipeText = Get-Content -LiteralPath (Join-Path $repoRoot $recipePath) -Raw
$recipeVersion = Read-UniqueValue $recipeText '^  - versionName: ([0-9A-Za-z.+-]+)\r?$' 'Builds.versionName (una build)'
$recipeCode = [int](Read-UniqueValue $recipeText '^    versionCode: ([0-9]+)\r?$' 'Builds.versionCode')
$recipeCommit = Read-UniqueValue $recipeText '^    commit: ([0-9a-f]{40}(?:[0-9a-f]{24})?)\r?$' 'Builds.commit'
$currentVersion = Read-UniqueValue $recipeText '^CurrentVersion: ([0-9A-Za-z.+-]+)\r?$' 'CurrentVersion'
$currentCode = [int](Read-UniqueValue $recipeText '^CurrentVersionCode: ([0-9]+)\r?$' 'CurrentVersionCode')
$pinnedProperties = Invoke-AuditGit @('show', "${recipeCommit}:gradle.properties")
$pinnedVersion = Read-ReleaseProperties $pinnedProperties

# Android also configures the desktop subproject in the normal Gradle settings.
# UI/resources of PC are checked separately; they do not enter the Android APK.
$androidInputs = @('app/src/main', 'app/build.gradle.kts', 'app/proguard-rules.pro',
    'protocol/src/main', 'protocol/build.gradle.kts', 'pc/build.gradle.kts',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle',
    'gradlew', 'gradlew.bat', '.gitattributes')
$desktopInputs = @('pc/src/main', 'pc/build.gradle.kts', 'protocol/src/main',
    'protocol/build.gradle.kts', 'build.gradle.kts', 'settings.gradle.kts',
    'gradle.properties', 'gradle', 'gradlew', 'gradlew.bat', '.gitattributes')
$androidDiff = @( (Invoke-AuditGit (@('diff', '--name-only', $recipeCommit, $head, '--') + $androidInputs)) -split "`n" | Where-Object { $_ } )
$desktopDiff = @( (Invoke-AuditGit (@('diff', '--name-only', $recipeCommit, $head, '--') + $desktopInputs)) -split "`n" | Where-Object { $_ } )
$androidWorktree = @( (Invoke-AuditGit (@('status', '--porcelain=v1', '--untracked-files=all', '--') + $androidInputs)) -split "`n" | Where-Object { $_ } )
$desktopWorktree = @( (Invoke-AuditGit (@('status', '--porcelain=v1', '--untracked-files=all', '--') + $desktopInputs)) -split "`n" | Where-Object { $_ } )
$protocolText = Get-Content -LiteralPath (Join-Path $repoRoot 'protocol/src/main/kotlin/com/example/wifidrop/protocol/WifiDropProtocol.kt') -Raw
$flashText = Get-Content -LiteralPath (Join-Path $repoRoot 'protocol/src/main/kotlin/com/example/wifidrop/protocol/flash/FlashWire.kt') -Raw
$issues = [System.Collections.Generic.List[string]]::new()
if ($recipeVersion -ne $pinnedVersion.version -or $recipeCode -ne $pinnedVersion.android_version_code) {
    $issues.Add('La version de la receta no coincide con gradle.properties de su commit fijado.')
}
if ($recipeVersion -ne $currentVersion -or $recipeCode -ne $currentCode) {
    $issues.Add('CurrentVersion/CurrentVersionCode no coinciden con la build propuesta.')
}
if ($recipeVersion -ne $sourceVersion.version -or $recipeCode -ne $sourceVersion.android_version_code) {
    $issues.Add('La receta propone otra version respecto de las fuentes de trabajo.')
}
if ($androidDiff.Count -gt 0) {
    $issues.Add('Hay cambios Android/build posteriores al commit de la receta; su validacion historica no acredita el APK actual.')
}
if ($androidWorktree.Count -gt 0) {
    $issues.Add('Hay entradas Android/build modificadas o sin versionar; no corresponden exclusivamente a un commit.')
}

$report = [ordered]@{
    schema_version = 1
    checked_utc = [DateTime]::UtcNow.ToString('o')
    scope = 'Local source/recipe consistency only; no build, signature, public access or installed-device verification.'
    source_commit = $head
    source = $sourceVersion
    protocol = [ordered]@{
        wdrp = [int](Read-UniqueValue $protocolText '^const val PROTOCOL_VERSION = ([0-9]+)\r?$' 'PROTOCOL_VERSION')
        flash = [int](Read-UniqueValue $flashText '^internal const val FLASH_VERSION = ([0-9]+)\r?$' 'FLASH_VERSION')
    }
    fdroid = [ordered]@{
        recipe = $recipePath
        commit = $recipeCommit
        version = $recipeVersion
        android_version_code = $recipeCode
        pinned_source = $pinnedVersion
        binary_url_configured = [regex]::IsMatch($recipeText, '(?m)^(?:Binaries:|    binary:)\s*https://\S+')
        source_consistent_with_recipe = ($issues.Count -eq 0)
        android_inputs_changed_since_recipe = $androidDiff
        android_worktree_changes = $androidWorktree
    }
    desktop = [ordered]@{
        inputs_changed_since_recipe = $desktopDiff
        worktree_changes = $desktopWorktree
    }
    findings = $issues.ToArray()
}
$report | ConvertTo-Json -Depth 6
if ($RequireCurrentRecipe -and $issues.Count -gt 0) { exit 1 }
