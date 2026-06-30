package com.minitimer.data

/**
 * Emoji representativo de cada ejercicio (como en el mock preview-create-workout-b).
 * Mapea por id del catálogo; para ejercicios personalizados intenta deducir por
 * palabras clave del nombre. Devuelve null si no hay match (se usa la inicial).
 */
object ExerciseIcons {

    private val byId: Map<String, String> = mapOf(
        // Warmup
        "ex_neck_lr" to "🙆",
        "ex_neck_circle" to "🔁",
        "ex_chest_opening" to "🤗",
        "ex_shoulder_rotation" to "🤷",
        "ex_trunk_rotation" to "🌀",
        "ex_hip_rotation" to "🔄",
        "ex_deep_squat" to "🧎",
        "ex_knee_rotation" to "🦵",
        "ex_ankle_rotation" to "🦶",
        // Base
        "ex_knee_circle" to "🦵",
        "ex_90_90" to "🧘",
        "ex_jumping_jacks" to "🤸",
        "ex_burpees" to "🔥",
        // Cardio
        "ex_running" to "🏃",
        "ex_rope_jumping" to "🪢",
        "ex_tire_jumping" to "🛞",
        "ex_shadow_boxing" to "🥊",
        // Lower
        "ex_knee_stand" to "🧎",
        "ex_knee_jump" to "🦘",
        "ex_cossack_squat" to "🦵",
        "ex_pistol_squat" to "🦩",
        "ex_hip_thrust" to "🍑",
        "ex_back_extension" to "🌉",
        "ex_nordic_curl" to "🦵",
        "ex_seated_calf" to "🪑",
        "ex_donkey_calf" to "🐴",
        "ex_deadlift" to "🏋️",
        "ex_zercher_squat" to "💪",
        // Upper
        "ex_leg_raises" to "🦵",
        "ex_pull_ups" to "🧗",
        "ex_shoulder_press" to "💪",
        "ex_assisted_dips" to "🤸",
        "ex_pushups" to "💪",
    )

    private val byKeyword: List<Pair<List<String>, String>> = listOf(
        listOf("run", "trot", "jog", "correr") to "🏃",
        listOf("jump", "salt", "tijera") to "🤸",
        listOf("burpee") to "🔥",
        listOf("box", "boxe") to "🥊",
        listOf("rope", "cuerda") to "🪢",
        listOf("pull", "domina") to "🧗",
        listOf("push", "flex") to "💪",
        listOf("press", "shoulder", "hombro") to "💪",
        listOf("dead", "muerto", "lift") to "🏋️",
        listOf("squat", "sentadilla") to "🦵",
        listOf("calf", "pantorrilla") to "🦵",
        listOf("plank", "plancha") to "📏",
        listOf("hip", "cadera", "thrust") to "🍑",
        listOf("yoga", "stretch", "estir") to "🧘",
    )

    /** Emoji del ejercicio por id; si no hay, deduce por nombre; null si no hay match. */
    fun emoji(id: String, name: String = ""): String? {
        byId[id]?.let { return it }
        if (name.isNotBlank()) {
            val n = name.lowercase()
            for ((keys, e) in byKeyword) if (keys.any { n.contains(it) }) return e
        }
        return null
    }
}
