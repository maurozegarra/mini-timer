# Mini Timer — Android nativo (Kotlin + Jetpack Compose)

Reimplementación 100% nativa del temporizador (independiente de la versión Expo/React Native),
con las mismas funcionalidades y, además, **Live Update** (notificación promovida de Android 16, visible en la Now Bar de One UI).

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
- **Live Update (Android 16)**: notificación promovida (`Notification.ProgressStyle` + cronómetro)
  con barra de progreso y cuenta regresiva. Aparece como chip en la barra de estado, en la sombra de
  notificaciones y en la **Now Bar** de One UI (en One UI 8, para apps de terceros, requiere activar
  *Opciones de desarrollador → "Live notifications for all apps"*).
- **El timer sobrevive a la muerte del proceso**: si cierras la app desde Recientes (swipe), al
  reabrirla el temporizador se restaura recalculando el tiempo restante con el reloj real
  (corriendo / pausado / terminado).
- **Recuerda la última duración usada** (de presets o tecleada) y la pre-rellena al abrir la app o
  tras terminar/cancelar, lista para reutilizarse.
- **Pantalla siempre encendida**: mientras la app está en primer plano no se apaga la pantalla
  (`keepScreenOn`); además, mientras el timer corre en background, un *screen wake lock* en el
  servicio intenta mantenerla encendida (deprecado, puede estar limitado en algunos OEM).
- **Tipografía**: dígitos en **JetBrains Mono** (incluida en `res/font`), con `0` ranurado.
- **Persistencia**: `SharedPreferences` (ajustes, estado del timer activo y última duración).

## Requisitos

- Android Studio (con JBR/JDK 21) — el Live Update de Android 16 requiere el toolchain moderno.
- `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`.

## Cómo compilar / ejecutar

1. Abre la carpeta `android-native/` en **Android Studio** (`File > Open`).
2. Android Studio descargará Gradle 9.4.1 y generará el `gradle-wrapper.jar` automáticamente.
   - Si compilas por línea de comandos y no existe el wrapper, genéralo con un Gradle local:
     `gradle wrapper --gradle-version 9.4.1`
3. Conecta un dispositivo/emulador (API 26+) y pulsa **Run**, o:
   ```bash
   ./gradlew installDebug
   ```

## Estructura

```
app/src/main/
  AndroidManifest.xml
  java/com/minitimer/
    MainActivity.kt              # Activity y permiso de notificaciones
    TimerViewModel.kt            # Lógica: countdown, alarma, auto-dismiss, ajustes, persistencia/restauración
    TimerBus.kt                  # Estado global compartido con el Live Update
    model/Settings.kt           # Modelo de ajustes + paletas
    data/SettingsStore.kt       # Persistencia (SharedPreferences)
    i18n/Strings.kt             # Traducciones es/en
    util/Format.kt              # Formato de tiempo/hora y parseo de presets
    ui/TimerApp.kt              # Setup, Countdown, anillo, componentes
    ui/SettingsScreen.kt        # Pantalla de configuración
    ui/theme/Theme.kt, Type.kt  # Tema oscuro y fuente JetBrains Mono
    notify/LiveTimerService.kt  # Foreground service que publica la notificación Live Update
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
Instalado en `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools + `platform-tools`, `platforms;android-36`,
`build-tools`, licencias aceptadas). La ruta queda en `local.properties` (`sdk.dir`).

### 5. Construir (verificado ✅)
- **Recomendado**: abre el proyecto en Android Studio y sincroniza.
- **CLI** (con el Gradle 9.4.1 ya cacheado y `JAVA_HOME` = JBR):
  ```powershell
  $env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
  & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat" assembleDebug --no-daemon
  ```
  APK resultante: `app/build/outputs/apk/debug/app-debug.apk`.

> Stack verificado: **AGP 9.2.1**, **Gradle 9.4.1**, **Kotlin 2.2.10**, `compileSdk 36.1`.
> Build exitoso end-to-end resolviendo todo desde JFrog.
>
> **Memoria (importante):** AGP 9 con G1 reserva mucho espacio virtual y en este equipo daba
> *"el archivo de paginación es demasiado pequeño"* (Windows). Por eso `gradle.properties` usa
> `-XX:+UseSerialGC` con heaps reducidos (`org.gradle.jvmargs` y `kotlin.daemon.jvmargs`).

## Notas

- El proyecto **no incluye `gradle-wrapper.jar`** (binario). Android Studio lo genera al abrir; o usa
  `gradle wrapper --gradle-version 9.4.1` si tienes Gradle instalado.
- Permisos usados: `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE`, `VIBRATE`, `WAKE_LOCK`.
- No se incluye icono de launcher personalizado (usa el del sistema); puedes añadir uno en `res/mipmap`.
