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

/** A single scrolling editor; moves keep the dialog, focus and scroll position intact. */
class FavoritesOrderEditor(private val activity: MainActivity, private val onChanged: () -> Unit) {
    val view = RecyclerView(activity)
    private data class Favorite(val id: String, val label: String)
    private val items = activity.store.favorites.mapNotNull { id ->
        val label = activity.store.folder(id)?.name ?: activity.catalog.find(id)?.let(activity.catalog::label)
        label?.let { Favorite(id, it) }
    }.toMutableList()
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
        view.contentDescription = "Порядок избранного"
    }

    private fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val favorite = items[from]
        // Use full IDs and retain unavailable apps/profiles that are absent from this editor.
        val stored = activity.store.favorites
        val source = stored.indexOf(favorite.id)
        val destination = stored.indexOf(items[to].id)
        if (source < 0 || destination < 0) return false
        activity.store.favorites = AppListRules.move(stored, favorite.id, destination - source)
        items.removeAt(from); items.add(to, favorite)
        orderAdapter.notifyItemMoved(from, to)
        orderAdapter.notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(to - from) + 1, "position")
        onChanged()
        return true
    }

    private fun remove(position: Int) {
        if (position !in items.indices) return
        val favorite = items.removeAt(position)
        activity.store.favorites = activity.store.favorites - favorite.id
        orderAdapter.notifyItemRemoved(position)
        orderAdapter.notifyItemRangeChanged(0, items.size, "position")
        onChanged()
    }

    private inner class OrderAdapter : Adapter<FavoriteHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FavoriteHolder()
        override fun onBindViewHolder(holder: FavoriteHolder, position: Int) = holder.bind(position)
        override fun onBindViewHolder(holder: FavoriteHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) holder.bind(position) else holder.updatePosition(position)
        }
    }

    private inner class FavoriteHolder : ViewHolder(activity.vertical()) {
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
            actions.addView(up); actions.addView(down); actions.addView(remove)
            enableDrag()
        }

        fun bind(position: Int) {
            val favorite = items[position]
            label.text = favorite.label
            handle.contentDescription = "Перетащить ${favorite.label}"
            up.contentDescription = "Переместить ${favorite.label} вверх"
            down.contentDescription = "Переместить ${favorite.label} вниз"
            remove.contentDescription = "Убрать ${favorite.label} из избранного"
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
