package com.minitimer.data

import com.minitimer.model.ExerciseDef

/**
 * Catálogo base de ejercicios (bilingüe ES/EN). Solo nombres; el placeholder de
 * animación se añadirá más adelante. El usuario puede agregar ejercicios propios
 * (persistidos aparte en [WorkoutStore]).
 */
object ExerciseCatalog {

    /** id estable, nombre ES, nombre EN. */
    private val base = listOf(
        Triple("ex_jumping_jacks", "Saltos de tijera", "Jumping Jacks"),
        Triple("ex_high_knees", "Rodillas altas", "High Knees"),
        Triple("ex_butt_kicks", "Talones al glúteo", "Butt Kicks"),
        Triple("ex_burpees", "Burpees", "Burpees"),
        Triple("ex_mountain_climbers", "Escaladores", "Mountain Climbers"),
        Triple("ex_pushups", "Flexiones", "Push-ups"),
        Triple("ex_incline_pushups", "Flexiones inclinadas", "Incline Push-ups"),
        Triple("ex_diamond_pushups", "Flexiones diamante", "Diamond Push-ups"),
        Triple("ex_decline_pushups", "Flexiones declinadas", "Decline Push-ups"),
        Triple("ex_pike_pushups", "Flexiones pica", "Pike Push-ups"),
        Triple("ex_squats", "Sentadillas", "Squats"),
        Triple("ex_jump_squats", "Sentadillas con salto", "Jump Squats"),
        Triple("ex_sumo_squats", "Sentadillas sumo", "Sumo Squats"),
        Triple("ex_lunges", "Zancadas", "Lunges"),
        Triple("ex_reverse_lunges", "Zancadas inversas", "Reverse Lunges"),
        Triple("ex_walking_lunges", "Zancadas caminando", "Walking Lunges"),
        Triple("ex_bulgarian_split_squat", "Sentadilla búlgara", "Bulgarian Split Squat"),
        Triple("ex_glute_bridge", "Puente de glúteos", "Glute Bridge"),
        Triple("ex_hip_thrust", "Empuje de cadera", "Hip Thrust"),
        Triple("ex_calf_raises", "Elevación de talones", "Calf Raises"),
        Triple("ex_wall_sit", "Sentadilla en pared", "Wall Sit"),
        Triple("ex_plank", "Plancha", "Plank"),
        Triple("ex_side_plank", "Plancha lateral", "Side Plank"),
        Triple("ex_crunches", "Abdominales", "Crunches"),
        Triple("ex_bicycle_crunches", "Abdominales bicicleta", "Bicycle Crunches"),
        Triple("ex_leg_raises", "Elevación de piernas", "Leg Raises"),
        Triple("ex_russian_twists", "Giros rusos", "Russian Twists"),
        Triple("ex_sit_ups", "Abdominales completos", "Sit-ups"),
        Triple("ex_flutter_kicks", "Patada de aleteo", "Flutter Kicks"),
        Triple("ex_v_ups", "V-ups", "V-ups"),
        Triple("ex_dead_bug", "Dead bug", "Dead Bug"),
        Triple("ex_superman", "Superman", "Superman"),
        Triple("ex_bird_dog", "Bird dog", "Bird Dog"),
        Triple("ex_pull_ups", "Dominadas", "Pull-ups"),
        Triple("ex_chin_ups", "Dominadas supinas", "Chin-ups"),
        Triple("ex_dips", "Fondos", "Dips"),
        Triple("ex_bench_press", "Press de banca", "Bench Press"),
        Triple("ex_overhead_press", "Press militar", "Overhead Press"),
        Triple("ex_bicep_curls", "Curl de bíceps", "Bicep Curls"),
        Triple("ex_tricep_extension", "Extensión de tríceps", "Triceps Extension"),
        Triple("ex_lateral_raises", "Elevaciones laterales", "Lateral Raises"),
        Triple("ex_bent_over_row", "Remo inclinado", "Bent-over Row"),
        Triple("ex_deadlift", "Peso muerto", "Deadlift"),
        Triple("ex_romanian_deadlift", "Peso muerto rumano", "Romanian Deadlift"),
        Triple("ex_kettlebell_swing", "Swing con kettlebell", "Kettlebell Swing"),
        Triple("ex_box_jumps", "Saltos al cajón", "Box Jumps"),
        Triple("ex_jump_rope", "Saltar la cuerda", "Jump Rope"),
        Triple("ex_skater_jumps", "Saltos de patinador", "Skater Jumps"),
        Triple("ex_bear_crawl", "Caminata del oso", "Bear Crawl"),
        Triple("ex_inchworm", "Gusano", "Inchworm"),
        Triple("ex_arm_circles", "Círculos de brazos", "Arm Circles"),
        Triple("ex_hamstring_stretch", "Estiramiento isquios", "Hamstring Stretch"),
        Triple("ex_hip_flexor_stretch", "Estiramiento flexor cadera", "Hip Flexor Stretch"),
        Triple("ex_cat_cow", "Gato-vaca", "Cat-Cow"),
        Triple("ex_child_pose", "Postura del niño", "Child's Pose"),
        Triple("ex_cobra_stretch", "Estiramiento cobra", "Cobra Stretch"),
    )

    fun base(lang: String): List<ExerciseDef> =
        base.map { (id, es, en) -> ExerciseDef(id = id, name = if (lang == "es") es else en, custom = false) }
}
