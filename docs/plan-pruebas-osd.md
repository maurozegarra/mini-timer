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
