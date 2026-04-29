$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "Missing Gradle wrapper: $gradlew"
}

$server = Start-Process -FilePath $gradlew -ArgumentList @("--no-daemon", "runServer") -WorkingDirectory $root -PassThru
$clientOne = Start-Process -FilePath $gradlew -ArgumentList @("--no-daemon", "runClient", "--args=--username CKD_Player_1") -WorkingDirectory $root -PassThru
$clientTwo = Start-Process -FilePath $gradlew -ArgumentList @("--no-daemon", "runClient", "--args=--username CKD_Player_2") -WorkingDirectory $root -PassThru

Write-Host "Started multiplayer test stack:"
Write-Host "  Server PID:   $($server.Id)"
Write-Host "  Client 1 PID: $($clientOne.Id)"
Write-Host "  Client 2 PID: $($clientTwo.Id)"
Write-Host "Connect both clients to localhost once the server finishes loading. Close the spawned windows/processes when done."
