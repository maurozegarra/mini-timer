# Reporte de consistencia de UI y opciones huérfanas

Fecha: 2026-07-03. Alcance: `app/src/main/java/com/minitimer/ui/**` + modelo/consumo de `Settings`.

Objetivo: listar inconsistencias de UI y opciones sin uso ("huérfanas") para ir
corrigiéndolas de forma incremental. Cada punto tiene severidad y propuesta.

Leyenda de severidad: **Alta** (rompe consistencia visible / bug), **Media**
(inconsistencia notoria), **Baja** (pulido).

---

## 1. Opciones huérfanas

### 1.1 Presets del temporizador — RESUELTO en esta sesión
Antes: la sección "Presets" de Ajustes permitía agregar/borrar/persistir valores
que **no se consumían en ninguna pantalla**. Ahora están conectados como chips en
la hoja "Nuevo temporizador" (`NewTimerSheet`) y el "agregar preset" de Ajustes usa
`WheelTimePicker`.

### 1.2 Código muerto derivado del cambio de Presets — RESUELTO
Eliminado lo que quedó sin uso tras migrar Ajustes a `WheelTimePicker`:
- `TimerViewModel.addPreset(input: String)` (borrado; queda `addPresetSeconds`).
- `util/Format.kt: parsePresetInput(...)` (borrado; solo lo usaba `addPreset`).
- String `presetPlaceholder` en `i18n/Strings.kt` (interfaz + ES + EN).

### 1.3 Verificación del resto de opciones — sin huérfanas
Se confirmó que TODAS las demás opciones de `Settings` se consumen:
- `showRing`, `showOverlay`, `showNowBar` → `TimerBus` → `LiveTimerService` /
  `TimerOverlay`.
- `ignoreSilent`, `headsetMode`, `alarmSoundUri`, `alarmVolume`, `vibrationEnabled`,
  `vibrationPattern` → alarma en `TimerViewModel`.
- `autoDismiss`, `addIncrementSec`, `padPlayerClock`, `accent`, `language` → en uso.
- `ringOffsetX/Y` → `TimerOverlay.positionRing`.

---

## 2. Entrada de tiempo/duración — inconsistencia principal — RESUELTO

Antes convivían dos paradigmas: `WheelTimePicker` (timer) vs `DurationStepper` +
diálogo de texto (`DurStep`, editor de ejercicios).

Ahora: el editor de ejercicios usa `DurationWheelField` (fila compacta label+valor
que abre un diálogo con `WheelTimePicker`) para prepare/work(TIME)/rest/cooldown.
Los `Stepper` +/- se mantienen solo para conteos discretos (sets, reps, finalCount).
Se eliminaron `DurationStepper`, `DurationDialog` (`AthleteComponents`) y `DurStep`
(`ExerciseEditorScreen`).

### 2.1 Bug: `WheelTimePicker` no refleja cambios externos — RESUELTO
Antes: `WheelColumn` fijaba la posición inicial con `initialFirstVisibleItemIndex`
(una sola vez); si el `value` cambiaba por fuera (p. ej. al tocar un chip de preset
en `NewTimerSheet`), la rueda no se desplazaba.

Ahora: `WheelColumn` es "controlada" con `LaunchedEffect(value)` que hace
`scrollToItem` al número correspondiente, evitando el bucle de realimentación
(ignora el ajuste mientras el usuario hace scroll y cuando el centro ya coincide).

---

## 3. Botones — dos paradigmas y radios dispares — RESUELTO

Antes: paradigmas mezclados (M3 `Button` en timer/ajustes vs `Box` custom en
Athlete) y radios dispares (12/16/20/28/30).

Ahora:
- `AppPrimaryButton` (M3, filled) y `AppOutlineButton` (M3, outlined) en
  `ui/CommonComponents.kt`. Athlete `PrimaryButton`/`AddButton` delegan en ellos
  (fin del paradigma `Box`).
