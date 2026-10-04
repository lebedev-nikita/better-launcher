package app.better.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

class WidgetController(private val activity: MainActivity, private val store: LauncherStore) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, 1201)
    private var pending = store.string("pendingWidgetId").toIntOrNull() ?: -1
    var index = 0
    fun start() { runCatching { host.startListening() } }
    fun stop() { host.stopListening() }
    fun add() {
        if (store.widgets.size >= 4) { activity.message("В стопку можно добавить до 4 виджетов"); return }
        val providers = manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager) }
        activity.choose("Добавить виджет", providers.map { it.loadLabel(activity.packageManager) }) { position -> bind(providers[position]) }
    }
    private fun bind(provider: AppWidgetProviderInfo) {
        cleanup()
        pending = host.allocateAppWidgetId()
        store.set("pendingWidgetId", pending.toString())
        if (manager.bindAppWidgetIdIfAllowed(pending, provider.provider)) configure(pending)
        else activity.safeStartForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider), BIND) { cleanup() }
    }
    private fun configure(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null) { cleanup(); return }
        if (info.configure != null) activity.safeStartForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), CONFIGURE) { cleanup() }
        else commit(id)
    }
    private fun commit(id: Int) { store.widgets = (store.widgets + id).distinct(); pending = -1; store.prefs.edit().remove("pendingWidgetId").apply(); activity.showHome() }
    private fun cleanup() { if (pending != -1) host.deleteAppWidgetId(pending); pending = -1; store.prefs.edit().remove("pendingWidgetId").apply() }
    fun onResult(request: Int, result: Int): Boolean {
        if (request != BIND && request != CONFIGURE) return false
        if (result != Activity.RESULT_OK) cleanup()
        else if (request == BIND) configure(pending) else commit(pending)
        return true
    }
    fun render(container: LinearLayout) {
        val valid = store.widgets.filter { manager.getAppWidgetInfo(it) != null }
        if (valid != store.widgets) store.widgets = valid
        if (valid.isEmpty()) return
        index = index.coerceIn(valid.indices)
        val id = valid[index]; val info = manager.getAppWidgetInfo(id) ?: return
        val header = activity.horizontal()
        header.addView(activity.text(info.loadLabel(activity.packageManager), 13f, activity.secondary), LinearLayout.LayoutParams(0, activity.dp(48), 1f))
        if (valid.size > 1) {
            header.addView(activity.iconButton(Glyph.PREVIOUS, "Предыдущий виджет") { index = (index - 1 + valid.size) % valid.size; activity.showHome() })
            header.addView(activity.iconButton(Glyph.NEXT, "Следующий виджет") { index = (index + 1) % valid.size; activity.showHome() })
        }
        header.addView(activity.iconButton(Glyph.MORE, "Настроить виджет") {
            activity.choose("Виджет", listOf("Добавить виджет", "Удалить этот виджет")) { choice ->
                if (choice == 0) add() else { host.deleteAppWidgetId(id); store.widgets = store.widgets - id; activity.showHome() }
            }
        })
        container.addView(header)
        val view = host.createView(activity, id, info)
        val width = (activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density).toInt() - 90
        val height = (info.minHeight / activity.resources.displayMetrics.density).toInt().coerceIn(100, 220)
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        })
        container.addView(view, LinearLayout.LayoutParams(-1, activity.dp(height)))
    }
    companion object { const val BIND = 41; const val CONFIGURE = 42 }
}
