package app.better.launcher

enum class WidgetEdge { TOP, BOTTOM, LEFT, RIGHT }

/** Dimensions are dp. Width stays centered; the upper edge keeps the lower edge fixed. */
class WidgetResizeGeometry(val widths: IntRange, val heights: IntRange, private val bottomLimit: Int) {
    fun drag(size: WidgetSize, edge: WidgetEdge, delta: Int): WidgetSize = when (edge) {
        WidgetEdge.LEFT -> size.copy(width = (size.width - 2 * delta).coerceIn(widths))
        WidgetEdge.RIGHT -> size.copy(width = (size.width + 2 * delta).coerceIn(widths))
        WidgetEdge.BOTTOM -> {
            val top = size.top.coerceAtMost((bottomLimit - heights.first).coerceAtLeast(0))
            size.copy(height = (size.height + delta).coerceIn(heightRange(top)), top = top)
        }
        WidgetEdge.TOP -> {
            val bottom = minOf(size.top + size.height, bottomLimit).coerceAtLeast(heights.first)
            val height = (size.height - delta).coerceIn(topRange(size))
            size.copy(height = height, top = (bottom - height).coerceIn(0, 1600))
        }
    }

    fun topRange(size: WidgetSize) = heights.first..minOf(heights.last, size.top + size.height, bottomLimit).coerceAtLeast(heights.first)

    fun heightRange(top: Int) = heights.first..minOf(heights.last, bottomLimit - top).coerceAtLeast(heights.first)
}
