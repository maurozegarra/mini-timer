# Training: Friki Niki (referencia)

Documento de referencia del training **Friki Niki**, corregido con las aclaraciones del usuario y mapeado al modelo de la app (Training > Workout > Exercise). Solo referencia, no implementación.

## Correcciones aplicadas

- **DeadLift → `BARBELL`**: barra = **20 kg**; discos **+0 / +10 / +20 / +30** ⇒ totales **20 / 30 / 40 / 50 kg**.
- **Bulgaras, Seated, Donkey, Hip Thrust, Polea → `TOTAL`**: el número es **kg totales** (no por mano).
- **Bulgaras de potencia**: descanso **único de 2 min** entre series (el "1 m / 90 s" fue error de dedo).

## Convenciones del modelo

- **workMode**: `TIME` (duración en segundos) o `REPS` (repeticiones).
- **sets**: número de series. **rest**: descanso entre series (`restSkipOnLastSet` = no descansa tras la última).
- **prepare**: cuenta previa al primer set (cuando se indica).
- **weightType**: `NONE` | `TOTAL` | `BARBELL` (barra + discos) | `DUMBBELL` (kg por mano ×2).
- **setList**: peso por serie cuando varía entre sets (modo `REPS`).
- `"each side"` = anotación del ejercicio (no es un campo del modelo); se conserva en el nombre/nota.

---

## Workout 1 — Warmup

Todos en modo `REPS`, 1 set, sin peso, sin descanso.

| Ejercicio | Modo | Sets | Reps | Nota |
|---|---|---|---|---|
| Neck Left to Right | REPS | 1 | 10 | |
| Neck Circle | REPS | 1 | 10 | each side |
| Chest Opening | REPS | 1 | 20 | |
| Shoulder Rotation | REPS | 1 | 10 | each side |
| Trunk Rotation | REPS | 1 | 20 | |
| Hip Rotation | REPS | 1 | 10 | each side |
| Knee Rotation | REPS | 1 | 10 | each side |
| Ankle Rotation | REPS | 1 | 10 | each side |

## Workout 2 — Base

| Ejercicio | Modo | Prepare | Sets | Work | Rest |
|---|---|---|---|---|---|
| Knee Circle | REPS | — | 1 | 20 reps | — |
| 90 to 90 | REPS | — | 1 | 20 reps | — |
| Jumping Jacks | TIME | 10s | 1 | 30s | — |
| Burpees | TIME | 10s | 5 | 45s | 15s |
| Parada de Rodilla | REPS | — | 1 | 20 reps | — |
| Salto de Rodilla | REPS | — | 1 | 20 reps | — |
| Estiramiento Frente/Lateral | REPS | — | 1 | 20 reps | — |
| Push-ups | REPS | — | 3 | 20 reps | 60s |

## Workout 3 — Cardio

| Ejercicio | Modo | Prepare | Sets | Work | Rest |
|---|---|---|---|---|---|
| Running | TIME | — | 1 | 10 min (600s) | — |
| Rope Jumping | TIME | 10s | 8 | 30s | 10s |

## Workout 4 — Potencia

| Ejercicio | Modo | Prepare | Sets | Work | Rest | Peso |
|---|---|---|---|---|---|---|
| Tire Jumping | TIME | 10s | 8 | 30s | 10s | — |
| Bulgaras de potencia | REPS | — | 3 | 10 reps | **2 min** | `TOTAL` por serie: 5 / 7.5 / 10 kg |

## Workout 5 — Box

| Ejercicio | Modo | Prepare | Sets | Work | Rest |
|---|---|---|---|---|---|
| Long Knees | TIME | 10s | 5 | 30s | 15s |
| Deep Knees | TIME | 10s | 4 | 30s | 15s |
| Shadow Boxing | TIME | 10s | 4 | 60s | 15s |
| Kicks | REPS | 10s | 3 | 10 reps | — (each side) |

## Workout 6 — Fuerza

Todos `weightType = TOTAL` salvo DeadLift (`BARBELL`). Descanso uniforme dentro de cada ejercicio.

| Ejercicio | Modo | Sets | Reps | Rest | Peso por serie | Nota |
|---|---|---|---|---|---|---|
| Seated Calf | REPS | 4 | 10 | 1 min | `TOTAL`: 2.5 / 5 / 7.5 / 7.5 kg | each side |
| Donkey Calf | REPS | 3 | 15 | 1 min | `TOTAL`: 5 / 5 / 5 kg | each side |
| Hip Thrust | REPS | 4 | 10 | 2 min | `TOTAL`: 40 / 50 / 60 / 70 kg | |
| Polea | REPS | 4 | 10 | 2 min | `TOTAL`: 2.5 / 2.5 / 5 / 5 kg | |
| DeadLift | REPS | 4 | 10 | 2 min | `BARBELL` barra 20 kg + discos 0/10/20/30 ⇒ **20 / 30 / 40 / 50 kg** | |

## Workout 7 — Extra

| Ejercicio | Modo | Sets | Work |
|---|---|---|---|
| Walking Dog | TIME | 2 | 60s |

---

## Resultado de la validación

- **Descansos variables por serie**: **resuelto**. Tras la corrección de Bulgaras, **todos** los ejercicios usan descanso **uniforme** entre series, que es exactamente lo que soporta el modelo (`restSec` único + `restSkipOnLastSet`).
- **Peso variable por serie**: **soportado** vía `setList[]` (`WorkSet{reps, weight}`) en modo `REPS`. Aplica a Bulgaras, Seated, Donkey, Hip Thrust, Polea y DeadLift.
- **Rotación**: Friki Niki **no** tiene workouts rotativos; los 7 workouts se ejecutan en **secuencia**, que es el caso base soportado. No requiere variantes ni split.
- **Observación**: **Fuerza** es un workout largo (5 ejercicios pesados). Es **opcional** (no una limitación) partirlo en dos para mejor usabilidad en el player.

**Conclusión**: Friki Niki es **100% implementable** con el modelo actual; no hay limitaciones bloqueantes tras las correcciones.
