---
trigger: always_on
description: Convenciones de comportamiento para el proyecto mini-timer
---

# Idioma
- Responder siempre en español.

# Código
- No modificar ni eliminar comentarios/documentación existentes, salvo que el usuario lo pida explícitamente.
- En código NUEVO (clases, funciones, bloques que se crean en la sesión) SÍ se permite agregar comentarios/KDoc que expliquen el PORQUÉ (intención, decisiones, sutilezas), no el "qué" obvio.
  - Preferir KDoc en clases/funciones públicas y comentarios cortos en lógica no evidente.
  - Evitar comentarios redundantes que solo repiten lo que el código ya dice.

# UI / Diseño
- Usar Material Design 3 (Material You) en toda la interfaz: componentes, tipografía, formas, elevación y paleta de color.

# Audio / Alarma
- Principio: "lo que pruebas es lo que suena": el preview de Ajustes debe sonar EXACTAMENTE igual que la alarma real (mismo stream, usage, escalado y comportamiento).
- Volumen INDEPENDIENTE del equipo: subir el stream de alarma al máximo durante la reproducción (preview y alarma) y restaurarlo al terminar; el nivel fino lo da `perceptualVolume` sobre `MediaPlayer.setVolume`. Así 100% = máximo real del hardware, sin depender del volumen configurado en el equipo.
- Reproducir con `USAGE_ALARM` para que suene aunque el equipo esté en silencio o en No molestar.
- Mientras suena, pedir foco de audio `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (baja la música de fondo) y abandonarlo al terminar para que la música recupere su volumen.
- Curva de volumen (cómo ajustarla más adelante):
  - El ajuste 0..1 (`Settings.alarmVolume`) se mapea a una ganancia perceptual en dB con `perceptualVolume` (en `TimerViewModel`): 100% -> 0 dB (máximo); 0% -> -`VOLUME_DB_RANGE` dB (silencio). El oído es logarítmico, por eso se usa dB.
  - `VOLUME_DB_RANGE` (constante en `TimerViewModel`) controla cuánto atenúan los porcentajes bajos: subirlo = niveles bajos MÁS silenciosos; bajarlo = MÁS altos. El 100% no cambia con este valor. Valor actual: 48f.
  - Default del volumen: `Settings.alarmVolume` (actual 0.25 = 25%). Cambiar el default solo afecta instalaciones limpias.

# Versionado
- El versionado sube +1 por cada compilación (debug o release, indistintamente).
- `versionName` = "1.0.<n>" y `versionCode` = <n> en `app/build.gradle.kts`; la fuente de verdad es el `versionName` actual.
- El número de versión es un control de iteraciones (mejoras, fixes, etc.); refleja cuántas veces se ha compilado la app.
- Subir la versión ANTES de compilar, no después.

# Releases
- Dejar únicamente el último APK en `releases/`, con el nombre `mini-timer-1.0.<n>.apk`.
- Eliminar la versión anterior al subir una nueva.
- No generar `mini-timer-debug.apk` ni APKs prestickman.

# Flujo de build
- Orden del build: compilar, copiar el APK a `releases/`, hacer commit y push.
- Commit y push requieren permiso explícito del usuario cada vez, independientemente del flujo.

# Reinstalar vs actualizar (al probar)
- Preferir el camino corto: instalar el APK encima (update), sin desinstalar.
- Antes de indicar cómo probar, evaluar y avisar EXPLÍCITAMENTE si basta con actualizar o si hace falta reinstalar (desinstalar + instalar) o borrar datos, y por qué.
- Indicar "reinstalar / borrar datos" cuando el cambio incluya alguno de estos casos (actualizar encima NO lo refleja):
  - Nuevos valores por defecto en Settings (los defaults solo aplican a instalación limpia; el usuario existente conserva las claves viejas).
  - Cambios en la persistencia (`SharedPreferences`, esquema o claves) que requieran estado limpio para probarse.
  - Cambios en canales de notificación (importancia, sonido, vibración, etc.): no se actualizan si el canal ya existe.
  - Permisos nuevos (especiales o runtime) que deban re-otorgarse.
  - Cambios en el ícono del launcher, componentes (activities/services nuevos) o shortcuts.
  - Bajar el `versionCode` (downgrade no permite update encima).
- En caso contrario (cambios de lógica/UI sin tocar lo anterior), basta con actualizar encima.
