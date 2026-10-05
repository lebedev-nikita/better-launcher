package app.better.launcher

import android.Manifest
import android.app.*
import android.app.role.RoleManager
import android.content.*
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.*
import android.view.*
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isNotEmpty
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import androidx.core.view.WindowCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.slider.TickVisibilityMode
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    lateinit var store: LauncherStore
    lateinit var catalog: AppCatalog
    lateinit var widgets: WidgetController
    val secondary: Int get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "Better Launcher")
    val surface: Int get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, "Better Launcher")
    private val executor = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var wallpaperScrim: WallpaperScrimView
    private lateinit var screen: PageSwipeLayout
    private lateinit var rail: AlphabetRail
    private var page = "home"
    private var allApps: ListView? = null
    private var appAdapter: AppListAdapter? = null
    private var appQuery = ""
    private var emptyApps: LinearLayout? = null
    private var keyboardRequest: Runnable? = null
    private var homeScroll: ScrollView? = null
    private var homeFooter: View? = null
    private var homeFooterHeight = 0
    private var widgetScroll: ScrollView? = null
    private var homeScrollPosition = 0
    private var widgetScrollPosition = 0
    private var search: EditText? = null
    private var weatherText: TextView? = null
    private var weather: Weather? = null
    private var weatherLoading = false
    private var weatherError = false
    private var weatherUpdated = 0L
    private var currentDialog: Dialog? = null
    private var loadingApps = false
    private var refreshQueued = false
    private var resumed = false
    private var notificationRefreshPending = false
    private var screenFocusBeforeWidgetEditor = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private val weatherTicker = object : Runnable {
        override fun run() { refreshWeather(); handler.postDelayed(this, 30_000) }
    }
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LauncherStore(this); catalog = AppCatalog(this, store); widgets = WidgetController(this, store)
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        root = FrameLayout(this)
        wallpaperScrim = WallpaperScrimView(this)
        root.addView(wallpaperScrim, FrameLayout.LayoutParams(-1, -1))
        screen = PageSwipeLayout(this)
        screen.onSwipe = { if (page == "home") showWidgets(true) else if (page == "widgets") returnFromWidgets() }
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        rail = AlphabetRail(this)
        rail.onSelect = { letter ->
            when (letter) {
                "★" -> { if (page != "home") showHome() }
                else -> { if (page != "apps") showApps(false); jumpTo(letter) }
            }
        }
        root.addView(rail, FrameLayout.LayoutParams(dp(92), -1, Gravity.END))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            screen.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            updateRailBounds()
            insets
        }
        setContentView(root)
        catalog.launcherApps.registerCallback(packageCallback, handler)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
        homeScrollPosition = savedInstanceState?.getInt("homeScrollPosition") ?: 0
        widgetScrollPosition = savedInstanceState?.getInt("widgetScrollPosition") ?: 0
        applyAppearance()
        if (savedInstanceState?.getString("page") == "widgets") showWidgets() else showHome()
        refreshApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page)
        outState.putInt("homeScrollPosition", homeScroll?.scrollY ?: homeScrollPosition)
        outState.putInt("widgetScrollPosition", widgetScroll?.scrollY ?: widgetScrollPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); currentDialog?.dismiss(); hideKeyboard(); showHome() }
    override fun onStart() { super.onStart(); widgets.start() }
    override fun onStop() { widgets.stop(); super.onStop() }
    override fun onResume() {
        super.onResume(); resumed = true
        NotificationHub.onChanged = {
            if (page == "home" && resumed) {
                if (currentDialog == null && !widgets.isResizing) refreshHomePreservingScroll() else notificationRefreshPending = true
            }
        }
        handler.post(weatherTicker); applyAppearance(); refreshApps(); refreshWeather()
    }
    override fun onPause() { resumed = false; handler.removeCallbacks(weatherTicker); NotificationHub.onChanged = null; super.onPause() }
    override fun onDestroy() {
        catalog.launcherApps.unregisterCallback(packageCallback)
        handler.removeCallbacksAndMessages(null); executor.shutdownNow(); currentDialog?.dismiss(); super.onDestroy()
    }
    private fun handleBack() {
        if (widgets.finishResize()) return
        if (currentDialog != null) currentDialog?.dismiss()
        else if (page == "widgets") returnFromWidgets()
        else if (page != "home") { hideKeyboard(); showHome() }
    }

    private fun refreshApps() {
        // Framework callbacks queued before unregisterCallback may arrive after onDestroy.
        if (isDestroyed) return
        if (loadingApps) { refreshQueued = true; return }
        loadingApps = true
        executor.execute {
            val result = runCatching { catalog.load(); catalog.seedFavorites() }
            handler.post {
                if (isDestroyed) return@post
                loadingApps = false; updateAlphabet()
                if (result.isFailure) message("Не удалось прочитать список приложений")
                when (page) { "home" -> refreshHomePreservingScroll(); "apps" -> populateApps(search?.text?.toString().orEmpty()) }
                if (refreshQueued) { refreshQueued = false; refreshApps() }
            }
        }
    }

    fun text(value: String, size: Float = 16f, color: Int = Color.WHITE): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        typeface = Typeface.create(store.string("font", "sans-serif"), Typeface.NORMAL)
        includeFontPadding = false
    }
    fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun iconButton(glyph: Glyph, label: String, action: () -> Unit): View = GlyphView(this, glyph).apply {
        contentDescription = label; isFocusable = true; background = touchBackground()
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); setOnClickListener { action() }
    }
    fun action(label: String, action: () -> Unit): TextView = text(label, 16f).apply {
        minHeight = dp(52); setPadding(dp(16), dp(8), dp(16), dp(8)); background = touchBackground(); isFocusable = true
        setOnClickListener { action() }
    }
    fun gap(height: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun clearScreen(newPage: String) {
        widgets.finishResize(false)
        if (page == "home" && newPage == "widgets") homeScrollPosition = homeScroll?.scrollY ?: 0
        widgetScroll?.let { widgetScrollPosition = it.scrollY }
        keyboardRequest?.let(handler::removeCallbacks); keyboardRequest = null
        page = newPage; screen.removeAllViews(); allApps = null; appAdapter = null; emptyApps = null; appQuery = ""; homeScroll = null; homeFooter = null; widgetScroll = null; search = null; weatherText = null
        screen.direction = when (newPage) { "home" -> 1; "widgets" -> -1; else -> 0 }
        screen.accessibilityPaneTitle = when (newPage) { "home" -> "Главный экран"; "widgets" -> "Виджеты"; "apps" -> "Все приложения"; else -> "Настройки" }
        rail.visibility = if (newPage == "home" || newPage == "apps") View.VISIBLE else View.GONE
        updateRailBounds()
    }
    private fun contentMargins(): Pair<Int, Int> = if (store.bool("leftHanded")) dp(84) to dp(28) else dp(28) to dp(84)
    private fun applyAppearance() {
        wallpaperScrim.shade = store.wallpaperDimming / 100f
        rail.leftHanded = store.bool("leftHanded")
        rail.layoutParams = FrameLayout.LayoutParams(dp(92), -1, if (rail.leftHanded) Gravity.START else Gravity.END)
        updateRailBounds()
        if (store.bool("hideStatus")) window.insetsController?.hide(WindowInsets.Type.statusBars()) else window.insetsController?.show(WindowInsets.Type.statusBars())
        window.insetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
    }
    private fun updateRailBounds() {
        val params = rail.layoutParams as FrameLayout.LayoutParams
        params.topMargin = screen.paddingTop
        params.leftMargin = screen.paddingLeft; params.rightMargin = screen.paddingRight
        homeFooter?.height?.takeIf { it > 0 }?.let { homeFooterHeight = it }
        // Keep the alphabet under the finger when a drag opens the app list.
        params.bottomMargin = screen.paddingBottom + homeFooterHeight.coerceAtLeast(dp(66))
        rail.layoutParams = params
    }
    private fun updateAlphabet() {
        val sections = catalog.sections()
        rail.activeLetters = sections
        rail.letters = AppListRules.railLetters(sections)
    }
    private fun refreshHomePreservingScroll() {
        if (widgets.isResizing) { notificationRefreshPending = true; return }
        val y = homeScroll?.scrollY ?: 0
        showHome(); homeScroll?.post { homeScroll?.scrollTo(0, y) }
    }

    fun showHome() {
        clearScreen("home"); rail.selected = "★"; notificationRefreshPending = false
        val homeLayout = vertical()
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        homeScroll = scroll
        val content = vertical()
        val (start, end) = contentMargins()
        val viewportDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val shortWindow = viewportDp < 500
        val widgetBox = vertical(); widgets.renderHome(widgetBox)
        val hasWidget = widgetBox.childCount > 0
        val topPadding = if (shortWindow) dp(24) else if (hasWidget) dp(56)
            else dp((viewportDp * .14f).coerceIn(36f, 126f))
        // Widgets have their own inset-safe width; only app/information rows reserve the alphabet.
        content.setPadding(0, topPadding, 0, dp(24))
        fun insetColumn() = vertical().apply { setPadding(start, 0, end, 0) }
        if (store.string("weatherCity").isNotBlank()) {
            val line = horizontal()
            line.addView(GlyphView(this, Glyph.SUN), LinearLayout.LayoutParams(dp(24), dp(32)))
            weatherText = text(weatherLabel(), 14f, secondary).apply { setPadding(dp(8), 0, 0, 0) }
            line.addView(weatherText)
            line.minimumHeight = dp(48); line.isFocusable = true; line.background = touchBackground()
            line.setOnClickListener { showWeather() }; content.addView(insetColumn().apply { addView(line) })
        }
        if (hasWidget) {
            if (content.isNotEmpty()) content.addView(gap(14))
            content.addView(widgetBox)
        }
        mediaView()?.let { content.addView(gap(12)); content.addView(insetColumn().apply { addView(it) }) }
        if (content.isNotEmpty()) content.addView(gap(if (shortWindow) 18 else 30))
        val favoriteColumn = insetColumn()
        val favorites = store.favorites.filter { catalog.find(it) != null || store.folder(it) != null }
        if (favorites.isEmpty()) {
            favoriteColumn.addView(text(if (loadingApps) "Загружаем приложения…" else "Ваши приложения, всегда под рукой", 20f))
            favoriteColumn.addView(gap(12)); favoriteColumn.addView(action("Выбрать избранные") { editFavorites() })
        }
        favorites.forEach { id ->
            val folder = store.folder(id)
            if (folder != null) favoriteColumn.addView(folderRow(folder)) else catalog.find(id)?.let { favoriteColumn.addView(appRow(it, true)) }
        }
        favoriteColumn.addView(gap(14))
        if (!store.bool("gesturesLearned")) {
            favoriteColumn.addView(text("Проведите по алфавиту, чтобы найти приложение", 12f, secondary).apply { setPadding(0, dp(8), dp(6), dp(8)) })
        }
        content.addView(favoriteColumn)
        scroll.addView(content); homeLayout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { content.performClick(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e1.y - e2.y > dp(80) && kotlin.math.abs(velocityY) > kotlin.math.abs(velocityX)) { showApps(true); return true }; return false
            }
            override fun onLongPress(e: MotionEvent) { showSettings() }
        })
        content.setOnTouchListener { _, event -> gesture.onTouchEvent(event) }
        content.isLongClickable = true; content.setOnLongClickListener { showSettings(); true }
        val footer = horizontal().apply { setPadding(dp(22), dp(7), dp(22), dp(7)); minimumHeight = dp(66) }
        homeFooter = footer
        footer.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateRailBounds()
        }
        footer.addView(iconButton(Glyph.SEARCH, "Все приложения и поиск") { showApps(true) })
        footer.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        footer.addView(iconButton(Glyph.WIDGETS, "Экран виджетов") { showWidgets(true) })
        footer.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        footer.addView(iconButton(Glyph.SETTINGS, "Настройки лаунчера") { showSettings() })
        homeLayout.addView(footer, LinearLayout.LayoutParams(-1, -2))
        screen.addView(homeLayout, FrameLayout.LayoutParams(-1, -1))
    }

    fun showWidgets(animate: Boolean = false) {
        hideKeyboard(); clearScreen("widgets")
        val column = vertical().apply { setPadding(dp(20), dp(12), dp(20), 0) }
        val header = horizontal()
        header.addView(iconButton(Glyph.BACK, "На главный экран") { returnFromWidgets() })
        header.addView(text("Виджеты", 26f), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(Glyph.PLUS, "Добавить виджет") { widgets.add() })
        column.addView(header)
        val content = vertical().apply { setPadding(dp(8), dp(12), dp(8), dp(24)) }
        widgets.renderPage(content)
        if (content.isEmpty()) {
            content.addView(gap(24))
            content.addView(text("Все нужные виджеты — здесь", 23f))
            content.addView(gap(12))
            content.addView(text("Добавьте виджеты. Удерживайте виджет, чтобы изменить размер или перенести его на главный экран.", 16f, secondary))
            content.addView(gap(16))
            content.addView(action("Добавить виджет") { widgets.add() })
        }
        widgetScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }
        column.addView(widgetScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(action("На главный экран") { returnFromWidgets() }, LinearLayout.LayoutParams(-1, -2))
        screen.addView(column, FrameLayout.LayoutParams(-1, -1))
        widgetScroll?.post { widgetScroll?.scrollTo(0, widgetScrollPosition) }
        if (animate) animatePage(column, -1)
    }

    private fun returnFromWidgets() {
        showHome()
        homeScroll?.post { homeScroll?.scrollTo(0, homeScrollPosition) }
        animatePage(screen.getChildAt(0), 1)
    }

    private fun animatePage(view: View, direction: Int) {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return
        view.translationX = direction * dp(48).toFloat()
        view.alpha = .6f
        view.animate().translationX(0f).alpha(1f).setDuration(180)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun appRow(app: AppEntry, favorite: Boolean): View = AppRow(favorite).apply { bind(app) }

    private inner class AppRow(private val favorite: Boolean) : LinearLayout(this@MainActivity) {
        private lateinit var app: AppEntry
        private val icon = ImageView(this@MainActivity)
        private val labels = vertical().apply { setPadding(0, dp(8), 0, dp(8)) }
        private val name = text("", if (store.bool("largeText")) 25f else 21f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val preview = text("", 13f, secondary).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val menu: View? = if (favorite) iconButton(Glyph.CHEVRON, "") { showAppPopup(app) } else null

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            tag = PageSwipeLayout.BLOCK_SWIPE
            minimumHeight = dp(if (store.bool("compact")) 54 else 62)
            background = touchBackground(); isFocusable = true
            if (!store.bool("hideIcons")) {
                icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                addView(icon, LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(16) })
            }
            labels.addView(name)
            if (favorite) {
                labels.addView(preview)
                preview.setOnClickListener { showAppPopup(app) }; preview.isFocusable = true
            }
            addView(labels, LayoutParams(0, -2, 1f))
            menu?.let { addView(it) }
            setOnClickListener { launch(app) }
            setOnLongClickListener { showAppMenu(app); true }
            var downX = 0f; var downY = 0f; var swiped = false
            // Unconsumed taps use LinearLayout's standard performClick.
            @Suppress("ClickableViewAccessibility")
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; swiped = false }
                    MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) > dp(32) && kotlin.math.abs(event.y - downY) < dp(28)) { swiped = true; view.parent.requestDisallowInterceptTouchEvent(true); view.isPressed = false }
                    MotionEvent.ACTION_UP -> if (swiped) { showAppPopup(app); return@setOnTouchListener true }
                }; swiped
            }
        }

        fun bind(entry: AppEntry) {
            app = entry
            name.text = catalog.label(app)
            if (!store.bool("hideIcons")) icon.setImageDrawable(app.icon.constantState?.newDrawable(resources)?.mutate() ?: app.icon)
            if (!favorite) return
            val notifications = if (store.bool("notifications", true)) NotificationHub.notifications(app.packageName, app.user) else emptyList()
            preview.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
            if (notifications.isNotEmpty()) {
                val notification = notifications.first().notification
                val value = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                preview.text = value.ifBlank { "${notifications.size} уведомл." }
                preview.contentDescription = "Уведомления ${catalog.label(app)}: $value"
            }
            menu?.apply {
                visibility = if (notifications.isNotEmpty() || store.popUp(app.id).isNotEmpty()) View.VISIBLE else View.GONE
                contentDescription = "Открыть меню ${catalog.label(app)}"
            }
        }
    }
    private fun folderRow(folder: AppFolder): View = horizontal().apply {
        tag = PageSwipeLayout.BLOCK_SWIPE
        minimumHeight = dp(62); background = touchBackground(); isFocusable = true
        addView(GlyphView(this@MainActivity, Glyph.FOLDER), LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(16) })
        addView(text(folder.name, 21f), LinearLayout.LayoutParams(0, -2, 1f))
        addView(iconButton(Glyph.CHEVRON, "Открыть ${folder.name}") { showFolder(folder) })
        setOnClickListener { showFolder(folder) }; setOnLongClickListener { editFolder(folder); true }
    }
    private fun launch(app: AppEntry) {
        try { catalog.launch(app); hideKeyboard(); currentDialog?.dismiss() }
        catch (_: Exception) { message("Не удалось открыть ${catalog.label(app)}. Возможно, приложение отключено."); refreshApps() }
    }

    private fun showApps(keyboard: Boolean) {
        clearScreen("apps"); store.set("gesturesLearned", true)
        val column = vertical().apply { isFocusableInTouchMode = true }
        val (start, end) = contentMargins(); column.setPadding(start - dp(8), dp(12), end, dp(12))
        val toolbar = horizontal()
        toolbar.addView(iconButton(Glyph.BACK, "На главный экран") { hideKeyboard(); showHome() })
        search = EditText(this).apply {
            hint = "Поиск приложений"; setHintTextColor(secondary); setTextColor(Color.WHITE); textSize = 19f
            background = null; isSingleLine = true; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populateApps(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, _, _ -> catalog.visible(text.toString()).firstOrNull()?.let { launch(it) }; true }
        }
        toolbar.addView(search, LinearLayout.LayoutParams(0, dp(56), 1f))
        toolbar.addView(iconButton(Glyph.CLOSE, "Очистить поиск") { search?.setText("") })
        column.addView(toolbar)
        val results = FrameLayout(this)
        appAdapter = AppListAdapter(catalog::label,
            { app, recycled -> ((recycled as? AppRow) ?: AppRow(false)).apply { bind(app) } },
            { section, recycled -> ((recycled as? TextView) ?: text("", 16f, secondary).apply { setPadding(0, dp(18), 0, dp(8)) }).apply { text = section } })
        allApps = ListView(this).apply {
            isVerticalScrollBarEnabled = false; divider = null; dividerHeight = 0
            setPadding(dp(8), dp(18), 0, dp(40)); clipToPadding = false
            adapter = appAdapter
        }
        emptyApps = vertical().apply { setPadding(dp(8), dp(18), 0, dp(40)) }
        results.addView(allApps, FrameLayout.LayoutParams(-1, -1))
        results.addView(emptyApps, FrameLayout.LayoutParams(-1, -2))
        allApps!!.emptyView = emptyApps
        column.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))
        screen.addView(column, FrameLayout.LayoutParams(-1, -1)); populateApps("")
        if (!keyboard) column.requestFocus()
        if (keyboard) search?.let { input ->
            input.requestFocus()
            keyboardRequest = Runnable {
                keyboardRequest = null
                if (page == "apps" && search === input) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }.also { handler.postDelayed(it, 180) }
        }
    }
    private fun populateApps(query: String) {
        val list = allApps ?: return
        val position = list.firstVisiblePosition
        val top = list.getChildAt(0)?.top ?: list.paddingTop
        val sameQuery = query == appQuery
        appQuery = query
        val apps = catalog.visible(query)
        appAdapter?.submit(apps, query.isBlank())
        emptyApps?.apply {
            removeAllViews()
            if (apps.isEmpty()) {
                addView(text(if (query.isBlank()) "Приложений пока нет" else "Ничего не найдено", 22f))
                addView(gap(12))
                addView(text(if (query.isBlank()) "Скрытые приложения доступны в настройках." else "Попробуйте другое название", 15f, secondary))
                if (query.isNotBlank()) addView(action("Искать в интернете") { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))) })
            }
        }
        list.setSelectionFromTop(if (sameQuery) position else 0, if (sameQuery) top - list.paddingTop else 0)
    }
    private fun jumpTo(letter: String) {
        if (search?.text?.isNotBlank() == true) search?.setText("")
        if (keyboardRequest != null || search?.hasFocus() == true) {
            keyboardRequest?.let(handler::removeCallbacks); keyboardRequest = null
            hideKeyboard(); search?.clearFocus()
        }
        val position = appAdapter?.sectionPosition(letter) ?: return
        allApps?.setSelectionFromTop(position, 0)
    }
    fun sheet(title: String, scrollContent: Boolean = true, build: (LinearLayout, Dialog) -> Unit): Dialog {
        currentDialog?.dismiss(); hideKeyboard()
        val dialog = Dialog(this)
        val body = vertical().apply { setPadding(dp(20), dp(8), dp(20), dp(24)); background = rounded(surface, dp(24).toFloat()) }
        val header = horizontal()
        header.addView(text(title, 23f), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(Glyph.CLOSE, "Закрыть") { dialog.dismiss() })
        body.addView(header); body.addView(gap(12))
        build(body, dialog)
        return presentSheet(dialog, body, scrollContent = scrollContent)
    }
    fun attachWidgetEditor(editor: View) {
        screenFocusBeforeWidgetEditor = screen.descendantFocusability
        screen.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        screen.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rail.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rail.isFocusable = false
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        editor.accessibilityPaneTitle = "Размер виджета"; editor.isFocusableInTouchMode = true; editor.requestFocus()
    }
    fun detachWidgetEditor(editor: View, refresh: Boolean) {
        root.removeView(editor)
        screen.descendantFocusability = screenFocusBeforeWidgetEditor
        screen.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        rail.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES; rail.isFocusable = true
        if (refresh && notificationRefreshPending && page == "home" && resumed) refreshHomePreservingScroll()
    }
    fun managementSheet(title: String, icon: android.graphics.drawable.Drawable?, build: (LinearLayout, Dialog) -> Unit): Dialog {
        currentDialog?.dismiss(); hideKeyboard()
        val dialog = Dialog(this, R.style.Theme_BetterLauncher_AppMenu).apply { setCanceledOnTouchOutside(true) }
        val foreground = getColor(R.color.app_menu_foreground)
        val body = vertical().apply {
            background = rounded(getColor(R.color.app_menu_surface), dp(24).toFloat()).apply {
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        val header = horizontal().apply { minimumHeight = dp(64); setPadding(0, dp(8), 0, dp(8)) }
        header.addView(ImageView(this).apply {
            setImageDrawable(icon); setPadding(dp(8), dp(8), dp(8), dp(8)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
        header.addView(text(title, 23f, foreground).apply { isAccessibilityHeading = true }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(header); body.addView(gap(4)); build(body, dialog)
        return presentSheet(dialog, body, appMenu = true)
    }
    fun managementAction(body: LinearLayout, dialog: Dialog, glyph: Glyph, label: String, action: () -> Unit) {
        body.addView(horizontal().apply {
            minimumHeight = dp(52); background = touchBackground(); isFocusable = true; contentDescription = label
            setPadding(0, dp(4), dp(8), dp(4)); setOnClickListener { dialog.dismiss(); action() }
            addView(GlyphView(this@MainActivity, glyph, getColor(R.color.app_menu_foreground)).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
            addView(text(label, 17f, getColor(R.color.app_menu_foreground)).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }
    private fun presentSheet(dialog: Dialog, body: LinearLayout, appMenu: Boolean = false, scrollContent: Boolean = true): Dialog {
        val scroll = if (scrollContent) object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val height = if (appMenu) MeasureSpec.makeMeasureSpec(
                    minOf(MeasureSpec.getSize(heightMeasureSpec), (resources.displayMetrics.heightPixels * .85f).toInt()),
                    MeasureSpec.AT_MOST
                ) else heightMeasureSpec
                super.onMeasure(widthMeasureSpec, height)
            }
        }.apply { isFillViewport = false; isVerticalScrollBarEnabled = false; isClickable = appMenu; addView(body) } else null
        // Keep the window fixed while the sheet is measured and animated inside it.
        // Resizing a bottom-gravity window can reach the compositor a frame after its content.
        val container = if (appMenu && scroll != null) FrameLayout(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(scroll, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            setOnClickListener { dialog.dismiss() }
        } else scroll ?: body
        val windowScale = if (appMenu) Settings.Global.getFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) else 0f
        val animatorScale = if (appMenu) Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) else 0f
        val animateMenu = appMenu && scroll != null && windowScale > 0f && animatorScale > 0f && android.animation.ValueAnimator.areAnimatorsEnabled()
        fun sheetHeight() = minOf(body.height + (scroll?.paddingTop ?: 0) + (scroll?.paddingBottom ?: 0), (resources.displayMetrics.heightPixels * .85f).toInt())
        if (animateMenu) scroll?.translationY = resources.displayMetrics.heightPixels.toFloat()
        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.3f); setGravity(Gravity.BOTTOM); setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (appMenu) {
                setLayout(-1, -1)
                if (!animateMenu) setWindowAnimations(0)
                WindowCompat.setDecorFitsSystemWindows(this, false)
                isNavigationBarContrastEnforced = false
                container.setOnApplyWindowInsetsListener { _, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    body.setPadding(dp(24) + bars.left, dp(12), dp(24) + bars.right, dp(16) + bars.bottom)
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    container.setPadding(0, bars.top, 0, (ime.bottom - bars.bottom).coerceAtLeast(0))
                    insets
                }
            }
        }
        dialog.setOnDismissListener {
            if (appMenu) scroll?.animate()?.cancel()
            if (currentDialog == dialog) {
                currentDialog = null
                if (notificationRefreshPending && page == "home" && resumed) refreshHomePreservingScroll()
            }
        }
        if (appMenu && scroll != null) {
            scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scroll.viewTreeObserver.removeOnPreDrawListener(this)
                    if (dialog.isShowing && animateMenu) {
                        // Insets and bottom anchoring are resolved in this layout, before the first visible frame.
                        scroll.translationY = scroll.height.toFloat()
                        scroll.animate().translationY(0f).setDuration((300f * windowScale / animatorScale).toLong())
                            .setInterpolator(AnimationUtils.loadInterpolator(this@MainActivity, R.interpolator.app_menu_arrive)).start()
                    }
                    return true
                }
            })
        }
        currentDialog = dialog; dialog.show()
        if (!appMenu) dialog.window?.setLayout(-1, if (scrollContent) -2 else (resources.displayMetrics.heightPixels * .85f).toInt())
        if (!appMenu && scrollContent) body.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val height = sheetHeight()
            if (height > 0 && dialog.window?.attributes?.height != height) dialog.window?.setLayout(-1, height)
        }
        return dialog
    }
    fun choose(title: String, options: List<String>, selected: Int = -1, action: (Int) -> Unit) {
        sheet(title) { body, dialog ->
            if (options.isEmpty()) body.addView(text("Нет доступных вариантов", 16f, secondary))
            options.forEachIndexed { index, option ->
                val row = horizontal(); row.addView(this.action(option) { dialog.dismiss(); action(index) }, LinearLayout.LayoutParams(0, -2, 1f))
                if (index == selected) row.addView(GlyphView(this, Glyph.CHECK), LinearLayout.LayoutParams(dp(48), dp(48)))
                body.addView(row)
            }
        }
    }
    private fun editText(title: String, current: String, hint: String = "Название", onSave: (String) -> Unit) {
        sheet(title) { body, dialog ->
            val input = EditText(this).apply { setText(current); setTextColor(Color.WHITE); setHintTextColor(secondary); this.hint = hint; isSingleLine = true; selectAll() }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(56)))
            body.addView(action("Сохранить") { onSave(input.text.toString().trim()); dialog.dismiss() },
                LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
            input.requestFocus(); input.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 200)
        }
    }
    private fun showAppMenu(app: AppEntry) {
        currentDialog?.dismiss(); hideKeyboard()
        val favorite = app.id in store.favorites
        val dialog = Dialog(this, R.style.Theme_BetterLauncher_AppMenu).apply { setCanceledOnTouchOutside(true) }
        val foreground = getColor(R.color.app_menu_foreground)
        val body = vertical().apply {
            setPadding(dp(24), dp(12), dp(24), dp(16))
            background = rounded(getColor(R.color.app_menu_surface), dp(24).toFloat()).apply {
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        val header = horizontal().apply { minimumHeight = dp(64); setPadding(0, dp(8), 0, dp(8)) }
        header.addView(ImageView(this).apply {
            setImageDrawable(app.icon); setPadding(dp(8), dp(8), dp(8), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
        val name = text(catalog.label(app), 23f, foreground).apply {
            isAccessibilityHeading = true; isFocusable = true; minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL; background = touchBackground()
            contentDescription = "Переименовать ${catalog.label(app)}"; tooltipText = "Переименовать"
        }
        header.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(header); body.addView(gap(4))
        fun menuAction(container: LinearLayout, glyph: Glyph, label: String, dismiss: Boolean = true, action: () -> Unit): LinearLayout {
            val row = horizontal().apply {
                minimumHeight = dp(52); background = touchBackground(); isFocusable = true
                setPadding(0, dp(4), dp(8), dp(4)); contentDescription = label
                setOnClickListener { if (dismiss) dialog.dismiss(); action() }
            }
            row.addView(GlyphView(this, glyph, foreground).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
            row.addView(text(label, 17f, foreground).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(0, -2, 1f))
            container.addView(row)
            return row
        }
        val commands = vertical()
        menuAction(commands, Glyph.STAR, if (favorite) "Убрать из избранного" else "В избранное") {
            store.favorites = if (favorite) store.favorites - app.id else store.favorites + app.id
            showHome(); message(if (favorite) "Убрано из избранного" else "Добавлено в избранное")
        }
        if (favorite) menuAction(commands, Glyph.REORDER, "Изменить порядок") { editFavorites() }
        menuAction(commands, Glyph.INFO, "О приложении") {
            runCatching { catalog.launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }.onFailure { message("Не удалось открыть настройки приложения") }
        }
        menuAction(commands, Glyph.NOTIFICATIONS, "Уведомления и быстрые действия") { showAppPopup(app) }
        menuAction(commands, Glyph.DELETE, "Удалить приложение") {
            if (app.user == Process.myUserHandle()) safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) else message("Удалите приложение через настройки рабочего профиля")
        }
        val extra = vertical().apply { visibility = View.GONE }
        lateinit var expand: LinearLayout
        expand = menuAction(commands, Glyph.DOWN, "Дополнительно", dismiss = false) {
            val expanded = extra.isGone
            extra.isGone = !expanded
            expand.stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
            expand.getChildAt(0).rotation = if (expanded) 180f else 0f
        }
        expand.stateDescription = "Свёрнуто"
        menuAction(extra, Glyph.FOLDER, "Приложения во всплывающем меню") { selectApps("Меню ${catalog.label(app)}", store.popUp(app.id)) { store.setPopUp(app.id, it); showHome() } }
        menuAction(extra, Glyph.HIDE, "Скрыть приложение") {
            store.hidden = store.hidden + app.id; store.favorites = store.favorites - app.id; showHome()
            message("Приложение скрыто", "Отменить") { store.hidden = store.hidden - app.id; if (favorite) store.favorites = store.favorites + app.id; showHome() }
        }
        commands.addView(extra); commands.addView(gap(16))
        commands.addView(View(this).apply { setBackgroundColor(foreground); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(8); marginEnd = dp(8) })
        commands.addView(gap(8))
        menuAction(commands, Glyph.SETTINGS, "Настройки Better Launcher") { showSettings() }
        body.addView(commands)
        val editor = vertical().apply { visibility = View.GONE }
        val input = EditText(this).apply {
            setTextColor(foreground); setHintTextColor(foreground); hint = "Название приложения"
            contentDescription = "Название приложения"; textSize = 17f; minHeight = dp(56)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.TRANSPARENT, dp(16).toFloat()).apply { setStroke(dp(2), foreground) }
            isSingleLine = true; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        fun saveName() {
            store.rename(app.id, input.text.toString())
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
            dialog.dismiss(); updateAlphabet(); refreshHomePreservingScroll()
        }
        editor.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(24) })
        editor.addView(text("Сохранить", 16f, foreground).apply {
            minHeight = dp(56); gravity = Gravity.END or Gravity.CENTER_VERTICAL; letterSpacing = .08f
            background = touchBackground(); isFocusable = true; setOnClickListener { saveName() }
        }, LinearLayout.LayoutParams(-1, -2))
        body.addView(editor)
        input.setOnEditorActionListener { _, action, event ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                saveName(); true
            } else false
        }
        name.setOnClickListener {
            val label = catalog.label(app)
            name.text = getString(R.string.rename_app_title, label); name.contentDescription = null; name.tooltipText = null
            name.isClickable = false; name.background = null
            commands.visibility = View.GONE; editor.visibility = View.VISIBLE
            input.setText(label); input.requestFocus(); input.selectAll()
            input.post {
                if (dialog.isShowing && input.hasFocus()) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        presentSheet(dialog, body, appMenu = true)
    }
    private fun showAppPopup(app: AppEntry) {
        sheet(catalog.label(app)) { body, dialog ->
            val notifications = NotificationHub.notifications(app.packageName, app.user)
            notifications.forEach { body.addView(notificationView(it)) }
            val shortcuts = catalog.shortcuts(app)
            shortcuts.filter { it.isEnabled }.forEach { shortcut ->
                body.addView(action(shortcut.shortLabel?.toString() ?: "Быстрое действие") {
                    runCatching { catalog.launcherApps.startShortcut(app.packageName, shortcut.id, null, null, app.user); dialog.dismiss() }.onFailure { message("Не удалось открыть быстрое действие") }
                })
            }
            store.popUp(app.id).mapNotNull(catalog::find).forEach { body.addView(appRow(it, false)) }
            if (notifications.isEmpty() && shortcuts.isEmpty() && store.popUp(app.id).isEmpty()) body.addView(text("Здесь появятся уведомления и быстрые действия приложения.", 16f, secondary))
            body.addView(gap(8)); body.addView(action("Добавить приложения") { dialog.dismiss(); selectApps("Меню ${catalog.label(app)}", store.popUp(app.id)) { store.setPopUp(app.id, it); showHome() } })
            body.addView(action("Открыть ${catalog.label(app)}") { launch(app) })
        }
    }
    private fun notificationView(sbn: StatusBarNotification): View {
        val n = sbn.notification
        val box = vertical().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(0xFF373430.toInt(), dp(16).toFloat()) }
        box.addView(text(n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(), 16f))
        box.addView(gap(6)); box.addView(text((n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(), 14f, secondary))
        box.isFocusable = true; box.setOnClickListener { runCatching { n.contentIntent?.send(); currentDialog?.dismiss() }.onFailure { message("Уведомление больше недоступно") } }
        val actions = horizontal()
        actions.addView(action("Скрыть") { NotificationHub.service?.cancelNotification(sbn.key); box.visibility = View.GONE })
        n.actions?.take(3)?.forEach { notificationAction ->
            actions.addView(action(notificationAction.title.toString()) {
                val inputs = notificationAction.remoteInputs?.filter { it.allowFreeFormInput }
                if (!inputs.isNullOrEmpty()) {
                    editText("Ответить", "", "Сообщение") { reply -> if (reply.isNotBlank()) {
                        val intent = Intent(); val bundle = Bundle(); inputs.forEach { bundle.putCharSequence(it.resultKey, reply) }
                        RemoteInput.addResultsToIntent(inputs.toTypedArray(), intent, bundle)
                        runCatching { notificationAction.actionIntent.send(this, 0, intent) }.onSuccess { message("Ответ отправлен") }.onFailure { message("Не удалось отправить ответ") }
                    } }
                } else runCatching { notificationAction.actionIntent.send() }.onFailure { message("Действие больше недоступно") }
            })
        }
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actions) }
        box.addView(horizontal)
        return vertical().apply { addView(box); addView(gap(8)) }
    }
    private fun showFolder(folder: AppFolder) {
        sheet(folder.name) { body, dialog ->
            val apps = folder.apps.mapNotNull(catalog::find)
            apps.forEach { body.addView(appRow(it, false)) }
            if (apps.isEmpty()) body.addView(text("В папке пока нет приложений", 16f, secondary))
            body.addView(action("Изменить папку") { dialog.dismiss(); editFolder(folder) })
        }
    }
    private fun editFolder(folder: AppFolder) {
        choose(folder.name, listOf("Выбрать приложения", "Переименовать", "Удалить папку", "Изменить порядок избранного")) { choice -> when (choice) {
            0 -> selectApps(folder.name, folder.apps) { store.saveFolder(folder.copy(apps = it)); showHome() }
            1 -> editText("Название папки", folder.name) { if (it.isNotBlank()) store.saveFolder(folder.copy(name = it)); showHome() }
            2 -> { store.removeFolder(folder.id); showHome() }
            3 -> editFavorites()
        } }
    }
    private fun selectApps(title: String, initial: List<String>, onSave: (List<String>) -> Unit) {
        val selected = initial.toMutableList()
        sheet(title) { body, dialog ->
            body.addView(text("Выберите приложения; порядок выбора сохраняется.", 14f, secondary))
            val input = EditText(this).apply { hint = "Поиск"; isSingleLine = true; setTextColor(Color.WHITE); setHintTextColor(secondary) }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
            val list = vertical()
            val scroll = ScrollView(this).apply { addView(list) }
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(320)))
            fun populate(query: String) {
                list.removeAllViews()
                catalog.apps.filter { AppListRules.matches(query, catalog.label(it), it.originalLabel) }.forEach { app ->
                    val check = CheckBox(this).apply {
                        text = catalog.label(app); textSize = 17f; setTextColor(Color.WHITE); minHeight = dp(52); isChecked = app.id in selected
                        buttonTintList = android.content.res.ColorStateList.valueOf(0xFFE9CBB6.toInt())
                        setOnCheckedChangeListener { _, checked -> if (checked && app.id !in selected) selected.add(app.id) else if (!checked) selected.remove(app.id) }
                    }; list.addView(check)
                }
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s.toString()) }
                override fun afterTextChanged(s: Editable?) {}
            })
            populate("")
            body.addView(action("Готово") { onSave(selected); dialog.dismiss() })
        }
    }
    private fun editFavorites() {
        sheet("Избранное", scrollContent = false) { body, dialog ->
            body.addView(text("Перетаскивайте строки за ≡ или используйте стрелки. Порядок сохраняется сразу.", 14f, secondary))
            body.addView(FavoritesOrderEditor(this) { showHome() }.view, LinearLayout.LayoutParams(-1, 0, 1f))
            body.addView(action("Выбрать приложения") { dialog.dismiss(); selectApps("Избранные приложения", store.favorites.filterNot { it.startsWith("folder:") }) { store.favorites = it + store.favorites.filter { id -> id.startsWith("folder:") }; showHome() } })
            body.addView(action("Создать папку") { dialog.dismiss(); editText("Новая папка", "", "Название папки") { name -> if (name.isNotBlank()) selectApps(name, emptyList()) { store.createFolder(name, it); showHome() } } })
        }
    }

    // The settings and optional integrations below use the same native surfaces as the launcher.
    fun showSettings() {
        clearScreen("settings"); hideKeyboard()
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; background = rounded(surface, 0f) }
        val body = vertical().apply { setPadding(dp(16), dp(10), dp(16), dp(30)) }
        val header = horizontal(); header.addView(iconButton(Glyph.BACK, "На главный экран") { showHome() })
        header.addView(text("Настройки", 26f), LinearLayout.LayoutParams(0, dp(64), 1f)); body.addView(header)
        body.addView(setting("Домашний экран", if (isDefaultHome()) "Better Launcher установлен по умолчанию" else "Выбрать Better Launcher по умолчанию") { requestHomeRole() })
        body.addView(setting("Избранные приложения", "Порядок, приложения и папки") { editFavorites() })
        body.addView(setting("Виджеты", "Экран слева · ${store.pageWidgets.size} виджетов") { showWidgets() })
        body.addView(setting("Виджет на главном экране", if (store.homeWidgetId == null) "Добавить один виджет" else "Заменить · прежний останется на экране виджетов") { widgets.add(toHome = true) })
        section(body, "Внешний вид")
        body.addView(setting("Обои", "Выбрать системные обои") { safeStart(Intent(Intent.ACTION_SET_WALLPAPER)) })
        body.addView(setting("Шрифт", when (store.string("font", "sans-serif")) { "sans-serif-light" -> "Лёгкий"; "sans-serif-condensed" -> "Компактный"; "serif" -> "С засечками"; else -> "Системный" }) {
            val names = listOf("sans-serif", "sans-serif-light", "sans-serif-condensed", "serif")
            choose("Шрифт", listOf("Системный", "Лёгкий", "Компактный", "С засечками"), names.indexOf(store.string("font", "sans-serif"))) { store.set("font", names[it]); showSettings() }
        })
        body.addView(wallpaperDimmingSetting())
        body.addView(toggle("Скрыть значки", "Оставить только названия приложений", "hideIcons"))
        body.addView(toggle("Крупные названия", "Увеличить текст приложений", "largeText"))
        body.addView(toggle("Компактный список", "Уменьшить расстояние между приложениями", "compact"))
        body.addView(toggle("Алфавит слева", "Для использования левой рукой", "leftHanded") { applyAppearance() })
        body.addView(toggle("Скрыть строку состояния", "Больше места на главном экране", "hideStatus") { applyAppearance() })
        section(body, "Информация")
        body.addView(setting("Погода", store.string("weatherCity").ifBlank { "Выбрать город · Open-Meteo" }) { chooseWeatherCity() })
        body.addView(setting("Календарь", if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) "Просмотр ближайших событий" else "Разрешить показ событий") { showAgenda() })
        section(body, "Уведомления")
        body.addView(setting("Доступ к уведомлениям", if (notificationAccess()) "Разрешён" else "Разрешить в настройках Android") { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        body.addView(toggle("Показывать уведомления", "Предпросмотр под избранными приложениями", "notifications", true))
        body.addView(setting("Скрытые приложения", "${store.hidden.size} скрыто · показать снова") {
            val hidden = catalog.apps.filter { it.id in store.hidden }
            selectApps("Скрытые приложения", hidden.map { it.id }) { store.hidden = it.toSet(); updateAlphabet(); showSettings() }
        })
        section(body, "Резервная копия")
        body.addView(setting("Экспорт настроек", "Сохранить избранное, папки и оформление") { safeStartForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "better-launcher.json"), 51) })
        body.addView(setting("Импорт настроек", "Восстановить из файла; текущие настройки заменятся") { safeStartForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), 52) })
        body.addView(gap(22)); body.addView(text("Better Launcher ${packageManager.getPackageInfo(packageName, 0).versionName}", 14f, secondary).apply { setPadding(dp(16), 0, 0, 0) })
        scroll.addView(body); screen.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }
    private fun wallpaperDimmingSetting(): View = vertical().apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        val percentage = text(getString(R.string.wallpaper_dimming_percentage, store.wallpaperDimming), 17f, secondary).apply {
            minWidth = dp(60); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val heading = horizontal().apply {
            addView(text("Затемнение обоев", 17f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(percentage)
        }
        addView(heading)
        addView(Slider(this@MainActivity).apply {
            valueFrom = 0f; valueTo = 100f; stepSize = 1f; value = store.wallpaperDimming.toFloat()
            setTickVisibilityMode(TickVisibilityMode.TICK_VISIBILITY_HIDDEN)
            contentDescription = "Затемнение обоев"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setLabelFormatter { getString(R.string.wallpaper_dimming_percentage, it.toInt()) }
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    store.wallpaperDimming = value.toInt()
                    percentage.text = getString(R.string.wallpaper_dimming_percentage, store.wallpaperDimming)
                    applyAppearance()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    private fun section(body: LinearLayout, label: String) {
        body.addView(gap(20)); body.addView(text(label, 14f, 0xFFE9CBB6.toInt()).apply { setPadding(dp(16), dp(8), 0, dp(8)) })
    }
    private fun setting(label: String, description: String, onClick: () -> Unit): View = horizontal().apply {
        minimumHeight = dp(76); setPadding(dp(16), dp(10), 0, dp(10)); background = touchBackground(); isFocusable = true
        val words = vertical(); words.addView(text(label, 17f)); words.addView(gap(5)); words.addView(text(description, 13f, secondary))
        addView(words, LinearLayout.LayoutParams(0, -2, 1f)); addView(GlyphView(this@MainActivity, Glyph.CHEVRON), LinearLayout.LayoutParams(dp(48), dp(48)))
        setOnClickListener { onClick() }
    }
    private fun toggle(label: String, description: String, key: String, default: Boolean = false, changed: () -> Unit = {}): View = horizontal().apply {
        minimumHeight = dp(76); setPadding(dp(16), dp(10), dp(8), dp(10))
        val words = vertical(); words.addView(text(label, 17f)); words.addView(gap(5)); words.addView(text(description, 13f, secondary))
        addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        val switch = MaterialSwitch(this@MainActivity).apply {
            isChecked = store.bool(key, default); minHeight = dp(48); minWidth = dp(56); contentDescription = label
            setOnCheckedChangeListener { _, enabled -> store.set(key, enabled); changed() }
        }; addView(switch); setOnClickListener { switch.isChecked = !switch.isChecked }
    }
    private fun isDefaultHome() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    private fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) safeStartForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 31)
        else safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
    }
    private fun notificationAccess(): Boolean = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.split(':')?.contains(ComponentName(this, LauncherNotificationService::class.java).flattenToString()) == true

    private fun showAgenda() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            sheet("События календаря") { body, dialog ->
                body.addView(text("Разрешите чтение календаря, чтобы видеть ближайшие события. Данные остаются на устройстве.", 16f, secondary))
                body.addView(action("Разрешить") { dialog.dismiss(); requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 61) })
                body.addView(action("Открыть календарь") { dialog.dismiss(); openCalendar() })
            }; return
        }
        executor.execute {
            data class Event(val id: Long, val title: String, val time: Long, val allDay: Boolean)
            val result = runCatching {
                val now = System.currentTimeMillis()
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 7 * 86_400_000L) }.build()
                contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
                    buildList { while (cursor.moveToNext() && size < 12) add(Event(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2), cursor.getInt(3) == 1)) }
                }.orEmpty()
            }
            handler.post { if (!isDestroyed) sheet("Ближайшие события") { body, dialog ->
                val events = result.getOrDefault(emptyList())
                if (result.isFailure) body.addView(text("Не удалось прочитать календарь", 16f, secondary))
                else if (events.isEmpty()) body.addView(text("На ближайшие 7 дней событий нет", 16f, secondary))
                events.forEach { event ->
                    body.addView(setting(event.title.ifBlank { "Без названия" }, SimpleDateFormat(if (event.allDay) "EEE, d MMM · весь день" else "EEE, d MMM · HH:mm", Locale("ru")).format(Date(event.time))) { dialog.dismiss(); safeStart(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))) })
                }
                body.addView(action("Открыть календарь") { dialog.dismiss(); openCalendar() })
            } }
        }
    }
    private fun openCalendar() = safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")))
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 61) { if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showAgenda() else message("Доступ не разрешён. Календарь можно открыть нажатием на дату.") }
    }

    private fun mediaView(): View? {
        if (!notificationAccess()) return null
        val sessions = runCatching { getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, LauncherNotificationService::class.java)) }.getOrDefault(emptyList())
        val session = sessions.firstOrNull { it.metadata != null && it.playbackState?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING) } ?: return null
        val row = horizontal().apply { background = rounded(0x38352E2A, dp(16).toFloat()); setPadding(dp(12), dp(4), dp(4), dp(4)) }
        val titles = vertical()
        titles.addView(text(session.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(), 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        titles.addView(text(session.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(), 12f, secondary).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        row.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(iconButton(if (session.playbackState?.state == PlaybackState.STATE_PLAYING) Glyph.PAUSE else Glyph.PLAY, "Воспроизвести или приостановить") {
            if (session.playbackState?.state == PlaybackState.STATE_PLAYING) session.transportControls.pause() else session.transportControls.play()
            handler.postDelayed({ if (page == "home") refreshHomePreservingScroll() }, 300)
        })
        row.addView(iconButton(Glyph.NEXT, "Следующий трек") { session.transportControls.skipToNext(); handler.postDelayed({ if (page == "home") refreshHomePreservingScroll() }, 300) })
        return row
    }
    private fun weatherLabel(): String = weather?.let { "${it.temperature}° · ${it.description}" }
        ?: if (weatherError) "Погода недоступна · повторить" else "Загружаем погоду…"
    private fun refreshWeather(force: Boolean = false) {
        val cityName = store.string("weatherCity")
        if (cityName.isBlank() || weatherLoading || (!force && System.currentTimeMillis() - weatherUpdated < 30 * 60_000)) return
        val lat = store.string("weatherLat").toDoubleOrNull() ?: return
        val lon = store.string("weatherLon").toDoubleOrNull() ?: return
        weatherLoading = true
        executor.execute {
            val result = runCatching { WeatherRepository.current(WeatherCity(cityName, lat, lon)) }
            handler.post {
                weatherLoading = false; weatherError = result.isFailure; weatherUpdated = System.currentTimeMillis()
                if (store.string("weatherCity") == cityName) weather = result.getOrNull()
                weatherText?.text = weatherLabel()
            }
        }
    }
    private fun chooseWeatherCity() {
        sheet("Погода") { body, dialog ->
            body.addView(text("Введите город. Поиск и прогноз загружаются из Open-Meteo; доступ к геолокации не нужен.", 15f, secondary))
            val input = EditText(this).apply { hint = "Например, Тбилиси"; isSingleLine = true; setTextColor(Color.WHITE); setHintTextColor(secondary) }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(56)))
            val resultBox = vertical(); body.addView(resultBox)
            val button = action("Найти город") {
                val query = input.text.toString().trim()
                if (query.length < 2) { input.error = "Введите не меньше двух букв"; return@action }
                resultBox.removeAllViews(); resultBox.addView(text("Ищем…", 15f, secondary))
                executor.execute {
                    val result = runCatching { WeatherRepository.cities(query) }
                    handler.post { if (dialog.isShowing) {
                        resultBox.removeAllViews()
                        if (result.isFailure) resultBox.addView(text("Не удалось загрузить города. Проверьте интернет и повторите.", 14f, secondary))
                        else if (result.getOrDefault(emptyList()).isEmpty()) resultBox.addView(text("Город не найден. Попробуйте другое название.", 14f, secondary))
                        result.getOrDefault(emptyList()).forEach { city -> resultBox.addView(action(city.name) {
                            store.set("weatherCity", city.name); store.set("weatherLat", city.latitude.toString()); store.set("weatherLon", city.longitude.toString())
                            weather = null; weatherError = false; weatherUpdated = 0; refreshWeather(true); dialog.dismiss(); showHome()
                        }) }
                    } }
                }
            }; body.addView(button)
            if (store.string("weatherCity").isNotBlank()) body.addView(action("Убрать погоду") { store.set("weatherCity", ""); weather = null; dialog.dismiss(); showHome() })
            body.addView(action("О сервисе Open-Meteo") { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) })
        }
    }
    private fun showWeather() {
        refreshWeather(weatherError)
        sheet(store.string("weatherCity", "Погода")) { body, dialog ->
            body.addView(text(weatherLabel(), 28f)); body.addView(gap(10))
            body.addView(text("Прогнозная текущая температура · Open-Meteo", 13f, secondary))
            body.addView(action("Обновить") { refreshWeather(true); dialog.dismiss() })
            body.addView(action("Выбрать другой город") { dialog.dismiss(); chooseWeatherCity() })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (widgets.onResult(requestCode, resultCode)) return
        if (requestCode == 31) { showSettings(); return }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 51 || requestCode == 52) executor.execute {
            val result = runCatching {
                if (requestCode == 51) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.export()) } ?: error("Файл недоступен")
                else {
                    val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val builder = StringBuilder(); val buffer = CharArray(4096)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(builder.length + count <= 1_000_000) { "Файл слишком большой" }
                            builder.append(buffer, 0, count)
                        }
                        builder.toString()
                    } ?: error("Файл недоступен")
                    store.import(content)
                }
            }
            handler.post { if (!isDestroyed) {
                if (result.isSuccess) { applyAppearance(); updateAlphabet(); showHome(); message(if (requestCode == 51) "Настройки сохранены" else "Настройки восстановлены") }
                else message("Не удалось ${if (requestCode == 51) "сохранить" else "восстановить"} настройки: ${result.exceptionOrNull()?.message.orEmpty()}")
            } }
        }
    }
    fun safeStart(intent: Intent) { runCatching { startActivity(intent) }.onFailure { message("На устройстве нет приложения для этого действия") } }
    fun safeStartForResult(intent: Intent, request: Int, failure: () -> Unit = {}) {
        runCatching { startActivityForResult(intent, request) }.onFailure { failure(); message("На устройстве нет приложения для этого действия") }
    }
    private fun hideKeyboard() { currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) } }
    fun message(value: String, undo: String? = null, onUndo: () -> Unit = {}) {
        val bar = horizontal().apply { setPadding(dp(16), dp(8), dp(8), dp(8)); background = rounded(surface, dp(12).toFloat()); elevation = dp(6).toFloat() }
        bar.addView(text(value, 14f), LinearLayout.LayoutParams(0, -2, 1f))
        if (undo != null) bar.addView(action(undo) { onUndo(); root.removeView(bar) })
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(84) })
        handler.postDelayed({ root.removeView(bar) }, if (undo != null) 6000 else 4000)
    }
}
