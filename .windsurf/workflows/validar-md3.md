---
description: Validar SIEMPRE cada botón, ícono y componente contra los lineamientos Material Design 3 antes de proponerlo, y reportar incoherencias al explorar el código
---

APLICA SIEMPRE, no solo en previews: cada vez que se proponga, diseñe, revise o
implemente un elemento de UI (botón, ícono, acción, componente, navegación), y
también al explorar código/composables. No es un slash-command opcional: es un
criterio permanente de calidad.

Principio: NUNCA proponer un ícono/botón/componente sin antes verificar que su
semántica y su uso son correctos según Material Design 3 (Material You). Un
ejemplo real de error a evitar: proponer el ícono de 3 puntos (overflow
`more_vert`) para una acción que NO abre un menú de varias opciones — el overflow
solo es correcto cuando hay múltiples acciones que no caben; para un destino
único se usa un ícono descriptivo.

Antes de PROPONER cualquier elemento de UI:

1. Identificar la intención real del elemento (¿navega a un destino único?, ¿abre
   un menú?, ¿ejecuta una acción?, ¿alterna un estado?) y elegir el patrón MD3
   correcto para esa intención.

2. Validar el ÍCONO contra su semántica MD3 y declararlo explícitamente en la
   respuesta (nombre Material + por qué es correcto). Reglas clave:
   - `more_vert` / `more_horiz` (overflow): SOLO si hay 2+ acciones agrupadas en
     un menú. Prohibido para un destino/acción única.
   - `menu` (hamburguesa): SOLO si abre un navigation drawer. Si no hay drawer, es
     incorrecto.
   - `arrow_back`: volver/cerrar una subpantalla.
   - Destino/acción única: usar un ícono descriptivo de esa acción (p. ej. `tune`
     para "ajustar/configurar", `settings` para ajustes globales, etc.).
   - Coherencia de estilo: filled vs outlined según su rol y consistencia con el
     resto de la pantalla; no mezclar sin criterio.

3. Validar el COMPONENTE y su tipo MD3 según jerarquía/énfasis:
   - Botones: `Filled` (acción principal), `FilledTonal`, `Elevated`, `Outlined`
     (secundaria), `Text` (baja prioridad). Elegir por énfasis, no al azar.
   - App bars, FAB, chips, switches, sliders, listas, diálogos, bottom nav: usar
     el componente MD3 correcto para el caso.

4. Validar métricas y accesibilidad MD3:
   - Área táctil mínima 48x48 dp.
   - Tipografía = escala type de MD3; formas/esquinas y elevación coherentes con
     los tokens del sistema.
   - Roles de color MD3 (primary, surface, onSurface, etc.), no colores sueltos.

5. Al EXPLORAR código o composables (aunque la tarea no sea de UI): si se detecta
   un uso incoherente o incorrecto respecto a MD3 (ícono con semántica errónea,
   componente inadecuado, área táctil chica, mezcla de estilos, color fuera de
   rol), REPORTARLO al usuario aunque no se haya pedido. No dejarlo pasar en
   silencio.

6. En la respuesta al usuario, al proponer UI: listar cada elemento con su ícono/
   componente elegido y una línea de justificación MD3. Si algo se aleja de MD3
   por una razón deliberada, decirlo explícitamente y ofrecer la alternativa
   correcta.
