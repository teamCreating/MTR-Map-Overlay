package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.mapdata.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteLineRenderingTest {
    private static final List<TrackRoutePalette.Entry> HIGH_SPEED = List.of(
            new TrackRoutePalette.Entry("h", "High speed", 0x008844, RouteLineStyle.HIGH_SPEED));

    @Test
    void mixedSharedRailKeepsWidthsAndWhiteInsideTheHighSpeedLane() {
        final MapTrack track = track("rail", 0, 64);
        final List<TrackRoutePalette.Entry> bands = List.of(
                new TrackRoutePalette.Entry("a", "Light", 0xFF0000, RouteLineStyle.LIGHT_RAIL),
                new TrackRoutePalette.Entry("b", "Normal", 0x0000FF, RouteLineStyle.NORMAL),
                HIGH_SPEED.getFirst());
        final List<Quad> quads = draw(track, bands, TrackPatternLayout.Placement.DEFAULT, 10, -100, 100);
        final Quad light = quads.stream().filter(q -> q.color == 0xFF0000).findFirst().orElseThrow();
        final Quad normal = quads.stream().filter(q -> q.color == 0x0000FF).findFirst().orElseThrow();
        final Quad high = quads.stream().filter(q -> q.color == 0x008844).findFirst().orElseThrow();
        assertEquals(0.7, light.width() / normal.width(), 1.0E-8);
        assertEquals(1.0, high.width() / normal.width(), 1.0E-8);
        assertEquals(light.z[0], normal.z[2], 1.0E-8);
        assertEquals(normal.z[0], high.z[2], 1.0E-8);
        final List<Quad> white = quads.stream().filter(q -> q.color == 0xFFFFFF).toList();
        assertFalse(white.isEmpty());
        for (Quad inset : white) {
            for (double z : inset.z) {
                assertTrue(z > high.z[2] && z < high.z[0], "White must leave both coloured borders intact");
            }
        }
    }

    @Test
    void splittingAndReversingAStoredRailDoesNotRestartBlocksOrSwapLanes() {
        final MapTrack whole = track("whole", 0, 64);
        final MapTrack first = track("a", 0, 10);
        final MapTrack reversed = track("b", 64, 10);
        final var layout = TrackPatternLayout.build(List.of(reversed, first));
        assertTrue(layout.get("b").reversed());
        assertEquals(10, layout.get("b").distance(), 1.0E-8);
        final List<Quad> split = new ArrayList<>(draw(first, HIGH_SPEED, layout.get("a"), 2, -100, 100));
        split.addAll(draw(reversed, HIGH_SPEED, layout.get("b"), 2, -100, 100));
        final List<Quad> single = draw(whole, HIGH_SPEED, TrackPatternLayout.Placement.DEFAULT, 2, -100, 100);
        for (int x = 0; x < 64; x++) {
            final double sample = x + 0.5;
            assertEquals(isWhite(single, sample), isWhite(split, sample), "Stripe phase at " + sample);
        }
    }

    @Test
    void panClippingPreservesPatternAndBoundsWorkForVeryLongRails() {
        final MapTrack track = track("long", -10_000_000, 10_000_000);
        final var placement = TrackPatternLayout.Placement.DEFAULT;
        final List<Quad> visible = draw(track, HIGH_SPEED, placement, 2, 0, 100);
        assertTrue(visible.size() < 12, "Do not enumerate offscreen blocks");
        for (int x = 0; x < 100; x++) {
            assertEquals((Math.floor((10_000_000 + x + 0.5) / 16) % 2) == 1,
                    isWhite(visible, x + 0.5));
        }
    }

    @Test
    void lowZoomOrNarrowSharedLanesSimplifyToSolid() {
        assertTrue(draw(track("rail", 0, 1000), HIGH_SPEED, TrackPatternLayout.Placement.DEFAULT,
                .75, -100, 1100).stream().anyMatch(q -> q.color == 0xFFFFFF),
                "Keep high-speed blocks at ordinary map zoom after switching to world widths");
        assertTrue(draw(track("rail", 0, 1000), HIGH_SPEED, TrackPatternLayout.Placement.DEFAULT,
                0.1, -100, 1100).stream().noneMatch(q -> q.color == 0xFFFFFF));
        final var crowded = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new TrackRoutePalette.Entry("" + i, "High speed", 0x008844, RouteLineStyle.HIGH_SPEED))
                .toList();
        assertTrue(draw(track("rail", 0, 1000), crowded, TrackPatternLayout.Placement.DEFAULT,
                1, -100, 1100).stream().noneMatch(q -> q.color == 0xFFFFFF));
    }

    @Test
    void curvedBandsMeetAtSampleJoinsWithoutGaps() {
        final MapTrack curve = new MapTrack("curve", List.of(new double[] {0, 0},
                new double[] {24, 0}, new double[] {40, 16}));
        final var base = draw(curve, HIGH_SPEED, TrackPatternLayout.Placement.DEFAULT, 2, -100, 100)
                .stream().filter(q -> q.color != 0xFFFFFF).toList();
        assertEquals(2, base.size());
        assertEquals(base.get(0).x[1], base.get(1).x[0], 1.0E-8);
        assertEquals(base.get(0).z[1], base.get(1).z[0], 1.0E-8);
        assertEquals(base.get(0).x[2], base.get(1).x[3], 1.0E-8);
        assertEquals(base.get(0).z[2], base.get(1).z[3], 1.0E-8);
    }

    @Test
    void closedLoopFitsWholeBlockPairsAndLayoutIsIndependentOfInputOrder() {
        final MapTrack a = track("a", 0, 25);
        final MapTrack b = new MapTrack("b", List.of(new double[] {25, 0}, new double[] {12, 20}, new double[] {0, 0}));
        final var forward = TrackPatternLayout.build(List.of(a, b));
        assertEquals(forward, TrackPatternLayout.build(List.of(b, a)));
        final double pairs = (a.length() + b.length()) / (2 * forward.get("a").blockLength());
        assertEquals(Math.rint(pairs), pairs, 1.0E-8);
    }

    private static boolean isWhite(List<Quad> quads, double x) {
        return quads.stream().anyMatch(q -> q.color == 0xFFFFFF
                && x >= Math.min(q.x[0], q.x[1]) && x < Math.max(q.x[0], q.x[1]));
    }

    private static MapTrack track(String id, double start, double end) {
        return new MapTrack(id, List.of(new double[] {start, 0}, new double[] {end, 0}));
    }

    private static List<Quad> draw(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale, double minX, double maxX) {
        final List<Quad> result = new ArrayList<>();
        RouteRibbonGeometry.draw(track, bands, placement, scale, minX, -100, maxX, 100,
                (x1, z1, x2, z2, x3, z3, x4, z4, color, alpha) -> result.add(
                        new Quad(new double[] {x1, x2, x3, x4}, new double[] {z1, z2, z3, z4}, color)));
        return result;
    }

    private record Quad(double[] x, double[] z, int color) {
        double width() {
            return z[0] - z[3];
        }
    }
}
