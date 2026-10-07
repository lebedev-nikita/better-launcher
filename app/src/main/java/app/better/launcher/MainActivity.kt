package app.better.launcher

import android.Manifest
import android.app.*
import android.app.role.RoleManager
import android.content.*
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
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
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.slider.TickVisibilityMode
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
    private var currentMessage: TransientMessageView? = null
    private lateinit var wallpaperScrim: WallpaperScrimView
    private lateinit var screen: PageSwipeLayout
    private lateinit var rail: AlphabetRail
    private var page = "home"
    private var orderingFavorites = false
    private var orderingWidgets = false
    private var inlineOrder: InlineOrderEditor? = null
    private var allApps: ListView? = null
    private var appAdapter: AppListAdapter? = null
    private var appQuery = ""
    private var emptyApps: LinearLayout? = null
    private var keyboardRequest: Runnable? = null
    private var homeScroll: ScrollView? = null
    private var homeFooter: View? = null
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
        screen.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (page == "home") {
                    listOf(R.id.home_search to getString(R.string.all_apps_search), R.id.home_widgets to getString(R.string.widget_page),
                        R.id.home_settings to getString(R.string.launcher_settings)).forEach { (id, label) ->
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(id, label))
                    }
                }
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (page == "home") when (action) {
                    R.id.home_search -> { showApps(true); return true }
                    R.id.home_widgets -> { showWidgets(true); return true }
                    R.id.home_settings -> { showSettings(); return true }
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        screen.onSwipe = { if (page == "home" || page == "apps") showWidgets(true) else if (page == "widgets") returnFromWidgets() }
        screen.onTouchFinished = {
            if (notificationRefreshPending && page == "home" && resumed) refreshHomePreservingScroll()
        }
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
            currentMessage?.takeIf { it.parent === root }?.let { updateMessageInsets(it, insets) }
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (page == "home" && currentDialog == null && !widgets.isResizing) when (keyCode) {
            KeyEvent.KEYCODE_SEARCH -> { showApps(true); return true }
            KeyEvent.KEYCODE_MENU -> { showSettings(); return true }
            KeyEvent.KEYCODE_F10 -> if (event.isShiftPressed) { showSettings(); return true }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); currentDialog?.dismiss(); hideKeyboard(); orderingFavorites = false; orderingWidgets = false; showHome() }
    override fun onStart() { super.onStart(); widgets.start() }
    override fun onStop() { currentMessage?.dismiss(); widgets.stop(); super.onStop() }
    override fun onResume() {
        super.onResume(); resumed = true
        NotificationHub.onChanged = {
            if (page == "home" && resumed) {
                if (currentDialog == null && !widgets.isResizing) refreshHomePreservingScroll() else notificationRefreshPending = true
            }
        }
        handler.post(weatherTicker); applyAppearance(); refreshApps(); refreshWeather()
    }
    override fun onPause() { inlineOrder?.cancelDrag(); resumed = false; handler.removeCallbacks(weatherTicker); NotificationHub.onChanged = null; super.onPause() }
    override fun onDestroy() {
        catalog.launcherApps.unregisterCallback(packageCallback)
        handler.removeCallbacksAndMessages(null); executor.shutdownNow(); currentDialog?.dismiss(); super.onDestroy()
    }
    private fun handleBack() {
        if (widgets.finishResize()) return
        currentDialog?.let { it.dismiss(); return }
        if (orderingFavorites) { orderingFavorites = false; showHome(); return }
        if (orderingWidgets) { orderingWidgets = false; showWidgets(); return }
        if (page == "widgets") returnFromWidgets()
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
                if (result.isFailure) message(getString(R.string.catalog_error))
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
        inlineOrder?.cancelDrag(); inlineOrder = null
        widgets.finishResize(false)
        if (newPage != "home") orderingFavorites = false
        if (newPage != "widgets") orderingWidgets = false
        if (page == "home" && newPage == "widgets") homeScrollPosition = homeScroll?.scrollY ?: 0
        widgetScroll?.let { widgetScrollPosition = it.scrollY }
        keyboardRequest?.let(handler::removeCallbacks); keyboardRequest = null
        page = newPage; screen.removeAllViews(); allApps = null; appAdapter = null; emptyApps = null; appQuery = ""; homeScroll = null; homeFooter = null; widgetScroll = null; search = null; weatherText = null
        // Paint behind transparent system bars as well as the inset-safe content.
        screen.setBackgroundColor(if (newPage == "settings") surface else Color.TRANSPARENT)
        screen.direction = when (newPage) { "home", "apps" -> 1; "widgets" -> -1; else -> 0 }
        screen.contentDescription = if (newPage == "home") getString(R.string.home_screen) else null
        screen.accessibilityPaneTitle = when (newPage) { "home" -> getString(R.string.home_screen); "widgets" -> getString(R.string.widgets); "apps" -> getString(R.string.all_apps); else -> getString(R.string.settings) }
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
        rail.contentTopInset = if (page == "apps") dp(68) else 0
        params.leftMargin = screen.paddingLeft; params.rightMargin = screen.paddingRight
        // Normal home and apps share the same anchor; only ordering reserves Done.
        params.bottomMargin = screen.paddingBottom + (homeFooter?.height ?: 0)
        rail.layoutParams = params
    }
    private fun updateAlphabet() {
        val sections = catalog.sections()
        rail.activeLetters = sections
        rail.letters = AppListRules.railLetters(sections)
    }
    private fun refreshHomePreservingScroll() {
        if (screen.isTouching || currentDialog != null || widgets.isResizing || orderingFavorites) { notificationRefreshPending = true; return }
        val y = homeScroll?.scrollY ?: 0
        showHome(); homeScroll?.post { homeScroll?.scrollTo(0, y) }
    }

    fun showHome() {
        clearScreen("home"); rail.selected = "★"; notificationRefreshPending = false
        updateAlphabet()
        val homeLayout = vertical()
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; isFillViewport = true }
        homeScroll = scroll
        val content = vertical()
        val (start, end) = contentMargins()
        val widgetBox = vertical(); widgets.renderHome(widgetBox)
        val hasWidget = widgetBox.childCount > 0
        // Widgets have their own inset-safe width; only app/information rows reserve the alphabet.
        content.setPadding(0, 0, 0, dp(24))
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
            if (content.isNotEmpty()) content.addView(gap(store.widgetSpacing))
            content.addView(widgetBox)
        }
        mediaView()?.let {
            if (content.isNotEmpty()) content.addView(gap(store.widgetSpacing))
            content.addView(insetColumn().apply { addView(it) })
        }
        if (content.isNotEmpty()) content.addView(gap(store.widgetSpacing))
        val favoriteColumn = insetColumn()
        val favorites = store.favorites.filter { catalog.find(it) != null || store.folder(it) != null }
        if (favorites.isEmpty()) {
            favoriteColumn.addView(text(if (loadingApps) getString(R.string.loading_apps) else getString(R.string.apps_at_hand), 20f))
            favoriteColumn.addView(gap(12)); favoriteColumn.addView(action(getString(R.string.choose_favorites)) { editFavorites() })
        }
        val favoriteItems = vertical()
        val order = if (orderingFavorites) InlineOrderEditor(this, favoriteItems, scroll,
            readOrder = { store.favorites }, saveOrder = { store.favorites = it },
            dragBackgroundColor = Color.TRANSPARENT).also { inlineOrder = it } else null
        favorites.forEach { id ->
            val folder = store.folder(id)
            val app = catalog.find(id)
            val row = if (folder != null) folderRow(folder) else app?.let { appRow(it, true) }
            if (row != null) {
                if (order != null) order.add(id, folder?.name ?: catalog.label(app!!), row,
                    handleRow = if (row is AppRow) row.orderHandleRow else row as LinearLayout)
                else favoriteItems.addView(row)
            }
        }
        favoriteColumn.addView(favoriteItems)
        favoriteColumn.addView(gap(14))
        if (!store.bool("gesturesLearned")) {
            favoriteColumn.addView(text(getString(R.string.alphabet_hint), 12f, secondary).apply { setPadding(0, dp(8), dp(6), dp(8)) })
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
        if (orderingFavorites) {
            val footer = horizontal().apply { setPadding(dp(22), dp(7), dp(22), dp(7)); minimumHeight = dp(66) }
            homeFooter = footer
            footer.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) updateRailBounds()
            }
            footer.addView(action(getString(R.string.done)) { orderingFavorites = false; showHome() })
            homeLayout.addView(footer, LinearLayout.LayoutParams(-1, -2))
        }
        screen.addView(homeLayout, FrameLayout.LayoutParams(-1, -1))
    }

    fun showWidgets(animate: Boolean = false) {
        hideKeyboard(); clearScreen("widgets")
        val content = vertical().apply {
            setPadding(dp(28), 0, dp(28), 0)
            isFocusableInTouchMode = true
            setOnLongClickListener {
                widgets.add()
                true
            }
            setOnKeyListener { view, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_F10 && event.isShiftPressed)) {
                    view.performLongClick()
                } else false
            }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        getString(R.string.add_widget)))
                }
            }
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
        }
        inlineOrder = widgets.renderPage(content, if (orderingWidgets) scroll else null)
        if (content.isEmpty()) {
            content.addView(text(getString(R.string.empty_widgets_hint), 16f, secondary))
        }
        scroll.addView(content); widgetScroll = scroll
        screen.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        scroll.post { scroll.scrollTo(0, widgetScrollPosition) }
        if (animate) animatePage(scroll, -1)
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
        private val labels = vertical().apply {
            val padding = dp(if (store.bool("compact")) 3 else 7)
            setPadding(0, padding, 0, padding)
        }
        private val name = text("", if (store.bool("largeText")) 25f else 21f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val preview = text("", 13f, secondary).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val orderHandleRow: LinearLayout? = if (favorite && orderingFavorites) horizontal() else null
        private val menu: View? = if (favorite && !orderingFavorites) iconButton(Glyph.CHEVRON, "") { showAppPopup(app) } else null

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            if (orderingFavorites) tag = PageSwipeLayout.BLOCK_SWIPE
            minimumHeight = dp(if (store.bool("compact")) 54 else 62)
            background = touchBackground(); isFocusable = true
            if (!store.bool("hideIcons")) {
                icon.setPadding(dp(5), dp(5), dp(5), dp(5))
                icon.background = touchBackground()
                icon.isFocusable = true
                icon.setOnClickListener { launch(app) }
                icon.setOnLongClickListener { showAppPopup(app); true }
                addView(icon, LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(6) })
            }
            if (orderHandleRow != null) {
                labels.setPadding(0, 0, 0, 0)
                orderHandleRow.addView(name, LayoutParams(0, -2, 1f))
                labels.addView(orderHandleRow)
            } else labels.addView(name)
            if (favorite) {
                labels.addView(preview)
                preview.setOnClickListener { showAppPopup(app) }; preview.isFocusable = true
            }
            addView(labels, LayoutParams(0, -2, 1f))
            menu?.let { addView(it) }
            setOnClickListener { launch(app) }
            setOnLongClickListener { showAppMenu(app); true }
            name.minimumHeight = dp(48)
            name.gravity = Gravity.CENTER_VERTICAL
            name.isFocusable = true
            name.setOnClickListener { launch(app) }
            name.setOnLongClickListener { showAppMenu(app); true }
            // The popup remains reachable when icons are hidden, including with TalkBack.
            name.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        R.id.app_quick_actions, getString(R.string.quick_actions_notifications)))
                    info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, getString(R.string.manage_app)))
                }
                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == R.id.app_quick_actions) { showAppPopup(app); return true }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        }

        fun bind(entry: AppEntry) {
            app = entry
            name.text = catalog.label(app)
            icon.contentDescription = getString(R.string.app_quick_actions_hint, catalog.label(app))
            if (!store.bool("hideIcons")) icon.setImageDrawable(app.icon.constantState?.newDrawable(resources)?.mutate() ?: app.icon)
            if (!favorite) return
            val notifications = if (store.bool("notifications", true)) NotificationHub.notifications(app.packageName, app.user) else emptyList()
            preview.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
            if (notifications.isNotEmpty()) {
                val notification = notifications.first().notification
                val value = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                preview.text = value.ifBlank { resources.getQuantityString(R.plurals.notification_count, notifications.size, notifications.size) }
                preview.contentDescription = getString(R.string.app_notifications, catalog.label(app), value)
            }
            menu?.apply {
                visibility = if (notifications.isNotEmpty() || store.popUp(app.id).isNotEmpty()) View.VISIBLE else View.GONE
                contentDescription = getString(R.string.open_menu, catalog.label(app))
            }
        }
    }
    private fun folderRow(folder: AppFolder): View = horizontal().apply {
        if (orderingFavorites) tag = PageSwipeLayout.BLOCK_SWIPE
        minimumHeight = dp(62); background = touchBackground(); isFocusable = true
        addView(GlyphView(this@MainActivity, Glyph.FOLDER), LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(16) })
        addView(text(folder.name, 21f), LinearLayout.LayoutParams(0, -2, 1f))
        if (!orderingFavorites) addView(iconButton(Glyph.CHEVRON, getString(R.string.open_folder, folder.name)) { showFolder(folder) })
        setOnClickListener { showFolder(folder) }; setOnLongClickListener { editFolder(folder); true }
    }
    private fun launch(app: AppEntry) {
        try { catalog.launch(app); hideKeyboard(); currentDialog?.dismiss() }
        catch (_: Exception) { message(getString(R.string.app_launch_error, catalog.label(app))); refreshApps() }
    }

    private fun showApps(keyboard: Boolean) {
        clearScreen("apps"); store.set("gesturesLearned", true)
        val column = vertical().apply { isFocusableInTouchMode = true }
        column.setPadding(0, dp(12), 0, dp(12))
        val toolbar = horizontal().apply { setPadding(dp(20), 0, dp(20), 0) }
        toolbar.addView(iconButton(Glyph.BACK, getString(R.string.back_home)) { hideKeyboard(); showHome() })
        search = EditText(this).apply {
            tag = PageSwipeLayout.BLOCK_SWIPE
            hint = getString(R.string.search_apps); setHintTextColor(secondary); setTextColor(Color.WHITE); textSize = 19f
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
        toolbar.addView(iconButton(Glyph.CLOSE, getString(R.string.clear_search)) { search?.setText("") })
        column.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        val (start, end) = contentMargins()
        val results = FrameLayout(this).apply { setPadding(start - dp(8), 0, end, 0) }
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
                addView(text(if (query.isBlank()) getString(R.string.no_apps) else getString(R.string.nothing_found), 22f))
                addView(gap(12))
                addView(text(if (query.isBlank()) getString(R.string.hidden_apps_search_hint) else getString(R.string.try_another_name), 15f, secondary))
                if (query.isNotBlank()) addView(action(getString(R.string.search_web)) { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))) })
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
    val menuForeground: Int get() = getColor(R.color.app_menu_foreground)
    fun menuText(value: String, size: Float = 16f): TextView = text(value, size, menuForeground)
    fun menuIconButton(glyph: Glyph, label: String, action: () -> Unit): View = iconButton(glyph, label, action).apply {
        (this as GlyphView).color = menuForeground
    }
    private fun menuBody() = vertical().apply {
        isClickable = true
        background = rounded(getColor(R.color.app_menu_surface), dp(24).toFloat()).apply {
            cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
        }
    }
    private fun menuHeader(title: String, icon: android.graphics.drawable.Drawable? = null): LinearLayout = horizontal().apply {
        minimumHeight = dp(64); setPadding(0, dp(8), 0, dp(8))
        if (icon != null) addView(ImageView(this@MainActivity).apply {
            setImageDrawable(icon); setPadding(dp(8), dp(8), dp(8), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
        addView(menuText(title, 23f).apply { isAccessibilityHeading = true }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    fun menuAction(label: String, glyph: Glyph = Glyph.CHEVRON, action: () -> Unit): LinearLayout = horizontal().apply {
        minimumHeight = dp(52); background = touchBackground(); isFocusable = true; contentDescription = label
        setPadding(0, dp(4), dp(8), dp(4)); setOnClickListener { action() }
        addView(GlyphView(this@MainActivity, glyph, menuForeground).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
        addView(menuText(label, 17f).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(0, -2, 1f))
    }
    private fun setMenuActionIcon(row: LinearLayout, icon: android.graphics.drawable.Drawable) {
        val slot = row.getChildAt(0).layoutParams
        row.removeViewAt(0)
        row.addView(ImageView(this).apply {
            setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 0, slot)
    }
    fun sheet(title: String, scrollContent: Boolean = true, icon: android.graphics.drawable.Drawable? = null, showHeader: Boolean = true, build: (LinearLayout, Dialog) -> Unit): Dialog {
        currentDialog?.dismiss(); hideKeyboard()
        val dialog = Dialog(this, R.style.Theme_BetterLauncher_AppMenu).apply { setCanceledOnTouchOutside(true) }
        val body = menuBody().apply { accessibilityPaneTitle = title }
        if (showHeader) {
            val header = menuHeader(title, icon)
            header.addView(menuIconButton(Glyph.CLOSE, getString(R.string.close)) { dialog.dismiss() })
            body.addView(header); body.addView(gap(4))
        }
        build(body, dialog)
        return presentSheet(dialog, body, scrollContent)
    }
    fun attachWidgetEditor(editor: View) {
        screenFocusBeforeWidgetEditor = screen.descendantFocusability
        screen.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        screen.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rail.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rail.isFocusable = false
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        editor.accessibilityPaneTitle = getString(R.string.widget_size); editor.isFocusableInTouchMode = true; editor.requestFocus()
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
        val body = menuBody()
        body.addView(menuHeader(title, icon)); body.addView(gap(4)); build(body, dialog)
        return presentSheet(dialog, body)
    }
    fun managementAction(body: LinearLayout, dialog: Dialog, glyph: Glyph, label: String, action: () -> Unit) {
        body.addView(menuAction(label, glyph) { dialog.dismiss(); action() })
    }
    private fun presentSheet(dialog: Dialog, body: LinearLayout, scrollContent: Boolean = true): Dialog {
        val scroll = if (scrollContent) object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    minOf(MeasureSpec.getSize(heightMeasureSpec), (resources.displayMetrics.heightPixels * .85f).toInt()),
                    MeasureSpec.AT_MOST
                ))
            }
        }.apply { isFillViewport = false; isVerticalScrollBarEnabled = false; isClickable = true; addView(body) } else null
        val panel = scroll ?: body
        // A fixed window keeps the bottom anchor stable during layout, IME changes and arrival.
        val container = FrameLayout(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(panel, FrameLayout.LayoutParams(-1, if (scrollContent) -2 else (resources.displayMetrics.heightPixels * .85f).toInt(), Gravity.BOTTOM))
            setOnClickListener { dialog.dismiss() }
        }
        val windowScale = Settings.Global.getFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        val animatorScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val animateMenu = windowScale > 0f && animatorScale > 0f && android.animation.ValueAnimator.areAnimatorsEnabled()
        if (animateMenu) panel.translationY = resources.displayMetrics.heightPixels.toFloat()
        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.3f); setGravity(Gravity.BOTTOM); setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(-1, -1)
            if (!animateMenu) setWindowAnimations(0)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            isNavigationBarContrastEnforced = false
            container.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                body.setPadding(dp(24) + bars.left, dp(12), dp(24) + bars.right, dp(16) + bars.bottom)
                val keyboard = (ime.bottom - bars.bottom).coerceAtLeast(0)
                container.setPadding(0, bars.top, 0, keyboard)
                if (!scrollContent) panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                    height = minOf((resources.displayMetrics.heightPixels * .85f).toInt(),
                        (resources.displayMetrics.heightPixels - bars.top - keyboard).coerceAtLeast(0))
                }
                insets
            }
        }
        dialog.setOnDismissListener {
            panel.animate().cancel()
            if (currentDialog == dialog) {
                currentDialog = null
                if (notificationRefreshPending && page == "home" && resumed) refreshHomePreservingScroll()
            }
        }
        panel.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                panel.viewTreeObserver.removeOnPreDrawListener(this)
                if (dialog.isShowing && animateMenu) {
                    panel.translationY = panel.height.toFloat()
                    panel.animate().translationY(0f).setDuration((300f * windowScale / animatorScale).toLong())
                        .setInterpolator(AnimationUtils.loadInterpolator(this@MainActivity, R.interpolator.app_menu_arrive)).start()
                }
                return true
            }
        })
        currentDialog = dialog; dialog.show()
        return dialog
    }
    fun choose(title: String, options: List<String>, selected: Int = -1, action: (Int) -> Unit) {
        sheet(title) { body, dialog ->
            if (options.isEmpty()) body.addView(menuText(getString(R.string.no_options), 16f))
            options.forEachIndexed { index, option ->
                body.addView(menuAction(option, if (index == selected) Glyph.CHECK else Glyph.CHEVRON) { dialog.dismiss(); action(index) }.apply {
                    if (index == selected) stateDescription = getString(R.string.selected)
                })
            }
        }
    }
    private fun editText(title: String, current: String, hint: String = getString(R.string.name), onSave: (String) -> Unit) {
        sheet(title) { body, dialog ->
            val input = EditText(this).apply { setText(current); setTextColor(menuForeground); setHintTextColor(menuForeground); this.hint = hint; isSingleLine = true; selectAll() }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(56)))
            body.addView(menuAction(getString(R.string.save), Glyph.CHECK) { onSave(input.text.toString().trim()); dialog.dismiss() },
                LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
            input.requestFocus(); input.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 200)
        }
    }
    private fun showAppMenu(app: AppEntry) {
        currentDialog?.dismiss(); hideKeyboard()
        val favorite = app.id in store.favorites
        val hidden = app.id in store.hidden
        val dialog = Dialog(this, R.style.Theme_BetterLauncher_AppMenu).apply { setCanceledOnTouchOutside(true) }
        val foreground = getColor(R.color.app_menu_foreground)
        val body = menuBody()
        val header = menuHeader(catalog.label(app), app.icon)
        val name = (header.getChildAt(header.childCount - 1) as TextView).apply {
            isFocusable = true; minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL; background = touchBackground()
            contentDescription = getString(R.string.rename_app_title, catalog.label(app)); tooltipText = getString(R.string.rename)
        }
        body.addView(header); body.addView(gap(4))
        fun menuAction(container: LinearLayout, glyph: Glyph, label: String, action: () -> Unit) {
            container.addView(this.menuAction(label, glyph) { dialog.dismiss(); action() })
        }
        val commands = vertical()
        if (hidden) menuAction(commands, Glyph.SHOW, getString(R.string.show_again)) {
            store.hidden = store.hidden - app.id
            updateAlphabet()
            if (page == "apps") populateApps(search?.text?.toString().orEmpty()) else showHome()
            message(getString(R.string.app_shown))
        }
        menuAction(commands, Glyph.STAR, if (favorite) getString(R.string.remove_favorite) else getString(R.string.add_favorite)) {
            store.favorites = if (favorite) store.favorites - app.id else store.favorites + app.id
            showHome(); message(if (favorite) getString(R.string.favorite_removed) else getString(R.string.favorite_added))
        }
        if (favorite) menuAction(commands, Glyph.REORDER, getString(R.string.reorder)) { startFavoritesOrder() }
        if (!hidden) menuAction(commands, Glyph.HIDE, getString(R.string.hide_app)) {
            store.hidden = store.hidden + app.id; store.favorites = store.favorites - app.id; showHome()
            message(getString(R.string.app_hidden), getString(R.string.undo)) { store.hidden = store.hidden - app.id; if (favorite) store.favorites = store.favorites + app.id; showHome() }
        }
        menuAction(commands, Glyph.DELETE, getString(R.string.uninstall_app)) {
            if (app.user == Process.myUserHandle()) safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) else message(getString(R.string.uninstall_work_app))
        }
        menuAction(commands, Glyph.INFO, getString(R.string.app_info)) {
            runCatching { catalog.launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }.onFailure { message(getString(R.string.app_settings_error)) }
        }
        commands.addView(gap(16))
        commands.addView(View(this).apply { setBackgroundColor(foreground); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(8); marginEnd = dp(8) })
        commands.addView(gap(8))
        menuAction(commands, Glyph.SETTINGS, getString(R.string.better_launcher_settings)) { showSettings() }
        body.addView(commands)
        val editor = vertical().apply { visibility = View.GONE }
        val input = EditText(this).apply {
            setTextColor(foreground); setHintTextColor(foreground); hint = getString(R.string.app_name)
            contentDescription = getString(R.string.app_name); textSize = 17f; minHeight = dp(56)
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
        editor.addView(text(getString(R.string.save), 16f, foreground).apply {
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
        presentSheet(dialog, body)
    }
    private fun setMenuAppColors(view: View) {
        when (view) {
            is TextView -> view.setTextColor(menuForeground)
            is GlyphView -> view.color = menuForeground
            is ViewGroup -> for (index in 0 until view.childCount) setMenuAppColors(view.getChildAt(index))
        }
    }
    private fun showAppPopup(app: AppEntry) {
        sheet(catalog.label(app), icon = app.icon) { body, dialog ->
            val notifications = NotificationHub.notifications(app.packageName, app.user)
            notifications.forEach { body.addView(notificationView(it)) }
            val shortcuts = catalog.shortcuts(app).filter { it.isEnabled }
            val attachedApps = store.popUp(app.id).mapNotNull(catalog::find)
            val shortcutRows = mutableListOf<Pair<ShortcutInfo, LinearLayout>>()
            var lastHidden: ShortcutInfo? = null
            lateinit var undoAction: LinearLayout
            lateinit var restoreAction: LinearLayout
            lateinit var emptyText: TextView
            fun updateVisibility() {
                val hidden = store.hiddenShortcuts(app.id)
                shortcutRows.forEach { (shortcut, row) -> (row.parent as View).isGone = shortcut.id in hidden }
                undoAction.isGone = lastHidden == null
                lastHidden?.let { shortcut ->
                    val label = getString(R.string.undo_hide_named, shortcut.shortLabel ?: getString(R.string.quick_action))
                    undoAction.contentDescription = label; (undoAction.getChildAt(1) as TextView).text = label
                }
                restoreAction.isGone = hidden.isEmpty()
                emptyText.isGone = notifications.isNotEmpty() || attachedApps.isNotEmpty() || shortcuts.any { it.id !in hidden }
                emptyText.text = if (shortcuts.isNotEmpty()) getString(R.string.all_quick_actions_hidden)
                    else getString(R.string.quick_actions_empty)
            }
            fun loadVisibleIcons() {
                val pending = shortcutRows.filter { (_, row) -> (row.parent as View).isVisible && row.getChildAt(0) is GlyphView }
                if (pending.isNotEmpty()) executor.execute {
                    for ((shortcut, row) in pending) {
                        if (Thread.currentThread().isInterrupted) break
                        val icon = catalog.shortcutIcon(shortcut) ?: continue
                        handler.post {
                            if (!isDestroyed && currentDialog === dialog && dialog.isShowing &&
                                (row.parent as View).isVisible) setMenuActionIcon(row, icon)
                        }
                    }
                }
            }
            shortcuts.forEach { shortcut ->
                val label = shortcut.shortLabel?.toString() ?: getString(R.string.quick_action)
                val row = menuAction(label) {
                    runCatching { catalog.launcherApps.startShortcut(app.packageName, shortcut.id, null, null, app.user); dialog.dismiss() }.onFailure { message(getString(R.string.shortcut_launch_error)) }
                }
                val wrapper = horizontal().apply { addView(row, LinearLayout.LayoutParams(0, -2, 1f)) }
                lateinit var hideButton: View
                hideButton = menuIconButton(Glyph.CLOSE, getString(R.string.hide_shortcut, label)) {
                    val focused = hideButton.hasFocus()
                    store.setHiddenShortcuts(app.id, store.hiddenShortcuts(app.id) + shortcut.id)
                    lastHidden = shortcut; updateVisibility()
                    if (focused) undoAction.requestFocus()
                }
                wrapper.addView(hideButton); body.addView(wrapper); shortcutRows.add(shortcut to row)
            }
            attachedApps.forEach { body.addView(appRow(it, false).apply { setMenuAppColors(this) }) }
            emptyText = menuText("", 16f); body.addView(emptyText)
            body.addView(gap(8))
            undoAction = menuAction(getString(R.string.undo_hide), Glyph.BACK) {
                lastHidden?.let { shortcut ->
                    val focused = undoAction.hasFocus()
                    store.setHiddenShortcuts(app.id, store.hiddenShortcuts(app.id) - shortcut.id)
                    lastHidden = null; updateVisibility(); loadVisibleIcons()
                    if (focused) shortcutRows.firstOrNull { it.first.id == shortcut.id }?.second?.requestFocus()
                }
            }
            restoreAction = menuAction(getString(R.string.restore_shortcuts), Glyph.SHOW) {
                val focused = restoreAction.hasFocus()
                store.setHiddenShortcuts(app.id, emptySet())
                lastHidden = null; updateVisibility(); loadVisibleIcons()
                if (focused) shortcutRows.firstOrNull()?.second?.requestFocus()
            }
            body.addView(undoAction); body.addView(restoreAction)
            body.addView(menuAction(getString(R.string.add_apps), Glyph.PLUS) { dialog.dismiss(); selectApps(getString(R.string.app_menu_title, catalog.label(app)), store.popUp(app.id)) { store.setPopUp(app.id, it); showHome() } })
            updateVisibility(); loadVisibleIcons()
        }
    }
    private fun notificationView(sbn: StatusBarNotification): View {
        val n = sbn.notification
        val box = vertical().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(getColor(R.color.app_menu_container), dp(16).toFloat()) }
        box.addView(menuText(n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(), 16f))
        box.addView(gap(6)); box.addView(menuText((n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(), 14f))
        box.isFocusable = true; box.setOnClickListener { runCatching { n.contentIntent?.send(); currentDialog?.dismiss() }.onFailure { message(getString(R.string.notification_unavailable)) } }
        val actions = horizontal()
        actions.addView(menuAction(getString(R.string.hide), Glyph.HIDE) { NotificationHub.service?.cancelNotification(sbn.key); box.visibility = View.GONE })
        n.actions?.take(3)?.forEach { notificationAction ->
            actions.addView(menuAction(notificationAction.title.toString()) {
                val inputs = notificationAction.remoteInputs?.filter { it.allowFreeFormInput }
                if (!inputs.isNullOrEmpty()) {
                    editText(getString(R.string.reply), "", getString(R.string.message)) { reply -> if (reply.isNotBlank()) {
                        val intent = Intent(); val bundle = Bundle(); inputs.forEach { bundle.putCharSequence(it.resultKey, reply) }
                        RemoteInput.addResultsToIntent(inputs.toTypedArray(), intent, bundle)
                        runCatching { notificationAction.actionIntent.send(this, 0, intent) }.onSuccess { message(getString(R.string.reply_sent)) }.onFailure { message(getString(R.string.reply_error)) }
                    } }
                } else runCatching { notificationAction.actionIntent.send() }.onFailure { message(getString(R.string.action_unavailable)) }
            })
        }
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(actions) }
        box.addView(horizontal)
        return vertical().apply { addView(box); addView(gap(8)) }
    }
    private fun showFolder(folder: AppFolder) {
        sheet(folder.name) { body, dialog ->
            val apps = folder.apps.mapNotNull(catalog::find)
            apps.forEach { body.addView(appRow(it, false).apply { setMenuAppColors(this) }) }
            if (apps.isEmpty()) body.addView(menuText(getString(R.string.folder_empty), 16f))
            body.addView(menuAction(getString(R.string.edit_folder), Glyph.EDIT) { dialog.dismiss(); editFolder(folder) })
        }
    }
    private fun editFolder(folder: AppFolder) {
        managementSheet(folder.name, null) { body, dialog ->
            managementAction(body, dialog, Glyph.PLUS, getString(R.string.choose_apps)) {
                selectApps(folder.name, folder.apps) { store.saveFolder(folder.copy(apps = it)); showHome() }
            }
            managementAction(body, dialog, Glyph.EDIT, getString(R.string.rename)) {
                editText(getString(R.string.folder_name), folder.name) { if (it.isNotBlank()) store.saveFolder(folder.copy(name = it)); showHome() }
            }
            managementAction(body, dialog, Glyph.DELETE, getString(R.string.delete_folder)) { store.removeFolder(folder.id); showHome() }
            managementAction(body, dialog, Glyph.REORDER, getString(R.string.reorder_favorites)) { editFavorites() }
        }
    }
    private fun selectApps(title: String, initial: List<String>, onSave: (List<String>) -> Unit) {
        val selected = initial.toMutableList()
        sheet(title) { body, dialog ->
            body.addView(menuText(getString(R.string.choose_apps_hint), 14f))
            val input = EditText(this).apply { hint = getString(R.string.search); isSingleLine = true; setTextColor(menuForeground); setHintTextColor(menuForeground) }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
            val list = vertical()
            val scroll = ScrollView(this).apply { addView(list) }
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(320)))
            fun populate(query: String) {
                list.removeAllViews()
                catalog.apps.filter { AppListRules.matches(query, catalog.label(it), it.originalLabel) }.forEach { app ->
                    val check = CheckBox(this).apply {
                        text = catalog.label(app); textSize = 17f; setTextColor(menuForeground); minHeight = dp(52); isChecked = app.id in selected
                        buttonTintList = android.content.res.ColorStateList.valueOf(menuForeground)
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
            body.addView(menuAction(getString(R.string.done), Glyph.CHECK) { onSave(selected); dialog.dismiss() })
        }
    }
    private fun startFavoritesOrder() {
        currentDialog?.dismiss(); orderingFavorites = true; showHome()
    }
    fun startWidgetOrder() {
        currentDialog?.dismiss(); orderingWidgets = true; showWidgets()
    }
    private fun editFavorites() {
        sheet(getString(R.string.favorites)) { body, dialog ->
            body.addView(menuAction(getString(R.string.reorder_on_home), Glyph.REORDER) { dialog.dismiss(); startFavoritesOrder() })
            body.addView(menuAction(getString(R.string.choose_apps), Glyph.PLUS) { dialog.dismiss(); selectApps(getString(R.string.favorite_apps), store.favorites.filterNot { it.startsWith("folder:") }) { store.favorites = it + store.favorites.filter { id -> id.startsWith("folder:") }; showHome() } })
            body.addView(menuAction(getString(R.string.create_folder), Glyph.FOLDER) { dialog.dismiss(); editText(getString(R.string.new_folder), "", getString(R.string.folder_name)) { name -> if (name.isNotBlank()) selectApps(name, emptyList()) { store.createFolder(name, it); showHome() } } })
        }
    }

    // The settings and optional integrations below use the same native surfaces as the launcher.
    fun showSettings() {
        clearScreen("settings"); hideKeyboard()
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val body = vertical().apply { setPadding(dp(16), dp(10), dp(16), dp(30)) }
        val header = horizontal(); header.addView(menuIconButton(Glyph.BACK, getString(R.string.back_home)) { showHome() })
        header.addView(menuText(getString(R.string.settings), 26f), LinearLayout.LayoutParams(0, dp(64), 1f)); body.addView(header)
        body.addView(setting(getString(R.string.default_home), if (isDefaultHome()) getString(R.string.home_default_set) else getString(R.string.home_default_choose)) { requestHomeRole() })
        body.addView(setting(getString(R.string.favorite_apps), getString(R.string.favorites_settings_hint)) { editFavorites() })
        body.addView(setting(getString(R.string.widgets), resources.getQuantityString(R.plurals.widget_page_count, store.pageWidgets.size, store.pageWidgets.size)) { showWidgets() })
        body.addView(setting(getString(R.string.home_widget), if (store.homeWidgetId == null) getString(R.string.add_one_widget) else getString(R.string.replace_home_widget_hint)) { widgets.add(toHome = true) })
        section(body, getString(R.string.appearance))
        body.addView(setting(getString(R.string.wallpaper), getString(R.string.choose_wallpaper)) { safeStart(Intent(Intent.ACTION_SET_WALLPAPER)) })
        body.addView(setting(getString(R.string.font), when (store.string("font", "sans-serif")) { "sans-serif-light" -> getString(R.string.font_light); "sans-serif-condensed" -> getString(R.string.font_condensed); "serif" -> getString(R.string.font_serif); else -> getString(R.string.font_system) }) {
            val names = listOf("sans-serif", "sans-serif-light", "sans-serif-condensed", "serif")
            choose(getString(R.string.font), listOf(getString(R.string.font_system), getString(R.string.font_light), getString(R.string.font_condensed), getString(R.string.font_serif)), names.indexOf(store.string("font", "sans-serif"))) { store.set("font", names[it]); showSettings() }
        })
        body.addView(wallpaperDimmingSetting())
        body.addView(widgetSpacingSetting())
        body.addView(toggle(getString(R.string.hide_icons), getString(R.string.hide_icons_hint), "hideIcons"))
        body.addView(toggle(getString(R.string.large_labels), getString(R.string.large_labels_hint), "largeText"))
        body.addView(toggle(getString(R.string.compact_list), getString(R.string.compact_list_hint), "compact"))
        body.addView(toggle(getString(R.string.alphabet_left), getString(R.string.alphabet_left_hint), "leftHanded") { applyAppearance() })
        body.addView(toggle(getString(R.string.hide_status_bar), getString(R.string.hide_status_bar_hint), "hideStatus") { applyAppearance() })
        section(body, getString(R.string.information))
        body.addView(setting(getString(R.string.weather), store.string("weatherCity").ifBlank { getString(R.string.choose_city_hint) }) { chooseWeatherCity() })
        body.addView(setting(getString(R.string.calendar), if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) getString(R.string.view_upcoming_events) else getString(R.string.allow_events)) { showAgenda() })
        section(body, getString(R.string.notifications))
        body.addView(setting(getString(R.string.notification_access), if (notificationAccess()) getString(R.string.allowed) else getString(R.string.allow_android_settings)) { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        body.addView(toggle(getString(R.string.show_notifications), getString(R.string.show_notifications_hint), "notifications", true))
        body.addView(setting(getString(R.string.hidden_apps), resources.getQuantityString(R.plurals.hidden_app_count, store.hidden.size, store.hidden.size)) {
            val hidden = catalog.apps.filter { it.id in store.hidden }
            selectApps(getString(R.string.hidden_apps), hidden.map { it.id }) { store.hidden = it.toSet(); updateAlphabet(); showSettings() }
        })
        section(body, getString(R.string.backup))
        body.addView(setting(getString(R.string.export_settings), getString(R.string.export_settings_hint)) { safeStartForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "better-launcher.json"), 51) })
        body.addView(setting(getString(R.string.import_settings), getString(R.string.import_settings_hint)) { safeStartForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), 52) })
        body.addView(gap(22)); body.addView(menuText("Better Launcher ${packageManager.getPackageInfo(packageName, 0).versionName}", 14f).apply { setPadding(dp(16), 0, 0, 0) })
        scroll.addView(body); screen.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }
    private fun wallpaperDimmingSetting(): View = vertical().apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        val percentage = menuText(getString(R.string.wallpaper_dimming_percentage, store.wallpaperDimming), 17f).apply {
            minWidth = dp(60); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val heading = horizontal().apply {
            addView(menuText(getString(R.string.wallpaper_dimming), 17f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(percentage)
        }
        addView(heading)
        addView(Slider(this@MainActivity).apply {
            valueFrom = 0f; valueTo = 100f; stepSize = 1f; value = store.wallpaperDimming.toFloat()
            setTickVisibilityMode(TickVisibilityMode.TICK_VISIBILITY_HIDDEN)
            contentDescription = getString(R.string.wallpaper_dimming)
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
    private fun widgetSpacingSetting(): View = vertical().apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        val dimension = menuText(getString(R.string.widget_dimension_value, store.widgetSpacing), 17f).apply {
            minWidth = dp(60); gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        addView(horizontal().apply {
            addView(menuText(getString(R.string.widget_spacing), 17f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(dimension)
        })
        addView(menuText(getString(R.string.widget_spacing_hint), 13f).apply { setPadding(0, dp(5), 0, 0) })
        val preview = WidgetSpacingPreview(this@MainActivity, dimension.typeface).apply { spacingDp = store.widgetSpacing }
        addView(menuText(getString(R.string.preview), 13f).apply { setPadding(0, dp(12), 0, 0) })
        addView(preview, LinearLayout.LayoutParams(-1, -2))
        addView(Slider(this@MainActivity).apply {
            valueFrom = 0f; valueTo = 32f; stepSize = 1f; value = store.widgetSpacing.toFloat()
            setTickVisibilityMode(TickVisibilityMode.TICK_VISIBILITY_HIDDEN)
            contentDescription = getString(R.string.widget_spacing)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setLabelFormatter { getString(R.string.widget_dimension_value, it.toInt()) }
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    store.widgetSpacing = value.toInt()
                    dimension.text = getString(R.string.widget_dimension_value, store.widgetSpacing)
                    preview.spacingDp = store.widgetSpacing
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    private fun section(body: LinearLayout, label: String) {
        body.addView(gap(20)); body.addView(menuText(label, 14f).apply { setPadding(dp(16), dp(8), 0, dp(8)) })
    }
    private fun setting(label: String, description: String, onClick: () -> Unit): View = horizontal().apply {
        minimumHeight = dp(76); setPadding(dp(16), dp(10), 0, dp(10)); background = touchBackground(); isFocusable = true
        val words = vertical(); words.addView(menuText(label, 17f)); words.addView(gap(5)); words.addView(menuText(description, 13f))
        addView(words, LinearLayout.LayoutParams(0, -2, 1f)); addView(GlyphView(this@MainActivity, Glyph.CHEVRON, menuForeground), LinearLayout.LayoutParams(dp(48), dp(48)))
        setOnClickListener { onClick() }
    }
    private fun toggle(label: String, description: String, key: String, default: Boolean = false, changed: () -> Unit = {}): View = horizontal().apply {
        minimumHeight = dp(76); setPadding(dp(16), dp(10), dp(8), dp(10))
        val words = vertical(); words.addView(menuText(label, 17f)); words.addView(gap(5)); words.addView(menuText(description, 13f))
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
            sheet(getString(R.string.calendar_events)) { body, dialog ->
                body.addView(menuText(getString(R.string.calendar_permission_hint), 16f))
                body.addView(menuAction(getString(R.string.allow)) { dialog.dismiss(); requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 61) })
                body.addView(menuAction(getString(R.string.open_calendar), Glyph.CHEVRON) { dialog.dismiss(); openCalendar() })
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
            handler.post { if (!isDestroyed) sheet(getString(R.string.upcoming_events)) { body, dialog ->
                val events = result.getOrDefault(emptyList())
                if (result.isFailure) body.addView(menuText(getString(R.string.calendar_error), 16f))
                else if (events.isEmpty()) body.addView(menuText(getString(R.string.no_upcoming_events), 16f))
                events.forEach { event ->
                    body.addView(setting(event.title.ifBlank { getString(R.string.untitled) }, formatCalendarEventTime(event.time, event.allDay)) { dialog.dismiss(); safeStart(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))) })
                }
                body.addView(menuAction(getString(R.string.open_calendar), Glyph.CHEVRON) { dialog.dismiss(); openCalendar() })
            } }
        }
    }
    private fun openCalendar() = safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")))
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 61) { if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showAgenda() else message(getString(R.string.calendar_permission_denied)) }
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
        row.addView(iconButton(if (session.playbackState?.state == PlaybackState.STATE_PLAYING) Glyph.PAUSE else Glyph.PLAY, getString(R.string.play_pause)) {
            if (session.playbackState?.state == PlaybackState.STATE_PLAYING) session.transportControls.pause() else session.transportControls.play()
            handler.postDelayed({ if (page == "home") refreshHomePreservingScroll() }, 300)
        })
        row.addView(iconButton(Glyph.NEXT, getString(R.string.next_track)) { session.transportControls.skipToNext(); handler.postDelayed({ if (page == "home") refreshHomePreservingScroll() }, 300) })
        return row
    }
    private fun weatherLabel(): String = weather?.let { getString(R.string.weather_temperature, it.temperature, getString(it.descriptionRes)) }
        ?: if (weatherError) getString(R.string.weather_retry) else getString(R.string.loading_weather)
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
        sheet(getString(R.string.weather)) { body, dialog ->
            body.addView(menuText(getString(R.string.weather_city_hint), 15f))
            val input = EditText(this).apply { hint = getString(R.string.city_example); isSingleLine = true; setTextColor(menuForeground); setHintTextColor(menuForeground) }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(56)))
            val resultBox = vertical(); body.addView(resultBox)
            val button = menuAction(getString(R.string.find_city), Glyph.SEARCH) {
                val query = input.text.toString().trim()
                if (query.length < 2) { input.error = getString(R.string.city_query_short); return@menuAction }
                resultBox.removeAllViews(); resultBox.addView(menuText(getString(R.string.searching), 15f))
                executor.execute {
                    val result = runCatching { WeatherRepository.cities(query, resources.configuration.locales[0].language) }
                    handler.post { if (dialog.isShowing) {
                        resultBox.removeAllViews()
                        if (result.isFailure) resultBox.addView(menuText(getString(R.string.city_search_error), 14f))
                        else if (result.getOrDefault(emptyList()).isEmpty()) resultBox.addView(menuText(getString(R.string.city_not_found), 14f))
                        result.getOrDefault(emptyList()).forEach { city -> resultBox.addView(menuAction(city.name) {
                            store.set("weatherCity", city.name); store.set("weatherLat", city.latitude.toString()); store.set("weatherLon", city.longitude.toString())
                            weather = null; weatherError = false; weatherUpdated = 0; refreshWeather(true); dialog.dismiss(); showHome()
                        }) }
                    } }
                }
            }; body.addView(button)
            if (store.string("weatherCity").isNotBlank()) body.addView(menuAction(getString(R.string.remove_weather), Glyph.DELETE) { store.set("weatherCity", ""); weather = null; dialog.dismiss(); showHome() })
            body.addView(menuAction(getString(R.string.about_open_meteo), Glyph.INFO) { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))) })
        }
    }
    private fun showWeather() {
        refreshWeather(weatherError)
        sheet(store.string("weatherCity", getString(R.string.weather))) { body, dialog ->
            body.addView(menuText(weatherLabel(), 28f)); body.addView(gap(10))
            body.addView(menuText(getString(R.string.current_temperature_hint), 13f))
            body.addView(menuAction(getString(R.string.refresh), Glyph.SUN) { refreshWeather(true); dialog.dismiss() })
            body.addView(menuAction(getString(R.string.choose_another_city), Glyph.SEARCH) { dialog.dismiss(); chooseWeatherCity() })
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
                if (requestCode == 51) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.export()) } ?: error(getString(R.string.file_unavailable))
                else {
                    val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val builder = StringBuilder(); val buffer = CharArray(4096)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(builder.length + count <= 1_000_000) { getString(R.string.file_too_large) }
                            builder.append(buffer, 0, count)
                        }
                        builder.toString()
                    } ?: error(getString(R.string.file_unavailable))
                    store.import(content)
                }
            }
            handler.post { if (!isDestroyed) {
                if (result.isSuccess) { applyAppearance(); updateAlphabet(); showHome(); message(if (requestCode == 51) getString(R.string.settings_saved) else getString(R.string.settings_restored)) }
                else message(getString(if (requestCode == 51) R.string.settings_export_error else R.string.settings_import_error, result.exceptionOrNull()?.message.orEmpty()))
            } }
        }
    }
    fun safeStart(intent: Intent) { runCatching { startActivity(intent) }.onFailure { message(getString(R.string.no_handler)) } }
    fun safeStartForResult(intent: Intent, request: Int, failure: () -> Unit = {}) {
        runCatching { startActivityForResult(intent, request) }.onFailure { failure(); message(getString(R.string.no_handler)) }
    }
    private fun hideKeyboard() { currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) } }
    fun message(value: String, undo: String? = null, onUndo: () -> Unit = {}) {
        currentMessage?.dismiss()
        val bar = TransientMessageView(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48)
            setPadding(dp(16), dp(8), dp(8), dp(8)); background = rounded(surface, dp(12).toFloat()); elevation = dp(6).toFloat()
        }
        bar.addView(text(value, 14f, secondary), LinearLayout.LayoutParams(0, -2, 1f))
        if (undo != null) bar.addView(action(undo) { bar.dismiss(); onUndo() }.apply { setTextColor(secondary) })
        bar.setOnApplyWindowInsetsListener { _, insets -> updateMessageInsets(bar, insets); insets }
        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { if (currentMessage === bar) currentMessage = null }
        })
        currentMessage = bar
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(84) })
        root.rootWindowInsets?.let { updateMessageInsets(bar, it) }
        // Choose the active window after the action has finished closing any menus.
        root.post {
            if (currentMessage !== bar) return@post
            val host = currentDialog?.takeIf { it.isShowing }?.window?.decorView as? FrameLayout ?: root
            if (host !== root) {
                val params = FrameLayout.LayoutParams(bar.layoutParams as FrameLayout.LayoutParams)
                root.removeView(bar)
                currentMessage = bar
                host.addView(bar, params)
                host.rootWindowInsets?.let { updateMessageInsets(bar, it) }
                bar.requestApplyInsets()
            }
        }
    }
    private fun updateMessageInsets(bar: View, insets: WindowInsets) {
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val ime = insets.getInsets(WindowInsets.Type.ime())
        bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = dp(16) + bars.left; rightMargin = dp(16) + bars.right
            bottomMargin = dp(84) + maxOf(bars.bottom, ime.bottom)
        }
    }
}
