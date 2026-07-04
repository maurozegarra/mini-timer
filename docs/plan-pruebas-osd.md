# Plan de pruebas — OSD flotante (Reloj)

Objetivo: re-validar cada funcionalidad tras cada cambio para no romper otras.
Marcar Estado con: OK / FALLA / N/A. Anotar versión (`versionName`) probada.

Convenciones:
- "Actualizar encima" = instalar el APK sin desinstalar.
- "Instalación limpia" = desinstalar y volver a instalar (necesario para defaults nuevos, cambios de canal de notificación o persistencia).
- P1 = panel izquierdo (por defecto hora + segundos). P2 = panel derecho (por defecto carga + volumen + batería).

## Tabla de casos

| ID | Funcionalidad | Pasos | Resultado esperado | Estado |
|----|---------------|-------|--------------------|--------|
| A1 | Mostrar OSD | Activar "Mostrar OSD flotante" | La franja aparece sobre otras apps | |
| A2 | Ocultar OSD | Desactivar el OSD | La franja desaparece; nada queda pegado | |
| B1 | Content: solo activos | Abrir Content | Solo se ven chips de controles activos + botón "+" | |
| B2 | Content: añadir | Clic "+" y elegir p. ej. "Battery" | Aparece el chip y el dato en el OSD | |
| B3 | Content: quitar | Clic en un chip activo | Se quita el chip y el dato del OSD | |
| B4 | Seconds | Añadir "Seconds" | La hora muestra segundos y actualiza cada 1s | |
| B5 | 24h | Alternar "24-hour format" | Cambia entre 12h/24h | |
| B6 | Date | Añadir "Date" | Aparece la fecha | |
| B7 | Volume | Añadir "Volume" | Muestra `[n]`; cambia al subir/bajar volumen | |
| B8 | Battery | Añadir "Battery" | Muestra `[nn%]`; refleja el nivel real | |
| B9 | Charging | Añadir "Charging"; enchufar/desenchufar | Aparece `[AC]`/`[USB]` al enchufar; desaparece al quitar | |
| B10 | Memo | Añadir "Memo" y escribir texto | Aparece el texto; respeta límite de 40 | |
| C1 | Align: precedencia X | Align OFF, mover X a posición errónea; activar Align | El panel salta a alinearse con el reloj del sistema (ignora X); control X se deshabilita | |
| C2 | Align: seguimiento | Con Align ON, cambiar hora del sistema (1→2 dígitos) | El panel se recoloca para seguir al reloj | |
| C3 | Align: desactivar | Desactivar Align | Vuelve al anclaje manual; control X se reactiva con su valor previo | |
| C4 | Align: sin Accesibilidad | Activar Align sin permiso | Abre Ajustes de Accesibilidad / muestra aviso | |
| C5 | Align: solo con hora | Panel sin "Time" | La opción Align no se muestra | |
| D1 | Posición X (manual) | Align OFF, usar +/- en X | El panel se mueve horizontalmente; valor persiste por orientación | |
| D2 | Posición Y | Usar +/- en Y | El panel se mueve verticalmente (incluye sobre la barra de estado) | |
| D3 | Baseline Y=0 | Instalación limpia, offY=0 | Ambos paneles a la misma altura base (-12dp respecto al inset) | |
| E1 | Borde derecho P2 fijo | P2 con carga+batería; enchufar/desenchufar varias veces | El borde derecho queda FIJO; el contenido no se desplaza al aparecer/desaparecer `[AC]` | |
| E2 | Borde derecho + memo | P2 con memo activo | El borde derecho se mantiene con el ancho del memo | |
| F1 | Color texto | Cambiar color del panel | El texto cambia de color | |
| F2 | Modo oscuro auto | Alternar modo oscuro del sistema | El color de texto se adapta si está en automático | |
| G1 | Tamaño texto | Usar +/- de Tamaño | El texto escala entre mín y máx | |
| H1 | Reset defaults | Botón restaurar | El panel vuelve a su posición/columna por defecto | |
| I1 | Dos paneles | P1 y P2 activos a la vez | Ambos visibles, sin solaparse, cada uno en su lado | |
| J1 | Orientación | Rotar el equipo | Cada orientación conserva su propio offset | |
| K1 | Persistencia | Reiniciar el equipo / la app | Los paneles y su config se restauran | |

