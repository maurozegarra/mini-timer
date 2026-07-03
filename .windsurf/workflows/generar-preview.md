---
description: Generar un preview HTML fiel de una pantalla/estado de la app en tools/preview
---

Objetivo: producir un preview en el navegador que sea 95-99% fiel a la app, SIN
inventar data. Todo el kit vive en `tools/preview/` (local, en .gitignore).

Reglas base (siempre):
- LEER el composable exacto de la pantalla/estado pedido y mapear 1:1 sus valores
  (sp/dp, colores, pesos, radios, espaciados). No aproximar de memoria.
- Colores SIEMPRE desde `tokens.css` (`var(--color-*)`). Nunca hardcodear.
- Data SIEMPRE real: sacarla del código (defaults en `Settings()` /
  `SettingsStore.defaultTimers()`) o de lo que el usuario indique. No inventarla.
- Avisar EXPLÍCITAMENTE lo que no se pueda replicar exacto en CSS (animaciones,
  efectos, dibujo con Canvas, iconos aproximados).

Pasos:

1. Confirmar con el usuario: qué pantalla, qué tab y qué estado/data quiere. Si no
   lo especifica, asumir la data por defecto real del código.

2. Si el tema cambió desde el último preview, regenerar tokens:
// turbo
   python tools\preview\extract_tokens.py

3. Localizar y LEER el composable exacto (usar code_search + read_file). Anotar
   cada dp/sp/color/peso/radio/icono que se vaya a replicar.

4. Crear el archivo `tools/preview/<nombre>.html` clonando la estructura de
   `tools/preview/timer.html` como plantilla:
   - `<link rel="stylesheet" href="tokens.css">` y `href="kit.css"`.
   - `:root { --accent: <color real del estado>; }` (default de fábrica = #ff5252).
   - Dentro de `.device`, en este orden: `.statusbar` (barra de estado),
     el contenido de la pantalla, `.sysnav` (3 botones), y `.ruler-row` fuera del
     device arriba.
   - Incluir `<script src="calib.js"></script>` antes de `</body>`.
   - Reusar clases del kit; solo añadir CSS específico de la pantalla en un
     `<style>` local con las métricas exactas leídas del código.

5. Iconos: si el diseño usa un drawable vectorial propio, copiar su `pathData`
   real (de `app/src/main/res/drawable/*.xml`) al SVG. Solo usar un icono Material
   equivalente si no hay drawable, y avisarlo.

6. Abrir el preview para el usuario:
// turbo
   start tools\preview\<nombre>.html

7. En la respuesta: listar qué quedó 1:1 y qué es aproximado. Pedir feedback y
   afinar. El tamaño físico ya se calibra solo con el selector Monitor 1/2
   (Monitor 1 = 1.1765, Monitor 2 = 0.964).
