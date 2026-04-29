$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "Missing Gradle wrapper: $gradlew"
}

& $gradlew runClient @args
exit $LASTEXITCODE
