package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.TrackRibbonGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackRibbonGeometryTest {

    @Test
    void adjacentBandsShareOnlyTheirBoundary() {
        final List<double[]> rail = List.of(new double[] {0, 0}, new double[] {10, 0});
        final List<double[]> first = TrackRibbonGeometry.sections(rail, -2, 0).getFirst();
        final List<double[]> second = TrackRibbonGeometry.sections(rail, 0, 2).getFirst();
        assertEquals(4, first.size());
        assertEquals(4, second.size());
        assertEquals(0, first.get(0)[1]);
        assertEquals(0, second.get(2)[1]);
        assertTrue(first.get(2)[1] < 0);
        assertTrue(second.get(0)[1] > 0);
    }

    @Test
    void duplicateSamplesDoNotProduceDegenerateBand() {
        final List<double[]> polygon = TrackRibbonGeometry.sections(
                List.of(new double[] {0, 0}, new double[] {0, 0}, new double[] {3, 4}), -1, 1).getFirst();
        assertEquals(4, polygon.size());
    }

    @Test
    void longStraightRailIsOneSmallPolygon() {
        final List<double[]> rail = IntStream.range(0, 80)
                .mapToObj(i -> new double[] {i * 8.0, 10})
                .toList();
        final List<List<double[]>> sections = TrackRibbonGeometry.sections(rail, -2, 2);
        assertEquals(1, sections.size());
        assertEquals(4, sections.getFirst().size());
        assertTrue(sections.getFirst().getFirst()[0] < rail.getFirst()[0]);
        assertTrue(sections.getFirst().get(1)[0] > rail.getLast()[0]);
    }

    @Test
    void curvedRailUsesConvexSegmentQuads() {
        final List<double[]> rail = IntStream.range(0, 30)
                .mapToObj(i -> new double[] {i * 8.0, (i % 2) * 3.0})
                .toList();
        final List<List<double[]>> sections = TrackRibbonGeometry.sections(rail, -2, 2);
        assertTrue(sections.size() > 1);
        for (List<double[]> quad : sections) {
            assertEquals(4, quad.size());
            double winding = 0;
            for (int i = 0; i < 4; i++) {
                final double[] a = quad.get(i);
                final double[] b = quad.get((i + 1) % 4);
                final double[] c = quad.get((i + 2) % 4);
                final double cross = (b[0] - a[0]) * (c[1] - b[1])
                        - (b[1] - a[1]) * (c[0] - b[0]);
                assertTrue(Math.abs(cross) > 1.0E-4);
                if (i == 0) {
                    winding = Math.signum(cross);
                } else {
                    assertEquals(winding, Math.signum(cross));
                }
            }
        }
    }

    @Test
    void neighbouringStraightQuadsOverlapAtJoin() {
        final List<List<double[]>> sections = TrackRibbonGeometry.sections(List.of(
                new double[] {0, 0}, new double[] {8, 0}, new double[] {16, 0.8}), -2, 2);
        assertEquals(2, sections.size());
        assertTrue(sections.get(0).get(1)[0] > 8);
        assertTrue(sections.get(1).get(0)[0] < 8);
    }

    @Test
    void invalidSamplesDoNotBreakSections() {
        final List<double[]> rail = java.util.Arrays.asList(
                new double[] {0, 0}, null, new double[] {0, 0},
                new double[] {Double.NaN, 1}, new double[] {16, 0});
        assertEquals(1, TrackRibbonGeometry.sections(rail, -2, 2).size());
    }
}
