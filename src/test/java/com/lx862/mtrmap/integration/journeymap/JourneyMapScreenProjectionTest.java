package com.lx862.mtrmap.integration.journeymap;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JourneyMapScreenProjectionTest {

    @Test
    void affineProjectionTracksPanZoomAndRotation() {
        final JourneyMapScreenProjection projection = JourneyMapScreenProjection.fromSamples(
                new Point2D.Double(100, 200), new Point2D.Double(104, 201),
                new Point2D.Double(99, 205), 2);
        assertEquals(60, projection.x(10, 20), 1.0E-8);
        assertEquals(155, projection.y(10, 20), 1.0E-8);
        assertEquals(Math.hypot(2, 0.5), projection.pixelsPerBlock(), 1.0E-8);
    }

    @Test
    void activeDragTranslatesEveryWorldPointLikeTheMap() {
        final JourneyMapScreenProjection initial = JourneyMapScreenProjection.fromSamples(
                new Point2D.Double(100, 200), new Point2D.Double(104, 201),
                new Point2D.Double(99, 205), 2);
        final JourneyMapScreenProjection dragged = initial.withDrag(3, -2);
        assertEquals(initial.x(10, 20) + 7, dragged.x(10, 20), 1.0E-8);
        assertEquals(initial.y(10, 20) - 3.5, dragged.y(10, 20), 1.0E-8);
        assertEquals(initial.pixelsPerBlock(), dragged.pixelsPerBlock(), 1.0E-8);
    }

    @Test
    void viewportCullingMovesWithDrag() {
        final JourneyMapScreenProjection initial = JourneyMapScreenProjection.fromSamples(
                new Point2D.Double(100, 100), new Point2D.Double(102, 100),
                new Point2D.Double(100, 102), 1);
        final JourneyMapScreenProjection.WorldBounds before = initial.visibleWorldBounds(200, 200, 0);
        final JourneyMapScreenProjection.WorldBounds after = initial.withDrag(10, 0)
                .visibleWorldBounds(200, 200, 0);
        assertEquals(-50, before.minX(), 1.0E-8);
        assertEquals(-60, after.minX(), 1.0E-8);
        assertEquals(40, after.maxX(), 1.0E-8);
        assertEquals(true, after.intersects(39, 0, 41, 1));
        assertEquals(false, after.intersects(41, 0, 42, 1));
    }
}
