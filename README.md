# Mini Timer (Expo / React Native)

Temporizador de cuenta atrás con estética **One UI (Samsung Clock)**:

- Pantalla de configuración con **teclado numérico** (los dígitos entran por la derecha: HHMMSS) y **presets** rápidos.
- Pantalla de cuenta atrás con **anillo de progreso** (SVG) que se vacía, **hora de finalización** (con segundos, p. ej. `Termina a las 02:44:35 p.m.`) y controles **Cancelar / Pausa / Reanudar**.
- Al terminar: **alarma** (vibración en móvil, beep vía Web Audio en navegador) con **Descartar / Reiniciar**.

## Funcionalidades

- **Configuración** (icono ⚙): accesible desde la pantalla de inicio.
  - **Idioma**: Español / English (toda la interfaz y el formato de hora). Default: **English**.
  - **Color de acento**: paleta de colores que se aplica al anillo, botones, presets y favicon. Default: **`#ff5252`**.
  - **Presets editables**: agrega (`mm:ss` o `hh:mm:ss`; un solo número = minutos) y elimina presets. Sin duplicados y ordenados.
  - **Auto descartar**: al terminar, vuelve solo a la pantalla de inicio tras `X` segundos (`Off, 3s, 5s, 10s, 30s, 60s`). Default: **3s**.
  - **Restablecer valores** a los defaults.
- **Ventana flotante (solo web)**: opción configurable (default **apagada**). Usa la **Document Picture-in-Picture API** (Chrome/Edge) para mostrar solo el tiempo restante en una mini ventana que flota sobre otras apps. Al activarla, se abre automáticamente cuando la pestaña pierde el foco y se cierra al volver; también hay un botón manual.
- **Favicon e título de pestaña (web)**: muestra un ícono de timer (con el color de acento) y el **tiempo restante** en el título de la pestaña.
- **Tipografía**: dígitos en **JetBrains Mono** (monoespaciada, con `0` ranurado que se distingue de la `O`), cargada con `expo-font` en web y nativo. La ventana flotante la carga vía Google Fonts (fallback a `Consolas`/`monospace`).

## Persistencia

Los ajustes se guardan en **`localStorage`** (solo web). En nativo funcionan en memoria durante la sesión (añadir `AsyncStorage` requeriría instalar una dependencia vía `nodefake`).

## Requisitos
- Node.js 18+
- App **Expo Go** en el teléfono (para probar en dispositivo).

## Dependencias principales
- `expo` (SDK 51), `react-native`, `react-native-web`.
- `react-native-svg` — anillo de progreso.
- `expo-font` + `@expo-google-fonts/jetbrains-mono` — fuente JetBrains Mono bundleada (offline). Declaradas en `app.json` vía el plugin `expo-font`.

## Entorno corporativo (laptop con Netskope + JFrog + McAfee)
Este proyecto se ejecuta en una laptop con restricciones. Notas importantes:

### Instalar dependencias (registro JFrog detras de Netskope)
`node.exe` esta exento del steering de Netskope y JFrog devuelve `403`. Hay que usar un binario de Node renombrado (`nodefake.exe`) para que el trafico pase por el egress corporativo:

```powershell
$env:NODE_EXTRA_CA_CERTS="C:\workspaces\devin-cli-env\jfrog-combined.pem"
& "C:\workspaces\devin-cli-env\nodefake.exe" "C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js" install
```

> No usar `npx expo install`: lanza un `npm install` con el `node.exe` real (exento) y vuelve el 403. Instala versiones con `npm install "<paquete>@<version>"` via nodefake.

### Arrancar el servidor de desarrollo
- Usar el **node normal** (no nodefake): el dev server solo sirve en local.
- El puerto **8081 esta ocupado por McAfee (`macmnsvc`)**, lo que provoca `ECONNRESET`. Usar otro puerto:

```powershell
# Web
node node_modules\expo\bin\cli start --web --port 8089

# Telefono (misma WiFi): anuncia la IP local en el QR
$env:REACT_NATIVE_PACKAGER_HOSTNAME="<IP_LOCAL_DE_LA_LAPTOP>"
node node_modules\expo\bin\cli start --port 8089 --host lan
```

Escanea el QR con **Expo Go**. Si el telefono no alcanza la laptop (aislamiento de red corporativa), usa `--tunnel` (requiere instalar `@expo/ngrok` via nodefake).

## Estructura
- `App.js` — toda la logica, estilos, configuracion, ventana flotante y carga de fuentes.
- `app.json` — configuracion de Expo (tema oscuro, plugin `expo-font`).
- `babel.config.js` — preset de Expo.
- `package.json` — dependencias y scripts.
