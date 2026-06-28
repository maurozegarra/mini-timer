package com.minitimer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * Respaldo y restauración de TODOS los datos persistentes de la app a una
 * carpeta elegida por el usuario (Storage Access Framework, sin dependencias
 * externas). El respaldo es un único JSON versionado ([SCHEMA_VERSION]) con un
 * volcado genérico de las SharedPreferences persistentes; el estado transitorio
 * del player ("athlete_player") se excluye a propósito.
 *
 * Flujo "auto-backup a carpeta":
 * - El usuario elige una carpeta una vez ([setFolder]); se persiste su tree Uri.
 * - La app escribe el respaldo automáticamente al pasar a segundo plano.
 * - Tras reinstalar, el usuario elige la MISMA carpeta y restaura ([readBackup]
 *   + [restoreFromJson]); también se detecta el respaldo existente para ofrecer
 *   la restauración de inmediato.
 */
object BackupManager {

    const val SCHEMA_VERSION = 1
    const val FILE_NAME = "mini-timer-backup.json"

    /** Prefs persistentes a respaldar. NO incluir el transitorio "athlete_player". */
    private val PREF_FILES = listOf("mini_timer", "athlete")

    private const val BACKUP_PREFS = "mini_timer"
    private const val KEY_FOLDER_URI = "backup_folder_uri"

    // ---------- Carpeta elegida ----------

    fun folderUri(context: Context): Uri? {
        val s = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    fun hasFolder(context: Context): Boolean = folderUri(context) != null

    /** Nombre legible de la carpeta elegida (para mostrar en Ajustes). */
    fun folderName(context: Context): String? {
        val uri = folderUri(context) ?: return null
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId.substringAfterLast(':').substringAfterLast('/').ifBlank { docId }
        }.getOrNull()
    }

    fun setFolder(context: Context, uri: Uri) {
        context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    // ---------- Serialización (volcado genérico de prefs) ----------

    fun buildBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        val prefsObj = JSONObject()
        PREF_FILES.forEach { name ->
            prefsObj.put(name, dumpPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        }
        root.put("prefs", prefsObj)
        return root.toString()
    }

    private fun dumpPrefs(prefs: SharedPreferences): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (k, v) ->
            if (v == null) return@forEach
            // No respaldar la propia referencia a la carpeta (se re-elige al restaurar).
            if (k == KEY_FOLDER_URI) return@forEach
            val entry = JSONObject()
            when (v) {
                is Boolean -> { entry.put("t", "b"); entry.put("v", v) }
                is Int -> { entry.put("t", "i"); entry.put("v", v) }
                is Long -> { entry.put("t", "l"); entry.put("v", v) }
                is Float -> { entry.put("t", "f"); entry.put("v", v.toDouble()) }
                is String -> { entry.put("t", "s"); entry.put("v", v) }
                is Set<*> -> {
                    entry.put("t", "ss")
                    val arr = JSONArray()
                    v.forEach { if (it is String) arr.put(it) }
                    entry.put("v", arr)
                }
                else -> return@forEach
            }
            obj.put(k, entry)
        }
        return obj
    }

    fun restoreFromJson(context: Context, json: String): Boolean = try {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", 1)
        val migrated = migrate(root, version)
        val prefsObj = migrated.getJSONObject("prefs")
        PREF_FILES.forEach { name ->
            if (prefsObj.has(name)) {
                applyPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE), prefsObj.getJSONObject(name))
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun applyPrefs(prefs: SharedPreferences, obj: JSONObject) {
        // Preservar la carpeta elegida actual (no viene en el respaldo).
        val keepFolder = prefs.getString(KEY_FOLDER_URI, null)
        val editor = prefs.edit()
        editor.clear()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val entry = obj.getJSONObject(k)
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(k, entry.getBoolean("v"))
                "i" -> editor.putInt(k, entry.getInt("v"))
                "l" -> editor.putLong(k, entry.getLong("v"))
                "f" -> editor.putFloat(k, entry.getDouble("v").toFloat())
                "s" -> editor.putString(k, entry.getString("v"))
                "ss" -> {
                    val arr = entry.getJSONArray("v")
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet(k, set)
                }
            }
        }
        if (keepFolder != null) editor.putString(KEY_FOLDER_URI, keepFolder)
        editor.apply()
    }

    /** Migraciones encadenadas de esquemas antiguos al actual. */
    private fun migrate(root: JSONObject, fromVersion: Int): JSONObject {
        // v1 es el esquema actual; aquí se añadirán migraciones futuras.
        return root
    }

    // ---------- E/S a la carpeta (DocumentsContract) ----------

    /** Escribe el respaldo en la carpeta elegida. true si tuvo éxito. */
    fun writeBackup(context: Context): Boolean {
        val tree = folderUri(context) ?: return false
        return try {
            val fileUri = findOrCreateBackupFile(context, tree) ?: return false
            context.contentResolver.openOutputStream(fileUri, "wt")?.use { os ->
                os.write(buildBackupJson(context).toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Lee el JSON del respaldo desde la carpeta elegida; null si no existe. */
    fun readBackup(context: Context): String? {
        val tree = folderUri(context) ?: return null
        return try {
            val fileUri = findBackupFile(context, tree) ?: return null
            context.contentResolver.openInputStream(fileUri)?.use { ins ->
                ins.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Marca de tiempo (epoch ms) del respaldo existente, o null si no hay. */
    fun backupExportedAt(context: Context): Long? {
        val json = readBackup(context) ?: return null
        return runCatching {
            JSONObject(json).optLong("exportedAt", 0L).takeIf { it > 0 }
        }.getOrNull()
    }

    private fun findBackupFile(context: Context, tree: Uri): Uri? {
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            val idCol = 0
            val nameCol = 1
            while (c.moveToNext()) {
                if (c.getString(nameCol) == FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol))
                }
            }
        }
        return null
    }

    private fun findOrCreateBackupFile(context: Context, tree: Uri): Uri? {
        findBackupFile(context, tree)?.let { return it }
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentDocUri,
            "application/json",
            FILE_NAME,
        )
    }
}
