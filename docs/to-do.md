# To-Do / Pendientes

Registro de funcionalidades pendientes de mini-timer. Al implementar cualquiera,
validar íconos/botones/componentes contra Material Design 3 (ver
`.windsurf/workflows/validar-md3.md`).

## Athlete

### Historial de sesiones (registrado, sin UI)
- **Estado**: las sesiones SÍ se registran y persisten al completar un training
  (`WorkoutPlayerService.recordSession()` -> `WorkoutStore.saveSessions()`), con
  `SessionLog{id, trainingId, trainingName, completedAt}` en prefs `"athlete"`,
  clave `sessions_json`.
- **Falta**: `WorkoutStore.loadSessions()` existe pero solo se usa para anexar
  dentro de `recordSession()`; NINGUNA pantalla lo lee/muestra. Construir una
  vista de historial (en la lista de Athlete o subpantalla) que liste los
  trainings completados con fecha. Base para futuro streak/dashboard.
- **Modelo listo**: `SessionLog` en `app/src/main/java/com/minitimer/model/Workout.kt`.

### Reordenar por arrastre (lógica sin UI)
- Existen `moveWorkout`, `moveVariant` y `moveExercise` en `AthleteViewModel`,
  pero no hay UI de drag-and-drop conectada. Falta la interacción de arrastre en
  el editor de Training, la lista de variantes y el editor de Workout.
