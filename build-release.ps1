param(
    [string]$Message = ''
)

$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
$gradle = (Resolve-Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat").Path

& $gradle assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed (exit $LASTEXITCODE)" }

$gradleFile = Join-Path $PSScriptRoot 'app\build.gradle.kts'
$content = Get-Content $gradleFile -Raw
$m = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"')
if (-not $m.Success) { throw "No se pudo leer versionName de $gradleFile" }
$version = $m.Groups[1].Value

$apkSrc = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkSrc)) { throw "No se encontro el APK generado: $apkSrc" }

$releasesDir = Join-Path $PSScriptRoot 'releases'
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }
Remove-Item (Join-Path $releasesDir '*.apk') -ErrorAction SilentlyContinue

$apkDst = Join-Path $releasesDir "mini-timer-$version.apk"
Copy-Item $apkSrc $apkDst -Force

Write-Host ""
Write-Host "OK -> releases\mini-timer-$version.apk"

if ($Message -ne '') {
    git add -A
    if ($LASTEXITCODE -ne 0) { throw "git add failed (exit $LASTEXITCODE)" }
    git commit -m "$Message (v$version)"
    if ($LASTEXITCODE -ne 0) { throw "git commit failed (exit $LASTEXITCODE)" }
    Write-Host "Commit -> $Message (v$version)"
}

