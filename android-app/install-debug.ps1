param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$ApkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk",
    [string]$PackageName = "org.sainm.psy.respondent",
    [string]$LaunchActivity = "org.sainm.psy.respondent/.MainActivity"
)

if (-not (Test-Path $AdbPath)) {
    Write-Error "adb not found: $AdbPath"
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK not found: $ApkPath"
    exit 1
}

$devices = & $AdbPath devices
$online = $devices | Select-String "`tdevice$"
if (-not $online) {
    Write-Error "No connected Android device or emulator."
    exit 1
}

Write-Host "Installing $ApkPath ..."
& $AdbPath install -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Launching $LaunchActivity ..."
& $AdbPath shell am start -n $LaunchActivity
exit $LASTEXITCODE
