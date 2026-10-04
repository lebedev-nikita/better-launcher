package app.better.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import java.text.Collator
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** A recycled provider list. Browsing previews never allocates or binds widget IDs. */
class WidgetPickerView(context: Context) : FrameLayout(context) {
    private val activity = context as MainActivity
    var onChoose: (AppWidgetProviderInfo) -> Unit = {}
    private data class Entry(val provider: AppWidgetProviderInfo, val label: String, val app: String, val section: String)
    private val worker = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var entries = emptyList<Entry>()
    private val rows = mutableSetOf<Row>()
    private val adapter = ProviderAdapter()
    private val list = ListView(activity).apply {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        divider = null; isVerticalScrollBarEnabled = false
        clipToPadding = false; descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        setPadding(0, 0, activity.dp(72), 0)
        adapter = this@WidgetPickerView.adapter
        setOnItemClickListener { _, _, position, _ -> onChoose(entries[position].provider) }
    }
    private val status = activity.text("Загружаем виджеты…", 16f, activity.secondary)
    private val rail = AlphabetRail(activity).apply {
        alphabetLabel = "Алфавит виджетов"
        visibility = GONE
        onSelect = { letter ->
            val position = if (letter == "★") 0 else entries.indexOfFirst { it.section == letter }
            if (position >= 0) list.setSelectionFromTop(position, 0)
        }
    }

