# Emulador y ADB inalámbrico — guía verificada (Windows)

Notas reproducibles para **probar/instalar la app sin USB** en este equipo (emulador + teléfono
físico por Wi-Fi). Complementa la sección *"Construir"* del `README.md`.

> PowerShell, desde la raíz del repo. No usar `cd` dentro de los comandos si se ejecutan por
> herramienta.

## Entorno de este equipo

| Recurso | Ruta |
| --- | --- |
| **JBR (JDK 21 de Android Studio)** | `C:\Users\mzegarra_ide\Downloads\android-studio\jbr` |
| **Android SDK** | `%LOCALAPPDATA%\Android\Sdk` |
| **adb** | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| **emulator** | `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` |
| **sdkmanager / avdmanager** | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\*.bat` |
| **Gradle 9.4.1 (cacheado)** | `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat` |

Atajos usados:
```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$p   = "com.minitimer"   # applicationId de este proyecto
```

---

## 1. Compilar

```powershell
$env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat" assembleRelease --no-daemon --console=plain
# APK: app\build\outputs\apk\release\app-release.apk  (release firmado con la clave debug)
# Copiar a releases\mini-timer-1.0.<n>.apk y subir versionCode/versionName (+1 por APK).
```
Solo verificar compilación: usar `:app:compileReleaseKotlin`.

---

## 2. Emulador (AVD) — sin teléfono

`minSdk = 26`, así que cualquier imagen `android-26+` sirve. Aquí `android-35`.

```powershell
# 2.1 Imagen de sistema
$env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
"y`n"*20 | & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="$sdk" "system-images;android-35;google_apis;x86_64"

# 2.2 Crear AVD
"no" | & "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n mt_test -k "system-images;android-35;google_apis;x86_64" -d "pixel_5" --force

# 2.3 Lanzar
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList '-avd','mt_test','-no-snapshot-load','-no-boot-anim'

# 2.4 Esperar boot
& $adb wait-for-device
do { Start-Sleep -Seconds 3; $b = (& $adb shell getprop sys.boot_completed) 2>$null } while ($b.Trim() -ne "1")

# 2.5 Instalar y abrir
& $adb install -r ".\app\build\outputs\apk\release\app-release.apk"
& $adb shell pm grant $p android.permission.POST_NOTIFICATIONS
& $adb shell am start -n "$p/.MainActivity"
```

---

## 3. ADB inalámbrico — instalar en el teléfono SIN USB (sin admin)

El PC se conecta *hacia* el teléfono (tráfico saliente), por lo que **el firewall del PC no lo
bloquea** (a diferencia de servir HTTP local, que requiere abrir puerto entrante → admin).
Requisitos: misma Wi-Fi, teléfono Android 11+.

En el teléfono: **Ajustes → Opciones de desarrollador → Depuración inalámbrica → "Vincular
dispositivo con código"**. Anota **IP:puerto de vinculación** y el **código de 6 dígitos**.

```powershell
# 3.1 Emparejar (puerto de VINCULACIÓN, cambia cada vez)
& $adb pair 192.168.X.Y:<puertoVinc> <codigo6>

# 3.2 Puerto de CONEXIÓN (distinto): autodetección por mDNS
& $adb mdns services        # -> _adb-tls-connect._tcp  192.168.X.Y:<puertoConn>

# 3.3 Conectar e instalar
$d = "192.168.X.Y:<puertoConn>"
& $adb connect $d
& $adb -s $d install -r ".\releases\mini-timer-1.0.<n>.apk"

# 3.4 (Opcional) permisos + abrir
& $adb -s $d shell pm grant $p android.permission.POST_NOTIFICATIONS
& $adb -s $d shell am start -n "$p/.MainActivity"
```

---

## 4. Plan alterno: servidor Wi-Fi + navegador (falla sin admin)

Windows Firewall bloquea el puerto entrante y no se puede crear la regla sin admin. Solo funciona si
tienes admin (`New-NetFirewallRule ... -LocalPort 8000`).
```powershell
python -m http.server 8000 --bind 0.0.0.0 --directory ".\releases"
# Teléfono: http://<IP-del-PC>:8000/mini-timer-1.0.<n>.apk
```

---

## 5. Gotchas

- **`am start-foreground-service` desde adb** falla si el service es `exported="false"`
  (*"Requires permission not exported from uid"*). Lanzar la Activity en su lugar.
- **`sdkmanager`/`avdmanager`** requieren `JAVA_HOME` apuntando al JBR de Android Studio.
- **Puerto de vinculación ≠ puerto de conexión**; el de vinculación cambia en cada emparejamiento.
- **mDNS** (`adb mdns services`) descubre el puerto de conexión tras el `pair`.
- **HTTP local sin admin = no**: el firewall bloquea el acceso del teléfono; usa ADB inalámbrico.
