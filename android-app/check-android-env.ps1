param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)

Write-Host "Android SDK root: $SdkRoot"

$checks = @(
    @{ Name = "SDK root"; Path = $SdkRoot },
    @{ Name = "platform-tools"; Path = Join-Path $SdkRoot "platform-tools" },
    @{ Name = "emulator"; Path = Join-Path $SdkRoot "emulator" },
    @{ Name = "platform android-35"; Path = Join-Path $SdkRoot "platforms\android-35" },
    @{ Name = "cmdline-tools latest"; Path = Join-Path $SdkRoot "cmdline-tools\latest" },
    @{ Name = "system-images"; Path = Join-Path $SdkRoot "system-images" }
)

foreach ($check in $checks) {
    $exists = Test-Path $check.Path
    "{0,-24} {1}" -f $check.Name, ($(if ($exists) { "OK" } else { "MISSING" }))
}

$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
if (Test-Path $adb) {
    Write-Host ""
    Write-Host "Connected devices:"
    & $adb devices
}

$emulator = Join-Path $SdkRoot "emulator\emulator.exe"
if (Test-Path $emulator) {
    Write-Host ""
    Write-Host "Available AVDs:"
    & $emulator -list-avds
}
