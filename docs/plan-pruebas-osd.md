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
| 1.0.187 | 2026-07-04 | E1/E2 = **FALLA** | Intento con FrameLayout de ancho explícito |
| 1.0.188 | 2026-07-04 | E1/E2 = **FALLA** | Anclaje nativo vía `Gravity.END` (WRAP_CONTENT) |
| 1.0.189 | 2026-07-04 | E1/E2 = pendiente (build de DIAGNÓSTICO) | Instrumentación de logging, ver sección abajo |
| 1.0.190 | 2026-07-04 | E1/E2 = **FALLA** (confirmado con medición de pixeles) | Log a archivo + botón compartir |
| 1.0.191 | 2026-07-04 | E1/E2 = pendiente de verificar | Corrección: anclaje START + lp.x = target − anchoReal |

### DATO DEFINITIVO (medición de pixeles sobre capturas reales, no `getLocationOnScreen`)
Midiendo los bordes del texto de P2 en las capturas (equipo MIUI, pantalla 1080, captura escalada a 738 px):

| Estado | left | right | width |
|--------|------|-------|-------|
| con `[AC]` (`[AC]·[3]·[76%]`) | **524** | 706 | 182 |
| sin `[AC]` (`[3]·[76%]`) | **524** | 638 | 114 |

El borde IZQUIERDO está fijo (524) y el DERECHO salta 68 px → el panel está **anclado por la izquierda**. `Gravity.END` NO se respeta en overlays de este equipo con `FLAG_LAYOUT_NO_LIMITS`, y `getLocationOnScreen` mentía (reportaba `rightEdge` fijo, lo contrario de la realidad). Por eso el log de v1.0.189/190 llevó a una conclusión errónea. **Lección: verificar SIEMPRE con pixeles reales, no con la posición "pedida" a la API.**

### Corrección aplicada (v1.0.191)
Anclar P2 con `Gravity.START` (sí se respeta) y fijar el borde derecho calculando
`lp.x = rightTargetPx − anchoReal`, recalculado en el `OnLayoutChangeListener` con el
ancho YA medido por el layout (no `measure()` manual como el intento 1). `rightTargetPx =
screenW − saRight − margin + offX`. Pendiente de verificar con nueva captura + medición.

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
   para evitar que `FLAG_LAYOUT_NO_LIMITS` encoja la ventana (v1.0.187). **Falla.**
5. Anclaje nativo del `WindowManager`: `lp.gravity = TOP or END` con la ventana `WRAP_CONTENT`
   (el OS debería sujetar el borde derecho automáticamente) (v1.0.188). **Falla.**

### Hipótesis pendientes de comprobar CON DATOS (no a ciegas)
- H1: La ventana no ocupa/ancla al ancho real esperado; `FLAG_LAYOUT_NO_LIMITS` /
  `FLAG_LAYOUT_IN_SCREEN` interfieren en el eje horizontal y neutralizan `Gravity.END`.
- H2: `displayMetrics.widthPixels` en el Service devuelve un ancho distinto al del espacio de
  coordenadas de la ventana (cutout / gesture / multiventana), dejando el borde fuera de sitio.
- H3: El re-layout al cambiar el texto no re-aplica el anclaje (el sistema mantiene la X previa).

## Herramienta de diagnóstico E1/E2 (v1.0.189)

Instrumentación TEMPORAL en `ClockOverlayService.kt` (activada por `DIAG = true` en el
companion; tag de log `OSD_DIAG`). Registra la **geometría REAL en pantalla** y así
convierte el bug en datos medibles en vez de conjeturas.

Qué loguea (a logcat con tag `OSD_DIAG` **y** a un archivo `osd_diag.log`):
- **`OnLayoutChangeListener`** del `container`: en cada re-layout (p. ej. al ensancharse por
  `[AC]`) imprime `winW`, `onScreenX` (posición absoluta con `getLocationOnScreen`),
  `leftEdge`, `rightEdge` (= onScreenX + winW), `lpGravity`, `lpX`, `lpY`, `screenW`.
- **Cambio de carga** en `updateContent()`: imprime la transición `'[X]' -> '[Y]'` para
  correlacionar el enchufar/desenchufar con los re-layouts.

Salida a archivo (SIN PC): la app escribe con timestamp a
`Android/data/com.minitimer/files/osd_diag.log` (vía `getExternalFilesDir`, sin permisos).
La notificación del overlay incluye un botón **"Compartir log OSD"** (solo en builds DIAG)
que abre el selector para enviar el archivo por WhatsApp / correo / subirlo aquí.

Cómo capturar (solo con el teléfono):
1. Instalar el APK de diagnóstico, activar el OSD con el panel derecho (P2) y dejar visibles
   carga + batería.
2. Enchufar el cargador, esperar 2s, desenchufar, esperar 2s. Repetir 2–3 veces.
3. Abrir la notificación del OSD (persistente) y pulsar **"Compartir log OSD"**; enviar el
   archivo. (Alternativa con PC: `adb logcat -s OSD_DIAG`.)

Cómo LEER el resultado (qué confirma cada cosa):
- Si `rightEdge` **cambia** entre el estado con `[AC]` y sin `[AC]` → el anclaje derecho NO
  sujeta (confirma H1/H3). El valor que se mantenga constante (leftEdge o rightEdge) revela
  por qué borde está anclando realmente el sistema.
- Si `winW` no crece al aparecer `[AC]` → el problema es de medición del contenido, no de
  anclaje.
- Comparar `screenW` con `rightEdge` sin `[AC]`: dice si el borde derecho está donde debería
  (≈ `screenW − saRight − margin`).

### Alternativa robusta si H1 se confirma
Quitar `FLAG_LAYOUT_NO_LIMITS` y comprobar si con ventana normal el `Gravity.END` ancla.
Coste: el panel no podría dibujarse SOBRE la barra de estado (perdería el `BASE_Y_DP`
negativo); decidir si ese "sobre la barra" se mantiene como feature o se sacrifica.

### Bitácora de datos capturados
_(pegar aquí la salida de `adb logcat -s OSD_DIAG` cuando esté disponible)_

Estado del código: v1.0.189, build de diagnóstico (anclaje `Gravity.END` + logging `OSD_DIAG`).