- Radio de CTA unificado a `Dims.button` (28): Start (NewTimerSheet), Start
  (detalle IDLE), `ControlButton` y botones primarios de Athlete.
- Excepciones intencionales: FAB (20, spec MD3), botones pequeños de Ajustes
  (`Dims.buttonSmall` = 12) y `PresetChip` (píldora 50).

---

## 4. Chips de selección — EXCEPCIÓN DOCUMENTADA (sin cambios)

Decisión del usuario: mantener "fill" como estándar (mayor claridad de selección)
y dejar `PresetChip` con estilo "outline" como excepción intencional (replica el
reloj stock de Android). No se modifica nada.

### Detalle (referencia)

| Componente | Dónde | "Seleccionado" |
|---|---|---|
| `Chip` (envuelve `FilterChip`) | Ajustes (idioma, autoDismiss, +tiempo, vibración) | Relleno accent |
| `SegmentToggle` | Athlete (modo work, etc.) | Relleno accent |
| `PresetChip` | Nuevo temporizador (agregado esta sesión) | **Borde accent + texto accent, fondo transparente** |
| `InputChip` | Ajustes (lista de presets, borrar con X) | Sin estado seleccionado (tag removible) |

---

## 5. Toggles duplicados — RESUELTO

Se unificó en un único `SwitchRow` compartido en `ui/CommonComponents.kt`
(package `com.minitimer.ui`), usado por `SettingsScreen` y `ExerciseEditorScreen`.
Se eliminó el `ToggleRow` del editor y el `SwitchRow` privado de Ajustes. Los
toggles del editor ahora igualan el estilo de Ajustes (label SemiBold).

---

## 6. Tarjetas / contenedores — RESUELTO

Radio de tarjetas de contenido unificado a `Dims.card` (20): tarjeta de timer
(antes 24), `SettingsGroup` (ya 20) y `SectionCard` de Athlete (antes 18). Las
filas "acción"/tiles menores (12/16/18) quedan fuera de alcance por ser otro tipo
de elemento.

---

## 7. Componentes duplicados entre módulos — RESUELTO

Compartidos en `ui/CommonComponents.kt`: botón primario/secundario
(`AppPrimaryButton`/`AppOutlineButton`), toggle (`SwitchRow`) y ahora el stepper
+/- (`AppStepButton` + `AppStepper`).

Se eliminaron las duplicaciones: `StepCircle` (Athlete) y `StepBox` (editor, solo
diferían en el fondo) ahora usan `AppStepButton`; `Stepper` (Athlete) delega en
`AppStepper`; los botones de `OffsetStepperRow` (Ajustes), `WeightStepper` (editor)
y el stepper de volumen (Ajustes) usan `AppStepButton`. Este último mantiene su
lógica propia (paso 5%, límites 0–100, preview de audio y botones deshabilitados
en los extremos vía el nuevo parámetro `enabled` de `AppStepButton`).

---

## 8. Tokens de diseño — RESUELTO (base)

Se creó `ui/theme/Dimens.kt` con `Dims`: `card=20`, `button=28`, `buttonSmall=12`,
`field=12`, `buttonHeight=52`. Ya lo consumen los botones compartidos y las
tarjetas (timer/ajustes/athlete). Migrar el resto de literales de forma gradual.

---

## Orden sugerido de corrección

1. ~~(Alta) Bug rueda no controlada (2.1)~~ — HECHO.
2. ~~(Media) Limpieza de código muerto de presets (1.2)~~ — HECHO.
3. ~~(Alta) Rueda en el editor de ejercicios (2)~~ — HECHO.
4. ~~(Media) Unificar botón primario y radios (3, 7)~~ — HECHO.
5. ~~(Media) Estilo de chip (4)~~ — excepción documentada (sin cambios).
6. ~~(Baja) Unificar toggles y radios de tarjeta (5, 6)~~ — HECHO.
7. ~~(Base) Tokens (8)~~ — HECHO.
8. ~~(Media) Unificar steppers `AppStepButton`/`AppStepper` (7)~~ — HECHO.
