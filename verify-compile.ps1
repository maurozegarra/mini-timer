# Verifica que el proyecto COMPILA (sin generar APK, sin subir version, sin commit).
# NOTA: en este repo NO existe gradlew; se usa el Gradle 9.4.1 cacheado. Compilar SIEMPRE es posible.
$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
$gradle = (Resolve-Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat").Path

& $gradle compileReleaseKotlin --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle compileReleaseKotlin failed (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "OK -> compila sin errores"
