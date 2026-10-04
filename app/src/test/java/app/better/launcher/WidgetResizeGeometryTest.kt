package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class WidgetResizeGeometryTest {
    private val geometry = WidgetResizeGeometry(132..360, 92..700, 600)
    @Test fun sideHandlesFollowTheFingerAndKeepWidthCenteredWithinProviderBounds() {
        val size = WidgetSize(240, 200)
        assertEquals(WidgetSize(200, 200), geometry.drag(size, WidgetEdge.RIGHT, -20))
        assertEquals(WidgetSize(200, 200), geometry.drag(size, WidgetEdge.LEFT, 20))
        assertEquals(360, geometry.drag(size, WidgetEdge.RIGHT, 500).width)
        assertEquals(132, geometry.drag(size, WidgetEdge.LEFT, 500).width)
    }
    @Test fun upperHandleKeepsBottomFixedAndCannotCrossTheInitialTop() {
        val size = WidgetSize(240, 250, 30)
        assertEquals(WidgetSize(240, 210, 70), geometry.drag(size, WidgetEdge.TOP, 40))
        assertEquals(WidgetSize(240, 280, 0), geometry.drag(size, WidgetEdge.TOP, -100))
        assertEquals(WidgetSize(240, 92, 188), geometry.drag(size, WidgetEdge.TOP, 500))
    }
    @Test fun oldTallSizesCanBeBroughtIntoAShortWindowWithoutCrossingProviderMinimum() {
        val narrow = WidgetResizeGeometry(132..360, 92..700, 250)
        assertEquals(WidgetSize(240, 250), narrow.drag(WidgetSize(240, 300, 100), WidgetEdge.TOP, 20))
        assertEquals(WidgetSize(240, 92, 158), narrow.drag(WidgetSize(240, 300, 250), WidgetEdge.BOTTOM, -500))
        assertEquals(320, geometry.topRange(WidgetSize(240, 296, 24)).last)
    }
    @Test fun bottomHandleRespectsProviderMinimumAndVisibleSpaceAboveDone() {
        val size = WidgetSize(240, 250, 100)
        assertEquals(92, geometry.drag(size, WidgetEdge.BOTTOM, -500).height)
        assertEquals(500, geometry.drag(size, WidgetEdge.BOTTOM, 500).height)
        assertEquals(700, WidgetResizeGeometry(132..360, 92..700, 1400).drag(size, WidgetEdge.BOTTOM, 1000).height)
    }
}
