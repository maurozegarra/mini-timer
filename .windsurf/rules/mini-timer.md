---
trigger: always_on
description: Convenciones de comportamiento para el proyecto mini-timer
---

# Idioma
- Responder siempre en español.

# Código
- No agregar ni eliminar comentarios o documentación, salvo que el usuario lo pida explícitamente.

# Versionado
- El versionado sube +1 por cada APK generado (no por commit).
- `versionName` = "1.0.<n>" y `versionCode` = <n> en `app/build.gradle.kts`; la fuente de verdad es el `versionName` actual.
- Solo los commits que generan un APK cambian la versión; commits de limpieza/refactor/docs no la tocan.

# Releases
- Dejar únicamente el último APK en `releases/`, con el nombre `mini-timer-1.0.<n>.apk`.
- Eliminar la versión anterior al subir una nueva.
- No generar `mini-timer-debug.apk` ni APKs prestickman.

# Flujo de build
- Orden: compilar, copiar el APK a `releases/`, hacer commit y push.
- Confirmar siempre el push con el usuario (no auto-ejecutar el push).

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
