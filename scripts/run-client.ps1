$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "Missing Gradle wrapper: $gradlew"
}

$javaToolOptions = ($env:JAVA_TOOL_OPTIONS -replace '(^|\s)-Xmx\S+', '').Trim()
$env:JAVA_TOOL_OPTIONS = (@($javaToolOptions, "-Xmx6G") | Where-Object { $_ }) -join " "

& $gradlew runClient @args
exit $LASTEXITCODE
