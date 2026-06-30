package com.minitimer.data

import com.minitimer.model.ExerciseDef

/**
 * Catálogo base de ejercicios (bilingüe ES/EN). Solo nombres; el placeholder de
 * animación se añadirá más adelante. El usuario puede agregar ejercicios propios
 * (persistidos aparte en [WorkoutStore]).
 */
object ExerciseCatalog {

    /** id estable, nombre ES, nombre EN. Solo los ejercicios usados en el set. */
    private val base = listOf(
        // Warmup
        Triple("ex_neck_lr", "Cuello izq-der", "Neck Left to Right"),
        Triple("ex_neck_circle", "Círculo de cuello", "Neck Circle"),
        Triple("ex_chest_opening", "Apertura de pecho", "Chest Opening"),
        Triple("ex_shoulder_rotation", "Rotación de hombros", "Shoulder Rotation"),
        Triple("ex_trunk_rotation", "Rotación de tronco", "Trunk Rotation"),
        Triple("ex_hip_rotation", "Rotación de cadera", "Hip Rotation"),
        Triple("ex_deep_squat", "Sentadilla profunda", "Deep Squat"),
        Triple("ex_knee_rotation", "Rotación de rodilla", "Knee Rotation"),
        Triple("ex_ankle_rotation", "Rotación de tobillo", "Ankle Rotation"),
        // Base
        Triple("ex_knee_circle", "Círculo de rodilla", "Knee Circle"),
        Triple("ex_90_90", "90 a 90", "90 to 90"),
        Triple("ex_jumping_jacks", "Saltos de tijera", "Jumping Jacks"),
        Triple("ex_burpees", "Burpees", "Burpees"),
        // Cardio
        Triple("ex_running", "Trote", "Running"),
        Triple("ex_rope_jumping", "Saltar la cuerda", "Rope Jumping"),
        Triple("ex_tire_jumping", "Salto de llanta", "Tire Jumping"),
        Triple("ex_shadow_boxing", "Boxeo de sombra", "Shadow Boxing"),
        // Lower
        Triple("ex_knee_stand", "Parada de rodilla", "Knee Stand"),
        Triple("ex_knee_jump", "Salto de rodilla", "Knee Jump"),
        Triple("ex_cossack_squat", "Sentadilla cosaca", "Cossack Squat"),
        Triple("ex_pistol_squat", "Sentadilla pistola", "Pistol Squat"),
        Triple("ex_hip_thrust", "Empuje de cadera", "Hip Thrust"),
        Triple("ex_back_extension", "Extensión lumbar", "Back Extension"),
        Triple("ex_nordic_curl", "Curl nórdico", "Nordic Curl"),
        Triple("ex_seated_calf", "Pantorrilla sentado", "Seated Calf"),
        Triple("ex_donkey_calf", "Pantorrilla burro", "Donkey Calf"),
        Triple("ex_deadlift", "Peso muerto", "Deadlift"),
        Triple("ex_zercher_squat", "Sentadilla Zercher", "Zercher Squat"),
        // Upper
        Triple("ex_leg_raises", "Elevación de piernas", "Leg Raises"),
        Triple("ex_pull_ups", "Dominadas", "Pull-up"),
        Triple("ex_shoulder_press", "Press de hombro", "Shoulder Press"),
        Triple("ex_assisted_dips", "Fondos asistidos", "Assisted Dips"),
        Triple("ex_pushups", "Flexiones", "Push-ups"),
    )

    fun base(lang: String): List<ExerciseDef> =
        base.map { (id, es, en) -> ExerciseDef(id = id, name = if (lang == "es") es else en, custom = false) }

    /** Nombre localizado de un id del catálogo (o el propio id si no existe). */
    fun name(id: String, lang: String): String =
        base.firstOrNull { it.first == id }?.let { if (lang == "es") it.second else it.third } ?: id
}
