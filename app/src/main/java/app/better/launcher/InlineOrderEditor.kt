package app.better.launcher

import android.annotation.SuppressLint
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
) {
    private data class Item(val id: String, val label: String, val row: LinearLayout, val up: View, val down: View)
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

    @SuppressLint("ClickableViewAccessibility") // Labeled arrows provide keyboard and accessibility moves.
    fun add(id: String, label: String, content: View, spacing: Int = 0) {
        val row = activity.vertical().apply {
            tag = PageSwipeLayout.BLOCK_SWIPE
            setPadding(0, 0, 0, activity.dp(spacing))
            addView(content)
        }
        val controls = activity.horizontal()
        controls.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        val handle = activity.iconButton(Glyph.REORDER, "Перетащить $label") {
            activity.message("Перетащите за ≡ или используйте стрелки")
        }
        lateinit var item: Item
        val up = activity.iconButton(Glyph.UP, "Переместить $label вверх") { move(item, items.indexOf(item) - 1) }
        val down = activity.iconButton(Glyph.DOWN, "Переместить $label вниз") { move(item, items.indexOf(item) + 1) }
        controls.addView(handle); controls.addView(up); controls.addView(down)
        row.addView(controls)
        item = Item(id, label, row, up, down)
        items.add(item); container.addView(row, LinearLayout.LayoutParams(-1, -2))
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragged = item; destination = items.indexOf(item); pointerY = event.rawY
                    row.getLocationOnScreen(location); offsetY = pointerY - location[1]
                    row.elevation = activity.dp(6).toFloat(); row.setBackgroundColor(activity.surface)
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

    private fun move(item: Item, to: Int) {
        val from = items.indexOf(item)
        if (dragged != null || from !in items.indices || to !in items.indices || from == to) return
        val stored = readOrder()
        val source = stored.indexOf(item.id)
        val target = stored.indexOf(items[to].id)
        if (source < 0 || target < 0) return
        saveOrder(AppListRules.move(stored, item.id, target - source))
        val focus = item.row.findFocus()
        items.removeAt(from); items.add(to, item)
        container.removeView(item.row); container.addView(item.row, to)
        updateControls()
        focus?.requestFocus()
    }

    private fun updateControls() {
        items.forEachIndexed { index, item ->
            item.row.stateDescription = "${index + 1} из ${items.size}"
            item.up.isEnabled = index > 0; item.down.isEnabled = index < items.lastIndex
            item.up.alpha = if (item.up.isEnabled) 1f else .35f
            item.down.alpha = if (item.down.isEnabled) 1f else .35f
        }
    }
}
