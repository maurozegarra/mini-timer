---
trigger: always_on
---
# Regla de implementación: referencias antes que invención

Antes de implementar cualquier funcionalidad, seguir SIEMPRE este orden:

1. **Buscar en otros proyectos del workspace** si ya existe una implementación similar que funcione. Si existe, usar ese patrón como base y adaptar.
2. **Revisar la documentación oficial** del componente/API/framework que se va a usar. Comparar el enfoque existente con lo que la documentación recomienda.
3. **Solo entonces implementar**, usando el patrón estándar encontrado.

Esto aplica especialmente cuando el usuario pide "hacer de cero": "de cero" significa usar el enfoque estándar correcto, no reinventar desde APIs de bajo nivel.

## Anti-patrones a evitar

- **Sesgo de anclaje**: no aferrarse al código existente como punto de partida cuando el enfoque mismo es el problema.
- **Sunk cost**: si un enfoque requiere múltiples parches encadenados, detenerse y cuestionar el enfoque entero, no seguir parchando.
- **Actuar a ciegas**: usar herramientas de debugging (adb logcat, etc.) desde el inicio, no como último recurso.
- **Confundir "de cero" con "desde lo más bajo nivel"**: de cero = patrón estándar limpio, no reimplementar lo que el framework ya provee.
