param(
    [switch] $SkipBuild,
    [switch] $NoLaunch
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$launcherRoot = "C:\Users\Arnel Glenn Jimenez\AppData\Roaming\gisketch\modsync\data\launchers\prismlauncher-cracked\11.0.2-1"
$instanceName = "modsync-ckdm-2026"
$instanceMinecraft = Join-Path $launcherRoot "instances\$instanceName\.minecraft"
$modsDir = Join-Path $instanceMinecraft "mods"
$prismExe = Join-Path $launcherRoot "prismlauncher.exe"

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "Missing Gradle wrapper: $gradlew"
}
if (-not (Test-Path $modsDir)) {
    throw "Missing Prism instance mods folder: $modsDir"
}
if (-not (Test-Path $prismExe)) {
    throw "Missing Prism launcher: $prismExe"
}

if (-not $SkipBuild) {
    & $gradlew build
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

$jar = Get-ChildItem (Join-Path $root "build\libs") -File |
    Where-Object { $_.Name -like "gisketchs_chowkingdom_*.jar" -and $_.Name -notlike "*-sources.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $jar) {
    throw "No built CKDM jar found in build\libs"
}

Get-ChildItem $modsDir -File |
    Where-Object { $_.Name -like "gisketchs_chowkingdom_*" } |
    Remove-Item -Force

$targetJar = Join-Path $modsDir $jar.Name
Copy-Item -LiteralPath $jar.FullName -Destination $targetJar -Force
Write-Host "Installed CKDM jar: $targetJar"

if (-not $NoLaunch) {
    Start-Process -FilePath $prismExe -ArgumentList @("--launch", $instanceName) -WorkingDirectory $launcherRoot
    Write-Host "Launched Prism instance: $instanceName"
}
