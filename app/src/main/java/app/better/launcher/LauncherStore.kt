package app.better.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppFolder(val id: String, val name: String, val apps: List<String>)

class LauncherStore(context: Context) {
    val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private fun array(key: String): List<String> = runCatching {
        val json = JSONArray(prefs.getString(key, "[]"))
        List(json.length()) { json.getString(it) }
    }.getOrDefault(emptyList())
    private fun putArray(key: String, values: List<String>) = prefs.edit().putString(key, JSONArray(values).toString()).apply()

    var favorites: List<String>
        get() = array("favorites")
        set(value) = putArray("favorites", value.distinct())
    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("hidden", value.toSet()).apply() }
    fun alias(id: String): String? = prefs.getString("alias:$id", null)
    fun rename(id: String, name: String) { prefs.edit().apply { if (name.isBlank()) remove("alias:$id") else putString("alias:$id", name.trim()) }.apply() }
    fun folder(id: String): AppFolder? = runCatching {
        val json = JSONObject(prefs.getString(id, null) ?: return null)
        val apps = json.getJSONArray("apps")
        AppFolder(id, json.getString("name"), List(apps.length()) { apps.getString(it) })
    }.getOrNull()
    fun saveFolder(folder: AppFolder) {
        prefs.edit().putString(folder.id, JSONObject().put("name", folder.name).put("apps", JSONArray(folder.apps.distinct())).toString()).apply()
    }
    fun createFolder(name: String, apps: List<String>): String {
        val id = "folder:${UUID.randomUUID()}"
        saveFolder(AppFolder(id, name, apps))
        favorites = favorites + id
        return id
    }
    fun removeFolder(id: String) { favorites = favorites - id; prefs.edit().remove(id).apply() }
    fun popUp(id: String): List<String> = array("popup:$id")
    fun setPopUp(id: String, ids: List<String>) = putArray("popup:$id", ids)
    var widgets: List<Int>
        get() = array("widgets").mapNotNull(String::toIntOrNull)
        set(value) = putArray("widgets", value.map(Int::toString))
    fun bool(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun string(key: String, default: String = "") = prefs.getString(key, default) ?: default
    fun set(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun set(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun export(): String {
        val json = JSONObject().put("version", 1)
        val values = JSONObject()
        prefs.all.filterKeys { it != "widgets" && it != "pendingWidgetId" }.forEach { (key, value) -> if (value is Set<*>) values.put(key, JSONArray(value.toList())) else values.put(key, value) }
        return json.put("preferences", values).toString(2)
    }
    fun import(text: String) {
        require(text.length <= 1_000_000) { "Файл слишком большой" }
        val json = JSONObject(text)
        require(json.getInt("version") == 1) { "Неподдерживаемая версия резервной копии" }
        val values = json.getJSONObject("preferences")
        // Validate completely before mutating persisted state.
        val accepted = mutableMapOf<String, Any>()
        val booleanKeys = setOf("initialized", "gesturesLearned", "dimWallpaper", "hideIcons", "largeText", "compact", "leftHanded", "hideStatus", "clock24", "smallClock", "notifications")
        // Keep the ignored legacy wallpaper key compatible with version 1 backups.
        val stringKeys = setOf("favorites", "widgets", "font", "wallpaper", "weatherCity", "weatherLat", "weatherLon")
        values.keys().forEach { key ->
            val value = values.get(key)
            require(key.length <= 300)
            when {
                key == "hidden" && value is JSONArray -> accepted[key] = List(value.length()) { value.getString(it) }.toSet()
                key in booleanKeys && value is Boolean -> accepted[key] = value
                (key in stringKeys || key.startsWith("alias:") || key.startsWith("folder:") || key.startsWith("popup:")) && value is String -> accepted[key] = value
                else -> error("Повреждённая резервная копия")
            }
        }
        (accepted["favorites"] as? String)?.let { value ->
            val favorites = JSONArray(value)
            repeat(favorites.length()) { favorites.getString(it) }
        }
        accepted.filterKeys { it.startsWith("folder:") }.values.forEach { value ->
            val folder = JSONObject(value as String)
            require(folder.getString("name").isNotBlank())
            val ids = folder.getJSONArray("apps")
            repeat(ids.length()) { ids.getString(it) }
        }
        accepted.filterKeys { it.startsWith("popup:") }.values.forEach { value ->
            val ids = JSONArray(value as String)
            repeat(ids.length()) { ids.getString(it) }
        }
        // Widget ids belong to this device and this host, never to a portable backup.
        accepted.remove("widgets")
        val localWidgets = prefs.getString("widgets", "[]")
        prefs.edit().apply {
            clear()
            putString("widgets", localWidgets)
            accepted.forEach { (key, value) -> when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
        }.apply()
    }
}
