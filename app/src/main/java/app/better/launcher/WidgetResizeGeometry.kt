package app.better.launcher

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class WidgetEdge { TOP, BOTTOM, LEFT, RIGHT }

/** Dimensions and offsets are dp. Edited values snap to a fine grid within provider/window bounds. */
class WidgetResizeGeometry(
    val widths: IntRange, val heights: IntRange, private val bottomLimit: Int,
    private val availableWidth: Int = widths.last
) {
    private val minimumHeight = snap(heights.first, heightRange(0))

    fun drag(size: WidgetSize, edge: WidgetEdge, delta: Int): WidgetSize = when (edge) {
        WidgetEdge.LEFT -> resizeWidth(size, size.width - 2 * delta)
        WidgetEdge.RIGHT -> resizeWidth(size, size.width + 2 * delta)
        WidgetEdge.BOTTOM -> {
            val top = size.top.coerceAtMost((bottomLimit - minimumHeight).coerceAtLeast(0))
            size.copy(height = snap(size.height + delta, heightRange(top)), top = top)
        }
        WidgetEdge.TOP -> {
            val bottom = minOf(size.top + size.height, bottomLimit).coerceAtLeast(minimumHeight)
            val height = snap(size.height - delta, topRange(size))
            size.copy(height = height, top = (bottom - height).coerceIn(0, 1600))
        }
    }

    fun move(size: WidgetSize, dx: Int, dy: Int): WidgetSize = size.copy(
        offsetX = snap(size.offsetX + dx, horizontalRange(size.width)),
        top = snap(size.top + dy, 0..(bottomLimit - size.height).coerceIn(0, 1600))
    )

    private fun horizontalRange(width: Int): IntRange {
        val limit = ((availableWidth - width) / 2).coerceIn(0, 1600)
        return -limit..limit
    }
    fun resizeWidth(size: WidgetSize, width: Int): WidgetSize {
        val selected = snap(width, widths)
        return size.copy(width = selected, offsetX = snap(size.offsetX, horizontalRange(selected)))
    }

    fun topRange(size: WidgetSize) = heights.first..minOf(heights.last, size.top + size.height, bottomLimit).coerceAtLeast(heights.first)
    fun heightRange(top: Int) = heights.first..minOf(heights.last, bottomLimit - top).coerceAtLeast(heights.first)

    companion object {
        const val GRID_DP = 12
        // Very tight provider constraints may contain no grid point; the provider bounds take priority.
        fun snap(value: Int, range: IntRange): Int {
            val first = ceil(range.first.toDouble() / GRID_DP).toInt() * GRID_DP
            val last = floor(range.last.toDouble() / GRID_DP).toInt() * GRID_DP
            return if (first <= last) (value.toDouble() / GRID_DP).roundToInt().times(GRID_DP).coerceIn(first, last)
                else value.coerceIn(range)
        }
    }
}
