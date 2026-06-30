package com.minitimer.data

import android.content.Context
import com.minitimer.model.ConfirmMode
import com.minitimer.model.DisplayMode
import com.minitimer.model.Exercise
import com.minitimer.model.ExerciseDef
import com.minitimer.model.SessionLog
import com.minitimer.model.StageConfig
import com.minitimer.model.WeightType
import com.minitimer.model.WorkMode
import com.minitimer.model.Training
import com.minitimer.model.WorkSet
import com.minitimer.model.Workout
import com.minitimer.model.WorkoutVariant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia de la sección Athlete (workouts, ejercicios propios e historial)
 * con SharedPreferences + JSON, en el mismo estilo que [SettingsStore].
 */
class WorkoutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("athlete", Context.MODE_PRIVATE)

    // ---------- Trainings ----------

    fun saveTrainings(items: List<Training>) {
        val arr = JSONArray()
        items.forEach { tr -> arr.put(trainingToJson(tr)) }
        prefs.edit().putString(KEY_TRAININGS, arr.toString()).apply()
    }

    /** true si nunca se ha guardado la lista de trainings (instalación limpia). */
    fun isFirstRun(): Boolean = !prefs.contains(KEY_TRAININGS)

    /** Marca de migración: si ya se sembró el training "Friki Niki" (una sola vez). */
    fun isFrikiSeeded(): Boolean = prefs.getBoolean(KEY_FRIKI_SEEDED, false)
    fun setFrikiSeeded() { prefs.edit().putBoolean(KEY_FRIKI_SEEDED, true).apply() }

    fun loadTrainings(): List<Training> {
        val raw = prefs.getString(KEY_TRAININGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { trainingFromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun trainingToJson(tr: Training): JSONObject {
        val workouts = JSONArray()
        tr.workouts.forEach { workouts.put(workoutToJson(it)) }
        return JSONObject()
            .put("id", tr.id)
            .put("name", tr.name)
            .put("createdAt", tr.createdAt)
            .put("updatedAt", tr.updatedAt)
            .put("workouts", workouts)
    }

    private fun trainingFromJson(o: JSONObject): Training {
        val workouts = mutableListOf<Workout>()
        o.optJSONArray("workouts")?.let { wa ->
            for (i in 0 until wa.length()) workouts.add(workoutFromJson(wa.getJSONObject(i)))
        }
        return Training(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            workouts = workouts,
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    private fun workoutToJson(w: Workout): JSONObject {
        val exercises = JSONArray()
        w.exercises.forEach { exercises.put(exerciseToJson(it)) }
        val variants = JSONArray()
        w.variants.forEach { variants.put(variantToJson(it)) }
        return JSONObject()
            .put("id", w.id)
            .put("name", w.name)
            .put("exercises", exercises)
            .put("rotating", w.rotating)
            .put("rotationIndex", w.rotationIndex)
            .put("variants", variants)
    }

    private fun workoutFromJson(o: JSONObject): Workout {
        val exercises = mutableListOf<Exercise>()
        o.optJSONArray("exercises")?.let { ea ->
            for (i in 0 until ea.length()) exercises.add(exerciseFromJson(ea.getJSONObject(i)))
        }
        val variants = mutableListOf<WorkoutVariant>()
        o.optJSONArray("variants")?.let { va ->
            for (i in 0 until va.length()) variants.add(variantFromJson(va.getJSONObject(i)))
        }
        return Workout(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            exercises = exercises,
            rotating = o.optBoolean("rotating", false),
            rotationIndex = o.optInt("rotationIndex", 0),
            variants = variants,
        )
    }

    private fun variantToJson(v: WorkoutVariant): JSONObject {
        val exercises = JSONArray()
        v.exercises.forEach { exercises.put(exerciseToJson(it)) }
        return JSONObject()
            .put("id", v.id)
            .put("name", v.name)
            .put("exercises", exercises)
    }

    private fun variantFromJson(o: JSONObject): WorkoutVariant {
        val exercises = mutableListOf<Exercise>()
        o.optJSONArray("exercises")?.let { ea ->
            for (i in 0 until ea.length()) exercises.add(exerciseFromJson(ea.getJSONObject(i)))
        }
        return WorkoutVariant(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            exercises = exercises,
        )
    }

    private fun exerciseToJson(e: Exercise): JSONObject {
        val sets = JSONArray()
        e.setList.forEach { sets.put(JSONObject().put("reps", it.reps).put("weight", it.weight)) }
        return JSONObject()
            .put("id", e.id)
            .put("exerciseId", e.exerciseId)
            .put("name", e.name)
            .put("note", e.note)
            .put("prepareSec", e.prepareSec)
            .put("sets", e.sets)
            .put("workMode", e.workMode.name)
            .put("workValue", e.workValue)
            .put("restSec", e.restSec)
            .put("restSkipOnLastSet", e.restSkipOnLastSet)
            .put("cooldownSec", e.cooldownSec)
            .put("weightType", e.weightType.name)
            .put("barWeight", e.barWeight)
            .put("setList", sets)
            .put("prepareCfg", stageToJson(e.prepareCfg))
            .put("workCfg", stageToJson(e.workCfg))
            .put("restCfg", stageToJson(e.restCfg))
            .put("cooldownCfg", stageToJson(e.cooldownCfg))
    }

    private fun exerciseFromJson(o: JSONObject): Exercise {
        val setList = mutableListOf<WorkSet>()
        o.optJSONArray("setList")?.let { sa ->
            for (i in 0 until sa.length()) {
                val s = sa.getJSONObject(i)
                setList.add(WorkSet(reps = s.optInt("reps", 12), weight = s.optDouble("weight", 0.0)))
            }
        }
        return Exercise(
            id = o.getLong("id"),
            exerciseId = o.optString("exerciseId", ""),
            name = o.optString("name", ""),
            note = o.optString("note", ""),
            prepareSec = o.optInt("prepareSec", 0),
            sets = o.optInt("sets", 1),
            workMode = runCatching { WorkMode.valueOf(o.optString("workMode")) }.getOrDefault(WorkMode.TIME),
            workValue = o.optInt("workValue", 30),
            restSec = o.optInt("restSec", 30),
            restSkipOnLastSet = o.optBoolean("restSkipOnLastSet", true),
            cooldownSec = o.optInt("cooldownSec", 0),
            weightType = runCatching { WeightType.valueOf(o.optString("weightType")) }.getOrDefault(WeightType.NONE),
            barWeight = o.optDouble("barWeight", 20.0),
            setList = setList,
            prepareCfg = stageFromJson(o.optJSONObject("prepareCfg"), StageConfig.COLOR_PREPARE, 3),
            workCfg = stageFromJson(o.optJSONObject("workCfg"), StageConfig.COLOR_WORK, 0),
            restCfg = stageFromJson(o.optJSONObject("restCfg"), StageConfig.COLOR_REST, 3),
            cooldownCfg = stageFromJson(o.optJSONObject("cooldownCfg"), StageConfig.COLOR_COOLDOWN, 0),
        )
    }

    private fun stageToJson(c: StageConfig): JSONObject = JSONObject()
        .put("color", c.color)
        .put("display", c.display.name)
        .put("alarm", c.alarm)
        .put("finalCount", c.finalCount)
        .put("confirm", c.confirm.name)

    private fun stageFromJson(o: JSONObject?, defColor: Long, defFinal: Int): StageConfig {
        if (o == null) return StageConfig(color = defColor, finalCount = defFinal)
        return StageConfig(
            color = o.optLong("color", defColor),
            display = runCatching { DisplayMode.valueOf(o.optString("display")) }.getOrDefault(DisplayMode.COUNTDOWN),
            alarm = o.optBoolean("alarm", true),
            finalCount = o.optInt("finalCount", defFinal),
            confirm = runCatching { ConfirmMode.valueOf(o.optString("confirm")) }.getOrDefault(ConfirmMode.AUTO),
        )
    }

    // ---------- Ejercicios propios (creados por el usuario) ----------

    fun saveCustomExercises(items: List<ExerciseDef>) {
        val arr = JSONArray()
        items.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("name", e.name).put("custom", true))
        }
        prefs.edit().putString(KEY_CUSTOM_EXERCISES, arr.toString()).apply()
    }

    fun loadCustomExercises(): List<ExerciseDef> {
        val raw = prefs.getString(KEY_CUSTOM_EXERCISES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ExerciseDef(id = o.getString("id"), name = o.getString("name"), custom = true)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------- Historial de sesiones ----------

    fun saveSessions(items: List<SessionLog>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("trainingId", s.trainingId)
                    .put("trainingName", s.trainingName)
                    .put("completedAt", s.completedAt),
            )
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun loadSessions(): List<SessionLog> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SessionLog(
                    id = o.getLong("id"),
                    trainingId = o.optLong("trainingId", 0L),
                    trainingName = o.optString("trainingName", ""),
                    completedAt = o.optLong("completedAt", 0L),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_TRAININGS = "trainings_json"
        const val KEY_CUSTOM_EXERCISES = "custom_exercises_json"
        const val KEY_SESSIONS = "sessions_json"
        const val KEY_FRIKI_SEEDED = "friki_seeded"
    }
}
