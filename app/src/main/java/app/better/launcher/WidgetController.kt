package app.better.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout

class WidgetController(private val activity: MainActivity, private val store: LauncherStore) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, 1201)
    private val hostContext = widgetHostContext(activity)
    private var pending = store.string("pendingWidgetId").toIntOrNull() ?: -1
    private var pendingHome = store.string("pendingWidgetDestination") == "home"
    fun start() { runCatching { host.startListening() } }
    fun stop() { host.stopListening() }
    fun add(toHome: Boolean = false) {
        activity.sheet(if (toHome) "Виджет на главном экране" else "Добавить виджет", scrollContent = false) { body, dialog ->
            body.addView(WidgetPickerView(activity).apply { onChoose = { provider -> dialog.dismiss(); bind(provider, toHome) } },
                LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }
    private fun bind(provider: AppWidgetProviderInfo, toHome: Boolean) {
        cleanup()
        pendingHome = toHome
        pending = host.allocateAppWidgetId()
        store.prefs.edit().putString("pendingWidgetId", pending.toString())
            .putString("pendingWidgetDestination", if (toHome) "home" else "widgets").apply()
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
    private fun commit(id: Int) {
        if (id == -1 || manager.getAppWidgetInfo(id) == null) { cleanup(); return }
        store.widgets = store.widgets + id
        if (pendingHome) store.homeWidgetId = id
        pending = -1
        clearPending()
        showDestination(pendingHome)
    }
    private fun clearPending() { store.prefs.edit().remove("pendingWidgetId").remove("pendingWidgetDestination").apply() }
    private fun cleanup() { if (pending != -1) host.deleteAppWidgetId(pending); pending = -1; clearPending() }
    private fun showDestination(home: Boolean) { if (home) activity.showHome() else activity.showWidgets() }
    fun onResult(request: Int, result: Int): Boolean {
        if (request != BIND && request != CONFIGURE) return false
        if (result != Activity.RESULT_OK) { cleanup(); showDestination(pendingHome) }
        else if (request == BIND) configure(pending) else commit(pending)
        return true
    }
    private fun prune() {
        val valid = store.widgets.filter { manager.getAppWidgetInfo(it) != null }
        if (valid != store.widgets) {
            (store.widgets - valid.toSet()).forEach(host::deleteAppWidgetId)
            store.widgets = valid
        }
    }
    fun renderHome(container: LinearLayout) {
        prune()
        store.homeWidgetId?.let { render(container, it, true) }
    }
    fun renderPage(container: LinearLayout) {
        prune()
        store.pageWidgets.forEachIndexed { index, id ->
            if (index > 0) container.addView(activity.gap(24))
            render(container, id, false)
        }
    }
    private fun render(container: LinearLayout, id: Int, home: Boolean) {
        val info = manager.getAppWidgetInfo(id) ?: return
        val header = activity.horizontal()
        header.addView(activity.text(info.loadLabel(activity.packageManager), 13f, activity.secondary).apply {
            minHeight = activity.dp(48)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(activity.iconButton(Glyph.MORE, "Настроить виджет ${info.loadLabel(activity.packageManager)}") {
            val options = listOf(if (home) "На экран виджетов" else "На главный экран", "Удалить виджет")
            activity.choose("Виджет", options) { choice ->
                if (choice == 0) store.homeWidgetId = if (home) null else id
                else { host.deleteAppWidgetId(id); store.widgets = store.widgets - id }
                showDestination(home)
            }
        })
        container.addView(header)
        val view = host.createView(hostContext, id, info)
        view.tag = PageSwipeLayout.BLOCK_SWIPE
        val height = (info.minHeight / activity.resources.displayMetrics.density).toInt().coerceIn(100, 220)
        // Measure the current page/window, including insets and handedness, rather than the display.
        view.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right > left && right - left != oldRight - oldLeft) {
                val density = activity.resources.displayMetrics.density
                val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
                val widthDp = ((right - left - padding.left - padding.right) / density).toInt().coerceAtLeast(1)
                val heightDp = (height - (padding.top + padding.bottom) / density).toInt().coerceAtLeast(1)
                manager.updateAppWidgetOptions(id, Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                })
            }
        }
        container.addView(view, LinearLayout.LayoutParams(-1, activity.dp(height)))
    }
    companion object { const val BIND = 41; const val CONFIGURE = 42 }
}
