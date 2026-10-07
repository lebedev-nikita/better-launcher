package app.better.launcher

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Reorders the real content views in their existing scrolling surface. */
class InlineOrderEditor(
    private val activity: MainActivity,
    private val container: LinearLayout,
    private val scroll: ScrollView,
    private val readOrder: () -> List<String>,
    private val saveOrder: (List<String>) -> Unit,
    private val dragBackgroundColor: Int = activity.surface,
) {
    private data class Item(val id: String, val label: String, val row: LinearLayout, val handle: View)
    private val items = mutableListOf<Item>()
    private var dragged: Item? = null
    private var destination = -1
    private var pointerY = 0f
    private var offsetY = 0f
    private val location = IntArray(2)
    private val scrollStep = object : Runnable {
        override fun run() {
            if (dragged == null) return
            scroll.getLocationOnScreen(location)
            val edge = activity.dp(48)
            val delta = when {
                pointerY < location[1] + edge -> -activity.dp(8)
                pointerY > location[1] + scroll.height - edge -> activity.dp(8)
                else -> 0
            }
            if (delta != 0) { scroll.scrollBy(0, delta); preview() }
            scroll.postOnAnimation(this)
        }
    }

    @SuppressLint("ClickableViewAccessibility") // The handle provides keyboard and named accessibility moves.
    fun add(id: String, label: String, content: View, spacing: Int = 0, handleRow: LinearLayout? = null) {
        val row = activity.vertical().apply {
            tag = PageSwipeLayout.BLOCK_SWIPE
            setPadding(0, 0, 0, activity.dp(spacing))
            addView(content)
        }
        val controls = handleRow ?: activity.horizontal().apply {
            addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(this)
        }
        val handle = activity.iconButton(Glyph.REORDER, activity.getString(R.string.drag_named_item, label)) {
            activity.message(activity.getString(R.string.drag_hint))
        }
        val item = Item(id, label, row, handle)
        controls.addView(handle)
        items.add(item); container.addView(row, LinearLayout.LayoutParams(-1, -2))
        handle.setOnKeyListener { _, key, event ->
            if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) false
            else {
                if (event.action == KeyEvent.ACTION_DOWN) move(item, items.indexOf(item) + if (key == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                true
            }
        }
        handle.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val index = items.indexOf(item)
                if (index > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.reorder_up, activity.getString(R.string.move_named_up, label)))
                if (index in 0 until items.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.reorder_down, activity.getString(R.string.move_named_down, label)))
            }
            override fun performAccessibilityAction(host: View, action: Int, arguments: Bundle?): Boolean = when (action) {
                R.id.reorder_up -> move(item, items.indexOf(item) - 1)
                R.id.reorder_down -> move(item, items.indexOf(item) + 1)
                else -> super.performAccessibilityAction(host, action, arguments)
            }
        }
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragged = item; destination = items.indexOf(item); pointerY = event.rawY
                    row.getLocationOnScreen(location); offsetY = pointerY - location[1]
                    row.elevation = activity.dp(6).toFloat(); row.setBackgroundColor(dragBackgroundColor)
                    handle.parent.requestDisallowInterceptTouchEvent(true)
                    handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    scroll.postOnAnimation(scrollStep)
                }
                MotionEvent.ACTION_MOVE -> { pointerY = event.rawY; preview() }
                MotionEvent.ACTION_UP -> finish(true)
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> finish(false)
            }
            true
        }
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { if (dragged === item) finish(false) }
        })
        updateControls()
    }

    private fun preview() {
        val item = dragged ?: return
        container.getLocationOnScreen(location)
        val top = pointerY - location[1] - offsetY
        val center = top + item.row.height / 2f
        val from = items.indexOf(item)
        var to = from
        if (center > item.row.top + item.row.height / 2f) {
            while (to < items.lastIndex && center > items[to + 1].row.top + items[to + 1].row.height / 2f) to++
        } else {
            while (to > 0 && center < items[to - 1].row.top + items[to - 1].row.height / 2f) to--
        }
        destination = to
        items.forEachIndexed { index, other ->
            other.row.translationY = when {
                other === item -> top - item.row.top
                index in (from + 1)..to -> -item.row.height.toFloat()
                index in to until from -> item.row.height.toFloat()
                else -> 0f
            }
        }
    }

    fun cancelDrag() { finish(false) }

    private fun finish(commit: Boolean) {
        val item = dragged ?: return
        dragged = null; scroll.removeCallbacks(scrollStep)
        items.forEach { it.row.translationY = 0f }
        item.row.elevation = 0f; item.row.background = null
        item.row.parent?.requestDisallowInterceptTouchEvent(false)
        if (commit) move(item, destination)
    }

    private fun move(item: Item, to: Int): Boolean {
        val from = items.indexOf(item)
        if (dragged != null || from !in items.indices || to !in items.indices || from == to) return false
        val stored = readOrder()
        val source = stored.indexOf(item.id)
        val target = stored.indexOf(items[to].id)
        if (source < 0 || target < 0) return false
        saveOrder(AppListRules.move(stored, item.id, target - source))
        val focus = item.row.findFocus()
        items.removeAt(from); items.add(to, item)
        container.removeView(item.row); container.addView(item.row, to)
        updateControls()
        focus?.requestFocus()
        return true
    }

    private fun updateControls() {
        items.forEachIndexed { index, item ->
            item.handle.stateDescription = activity.getString(R.string.item_position, index + 1, items.size)
        }
    }
}
