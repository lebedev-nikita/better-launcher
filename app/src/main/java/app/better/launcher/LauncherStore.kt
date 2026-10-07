package app.better.launcher

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WidgetSize(val width: Int, val height: Int, val top: Int = 0, val offsetX: Int = 0)

data class AppFolder(val id: String, val name: String, val apps: List<String>)

class LauncherStore(private val context: Context) {
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
    fun hiddenShortcuts(id: String): Set<String> = array("hiddenShortcuts:$id").toSet()
    fun setHiddenShortcuts(id: String, ids: Set<String>) {
        require(id.isNotEmpty() && ids.all { it.isNotEmpty() })
        if (ids.isEmpty()) prefs.edit { remove("hiddenShortcuts:$id") }
        else putArray("hiddenShortcuts:$id", ids.toList())
    }
    var widgets: List<Int>
        get() = array("widgets").mapNotNull(String::toIntOrNull)
        set(value) {
            // Freeze the legacy stack's first widget before changing its contents.
            val home = homeWidgetId
            prefs.edit {
                putString("widgets", JSONArray(value.distinct().map(Int::toString)).toString())
                putString("homeWidgetId", home?.takeIf { it in value }?.toString().orEmpty())
                val sizes = JSONObject(string("widgetSizes", "{}"))
                sizes.keys().asSequence().toList().filter { it.toIntOrNull() !in value }.forEach(sizes::remove)
                putString("widgetSizes", sizes.toString())
            }
        }
    var homeWidgetId: Int?
        get() = if (prefs.contains("homeWidgetId")) string("homeWidgetId").toIntOrNull()?.takeIf { it in widgets }
            else widgets.firstOrNull()
        set(value) {
            require(value == null || value in widgets)
            set("homeWidgetId", value?.toString().orEmpty())
        }
    val pageWidgets: List<Int> get() { val home = homeWidgetId; return widgets.filter { it != home } }
    fun widgetSize(id: Int): WidgetSize? = runCatching {
        val size = JSONObject(string("widgetSizes", "{}")).getJSONObject(id.toString())
        WidgetSize(size.getInt("width"), size.getInt("height"), size.optInt("top", 0), size.optInt("offsetX", 0))
    }.getOrNull()
    fun setWidgetSize(id: Int, size: WidgetSize?) {
        require(id in widgets)
        require(size == null || size.width in 0..1600 && size.height in 32..1600 && size.top in 0..1600 && size.offsetX in -1600..1600)
        val sizes = JSONObject(string("widgetSizes", "{}"))
        if (size == null) sizes.remove(id.toString())
        else sizes.put(id.toString(), JSONObject().put("width", size.width).put("height", size.height).put("top", size.top).put("offsetX", size.offsetX))
        set("widgetSizes", sizes.toString())
    }
    var widgetSpacing: Int
        get() = prefs.getInt("widgetSpacing", 8).coerceIn(0, 32)
        set(value) {
            require(value in 0..32)
            prefs.edit { putInt("widgetSpacing", value) }
        }
    var wallpaperDimming: Int
        get() = prefs.getInt("wallpaperDimming", if (bool("dimWallpaper", true)) 20 else 0).coerceIn(0, 100)
        set(value) {
            require(value in 0..100)
            prefs.edit { putInt("wallpaperDimming", value) }
        }
    fun bool(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun string(key: String, default: String = "") = prefs.getString(key, default) ?: default
    fun set(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun set(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun export(): String {
        val json = JSONObject().put("version", 1)
        val values = JSONObject()
        prefs.all.filterKeys { it !in localWidgetKeys }.forEach { (key, value) -> if (value is Set<*>) values.put(key, JSONArray(value.toList())) else values.put(key, value) }
        return json.put("preferences", values).toString(2)
    }
    fun import(text: String) {
        require(text.length <= 1_000_000) { context.getString(R.string.file_too_large) }
        val json = JSONObject(text)
        require(json.getInt("version") == 1) { context.getString(R.string.backup_version_error) }
        val values = json.getJSONObject("preferences")
        // Validate completely before mutating persisted state.
        val accepted = mutableMapOf<String, Any>()
        val booleanKeys = setOf("initialized", "gesturesLearned", "dimWallpaper", "hideIcons", "largeText", "compact", "leftHanded", "hideStatus", "clock24", "smallClock", "notifications")
        // Keep the ignored legacy wallpaper key compatible with version 1 backups.
        val stringKeys = setOf("favorites", "font", "wallpaper", "weatherCity", "weatherLat", "weatherLon") + localWidgetKeys
        values.keys().forEach { key ->
            val value = values.get(key)
            require(key.length <= 300)
            when {
                key == "wallpaperDimming" && value is Int && value in 0..100 -> accepted[key] = value
                key == "widgetSpacing" && value is Int && value in 0..32 -> accepted[key] = value
                key == "hidden" && value is JSONArray -> accepted[key] = List(value.length()) { value.getString(it) }.toSet()
                key in booleanKeys && value is Boolean -> accepted[key] = value
                (key in stringKeys || key.startsWith("alias:") || key.startsWith("folder:") || key.startsWith("popup:") || key.startsWith("hiddenShortcuts:")) && value is String -> accepted[key] = value
                else -> error(context.getString(R.string.backup_corrupt))
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
        accepted.filterKeys { it.startsWith("hiddenShortcuts:") }.forEach { (key, value) ->
            require(key.removePrefix("hiddenShortcuts:").isNotEmpty())
            val ids = JSONArray(value as String)
            repeat(ids.length()) { require(ids.get(it) is String && ids.getString(it).isNotEmpty()) }
        }
        (accepted["widgets"] as? String)?.let { value ->
            val ids = JSONArray(value)
            repeat(ids.length()) { require(ids.getString(it).toIntOrNull()?.let { id -> id > 0 } == true) }
        }
        listOf("homeWidgetId", "pendingWidgetId").forEach { key ->
            (accepted[key] as? String)?.let { require(it.isEmpty() && key == "homeWidgetId" || it.toIntOrNull()?.let { id -> id > 0 } == true) }
        }
        (accepted["pendingWidgetDestination"] as? String)?.let { require(it == "home" || it == "widgets") }
        (accepted["widgetSizes"] as? String)?.let { value ->
            val sizes = JSONObject(value)
            sizes.keys().forEach { key ->
                require(key.toIntOrNull()?.let { it > 0 } == true)
                val size = sizes.getJSONObject(key)
                val keys = size.keys().asSequence().toSet()
                require(keys.containsAll(setOf("width", "height")) && keys.all { it in setOf("width", "height", "top", "offsetX") })
                require(size.get("width") is Int && size.get("height") is Int)
                require(size.getInt("width") in 0..1600 && size.getInt("height") in 32..1600)
                require(!size.has("top") || size.get("top") is Int && size.getInt("top") in 0..1600)
                require(!size.has("offsetX") || size.get("offsetX") is Int && size.getInt("offsetX") in -1600..1600)
            }
        }
        // Widget ids belong to this device and this host, never to a portable backup.
        localWidgetKeys.forEach(accepted::remove)
        val localWidgets = prefs.all.filterKeys { it in localWidgetKeys }
        prefs.edit().apply {
            clear()
            localWidgets.forEach { (key, value) -> putString(key, value as String) }
            accepted.forEach { (key, value) -> when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
        }.apply()
    }
    private val localWidgetKeys = setOf("widgets", "homeWidgetId", "pendingWidgetId", "pendingWidgetDestination", "widgetSizes")
}
