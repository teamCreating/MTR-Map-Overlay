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
    void curvedRailSectionsOverlapWithoutGaps() {
        final List<double[]> rail = IntStream.range(0, 30)
                .mapToObj(i -> new double[] {i * 8.0, (i % 2) * 3.0})
                .toList();
        final List<List<double[]>> sections = TrackRibbonGeometry.sections(rail, -2, 2);
        assertTrue(sections.size() > 1);
        for (int i = 0; i < sections.size() - 1; i++) {
            final List<double[]> current = sections.get(i);
            final List<double[]> next = sections.get(i + 1);
            assertEquals(next.getFirst()[0], current.get(current.size() / 2 - 2)[0]);
            assertEquals(next.getFirst()[1], current.get(current.size() / 2 - 2)[1]);
            assertEquals(next.get(1)[0], current.get(current.size() / 2 - 1)[0]);
            assertEquals(next.get(1)[1], current.get(current.size() / 2 - 1)[1]);
        }
    }

    @Test
    void invalidSamplesDoNotBreakSections() {
        final List<double[]> rail = java.util.Arrays.asList(
                new double[] {0, 0}, null, new double[] {0, 0},
                new double[] {Double.NaN, 1}, new double[] {16, 0});
        assertEquals(1, TrackRibbonGeometry.sections(rail, -2, 2).size());
    }
}
