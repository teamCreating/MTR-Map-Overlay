package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.mapdata.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RouteJunctionTest {
    private static MapTrack rail(String id, double[]... points) { return new MapTrack(id, List.of(points)); }
    private static final TrackRoutePalette.Entry A = new TrackRoutePalette.Entry("a", "A", 0xFF0000, RouteLineStyle.NORMAL);
    private static final TrackRoutePalette.Entry B = new TrackRoutePalette.Entry("b", "B", 0x00FF00, RouteLineStyle.HIGH_SPEED);

    @Test void curveEndpointRoundingDoesNotResetPhaseOrDirection() {
        final var a = rail("a", new double[]{0, 0, 64}, new double[]{16, 0, 64});
        final var b = rail("b", new double[]{32, 4, 64}, new double[]{16 + 1E-10, -1E-14, 64});
        final var layout = TrackPatternLayout.build(List.of(a, b));
        assertTrue(layout.get("b").reversed());
        assertEquals(a.length(), layout.get("b").distance(), 1E-8);
        final var p = layout.get("a");
        final var q = layout.get("b");
        assertEquals(p.normalX(a, 1), -q.normalX(b, 1), 1E-8);
        assertEquals(p.normalZ(a, 1), -q.normalZ(b, 1), 1E-8);
    }

    @Test void unrelatedHeightsAreNeverJoined() {
        final var a = rail("a", new double[]{0, 0, 64}, new double[]{16, 0, 64});
        final var b = rail("b", new double[]{16, 0, 65}, new double[]{32, 4, 65});
        final var layout = TrackPatternLayout.build(List.of(a, b));
        assertNull(layout.get("a").end());
        assertNull(layout.get("b").start());
    }

    @Test void branchDoesNotResetTheMainCorridorPhase() {
        final var a = rail("a", new double[]{0, 0}, new double[]{16, 0});
        final var b = rail("b", new double[]{32, 0}, new double[]{16, 0});
        final var c = rail("c", new double[]{16, 0}, new double[]{32, 10});
        final var layout = TrackPatternLayout.build(List.of(c, b, a),
                Map.of("a", List.of(A, B), "b", List.of(A, B), "c", List.of(A)));
        assertTrue(layout.get("b").reversed());
        assertEquals(16, layout.get("b").distance(), 1E-8);
        assertEquals("b", layout.get("a").end().neighborId());
    }

    @Test void separatePhysicalRailsShareActualRibbonEdges() {
        final var a = rail("a", new double[]{0, 0}, new double[]{20, 0});
        final var b = rail("b", new double[]{20, 0}, new double[]{40, 15});
        final var layout = TrackPatternLayout.build(List.of(a, b));
        final var scene = PlanarMapScene.build(List.of(a, b), Map.of("a", List.of(A), "b", List.of(A)),
                layout, List.of(), false, true, 2, -100, -100, 100, 100);
        final var red = scene.quads().stream().filter(q -> q.color() == A.color()).toList();
        final var before = red.stream().filter(q -> Math.abs((q.x2()+q.x3())/2 - 20) < 1E-8).findFirst().orElseThrow();
        final var after = red.stream().filter(q -> Math.abs((q.x1()+q.x4())/2 - 20) < 1E-8).findFirst().orElseThrow();
        assertEquals(before.x2(), after.x1(), 1E-8);
        assertEquals(before.z2(), after.z1(), 1E-8);
        assertEquals(before.x3(), after.x4(), 1E-8);
        assertEquals(before.z3(), after.z4(), 1E-8);
    }

    @Test void bundleChangeTapersAtJoinAndPreservesContinuingLane() {
        final var a = rail("a", new double[]{0, 0}, new double[]{1000, 0});
        final var b = rail("b", new double[]{1000, 0}, new double[]{2000, 0});
        final var layout = TrackPatternLayout.build(List.of(a, b));
        final var palette = Map.of("a", List.of(A, B), "b", List.of(A));
        final var profiles = RouteRibbonProfile.build(List.of(a, b), palette, layout, 1);
        final var local = profiles.get("a").bands().get("a");
        final var expected = profiles.get("a").at("a", local.left(), local.right(), a.length(), a.length());
        final var actual = profiles.get("b").at("a", -1.25, 1.25, 0, b.length());
        assertEquals(expected, actual);
        assertEquals(new RouteRibbonProfile.Band(-1.25, 1.25), profiles.get("b").at("a", -1.25, 1.25, 12, b.length()));
        final var scene = PlanarMapScene.build(List.of(a, b), palette, layout, List.of(), false, true,
                1, -100, -100, 2100, 100);
        final var ending = profiles.get("a").bands().get("b");
        final var tip = profiles.get("a").at("b", ending.left(), ending.right(), a.length(), a.length());
        assertEquals(tip.left(), tip.right(), 1E-8, "Ending lane must not leave a square lip");
        assertTrue(scene.quads().stream().filter(q -> q.color() == A.color())
                .anyMatch(q -> Math.abs((q.x2()+q.x3())/2 - (1000 - profiles.get("a").taper())) < 1E-8));
    }

    @Test void routeToUncolouredTrackSharesTheActualBackingWithoutAddingWidthTwice() {
        final var a = rail("a", new double[]{0, 0}, new double[]{100, 0});
        final var b = rail("b", new double[]{100, 0}, new double[]{200, 0});
        final var tracks = List.of(a, b);
        for (double scale : new double[]{.02, .25, 1, 8}) {
            final var p = RouteRibbonProfile.build(tracks, Map.of("a", List.of(B)), TrackPatternLayout.build(tracks), scale);
            final double before = p.get("a").widthAt(p.get("a").backingWidth(), a.length(), a.length());
            final double after = p.get("b").widthAt(p.get("b").backingWidth(), 0, b.length());
            assertEquals(before, after, 1E-8);
            assertEquals(Math.min(p.get("a").backingWidth(), p.get("b").backingWidth()), before, 1E-8);
        }
    }

    @Test void reversedSharedToSingleRouteHasIdenticalPhysicalEdgesAndNoWhiteSpill() {
        final var a = rail("a", new double[]{0, 0}, new double[]{63, 0});
        final var b = rail("b", new double[]{126, 0}, new double[]{63, 0});
        final var tracks = List.of(a, b);
        final var palette = Map.of("a", List.of(A, B), "b", List.of(A));
        final var layout = TrackPatternLayout.build(tracks, palette);
        final var scene = PlanarMapScene.build(tracks, palette, layout, List.of(), true, true, 4, -100, -100, 200, 100);
        final var red = scene.quads().stream().filter(q -> q.color() == A.color()).toList();
        final var before = red.stream().filter(q -> Math.abs(q.x2() - 63) < 1E-8).findFirst().orElseThrow();
        final var after = red.stream().filter(q -> Math.abs(q.x1() - 63) < 1E-8).findFirst().orElseThrow();
        assertEquals(before.z2(), after.z1(), 1E-8);
        assertEquals(before.z3(), after.z4(), 1E-8);
        final var green = scene.quads().stream().filter(q -> q.color() == B.color()).toList();
        for (var white : scene.quads().stream().filter(q -> q.color() == 0xFFFFFF).toList()) {
            for (double[] v : new double[][]{{white.x1(), white.z1()}, {white.x2(), white.z2()},
                    {white.x3(), white.z3()}, {white.x4(), white.z4()}}) {
                assertTrue(green.stream().anyMatch(q -> q.contains(v[0], v[1])), "White block escaped ending lane");
            }
        }
    }
}
