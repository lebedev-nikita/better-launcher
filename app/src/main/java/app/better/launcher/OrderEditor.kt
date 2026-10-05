package app.better.launcher

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder

data class OrderEntry(val id: String, val label: String)

/** Moves keep the dialog, focus and scroll intact, retaining IDs omitted from the visible list. */
class OrderEditor(
    private val activity: MainActivity,
    entries: List<OrderEntry>,
    description: String,
    private val readOrder: () -> List<String>,
    private val saveOrder: (List<String>) -> Unit,
    private val onChanged: () -> Unit,
    private val onRemove: ((String) -> Unit)? = null,
) {
    val view = RecyclerView(activity)
    private val items = entries.toMutableList()
    private val orderAdapter = OrderAdapter()
    private val dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = false
        override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean =
            move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        override fun onSwiped(viewHolder: ViewHolder, direction: Int) = Unit
        override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.setBackgroundColor(activity.surface)
                viewHolder?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.background = null
        }
    })

    init {
        view.layoutManager = LinearLayoutManager(activity)
        view.adapter = orderAdapter
        dragHelper.attachToRecyclerView(view)
        view.contentDescription = description
    }

    private fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val entry = items[from]
        // Retain full identities, including entries absent from this editor.
        val stored = readOrder()
        val source = stored.indexOf(entry.id)
        val destination = stored.indexOf(items[to].id)
        if (source < 0 || destination < 0) return false
        saveOrder(AppListRules.move(stored, entry.id, destination - source))
        items.removeAt(from); items.add(to, entry)
        orderAdapter.notifyItemMoved(from, to)
        orderAdapter.notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(to - from) + 1, "position")
        onChanged()
        return true
    }

    private fun remove(position: Int) {
        if (position !in items.indices) return
        val removeItem = onRemove ?: return
        val entry = items.removeAt(position)
        removeItem(entry.id)
        orderAdapter.notifyItemRemoved(position)
        orderAdapter.notifyItemRangeChanged(0, items.size, "position")
        onChanged()
    }

    private inner class OrderAdapter : Adapter<OrderHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = OrderHolder()
        override fun onBindViewHolder(holder: OrderHolder, position: Int) = holder.bind(position)
        override fun onBindViewHolder(holder: OrderHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) holder.bind(position) else holder.updatePosition(position)
        }
    }

    private inner class OrderHolder : ViewHolder(activity.vertical()) {
        private val label = activity.text("", 17f)
        private val handle = activity.iconButton(Glyph.REORDER, "Перетащить") {}
        private val up = activity.iconButton(Glyph.UP, "Вверх") { move(bindingAdapterPosition, bindingAdapterPosition - 1) }
        private val down = activity.iconButton(Glyph.DOWN, "Вниз") { move(bindingAdapterPosition, bindingAdapterPosition + 1) }
        private val remove = activity.iconButton(Glyph.CLOSE, "Убрать из избранного") { remove(bindingAdapterPosition) }

        @SuppressLint("ClickableViewAccessibility") // Keyboard and TalkBack use the labeled arrow buttons.
        private fun enableDrag() {
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    dragHelper.startDrag(this)
                }
                false
            }
            // A tap explains the gesture without changing the order.
            handle.setOnClickListener { activity.message("Перетащите строку за ≡ или используйте стрелки") }
        }

        init {
            val row = itemView as LinearLayout
            row.layoutParams = RecyclerView.LayoutParams(-1, -2)
            row.setPadding(0, activity.dp(4), 0, activity.dp(4))
            val heading = activity.horizontal()
            heading.addView(handle)
            label.minHeight = activity.dp(48)
            heading.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(heading)
            val actions = if (activity.resources.configuration.screenWidthDp >= 600) heading else activity.horizontal().apply {
                addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
                row.addView(this)
            }
            actions.addView(up); actions.addView(down)
            if (onRemove != null) actions.addView(remove)
            enableDrag()
        }

        fun bind(position: Int) {
            val entry = items[position]
            label.text = entry.label
            handle.contentDescription = "Перетащить ${entry.label}"
            up.contentDescription = "Переместить ${entry.label} вверх"
            down.contentDescription = "Переместить ${entry.label} вниз"
            remove.contentDescription = "Убрать ${entry.label} из избранного"
            listOf(handle, up, down, remove).forEach { it.tooltipText = it.contentDescription }
            updatePosition(position)
        }

        fun updatePosition(position: Int) {
            label.stateDescription = "${position + 1} из ${items.size}"
            up.isEnabled = position > 0
            down.isEnabled = position < items.lastIndex
            up.alpha = if (up.isEnabled) 1f else .35f
            down.alpha = if (down.isEnabled) 1f else .35f
        }
    }
}
