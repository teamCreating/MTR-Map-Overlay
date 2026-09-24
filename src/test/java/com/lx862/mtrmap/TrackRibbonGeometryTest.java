package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.TrackRibbonGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackRibbonGeometryTest {

    @Test
    void adjacentBandsShareOnlyTheirBoundary() {
        final List<double[]> rail = List.of(new double[] {0, 0}, new double[] {10, 0});
        final List<double[]> first = TrackRibbonGeometry.band(rail, -2, 0);
        final List<double[]> second = TrackRibbonGeometry.band(rail, 0, 2);
        assertEquals(4, first.size());
        assertEquals(4, second.size());
        assertEquals(0, first.get(0)[1]);
        assertEquals(0, second.get(2)[1]);
        assertTrue(first.get(2)[1] < 0);
        assertTrue(second.get(0)[1] > 0);
    }

    @Test
    void duplicateSamplesDoNotProduceDegenerateBand() {
        final List<double[]> polygon = TrackRibbonGeometry.band(
                List.of(new double[] {0, 0}, new double[] {0, 0}, new double[] {3, 4}), -1, 1);
        assertEquals(4, polygon.size());
    }
}
