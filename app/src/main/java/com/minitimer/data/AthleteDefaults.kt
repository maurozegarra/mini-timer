package com.minitimer.data

import com.minitimer.model.Exercise
import com.minitimer.model.Training
import com.minitimer.model.WorkMode
import com.minitimer.model.WorkSet
import com.minitimer.model.WeightType
import com.minitimer.model.Workout
import com.minitimer.model.WorkoutVariant

/**
 * Datos por defecto de la sección Athlete que se siembran en la primera ejecución.
 * Incluye el Training "Master" con Warmup, Base, Cardio (rotación de 4 opciones)
 * y un bloque de fuerza que alterna Lower/Upper. La rotación avanza al completar.
 */
object AthleteDefaults {

    fun masterTraining(lang: String): Training {
        var seq = 1L
        fun id(): Long = seq++

        fun ex(
            exerciseId: String,
            note: String = "",
            sets: Int = 1,
            mode: WorkMode = WorkMode.TIME,
            work: Int = 30,
            prep: Int = 0,
            rest: Int = 0,
            cooldown: Int = 0,
            weightType: WeightType = WeightType.NONE,
            barWeight: Double = 20.0,
            setList: List<WorkSet> = emptyList(),
        ): Exercise = Exercise(
            id = id(),
            exerciseId = exerciseId,
            name = ExerciseCatalog.name(exerciseId, lang),
            note = note,
            prepareSec = prep,
            sets = sets,
            workMode = mode,
            workValue = work,
            restSec = rest,
            restSkipOnLastSet = true,
            cooldownSec = cooldown,
            weightType = weightType,
            barWeight = barWeight,
            setList = setList,
        )

        fun reps(exerciseId: String, count: Int, note: String = ""): Exercise =
            ex(exerciseId, note = note, mode = WorkMode.REPS, work = count)

        // ----- Warmup -----
        val warmup = Workout(
            id = id(),
            name = if (lang == "es") "Calentamiento" else "Warmup",
            exercises = listOf(
                reps("ex_neck_lr", 10),
                reps("ex_neck_circle", 10, note = sideNote(lang)),
                reps("ex_chest_opening", 20),
                reps("ex_shoulder_rotation", 10, note = sideNote(lang)),
                reps("ex_trunk_rotation", 20),
                reps("ex_hip_rotation", 10, note = sideNote(lang)),
                reps("ex_deep_squat", 20),
                reps("ex_ankle_rotation", 10, note = sideNote(lang)),
            ),
        )

        // ----- Base -----
        val base = Workout(
            id = id(),
            name = "Base",
            exercises = listOf(
                ex("ex_jumping_jacks", sets = 2, mode = WorkMode.TIME, work = 30, prep = 10, rest = 10, cooldown = 30),
                ex("ex_burpees", sets = 2, mode = WorkMode.TIME, work = 45, prep = 10, rest = 15, cooldown = 60),
                reps("ex_knee_circle", 20),
                reps("ex_90_90", 20),
            ),
        )

        // ----- Cardio (rotación de 4) -----
        val cardio = Workout(
            id = id(),
            name = "Cardio",
            rotating = true,
            variants = listOf(
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_rope_jumping", lang),
                    exercises = listOf(ex("ex_rope_jumping", sets = 15, work = 30, prep = 10, rest = 10, cooldown = 60)),
                ),
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_tire_jumping", lang),
                    exercises = listOf(ex("ex_tire_jumping", sets = 8, work = 24, prep = 10, rest = 10, cooldown = 60)),
                ),
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_shadow_boxing", lang),
                    exercises = listOf(ex("ex_shadow_boxing", sets = 3, work = 45, prep = 10, rest = 15, cooldown = 60)),
                ),
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_running", lang),
                    exercises = listOf(ex("ex_running", mode = WorkMode.TIME, work = 600, prep = 10, cooldown = 60)),
                ),
            ),
        )

        // ----- Strength (alterna Lower / Upper) -----
        val lower = WorkoutVariant(
            id = id(), name = "Lower",
            exercises = listOf(
                reps("ex_knee_rotation", 10, note = sideNote(lang)),
                reps("ex_knee_stand", 20),
                reps("ex_knee_jump", 20),
                reps("ex_cossack_squat", 20),
                reps("ex_pistol_squat", 20, note = altNote(lang)),
                reps("ex_hip_thrust", 20, note = sideNote(lang)),
                reps("ex_back_extension", 20),
                reps("ex_nordic_curl", 15),
                ex(
                    "ex_seated_calf", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(15, 5.0), WorkSet(15, 10.0)),
                ),
                ex(
                    "ex_donkey_calf", note = sideNote(lang), sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(15, 15.0), WorkSet(15, 20.0)),
                ),
                ex(
                    "ex_deadlift", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.BARBELL, barWeight = 20.0,
                    setList = listOf(WorkSet(15, 0.0), WorkSet(15, 10.0)),
                ),
                ex(
                    "ex_zercher_squat", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.BARBELL, barWeight = 20.0,
                    setList = listOf(WorkSet(15, 0.0), WorkSet(15, 10.0)),
                ),
            ),
        )
        val upper = WorkoutVariant(
            id = id(), name = "Upper",
            exercises = listOf(
                ex("ex_leg_raises", sets = 2, mode = WorkMode.REPS, work = 10, prep = 5, rest = 60),
                // Pull-up rep-by-rep: 10 series de 1 rep (confirmación manual) con 15s de descanso entre reps.
                ex("ex_pull_ups", sets = 10, mode = WorkMode.REPS, work = 1, prep = 5, rest = 15),
                ex(
                    "ex_shoulder_press", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.DUMBBELL,
                    setList = listOf(WorkSet(15, 5.0), WorkSet(15, 7.5)),
                ),
                ex("ex_assisted_dips", sets = 1, mode = WorkMode.REPS, work = 15),
                ex("ex_pushups", sets = 2, mode = WorkMode.REPS, work = 20, rest = 60),
            ),
        )
        val strength = Workout(
            id = id(),
            name = if (lang == "es") "Fuerza" else "Strength",
            rotating = true,
            variants = listOf(lower, upper),
        )

        val now = System.currentTimeMillis()
        return Training(
            id = id(),
            name = "Master",
            workouts = listOf(warmup, base, cardio, strength),
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Training "Friki Niki": 7 workouts en secuencia (Warmup, Base, Cardio, Potencia,
     * Box, Fuerza, Extra). Pesos por serie en modo REPS; descansos uniformes por ejercicio.
     * DeadLift en BARBELL (barra 20 kg + discos 0/10/20/30). Sin rotación.
     */
    fun frikiNikiTraining(lang: String): Training {
        var seq = 1000L
        fun id(): Long = seq++

        fun ex(
            exerciseId: String,
            note: String = "",
            sets: Int = 1,
            mode: WorkMode = WorkMode.TIME,
            work: Int = 30,
            prep: Int = 0,
            rest: Int = 0,
            cooldown: Int = 0,
            weightType: WeightType = WeightType.NONE,
            barWeight: Double = 20.0,
            setList: List<WorkSet> = emptyList(),
        ): Exercise = Exercise(
            id = id(),
            exerciseId = exerciseId,
            name = ExerciseCatalog.name(exerciseId, lang),
            note = note,
            prepareSec = prep,
            sets = sets,
            workMode = mode,
            workValue = work,
            restSec = rest,
            restSkipOnLastSet = true,
            cooldownSec = cooldown,
            weightType = weightType,
            barWeight = barWeight,
            setList = setList,
        )

        fun reps(exerciseId: String, count: Int, note: String = ""): Exercise =
            ex(exerciseId, note = note, mode = WorkMode.REPS, work = count)

        val warmup = Workout(
            id = id(),
            name = if (lang == "es") "Calentamiento" else "Warmup",
            exercises = listOf(
                reps("ex_neck_lr", 10),
                reps("ex_neck_circle", 10, note = sideNote(lang)),
                reps("ex_chest_opening", 20),
                reps("ex_shoulder_rotation", 10, note = sideNote(lang)),
                reps("ex_trunk_rotation", 20),
                reps("ex_hip_rotation", 10, note = sideNote(lang)),
                reps("ex_knee_rotation", 10, note = sideNote(lang)),
                reps("ex_ankle_rotation", 10, note = sideNote(lang)),
            ),
        )

        val base = Workout(
            id = id(),
            name = "Base",
            exercises = listOf(
                reps("ex_knee_circle", 20),
                reps("ex_90_90", 20),
                ex("ex_jumping_jacks", sets = 1, mode = WorkMode.TIME, work = 30, prep = 10),
                ex("ex_burpees", sets = 5, mode = WorkMode.TIME, work = 45, prep = 10, rest = 15),
                reps("ex_knee_stand", 20),
                reps("ex_knee_jump", 20),
                reps("ex_front_side_stretch", 20),
                ex("ex_pushups", sets = 3, mode = WorkMode.REPS, work = 20, rest = 60),
            ),
        )

        val cardio = Workout(
            id = id(),
            name = "Cardio",
            exercises = listOf(
                ex("ex_running", mode = WorkMode.TIME, work = 600),
                ex("ex_rope_jumping", sets = 8, mode = WorkMode.TIME, work = 30, prep = 10, rest = 10),
            ),
        )

        val potencia = Workout(
            id = id(),
            name = if (lang == "es") "Potencia" else "Power",
            exercises = listOf(
                ex("ex_tire_jumping", sets = 8, mode = WorkMode.TIME, work = 30, prep = 10, rest = 10),
                ex(
                    "ex_bulgarian_split_squat", sets = 3, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 5.0), WorkSet(10, 7.5), WorkSet(10, 10.0)),
                ),
            ),
        )

        val box = Workout(
            id = id(),
            name = "Box",
            exercises = listOf(
                ex("ex_long_knees", sets = 5, mode = WorkMode.TIME, work = 30, prep = 10, rest = 15),
                ex("ex_deep_knees", sets = 4, mode = WorkMode.TIME, work = 30, prep = 10, rest = 15),
                ex("ex_shadow_boxing", sets = 4, mode = WorkMode.TIME, work = 60, prep = 10, rest = 15),
                ex("ex_kicks", note = sideNote(lang), sets = 3, mode = WorkMode.REPS, work = 10, prep = 10),
            ),
        )

        val fuerza = Workout(
            id = id(),
            name = if (lang == "es") "Fuerza" else "Strength",
            exercises = listOf(
                ex(
                    "ex_seated_calf", note = sideNote(lang), sets = 4, mode = WorkMode.REPS, work = 10, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 2.5), WorkSet(10, 5.0), WorkSet(10, 7.5), WorkSet(10, 7.5)),
                ),
                ex(
                    "ex_donkey_calf", note = sideNote(lang), sets = 3, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(15, 5.0), WorkSet(15, 5.0), WorkSet(15, 5.0)),
                ),
                ex(
                    "ex_hip_thrust", sets = 4, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 40.0), WorkSet(10, 50.0), WorkSet(10, 60.0), WorkSet(10, 70.0)),
                ),
                ex(
                    "ex_cable_pull", sets = 4, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 2.5), WorkSet(10, 2.5), WorkSet(10, 5.0), WorkSet(10, 5.0)),
                ),
                ex(
                    "ex_deadlift", sets = 4, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.BARBELL, barWeight = 20.0,
                    setList = listOf(WorkSet(10, 0.0), WorkSet(10, 10.0), WorkSet(10, 20.0), WorkSet(10, 30.0)),
                ),
            ),
        )

        val extra = Workout(
            id = id(),
            name = "Extra",
            exercises = listOf(
                ex("ex_walking_dog", sets = 2, mode = WorkMode.TIME, work = 60),
            ),
        )

        val now = System.currentTimeMillis()
        return Training(
            id = id(),
            name = "Friki Niki",
            workouts = listOf(warmup, base, cardio, potencia, box, fuerza, extra),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun sideNote(lang: String) = if (lang == "es") "cada lado" else "each side"
    private fun altNote(lang: String) = if (lang == "es") "alternado" else "alternating"
}
