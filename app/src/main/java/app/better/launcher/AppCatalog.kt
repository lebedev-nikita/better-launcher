package app.better.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

data class AppEntry(val id: String, val component: ComponentName, val user: UserHandle, val originalLabel: String, val icon: Drawable) {
    val packageName: String get() = component.packageName
}

class AppCatalog(private val context: Context, private val store: LauncherStore) {
    val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    @Volatile var apps: List<AppEntry> = emptyList()
        private set
    private data class Prepared(val source: List<AppEntry>, val settings: Map<String, Any?>, val index: AppSearchIndex<AppEntry>)
    @Volatile private var prepared: Prepared? = null
    private fun searchSettings(): Map<String, Any?> = store.prefs.all.filterKeys { it == "hidden" || it.startsWith("alias:") }
    private fun prepare(source: List<AppEntry>, settings: Map<String, Any?>): Prepared {
        val hidden = settings["hidden"] as? Set<*> ?: emptySet<String>()
        val index = AppSearchIndex(source,
            { settings["alias:${it.id}"] as? String ?: it.originalLabel }, AppEntry::originalLabel,
            { it.id !in hidden })
        return Prepared(source, settings, index)
    }
    fun load() {
        AppListRules.prepareSections()
        val loaded = users.userProfiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user).mapNotNull { info ->
                if (info.componentName.packageName == context.packageName) null
                else AppEntry("${users.getSerialNumberForUser(user)}:${info.componentName.flattenToString()}", info.componentName, user, info.label.toString(), info.getBadgedIcon(context.resources.displayMetrics.densityDpi))
            } }.getOrDefault(emptyList())
        }.distinctBy { it.id }
        val next = prepare(loaded, searchSettings())
        apps = loaded
        prepared = next
    }
    fun label(app: AppEntry) = store.alias(app.id) ?: app.originalLabel
    fun find(id: String) = apps.find { it.id == id }
    private fun searchIndex(): AppSearchIndex<AppEntry> {
        val source = apps
        val settings = searchSettings()
        val cached = prepared
        if (cached != null && cached.source === source && cached.settings == settings) return cached.index
        return prepare(source, settings).also { prepared = it }.index
    }
    fun visible(query: String = ""): List<AppEntry> = searchIndex().matching(query)
    fun sections(): Set<String> = searchIndex().sections
    fun launch(app: AppEntry) = launcherApps.startMainActivity(app.component, app.user, null, null)
    fun shortcuts(app: AppEntry): List<ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), app.user) ?: emptyList()
    }.getOrDefault(emptyList())

    // May read the publisher's bitmap or URI; call from a background thread.
    fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = runCatching {
        launcherApps.getShortcutBadgedIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    }.getOrNull()

    fun seedFavorites() {
        if (store.bool("initialized")) return
        val priorities = listOf("dialer", "messaging", "chrome", "camera", "photos", "maps", "vending", "settings")
        val chosen = priorities.mapNotNull { keyword -> apps.firstOrNull { it.user == Process.myUserHandle() && it.packageName.contains(keyword) }?.id }.distinct().take(7)
        store.favorites = (chosen + apps.filter { it.user == Process.myUserHandle() }.map { it.id }).distinct().take(7)
        store.set("initialized", true)
    }
}
