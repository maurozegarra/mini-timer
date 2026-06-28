package com.minitimer.data

import android.content.Context
import com.minitimer.model.ExerciseDef
import com.minitimer.model.ExerciseItem
import com.minitimer.model.ExerciseMode
import com.minitimer.model.RestItem
import com.minitimer.model.Round
import com.minitimer.model.SessionLog
import com.minitimer.model.Workout
import com.minitimer.model.WorkoutItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia de la sección Athlete (workouts, ejercicios propios e historial)
 * con SharedPreferences + JSON, en el mismo estilo que [SettingsStore].
 */
class WorkoutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("athlete", Context.MODE_PRIVATE)

    // ---------- Workouts ----------

    fun saveWorkouts(items: List<Workout>) {
        val arr = JSONArray()
        items.forEach { w -> arr.put(workoutToJson(w)) }
        prefs.edit().putString(KEY_WORKOUTS, arr.toString()).apply()
    }

    fun loadWorkouts(): List<Workout> {
        val raw = prefs.getString(KEY_WORKOUTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { workoutFromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun workoutToJson(w: Workout): JSONObject {
        val rounds = JSONArray()
        w.rounds.forEach { r ->
            val itemsArr = JSONArray()
            r.items.forEach { itemsArr.put(itemToJson(it)) }
            rounds.put(JSONObject().put("id", r.id).put("items", itemsArr))
        }
        return JSONObject()
            .put("id", w.id)
            .put("name", w.name)
            .put("createdAt", w.createdAt)
            .put("updatedAt", w.updatedAt)
            .put("rounds", rounds)
    }

    private fun workoutFromJson(o: JSONObject): Workout {
        val rounds = mutableListOf<Round>()
        val ra = o.optJSONArray("rounds")
        if (ra != null) {
            for (i in 0 until ra.length()) {
                val ro = ra.getJSONObject(i)
                val items = mutableListOf<WorkoutItem>()
                val ia = ro.optJSONArray("items")
                if (ia != null) {
                    for (j in 0 until ia.length()) {
                        itemFromJson(ia.getJSONObject(j))?.let { items.add(it) }
                    }
                }
                rounds.add(Round(id = ro.getLong("id"), items = items))
            }
        }
        return Workout(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            rounds = rounds,
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    private fun itemToJson(item: WorkoutItem): JSONObject = when (item) {
        is ExerciseItem -> JSONObject()
            .put("type", "exercise")
            .put("id", item.id)
            .put("exerciseId", item.exerciseId)
            .put("name", item.name)
            .put("mode", item.mode.name)
            .put("reps", item.reps)
            .put("durationSec", item.durationSec)
            .put("timeToPositionSec", item.timeToPositionSec)
        is RestItem -> JSONObject()
            .put("type", "rest")
            .put("id", item.id)
            .put("durationSec", item.durationSec)
    }

    private fun itemFromJson(o: JSONObject): WorkoutItem? = when (o.optString("type")) {
        "exercise" -> ExerciseItem(
            id = o.getLong("id"),
            exerciseId = o.optString("exerciseId", ""),
            name = o.optString("name", ""),
            mode = runCatching { ExerciseMode.valueOf(o.optString("mode")) }
                .getOrDefault(ExerciseMode.REPS),
            reps = o.optInt("reps", 10),
            durationSec = o.optInt("durationSec", 0),
            timeToPositionSec = o.optInt("timeToPositionSec", 0),
        )
        "rest" -> RestItem(
            id = o.getLong("id"),
            durationSec = o.optInt("durationSec", 30),
        )
        else -> null
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
                    .put("workoutId", s.workoutId)
                    .put("workoutName", s.workoutName)
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
                    workoutId = o.optLong("workoutId", 0L),
                    workoutName = o.optString("workoutName", ""),
                    completedAt = o.optLong("completedAt", 0L),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_WORKOUTS = "workouts_json"
        const val KEY_CUSTOM_EXERCISES = "custom_exercises_json"
        const val KEY_SESSIONS = "sessions_json"
    }
}
