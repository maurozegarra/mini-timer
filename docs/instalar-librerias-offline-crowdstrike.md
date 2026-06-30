# Instalar librerías de Python sin despertar a CrowdStrike (instalación offline)

> Guía para instalar paquetes de Python en este equipo (Windows + **CrowdStrike**)
> evitando bloqueos. Pensada para anticiparnos: **siempre** instalar offline desde
> wheels descargados a mano.

## Por qué

En este equipo hay dos guardianes que bloquean lo "normal":

- **CrowdStrike** bloquea a `pip` cuando **descarga desde la red** (`pip install <pkg>`
  con acceso a internet). El proceso falla con exit code 1 y sin salida útil.
- **SmartScreen** bloquea **ejecutables `.exe` descargados** (p. ej. un `ffmpeg.exe`
  suelto, o el binario que baja `imageio-ffmpeg`).

La estrategia que **sí funciona**:

1. Descargar los `.whl` (wheels) **manualmente con el navegador**. Un `.whl` es un
   `.zip`, **no** un ejecutable → no dispara CrowdStrike ni SmartScreen.
2. Instalar **offline** (`--no-index`), es decir, sin que `pip` toque la red.
3. Preferir paquetes **autocontenidos** (sin `.exe` externo). Ej.: usar
   `opencv-python-headless` en vez de `imageio-ffmpeg` para leer/decodificar video.

## Proceso paso a paso

### 1. Conocer el entorno (versión y arquitectura de Python)

```powershell
python --version
python -c "import platform; print(platform.machine())"
```

Ejemplo de este equipo: **Python 3.13.x**, **AMD64** (64 bits).
De aquí salen los tags que deben tener los wheels:

- Python 3.13 → `cp313`
- 64 bits Windows → `win_amd64`

### 2. Elegir el wheel correcto en PyPI

En la página del paquete, pestaña **"Download files"** (`https://pypi.org/project/<pkg>/#files`),
elige según el tipo de paquete:

| Tipo de paquete | Tag del wheel a elegir | Ejemplo |
|---|---|---|
| Con código nativo atado a la versión de Python (numpy, scipy, pandas…) | `cp313-cp313-win_amd64` | `numpy-2.5.0-cp313-cp313-win_amd64.whl` |
| Con ABI estable `abi3` (sirve para Python 3.7+) | `cp37-abi3-win_amd64` | `opencv_python_headless-4.13.0.92-cp37-abi3-win_amd64.whl` |
| Puro Python (sin binarios) | `py3-none-any` | `<pkg>-x.y.z-py3-none-any.whl` |

> Regla rápida: el tag `cpXYZ` debe **coincidir** con tu versión de Python, salvo
> que el wheel sea `abi3` (compatible hacia adelante) o `py3-none-any` (universal).

### 3. Descargar TODOS los wheels (incluidas dependencias)

Guárdalos en una carpeta dedicada, p. ej.:

```
C:\Users\<usuario>\Downloads\wheels
```

**Importante:** `--no-index` **no** resuelve dependencias que falten. Hay que tener
en la carpeta también las dependencias transitivas. Dos formas de conseguirlas:

- **A mano:** revisa la sección *Requirements* del paquete en PyPI y descarga sus
  deps. Ej.: `opencv-python-headless` necesita `numpy`.
- **Con red permitida (otra máquina/sesión):** junta todo de una vez con
  `pip download <pkg> -d wheels` y luego copia la carpeta `wheels` a este equipo.

### 4. Instalar offline (sin red → CrowdStrike no se activa)

```powershell
python -m pip install --user --no-index --find-links "C:\Users\<usuario>\Downloads\wheels" <pkg> [dep1] [dep2]
```

- `--no-index`: prohíbe a pip ir a internet (clave para no despertar a CrowdStrike).
- `--find-links <carpeta>`: pip resuelve **solo** con los `.whl` locales.
- `--user`: instala en el perfil del usuario (no requiere admin).

> En PowerShell, el `WARNING ... not on PATH` o cualquier texto en stderr puede hacer
> que el comando reporte **exit code 1** aunque imprima `Successfully installed`.
> Fíjate en el mensaje, no solo en el código de salida.

### 5. Verificar

```powershell
python -c "import cv2, numpy; print('ok', cv2.__version__, numpy.__version__)"
```

Si CrowdStrike pusiera en cuarentena algún binario nativo al importarlo (raro con
wheels, pero posible), el plan B es no depender de ese paquete (p. ej. pedir que
exporten los datos ya procesados: imágenes/frames en vez de leer el video).

## Qué NO hacer

- ❌ `pip install <pkg>` con internet → **bloqueado por CrowdStrike**.
- ❌ Descargar y ejecutar `.exe` sueltos (ffmpeg, etc.) → **bloqueado por SmartScreen**.
- ❌ Usar paquetes que descarguen un binario externo en tiempo de instalación/uso
  (p. ej. `imageio-ffmpeg`) → preferir alternativas autocontenidas.

## Registro de lo ya instalado en este equipo

Instalados offline (modo `--user`), disponibles para análisis de imagen/video a futuro:

- `numpy 2.5.0` (`cp313-win_amd64`)
- `opencv-python-headless 4.13.0.92` (`cp37-abi3-win_amd64`)

Wheels guardados en: `C:\Users\mzegarra_ide\Downloads\wheels`

### Caso de uso real (referencia)

Para implementar la animación del borde (APK 1.0.137) necesitábamos "ver" un `.mp4`.
Sin `ffmpeg` y con CrowdStrike bloqueando `pip` por red, se instaló `opencv-python-headless`
offline y se extrajeron fotogramas con `cv2` para analizar la animación.
