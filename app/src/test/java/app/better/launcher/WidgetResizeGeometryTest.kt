package app.better.launcher

import org.junit.Assert.*
import org.junit.Test

class WidgetResizeGeometryTest {
    private val geometry = WidgetResizeGeometry(132..360, 92..700, 600)
    @Test fun sideHandlesFollowTheFingerAndKeepWidthCenteredWithinProviderBounds() {
        val size = WidgetSize(240, 200)
        assertEquals(WidgetSize(204, 200), geometry.drag(size, WidgetEdge.RIGHT, -20))
        assertEquals(WidgetSize(204, 200), geometry.drag(size, WidgetEdge.LEFT, 20))
        assertEquals(360, geometry.drag(size, WidgetEdge.RIGHT, 500).width)
        assertEquals(132, geometry.drag(size, WidgetEdge.LEFT, 500).width)
    }
    @Test fun upperHandleKeepsBottomFixedAndCannotCrossTheInitialTop() {
        val size = WidgetSize(240, 250, 30)
        assertEquals(WidgetSize(240, 216, 64), geometry.drag(size, WidgetEdge.TOP, 40))
        assertEquals(WidgetSize(240, 276, 4), geometry.drag(size, WidgetEdge.TOP, -100))
        assertEquals(WidgetSize(240, 96, 184), geometry.drag(size, WidgetEdge.TOP, 500))
    }
    @Test fun oldTallSizesCanBeBroughtIntoAShortWindowWithoutCrossingProviderMinimum() {
        val narrow = WidgetResizeGeometry(132..360, 92..700, 250)
        assertEquals(WidgetSize(240, 240, 10), narrow.drag(WidgetSize(240, 300, 100), WidgetEdge.TOP, 20))
        assertEquals(WidgetSize(240, 96, 154), narrow.drag(WidgetSize(240, 300, 250), WidgetEdge.BOTTOM, -500))
        assertEquals(320, geometry.topRange(WidgetSize(240, 296, 24)).last)
    }
    @Test fun bottomHandleRespectsProviderMinimumAndVisibleSpaceAboveDone() {
        val size = WidgetSize(240, 250, 100)
        assertEquals(96, geometry.drag(size, WidgetEdge.BOTTOM, -500).height)
        assertEquals(492, geometry.drag(size, WidgetEdge.BOTTOM, 500).height)
        assertEquals(696, WidgetResizeGeometry(132..360, 92..700, 1400).drag(size, WidgetEdge.BOTTOM, 1000).height)
    }

    @Test fun movingTheBodySnapsBothAxesWithoutChangingDimensionsAndKeepsItInBounds() {
        val size = WidgetSize(240, 200, 40, 16)
        val geometry = WidgetResizeGeometry(132..360, 92..700, 600, 400)
        assertEquals(WidgetSize(240, 200, 48, 24), geometry.move(size, 11, 9))
        assertEquals(WidgetSize(240, 200, 396, 72), geometry.move(size, 1000, 1000))
        assertEquals(WidgetSize(240, 200, 0, -72), geometry.move(size, -1000, -1000))
    }
    @Test fun sizesSnapInsideOddProviderBoundsAndExpandingKeepsAMovedWidgetVisible() {
        val geometry = WidgetResizeGeometry(133..359, 95..701, 600, 360)
        assertEquals(144, geometry.resizeWidth(WidgetSize(180, 320), 1).width)
        assertEquals(348, geometry.resizeWidth(WidgetSize(180, 320), 1000).width)
        assertEquals(WidgetSize(324, 320, 0, 12), geometry.resizeWidth(WidgetSize(180, 320, 0, 80), 320))
        assertEquals(324, geometry.drag(WidgetSize(180, 320), WidgetEdge.BOTTOM, 3).height)
        assertEquals(312, geometry.drag(WidgetSize(180, 320), WidgetEdge.TOP, 3).height)
    }
    @Test fun providerBoundsTakePriorityWhenTheyContainNoGridPoint() {
        assertEquals(133, WidgetResizeGeometry.snap(132, 133..134))
        assertEquals(134, WidgetResizeGeometry.snap(140, 133..134))
        assertEquals(0, WidgetResizeGeometry.snap(-5, -7..7))
    }
}
