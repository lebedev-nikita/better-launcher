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
import java.text.Collator

data class AppEntry(val id: String, val component: ComponentName, val user: UserHandle, val originalLabel: String, val icon: Drawable) {
    val packageName: String get() = component.packageName
}

class AppCatalog(private val context: Context, private val store: LauncherStore) {
    val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    var apps: List<AppEntry> = emptyList()
        private set
    fun load() {
        apps = users.userProfiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user).mapNotNull { info ->
                if (info.componentName.packageName == context.packageName) null
                else AppEntry("${users.getSerialNumberForUser(user)}:${info.componentName.flattenToString()}", info.componentName, user, info.label.toString(), info.getBadgedIcon(context.resources.displayMetrics.densityDpi))
            } }.getOrDefault(emptyList())
        }.distinctBy { it.id }
    }
    fun label(app: AppEntry) = store.alias(app.id) ?: app.originalLabel
    fun find(id: String) = apps.find { it.id == id }
    fun visible(query: String = ""): List<AppEntry> {
        val collator = Collator.getInstance()
        return apps.filter { it.id !in store.hidden && AppListRules.matches(query, label(it), it.originalLabel) }
            .sortedWith(compareBy<AppEntry> { AppListRules.sectionOrder(AppListRules.section(label(it))) }
                .thenComparator { a, b -> collator.compare(label(a), label(b)) })
    }
    fun launch(app: AppEntry) = launcherApps.startMainActivity(app.component, app.user, null, null)
    fun shortcuts(app: AppEntry): List<ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), app.user) ?: emptyList()
    }.getOrDefault(emptyList())

    fun seedFavorites() {
        if (store.bool("initialized")) return
        val priorities = listOf("dialer", "messaging", "chrome", "camera", "photos", "maps", "vending", "settings")
        val chosen = priorities.mapNotNull { keyword -> apps.firstOrNull { it.user == Process.myUserHandle() && it.packageName.contains(keyword) }?.id }.distinct().take(7)
        store.favorites = (chosen + apps.filter { it.user == Process.myUserHandle() }.map { it.id }).distinct().take(7)
        store.set("initialized", true)
    }
}
