# Mini Timer — Android nativo (Kotlin + Jetpack Compose)

Temporizador 100% nativo para Android con **Live Update** (notificación promovida de Android 16, visible en la Now Bar de One UI).

## Funcionalidades

- **Pantalla de configuración**: teclado numérico (los dígitos entran por la derecha: HHMMSS) y **presets** rápidos.
- **Cuenta atrás** con **anillo de progreso** (Compose Canvas), **hora de finalización** con segundos y controles **Cancelar / Pausa / Reanudar**.
- **Alarma** al terminar: tono en bucle + vibración opcional, con **Descartar / Reiniciar**.
- **Configuración** (icono ⚙):
  - **Idioma**: Español / English (default **English**).
  - **Color de acento**: paleta (default **#FF5252**).
  - **Presets editables**: agrega (`mm:ss` o `hh:mm:ss`; un solo número = minutos) y elimina.
  - **Auto descartar**: `Off, 3s, 5s, 10s, 30s, 60s` (default **3s**).
  - **Ignorar modo silencio**: reproduce el sonido aunque el teléfono esté en silencio/vibración.
  - **Tono de alarma**: selector **in-app** con lista de tonos del dispositivo y **previsualización** de cada uno (botón ▶). Default **"Beep"**.
  - **Volumen de la alarma**: control **+/-** (pasos de 5%) estilo Material 3.
  - **Salida con audífonos**: `Altavoz + Audífonos` o `Solo audífonos` (default **Solo audífonos**).
  - **Vibración**: activable (default **off**) con patrones seleccionables: *Simple, Zig-Zig, Zig-zig-zig, Tap, Knock, Heartbeat, Bounce, Dubstep, Gallop* (preview al tocar).
  - **Restablecer valores** (vuelve a default y reaplica **"Beep"**).
- **Enrutamiento de audio**: reproduce con `MediaPlayer` usando `USAGE_MEDIA` para que el sonido (y la previsualización) llegue a **Bluetooth A2DP / LE Audio** y cableados; usa `USAGE_ALARM` para el altavoz. Elige la salida de media adecuada y **excluye Bluetooth SCO** (canal de llamadas).
- **Aparece primera en el cajón de apps**: la etiqueta del launcher es **`!Timer`** para ordenar al inicio.
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
- **Múltiples temporizadores**: lista de timers nombrados, cada uno con su estado; en instalación limpia se siembran dos por defecto (**rest** 1:00 y **potty** 5:00).
- **Tipografía**: dígitos en **JetBrains Mono** (incluida en `res/font`), con `0` ranurado. El branding de **Athlete** usa dos fuentes (solo ahí): **Neuropol Nova** para el título `ATHLETE` y **Wallpoet** para la `M` del ícono del tab.
- **Persistencia**: `SharedPreferences` (ajustes, lista de timers, estado del timer activo y última duración).

## Atleta (Athlete)

Segunda pestaña de la app: editor y reproductor de rutinas de entrenamiento con jerarquía de **3 niveles**.

- **Jerarquía**: `Training` > `Workout` > `Exercise`.
  - **Training**: lo que se ejecuta de corrido en el player (p. ej. *Master*).
  - **Workout**: agrupador de ejercicios (p. ej. Warmup, Cardio, Lower/Upper).
  - **Exercise**: unidad mínima, con etapas configurables.
- **Etapas por ejercicio** (`prepare`, `work`, `rest`, `cooldown`), cada una con `StageConfig`: color (ARGB), `display` (COUNTDOWN/STATIC/COUNTUP), alarma, conteo final y `confirm` (AUTO/MANUAL).
  - `work` admite modo **TIME** (duración) o **REPS** (repeticiones).
  - Colores por etapa: PREPARE naranja, WORK rojo, REST azul, COOLDOWN plomo.
- **Peso por serie** (cuando `work` es REPS): `weightType = NONE | TOTAL | BARBELL | DUMBBELL` (+ `barWeight`), con `WorkSet{reps, weight}` por set. BARBELL = barra + discos; DUMBBELL = 2× peso por mano; TOTAL = directo.
- **Formato rep-by-rep**: ejercicios por reps con un set por repetición (`sets = nº reps`, `workValue = 1`, confirm manual) y descanso entre reps; el player muestra *Rep n/N*.
- **Rotación** (workouts rotativos): un workout puede tener **variantes** (`WorkoutVariant`) que rotan **al completar** (p. ej. Cardio entre 4 variantes; Fuerza alternando Lower/Upper). Editor de variantes incluido.
- **Seed *Master***: en instalación limpia se siembra un Training completo (Warmup, Base, Cardio rotativo, Fuerza Lower/Upper rotativo), localizado ES/EN.
- **Player en segundo plano**: corre con `WorkoutPlayerService` (foreground) reutilizando el motor de alarma; encadena prepare → sets×(work, rest) → cooldown por cada ejercicio del training.
- **Catálogo de ejercicios** con íconos (emoji por id) y posibilidad de crear ejercicios propios persistidos.
- **Entrada de duración** rápida en `mm:ss` (diálogo) además de steppers +/-.

## Respaldo automático

- **Estrategia**: auto-backup a una carpeta elegida por el usuario vía **SAF** (`DocumentsContract`, sin dependencias nuevas).
- **Formato**: JSON versionado (`schemaVersion`) con volcado tipado de las `SharedPreferences` persistentes (`mini_timer` y `athlete`); se excluye el estado transitorio del player.
- **Disparo**: en cada cambio de datos (listener con *debounce* 2.5s) y al pasar la app a segundo plano. Archivo `mini-timer-backup.json` (rolling) en la carpeta elegida (`tree Uri` con permiso persistible).
- **Restaurar**: relee el JSON, reescribe prefs y recarga en caliente (`TimerViewModel.reload()` / `AthleteViewModel.reload()`). UI en Ajustes → grupo **Respaldo**.
- **Tras reinstalar** se pierde el permiso de la carpeta y hay que re-elegirla; si tiene respaldo, ofrece restaurar.

## Requisitos

- Android Studio (con JBR/JDK 21) — el Live Update de Android 16 requiere el toolchain moderno.
- `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`.

## Cómo compilar / ejecutar

> ⚠️ **IMPORTANTE — NO existe `gradlew` ni `gradle-wrapper.jar` en el repo, y `gradle` NO está en el PATH.**
> Esto es **esperado** (el binario del wrapper no se commitea). **No** es un error: para compilar por
> línea de comandos se usa el **Gradle 9.4.1 ya cacheado**. Comando exacto (copiar/pegar):
> ```powershell
> $env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
> & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat" assembleRelease --no-daemon --console=plain
> ```
> APK resultante: `app/build/outputs/apk/release/app-release.apk` → copiar a `releases/mini-timer-1.0.<n>.apk`.
> Para verificar solo compilación sin generar APK: usar la misma ruta con `:app:compileReleaseKotlin`.

1. Abre la carpeta del proyecto (raíz del repo) en **Android Studio** (`File > Open`).
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
    MainActivity.kt              # Activity, permiso de notificaciones, init de BackupManager
    TimerViewModel.kt            # Lógica: countdown, alarma, auto-dismiss, ajustes, persistencia/restauración
    TimerBus.kt                  # Estado global compartido con el Live Update
    AthleteViewModel.kt          # CRUD Training/Workout/Exercise, variantes/rotación, buildSteps del player
    PlayerBus.kt                 # PlayerSnapshot (estado del player de Athlete)
    model/Settings.kt           # Modelo de ajustes + paletas + patrones de vibración
    model/TimerItem.kt          # Modelo de un temporizador individual
    model/Workout.kt            # Training/Workout/Exercise/StageConfig/WorkSet/WeightType/WorkoutVariant/SessionLog
    model/PlayerStep.kt         # Paso del player (StepKind PREP/WORK/REST/COOLDOWN + owner/peso/note)
    data/SettingsStore.kt       # Persistencia de ajustes y timers (SharedPreferences)
    data/WorkoutStore.kt        # Persistencia de trainings, ejercicios propios y sesiones (JSON)
    data/AthleteDefaults.kt     # Seed del Training "Master" (instalación limpia)
    data/ExerciseCatalog.kt     # Catálogo base de ejercicios (ES/EN)
    data/ExerciseIcons.kt       # Mapa de emoji por ejercicio (fallback por palabra clave)
    data/BackupManager.kt       # Auto-backup/restore a carpeta vía SAF
    i18n/Strings.kt             # Traducciones es/en
    util/Format.kt              # Formato de tiempo/hora y parseo de presets
    ui/TimerApp.kt              # Setup, Countdown, anillo, tab bar, ícono hexagonal de Athlete
    ui/SettingsScreen.kt        # Pantalla de configuración (incluye grupo Respaldo)
    ui/athlete/                 # Pantallas de Athlete (lista, editores, selector, player, variantes)
    ui/theme/Theme.kt, Type.kt  # Tema oscuro y fuentes (JetBrains Mono, Neuropol, Wallpoet)
    notify/LiveTimerService.kt  # Foreground service del Live Update del timer
    notify/WorkoutPlayerService.kt # Foreground service del player de Athlete
    notify/WorkoutAlarm.kt      # Alarma del player de Athlete
  res/font/                     # JetBrains Mono, Neuropol Nova, Wallpoet
  res/values/themes.xml         # Tema de la Activity
```

## Entorno corporativo (Netskope + JFrog) — descargar dependencias

**Java pasa por Netskope normalmente** (no está exento del steering), así que solo hay que hacer que la
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
- **CLI** (con el Gradle 9.4.1 ya cacheado y `JAVA_HOME` = JBR) — se publica el build **release** (R8 + shrinkResources):
  ```powershell
  $env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
  & "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat" assembleRelease --no-daemon
  ```
  APK resultante: `app/build/outputs/apk/release/app-release.apk` → copiar a `releases/mini-timer-1.0.<n>.apk`.

#### Build de release y versionado
- El `release` tiene `isMinifyEnabled = true` + `isShrinkResources = true` y se **firma con la clave debug**
  (`signingConfig = signingConfigs.getByName("debug")`) para conservar la **misma firma** que los APK anteriores
  y poder **actualizar encima sin desinstalar**. Resultado: ~15.4 MB (debug) → ~1.3 MB (release).
- `proguard-rules.pro` conserva los nombres de enums del proyecto
  (`-keepclassmembers enum com.minitimer.** { *; }`) porque la persistencia serializa enums por `name()`/`valueOf()`.
- **Versionado**: sube +1 por **cada APK generado** (no por commit). `versionName = "1.0.<n>"`, `versionCode = <n>`
  en `app/build.gradle.kts`. Se deja **solo el último** APK en `releases/`.
- **Flujo**: compilar → copiar el APK a `releases/` → commit → push (el push se confirma con el usuario).

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
- La etiqueta visible del launcher es **`!Timer`** (en `MainActivity`), elegida para que la app quede primera en la lista; el nombre de la aplicación sigue siendo **Mini Timer**.
- **No versionar `tools/`**: los scripts y previews del wordmark (`tools/`) son **solo locales** (están en `.gitignore`). Sirven para regenerar el wordmark `TIMES` (parser TrueType con stdlib + generador de paths) pero **no se incluyen en commits**. El resultado ya vive en el código: `ui/TimesWordmark.kt`.

## Pendientes (TODO)

- [ ] **(media)** Instalar **offline** una librería Python para renderizar/medir fuentes y generar imágenes de glifos (preferible **Pillow** con FreeType, autocontenida; o **fonttools**, Python puro). Se usará para previsualizar/rasterizar la tipografía (`ui/theme/Type.kt`) o producir PNG/SVG de glifos. Seguir [`docs/instalar-librerias-offline-crowdstrike.md`](docs/instalar-librerias-offline-crowdstrike.md) (descargar wheels a mano e instalar con `--no-index` por el bloqueo de CrowdStrike).
