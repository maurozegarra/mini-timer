---
description: Workflow de versionado, compilación e instalación en dispositivo
---

# Workflow: Versionado, compilación e instalación

1. **Versionar ANTES de compilar**:
   - `versionCode += 1`
   - `versionName = "1.0.<versionCode>"` en `app/build.gradle.kts`
   - La fuente de verdad es el `versionName` actual
   - Subir versión antes de compilar, no después

2. **Conectar por wireless debugging**: `adb connect 192.168.18.128:37345`

3. **Compilar, instalar y lanzar** la app en el dispositivo

