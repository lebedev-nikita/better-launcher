package app.better.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.ceil
import kotlin.math.roundToInt

class WidgetController(private val activity: MainActivity, private val store: LauncherStore) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, 1201)
    private val hostContext = widgetHostContext(activity)
    private var pending = store.string("pendingWidgetId").toIntOrNull() ?: -1
    private var editor: View? = null
    val isResizing: Boolean get() = editor != null
    private var pendingHome = store.string("pendingWidgetDestination") == "home"
    fun start() { runCatching { host.startListening() } }
    fun stop() { finishResize(false); host.stopListening() }
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
        if (request == RECONFIGURE) return true
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
        val view = host.createView(hostContext, id, info)
        val frame = WidgetTouchFrame(activity).apply {
            contentDescription = "Виджет ${info.loadLabel(activity.packageManager)}"
            setOnLongClickListener { showControls(id, info, home, this, view); true }
        }
        frame.addView(view, FrameLayout.LayoutParams(-1, defaultHeight(info), Gravity.CENTER_HORIZONTAL))
        // The wrapper keeps the page width even when the hosted widget becomes narrower.
        frame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right > left && right - left != oldRight - oldLeft) applySize(id, info, frame, view)
        }
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right > left && bottom > top && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                val density = activity.resources.displayMetrics.density
                // Framework removes host padding and publishes both legacy ranges and Android 12 sizes.
                view.updateAppWidgetSize(Bundle(), listOf(SizeF((right - left) / density, (bottom - top) / density)))
            }
        }
        container.addView(frame, LinearLayout.LayoutParams(-1, -2))
    }

    private fun defaultHeight(info: AppWidgetProviderInfo) = activity.dp(
        (info.minHeight / activity.resources.displayMetrics.density).toInt().coerceIn(100, 220))

    private fun sizeRange(min: Int, minResize: Int, maxResize: Int, padding: Int, limit: Int): IntRange {
        val density = activity.resources.displayMetrics.density
        val minimum = if (minResize > 0 && minResize <= min) minResize else min
        val lower = ceil((minimum + padding) / density).toInt().coerceIn(32, limit)
        val upper = if (maxResize > 0 && maxResize >= min) ((maxResize + padding) / density).toInt().coerceIn(lower, limit) else limit
        return lower..upper
    }

    private fun ranges(info: AppWidgetProviderInfo, frame: View): Pair<IntRange, IntRange> {
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
        val width = (frame.width / activity.resources.displayMetrics.density).toInt().coerceIn(32, 1600)
        return sizeRange(info.minWidth, info.minResizeWidth, info.maxResizeWidth, padding.left + padding.right, width) to
            sizeRange(info.minHeight, info.minResizeHeight, info.maxResizeHeight, padding.top + padding.bottom, 1600)
    }

    private fun applySize(id: Int, info: AppWidgetProviderInfo, frame: View, view: AppWidgetHostView) {
        val size = store.widgetSize(id)
        val (widths, heights) = ranges(info, frame)
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = if (size == null || size.width == 0 || info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL == 0) -1
            else activity.dp(size.width.coerceIn(widths))
        params.topMargin = activity.dp(size?.top ?: 0)
        params.height = if (size == null || info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0) defaultHeight(info)
            else activity.dp(size.height.coerceIn(heights))
        view.layoutParams = params
    }

    private fun showControls(id: Int, info: AppWidgetProviderInfo, home: Boolean, frame: View, view: AppWidgetHostView) {
        val icon = runCatching { info.loadIcon(activity, activity.resources.displayMetrics.densityDpi) }.getOrNull()
        activity.managementSheet(info.loadLabel(activity.packageManager), icon) { body, dialog ->
            fun command(glyph: Glyph, label: String, action: () -> Unit) = activity.managementAction(body, dialog, glyph, label, action)
            command(Glyph.INFO, "О приложении") {
                runCatching { activity.catalog.launcherApps.startAppDetailsActivity(info.provider, info.profile, null, null) }
                    .onFailure { activity.message("Не удалось открыть настройки приложения") }
            }
            command(Glyph.RESIZE, "Изменить размер") { resize(id, info, frame, view) }
            if (info.configure != null) command(Glyph.EDIT, "Настройки виджета") {
                runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, RECONFIGURE, null) }
                    .onFailure { activity.message("Приложение не смогло открыть настройки виджета") }
            }
            command(if (home) Glyph.WIDGETS else Glyph.STAR, if (home) "На экран виджетов" else "На главный экран") {
                store.homeWidgetId = if (home) null else id; showDestination(home)
            }
            if (store.widgetSize(id) != null) command(Glyph.RESIZE, "Сбросить размер") {
                store.setWidgetSize(id, null); applySize(id, info, frame, view)
            }
            command(Glyph.PLUS, "Добавить виджет") { add() }
            command(Glyph.DELETE, "Удалить виджет") {
                host.deleteAppWidgetId(id); store.widgets = store.widgets - id; showDestination(home)
            }
            body.addView(activity.gap(16))
            body.addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.app_menu_foreground)) },
                LinearLayout.LayoutParams(-1, activity.dp(1)).apply { marginStart = activity.dp(8); marginEnd = activity.dp(8) })
            body.addView(activity.gap(8))
            command(Glyph.SETTINGS, "Настройки Better Launcher") { activity.showSettings() }
        }
    }

    private fun resize(id: Int, info: AppWidgetProviderInfo, frame: View, view: AppWidgetHostView) {
        finishResize(false)
        val density = activity.resources.displayMetrics.density
        var original: WidgetSize? = store.widgetSize(id)
        var editingSize = WidgetSize((view.width / density).roundToInt().coerceAtMost((frame.width / density).toInt()), (view.height / density).roundToInt(), original?.top ?: 0)
        var gestureStart = editingSize
        val layer = widgetResizeOverlay(activity, view,
            info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            readSize = { editingSize },
            ranges = { ranges(info, frame) },
            onChange = { size ->
                editingSize = size
                store.setWidgetSize(id, if (size.width == (frame.width / density).toInt()) size.copy(width = 0) else size)
                applySize(id, info, frame, view)
            },
            onGestureStart = { original = store.widgetSize(id); gestureStart = editingSize },
            onCancel = { editingSize = gestureStart; store.setWidgetSize(id, original); applySize(id, info, frame, view) },
            onFinish = { finishResize() })
        editor = layer
        activity.attachWidgetEditor(layer)
    }

    fun finishResize(refresh: Boolean = true): Boolean {
        val layer = editor ?: return false
        editor = null
        activity.detachWidgetEditor(layer, refresh)
        return true
    }
    companion object { const val BIND = 41; const val CONFIGURE = 42; const val RECONFIGURE = 43 }
}
