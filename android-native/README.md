# Mini Timer — Android nativo (Kotlin + Jetpack Compose)

Reimplementación 100% nativa del temporizador (independiente de la versión Expo/React Native),
con las mismas funcionalidades y, además, **ventana flotante real** sobre otras apps.

## Funcionalidades

- **Pantalla de configuración**: teclado numérico (los dígitos entran por la derecha: HHMMSS) y **presets** rápidos.
- **Cuenta atrás** con **anillo de progreso** (Compose Canvas), **hora de finalización** con segundos y controles **Cancelar / Pausa / Reanudar**.
- **Alarma** al terminar: vibración + tono de alarma del sistema, con **Descartar / Reiniciar**.
- **Configuración** (icono ⚙):
  - **Idioma**: Español / English (default **English**).
  - **Color de acento**: paleta (default **#FF5252**).
  - **Presets editables**: agrega (`mm:ss` o `hh:mm:ss`; un solo número = minutos) y elimina.
  - **Auto descartar**: `Off, 3s, 5s, 10s, 30s, 60s` (default **3s**).
  - **Restablecer valores**.
- **Ventana flotante (system overlay)**: opción configurable (default **apagada**). Cuando está activa
  y sales de la app (Home/Recientes) con un timer en curso, aparece una pequeña ventana sobre otras
  apps mostrando solo el tiempo restante. Se oculta al volver a la app.
- **Tipografía**: dígitos en **JetBrains Mono** (incluida en `res/font`), con `0` ranurado.
- **Persistencia**: `SharedPreferences`.

## Requisitos

- Android Studio (Koala o superior) con JDK 17.
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`.
- Permiso **"Mostrar sobre otras apps"** (`SYSTEM_ALERT_WINDOW`) para la ventana flotante:
  al activar la opción, la app abre la pantalla de ajustes del sistema para concederlo.

## Cómo compilar / ejecutar

1. Abre la carpeta `android-native/` en **Android Studio** (`File > Open`).
2. Android Studio descargará Gradle 8.7 y generará el `gradle-wrapper.jar` automáticamente.
   - Si compilas por línea de comandos y no existe el wrapper, genéralo con un Gradle local:
     `gradle wrapper --gradle-version 8.7`
3. Conecta un dispositivo/emulador (API 26+) y pulsa **Run**, o:
   ```bash
   ./gradlew installDebug
   ```

## Estructura

```
app/src/main/
  AndroidManifest.xml
  java/com/minitimer/
    MainActivity.kt              # Activity, permiso overlay, disparo del overlay en onUserLeaveHint
    TimerViewModel.kt            # Lógica: countdown, alarma, auto-dismiss, ajustes
    TimerBus.kt                  # Estado global compartido con el overlay
    model/Settings.kt           # Modelo de ajustes + paletas
    data/SettingsStore.kt       # Persistencia (SharedPreferences)
    i18n/Strings.kt             # Traducciones es/en
    util/Format.kt              # Formato de tiempo/hora y parseo de presets
    ui/TimerApp.kt              # Setup, Countdown, anillo, componentes
    ui/SettingsScreen.kt        # Pantalla de configuración
    ui/theme/Theme.kt, Type.kt  # Tema oscuro y fuente JetBrains Mono
    overlay/OverlayService.kt   # Ventana flotante (foreground service + WindowManager)
  res/font/                     # JetBrains Mono (light/regular/semibold)
  res/values/themes.xml         # Tema de la Activity
```

## Entorno corporativo (Netskope + JFrog) — descargar dependencias

`node.exe` está exento del steering de Netskope (por eso la versión web usa `nodefake`).
**Java NO está exento**: pasa por Netskope normalmente, así que solo hay que hacer que la
JVM confíe en el CA que inyecta Netskope (equivalente a `NODE_EXTRA_CA_CERTS`, pero en el
truststore). El `cacerts` del JBR de Android Studio es el que usan tanto el IDE como Gradle.

### 1. Importar la cadena corporativa al cacerts del JBR (una sola vez)
```powershell
$pem = Get-Content 'C:\workspaces\devin-cli-env\jfrog-combined.pem' -Raw
$certs = [regex]::Matches($pem, '(?s)-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----')
$keytool = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr\bin\keytool.exe'
$store = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr\lib\security\cacerts'
$i = 0
foreach ($m in $certs) {
  $i++
  $tmp = Join-Path $env:TEMP "corp_cert_$i.pem"
  Set-Content -Path $tmp -Value $m.Value -Encoding ascii
  & $keytool -importcert -noprompt -trustcacerts -alias "corp-netskope-$i" -file $tmp -keystore $store -storepass changeit
  Remove-Item $tmp -Force
}
```
Para revertir: `keytool -delete -alias corp-netskope-1 -keystore <store> -storepass changeit` (y -2, -3).

### 2. Mirror de JFrog para Maven/Gradle
`plugins.gradle.org` y `repo1.maven.org` devuelven **403** (bloqueados por política), así que
`settings.gradle.kts` apunta los repos al virtual corporativo **`scp-gradle-public`** (agrega
Maven Central + Google Maven + Gradle Plugin Portal). El token se reutiliza desde `~/.npmrc`
(clave `_authToken`) y se envía como `Authorization: Bearer ...` (no se commitea).

> Si el virtual cambia de nombre, lista los repos con
> `https://gluonlatam.jfrog.io/artifactory/api/repositories?type=virtual`.

### 3. Gradle usa ese JBR
`gradle.properties` incluye `org.gradle.java.home=C:/Users/mzegarra_ide/Downloads/android-studio/jbr`.

### 4. Android SDK
Instalado en `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools + `platform-tools`, `platforms;android-34`,
`build-tools;34.0.0`, licencias aceptadas). La ruta queda en `local.properties` (`sdk.dir`).

### 5. Construir (verificado ✅)
- **Recomendado**: abre el proyecto en Android Studio y sincroniza.
- **CLI** (con el Gradle 8.11.1 ya cacheado y `JAVA_HOME` = JBR):
  ```powershell
  $env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
  & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\*\gradle-8.11.1\bin\gradle.bat" assembleDebug --no-daemon
  ```
  APK resultante: `app/build/outputs/apk/debug/app-debug.apk`.

> Stack verificado: **AGP 8.7.3**, **Gradle 8.11.1**, **Kotlin 1.9.24**, `compileSdk 34`.
> Build exitoso end-to-end resolviendo todo desde JFrog.

## Notas

- El proyecto **no incluye `gradle-wrapper.jar`** (binario). Android Studio lo genera al abrir; o usa
  `gradle wrapper --gradle-version 8.7` si tienes Gradle instalado.
- La ventana flotante se inicia desde `onUserLeaveHint()` (estado foreground) para cumplir con las
  restricciones de inicio de *foreground services* en segundo plano de Android 12+.
- No se incluye icono de launcher personalizado (usa el del sistema); puedes añadir uno en `res/mipmap`.