## Bitácora de versiones probadas

| Versión | Fecha | Casos verificados | Notas |
|---------|-------|-------------------|-------|
| 1.0.171 | - | C1–C3 (Align original, referencia buena) | APK de referencia extraído del historial |
| 1.0.187 | 2026-07-04 | E1/E2 = **FALLA** | Ver "Bug abierto E1/E2" abajo |

## Bug abierto: E1/E2 — borde derecho de P2 no queda fijo

Síntoma (confirmado por el usuario): el panel derecho se comporta como si estuviera
anclado por la IZQUIERDA. Al aparecer `[AC]` el contenido se ensancha y el borde derecho
se desplaza a la derecha; al quitarlo, "regresa a su sitio". O sea: el lado que se mantiene
fijo es el izquierdo, no el derecho.

### Intentos que NO funcionaron
1. Ventana `WRAP_CONTENT` + recalcular `x = bordeDerecho - anchoMedido` en cada `bind()`
   (medición manual con `container.measure`).
2. Re-attach (detach+attach) del panel al detectar cambio de ancho. Falla por timing:
   la ventana recién re-añadida aún no tiene insets/medición al recolocar.
3. `FrameLayout` de ancho `MATCH_PARENT` + píldora anclada con `layout_gravity = RIGHT`.
4. Igual que (3) pero forzando `lp.width = displayMetrics.widthPixels` (ancho explícito en px)
   para evitar que `FLAG_LAYOUT_NO_LIMITS` encoja la ventana. **Sigue fallando** (v1.0.187).

### Hipótesis pendientes de comprobar CON DATOS (no a ciegas)
- H1: La ventana NO ocupa el ancho completo real (aunque se fije en px), por lo que el
  `FrameLayout` no tiene espacio libre y `gravity RIGHT` equivale a LEFT. Posible interacción
  de `FLAG_LAYOUT_NO_LIMITS` / `FLAG_LAYOUT_IN_SCREEN` con el ancho de la ventana overlay.
- H2: `displayMetrics.widthPixels` en el Service devuelve un ancho distinto al del espacio de
  coordenadas de la ventana (cutout / gesture / multiventana), dejando el borde fuera de sitio.
- H3: El re-layout al cambiar el texto no vuelve a aplicar la `gravity` de la píldora.

### Próximo paso recomendado (obtener DATOS primero)
Añadir logging TEMPORAL en `ClockOverlayService.applyPosition()` y en un
`OnLayoutChangeListener` del `container`:
`Log.d("OSD", "win=${container.width} pill=${pill.width} frameGravity=${flp.gravity} x=${lp.x} y=${lp.y} screenW=${resources.displayMetrics.widthPixels} saRight=${sa.right}")`
Enchufar/desenchufar y leer con `adb logcat -s OSD`. Con eso se sabe si `container.width`
es el ancho de pantalla (descarta/confirma H1) y si la píldora se reposiciona.

### Alternativa robusta si H1 se confirma
Quitar `FLAG_LAYOUT_NO_LIMITS` y comprobar si con ventana normal el ancho `MATCH_PARENT`
funciona y `gravity RIGHT` ancla. Coste: el panel no podría dibujarse SOBRE la barra de
estado (perdería el `BASE_Y_DP` negativo); habría que decidir si ese "sobre la barra" se
mantiene como feature o se sacrifica por el anclaje correcto.

Estado del código al pausar: v1.0.187, enfoque `FrameLayout` ancho completo + ancho explícito.