    init {
        // This physical right-side rail follows the requested picker layout in all locales.
        layoutDirection = LAYOUT_DIRECTION_LTR
        addView(list, LayoutParams(-1, -1))
        addView(status, LayoutParams(-1, -2, Gravity.TOP).apply { rightMargin = activity.dp(72) })
        addView(rail, LayoutParams(activity.dp(92), -1, Gravity.END))
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, state: Int) = Unit
            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) {
                entries.getOrNull(first)?.let { rail.selected = it.section }
            }
        })
        worker.execute {
            val result = runCatching {
                val pm = activity.packageManager
                val collator = Collator.getInstance()
                AppWidgetManager.getInstance(activity).installedProviders
                    .filter { it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0 }
                    .map { provider ->
                        val label = runCatching { provider.loadLabel(pm) }.getOrNull().orEmpty().ifBlank { provider.provider.shortClassName }
                        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(provider.provider.packageName, 0)).toString() }
                            .getOrDefault(provider.provider.packageName)
                        Entry(provider, label, app, AppListRules.section(label))
                    }.sortedWith { a, b ->
                        compareValues(AppListRules.sectionOrder(a.section), AppListRules.sectionOrder(b.section))
                            .takeIf { it != 0 } ?: collator.compare(a.label, b.label).takeIf { it != 0 }
                            ?: collator.compare(a.app, b.app).takeIf { it != 0 }
                            ?: a.provider.provider.flattenToString().compareTo(b.provider.provider.flattenToString())
                    }
            }
            handler.post {
                if (!closed) {
                    entries = result.getOrDefault(emptyList())
                    status.text = if (result.isFailure) "Не удалось загрузить виджеты. Закройте список и попробуйте снова." else "Нет доступных виджетов"
                    status.visibility = if (entries.isEmpty()) VISIBLE else GONE
                    rail.visibility = if (entries.isEmpty()) GONE else VISIBLE
                    updateLetters()
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun updateLetters() {
        val sections = entries.map { it.section }.toSet()
        rail.activeLetters = sections
        // The sheet can be short even in portrait, especially with a larger system font.
        val short = height / resources.displayMetrics.density < 500 || resources.configuration.fontScale > 1.1f
        rail.letters = AppListRules.railLetters(sections, short)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); updateLetters()
    }

    override fun onDetachedFromWindow() {
        closed = true
        rows.forEach(Row::cancel)
        worker.shutdownNow(); handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private inner class ProviderAdapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? Row ?: Row().also(rows::add)
            row.bind(entries[position])
            return row
        }
    }

    private inner class Row : LinearLayout(activity) {
        private val label = activity.text("", 18f)
        private val app = activity.text("", 13f, activity.secondary)
        private val preview = PreviewFrame(activity)
        private val fallback = activity.text("Загружаем превью…", 13f, activity.secondary).apply { gravity = Gravity.CENTER }
        private var imageJob: Future<*>? = null
        private var layoutPreview: AppWidgetHostView? = null
        private var token = 0

        init {
            orientation = VERTICAL
            setPadding(activity.dp(8), activity.dp(12), activity.dp(8), activity.dp(16))
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(activity.gap(4))
            addView(app, LinearLayout.LayoutParams(-1, -2))
            addView(activity.gap(12))
            addView(preview, LinearLayout.LayoutParams(-1, activity.dp(144)))
            // The row is one accessible choice; preview layouts can contain buttons and clocks.
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            label.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            app.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun cancel() { token++; imageJob?.cancel(true); layoutPreview?.setExecutor(null); imageJob = null; layoutPreview = null }

        fun bind(entry: Entry) {
            cancel()
            val binding = token
            label.text = entry.label; app.text = entry.app
            contentDescription = "Добавить виджет ${entry.label}, ${entry.app}"
            preview.removeAllViews(); preview.nativeWidth = 0; preview.nativeHeight = 0
            fallback.text = "Загружаем превью…"
            preview.addView(fallback, LayoutParams(-1, -1))
            val info = entry.provider
            fun current() = !closed && token == binding
            fun loadImage() {
                imageJob = worker.submit {
                    val drawable = runCatching { info.loadPreviewImage(activity, resources.displayMetrics.densityDpi) }.getOrNull()
                    val icon = if (drawable == null) runCatching { info.loadIcon(activity, resources.displayMetrics.densityDpi) }.getOrNull() else null
                    handler.post {
                        if (current()) {
                            preview.removeAllViews()
                            preview.nativeWidth = 0; preview.nativeHeight = 0
                            if (drawable != null) showImage(drawable)
                            else {
                                val empty = activity.vertical().apply { gravity = Gravity.CENTER }
                                icon?.let { empty.addView(ImageView(activity).apply { setImageDrawable(it) }, LinearLayout.LayoutParams(activity.dp(40), activity.dp(40))) }
                                empty.addView(activity.gap(8))
                                empty.addView(activity.text("Превью недоступно", 13f, activity.secondary).apply { gravity = Gravity.CENTER })
                                preview.addView(empty, LayoutParams(-1, -1))
                            }
                        }
                    }
                }
            }
            if (info.previewLayout != 0) {
                // The host's public executor API inflates RemoteViews in the background.
                // This view has no allocated ID and is never registered with AppWidgetHost.
                var fallbackRequested = false
                runCatching {
                    val hostView = object : AppWidgetHostView(activity) {
                        override fun getErrorView(): View {
                            if (current() && !fallbackRequested) {
                                fallbackRequested = true
                                handler.post { if (current()) loadImage() }
                            }
                            return activity.text("Загружаем превью…", 13f, activity.secondary)
                        }
                    }
                    layoutPreview = hostView
                    hostView.setAppWidget(-1, info)
                    hostView.setExecutor(worker)
                    preview.removeAllViews()
                    preview.nativeWidth = info.minWidth.coerceIn(activity.dp(180), activity.dp(600))
                    preview.nativeHeight = info.minHeight.coerceIn(activity.dp(100), activity.dp(400))
                    preview.addView(hostView)
                    hostView.updateAppWidget(RemoteViews(info.provider.packageName, info.previewLayout))
                }.onFailure { if (!fallbackRequested) { fallbackRequested = true; loadImage() } }
            } else loadImage()
        }

        private fun showImage(drawable: Drawable) {
            preview.addView(ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(drawable) }, LayoutParams(-1, -1))
        }
    }

    /** Scale static provider layouts to fit; never forward taps into their controls. */
    private class PreviewFrame(context: Context) : FrameLayout(context) {
        var nativeWidth = 0
        var nativeHeight = 0
        private var measuredNativeHeight = 0
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS; descendantFocusability = FOCUS_BLOCK_DESCENDANTS }
        override fun onInterceptTouchEvent(event: MotionEvent) = true
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (nativeWidth == 0 || getChildAt(0) == null) { super.onMeasure(widthMeasureSpec, heightMeasureSpec); return }
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
            val child = getChildAt(0)
            val nativeWidthSpec = MeasureSpec.makeMeasureSpec(nativeWidth, MeasureSpec.EXACTLY)
            // minHeight is a lower bound, not the height needed by a provider's preview text.
            child.measure(nativeWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            measuredNativeHeight = maxOf(nativeHeight, child.measuredHeight).coerceAtMost(context.dp(600))
            child.measure(nativeWidthSpec, MeasureSpec.makeMeasureSpec(measuredNativeHeight, MeasureSpec.EXACTLY))
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (nativeWidth == 0 || getChildAt(0) == null) { super.onLayout(changed, left, top, right, bottom); return }
            val child = getChildAt(0)
            val scale = minOf(width.toFloat() / nativeWidth, height.toFloat() / measuredNativeHeight, 1f)
            child.layout(0, 0, nativeWidth, measuredNativeHeight)
            child.pivotX = 0f; child.pivotY = 0f; child.scaleX = scale; child.scaleY = scale
            child.translationX = (width - nativeWidth * scale) / 2f
            child.translationY = (height - measuredNativeHeight * scale) / 2f
        }
    }
}
