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
import com.google.android.material.slider.Slider
import com.google.android.material.slider.TickVisibilityMode
import kotlin.math.ceil

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
        params.height = if (size == null || info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0) defaultHeight(info)
            else activity.dp(size.height.coerceIn(heights))
        view.layoutParams = params
    }

    private fun showControls(id: Int, info: AppWidgetProviderInfo, home: Boolean, frame: View, view: AppWidgetHostView) {
        activity.sheet(info.loadLabel(activity.packageManager)) { body, dialog ->
            body.addView(activity.action("Изменить размер") { dialog.dismiss(); resize(id, info, frame, view) })
            if (info.configure != null) body.addView(activity.action("Настройки виджета") {
                dialog.dismiss()
                runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, RECONFIGURE, null) }
                    .onFailure { activity.sheet("Настройки недоступны") { content, _ ->
                        content.addView(activity.text("Приложение не смогло открыть настройки виджета.", 16f, activity.secondary))
                    } }
            })
            body.addView(activity.action(if (home) "На экран виджетов" else "На главный экран") {
                dialog.dismiss(); store.homeWidgetId = if (home) null else id; showDestination(home)
            })
            body.addView(activity.action("Удалить виджет") {
                dialog.dismiss(); host.deleteAppWidgetId(id); store.widgets = store.widgets - id; showDestination(home)
            })
        }
    }

    private fun resize(id: Int, info: AppWidgetProviderInfo, frame: View, view: AppWidgetHostView) {
        val (widths, heights) = ranges(info, frame)
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        var size = store.widgetSize(id) ?: WidgetSize(0, (view.height / activity.resources.displayMetrics.density).toInt().coerceIn(32, 1600))
        activity.sheet("Размер виджета") { body, dialog ->
            body.addView(activity.text("Изменения сохраняются сразу", 14f, activity.secondary))
            body.addView(activity.gap(16))
            fun dimension(label: String, range: IntRange, value: Int, enabled: Boolean, update: (Int) -> Unit) {
                val heading = activity.text(activity.getString(R.string.widget_dimension, label, value), 17f)
                body.addView(heading)
                if (enabled && range.first < range.last) body.addView(Slider(activity).apply {
                    valueFrom = range.first.toFloat(); valueTo = range.last.toFloat(); stepSize = 1f
                    this.value = value.coerceIn(range).toFloat()
                    contentDescription = label
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    setTickVisibilityMode(TickVisibilityMode.TICK_VISIBILITY_HIDDEN)
                    setLabelFormatter { activity.getString(R.string.widget_dimension_value, it.toInt()) }
                    addOnChangeListener { _, selected, fromUser ->
                        if (fromUser) {
                            val number = selected.toInt()
                            heading.text = activity.getString(R.string.widget_dimension, label, number)
                            update(number)
                            store.setWidgetSize(id, size)
                            applySize(id, info, frame, view)
                        }
                    }
                }, LinearLayout.LayoutParams(-1, activity.dp(48)))
                else body.addView(activity.text(if (enabled) "Доступный размер ограничен шириной экрана" else "Приложение не поддерживает изменение этой стороны", 14f, activity.secondary))
                body.addView(activity.gap(16))
            }
            dimension("Ширина", widths, (view.width / activity.resources.displayMetrics.density).toInt(), horizontal) { size = size.copy(width = it) }
            dimension("Высота", heights, (view.height / activity.resources.displayMetrics.density).toInt(), vertical) { size = size.copy(height = it) }
            body.addView(activity.action("Сбросить размер") {
                store.setWidgetSize(id, null); applySize(id, info, frame, view); dialog.dismiss()
            })
        }
    }
    companion object { const val BIND = 41; const val CONFIGURE = 42; const val RECONFIGURE = 43 }
}
