package com.lx862.mtrsurveyor;

import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.RoutePathfinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the route pathfinder: routes must snap onto the actual
 * rail geometry (following bends instead of straight chords) and routes
 * sharing rails must receive distinct lane offsets.
 */
class RoutePathfinderTest {

    private static final ClientData DATA = new ClientData();

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static Position node(long x, long z) {
        return new Position(x, 64, z);
    }

    private static Angle angleOf(Position from, Position to) {
        // MTR angles: 0deg = east (+X), rotating clockwise towards south (+Z); 16 steps of 22.5deg
        final double deg = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        final int idx = ((int) Math.round(deg / 22.5) + 16) % 16;
        return Angle.values()[idx];
    }

    private static Rail makeRail(Position p1, Position p2) {
        final Rail rail = Rail.newRail(p1, angleOf(p1, p2), p2, angleOf(p2, p1),
                Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
        assertNotNull(rail);
        assertTrue(rail.isValid(), "rail " + p1 + "->" + p2 + " should be valid");
        return rail;
    }

    private static Platform makePlatform(Rail rail) {
        // The platform spans the whole rail; MTR keeps a live rail reference on it
        final Position p1 = new Position((long) rail.railMath.getPosition(0, false).x(),
                (long) rail.railMath.getPosition(0, false).y(),
                (long) rail.railMath.getPosition(0, false).z());
        final Position p2 = new Position((long) rail.railMath.getPosition(rail.railMath.getLength(), false).x(),
                (long) rail.railMath.getPosition(rail.railMath.getLength(), false).y(),
                (long) rail.railMath.getPosition(rail.railMath.getLength(), false).z());
        final Platform platform = new Platform(p1, p2, TransportMode.TRAIN, DATA);
        platform.rail = rail;
        return platform;
    }

    private record TestGraph(RoutePathfinder.Graph graph, Map<Position, Map<Position, Rail>> index) {
    }

    private static TestGraph buildGraph(Rail... rails) {
        final Map<Position, Map<Position, Rail>> index = new HashMap<>();
        for (Rail rail : rails) {
            index.computeIfAbsent(new Position((long) rail.railMath.getPosition(0, false).x(), 64,
                    (long) rail.railMath.getPosition(0, false).z()), k -> new HashMap<>())
                    .put(new Position((long) rail.railMath.getPosition(rail.railMath.getLength(), false).x(), 64,
                            (long) rail.railMath.getPosition(rail.railMath.getLength(), false).z()), rail);
            index.computeIfAbsent(new Position((long) rail.railMath.getPosition(rail.railMath.getLength(), false).x(),
                    64, (long) rail.railMath.getPosition(rail.railMath.getLength(), false).z()), k -> new HashMap<>())
                    .put(new Position((long) rail.railMath.getPosition(0, false).x(), 64,
                            (long) rail.railMath.getPosition(0, false).z()), rail);
        }
        final RoutePathfinder.Graph graph = RoutePathfinder.buildGraph(List.of(rails), index);
        return new TestGraph(graph, index);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void pathFollowsRailBend() {
        // A bending line: n0 --n1 / n2--n3 with a 90 degree bend between n1 and n2
        final Position n0 = node(0, 0);
        final Position n1 = node(100, 0);
        final Position n2 = node(200, 100);
        final Position n3 = node(300, 100);

        final Rail pa = makeRail(n0, n1);
        final Rail mid = makeRail(n1, n2);
        final Rail pb = makeRail(n2, n3);

        final TestGraph testGraph = buildGraph(pa, mid, pb);
        final Platform platformA = makePlatform(pa);
        final Platform platformB = makePlatform(pb);

        final List<RoutePathfinder.SegmentPath> segments =
                RoutePathfinder.findRoutePath(testGraph.graph(), List.of(platformA, platformB), false);
        assertNotNull(segments);
        assertNotNull(segments.get(0), "path should be found along connected rails");

        final List<double[]> points = RoutePathfinder.flattenToPoints(testGraph.graph(),
                List.of(platformA, platformB), segments, false);

        // The polyline must pass near the bend node (200, 100). A straight chord
        // from (50, 0) to (250, 100) would be ~33 blocks away at that x.
        double minDist = Double.MAX_VALUE;
        for (double[] point : points) {
            minDist = Math.min(minDist, Math.hypot(point[0] - 200, point[1] - 100));
        }
        assertTrue(minDist < 8, "path should pass near the bend node (200,100); closest was " + minDist);
    }

    @Test
    void sharedRailsGetDistinctLanes() {
        final Position n0 = node(0, 0);
        final Position n1 = node(100, 0);
        final Position n2 = node(200, 0);
        final Position n3 = node(300, 0);

        final Rail pa = makeRail(n0, n1);
        final Rail mid = makeRail(n1, n2);
        final Rail pb = makeRail(n2, n3);

        final TestGraph testGraph = buildGraph(pa, mid, pb);
        final Platform platformA = makePlatform(pa);
        final Platform platformB = makePlatform(pb);

        // Two routes running along the same corridor
        final List<RoutePathfinder.SegmentPath> route1 =
                RoutePathfinder.findRoutePath(testGraph.graph(), List.of(platformA, platformB), false);
        final List<RoutePathfinder.SegmentPath> route2 =
                RoutePathfinder.findRoutePath(testGraph.graph(), List.of(platformA, platformB), false);
        assertNotNull(route1.get(0));
        assertNotNull(route2.get(0));

        RoutePathfinder.assignLanes(List.of(route1, route2));

        // The shared middle rail: lanes must differ and be non-zero
        final String sharedHex = mid.getHexId();
        final int lane1 = route1.get(0).edges.stream().filter(e -> e.hexId.equals(sharedHex)).findFirst().orElseThrow().getLane();
        final int lane2 = route2.get(0).edges.stream().filter(e -> e.hexId.equals(sharedHex)).findFirst().orElseThrow().getLane();
        assertNotEquals(lane1, lane2, "routes sharing a rail must not overlap");
        assertNotEquals(0, lane1);
        assertNotEquals(0, lane2);

        // Anchor platform rails are drawn separately (never part of the path edges)
    }

    @Test
    void disconnectedPlatformFallsBackToStraight() {
        final Position n0 = node(0, 0);
        final Position n1 = node(100, 0);
        final Position far0 = node(10_000, 10_000);
        final Position far1 = node(10_100, 10_000);

        final Rail pa = makeRail(n0, n1);
        final Rail isolated = makeRail(far0, far1);

        final TestGraph testGraph = buildGraph(pa, isolated);
        final Platform platformA = makePlatform(pa);
        final Platform platformFar = makePlatform(isolated);

        final List<RoutePathfinder.SegmentPath> segments =
                RoutePathfinder.findRoutePath(testGraph.graph(), List.of(platformA, platformFar), false);
        assertNull(segments.get(0), "no path exists - segment should be null");

        final List<double[]> points = RoutePathfinder.flattenToPoints(testGraph.graph(),
                List.of(platformA, platformFar), segments, false);
        // Straight fallback: exactly the two stop anchors
        assertEquals(2, points.size());
    }

    @Test
    void reverseTraversalWorks() {
        final Position n0 = node(0, 0);
        final Position n1 = node(100, 0);
        final Position n2 = node(200, 0);

        final Rail pa = makeRail(n0, n1);
        final Rail pb = makeRail(n1, n2);

        final TestGraph testGraph = buildGraph(pa, pb);
        final Platform platformA = makePlatform(pa);
        final Platform platformB = makePlatform(pb);

        // Travel B -> A: the graph must be traversable in reverse
        final List<RoutePathfinder.SegmentPath> segments =
                RoutePathfinder.findRoutePath(testGraph.graph(), List.of(platformB, platformA), false);
        assertNotNull(segments.get(0), "reverse traversal should find the path");

        final List<double[]> points = RoutePathfinder.flattenToPoints(testGraph.graph(),
                List.of(platformB, platformA), segments, false);
        assertTrue(points.size() >= 2);
        // Starts near platform B (mid 150,0), ends near platform A (mid 50,0)
        assertTrue(Math.hypot(points.get(0)[0] - 150, points.get(0)[1]) < 60);
        final double[] last = points.get(points.size() - 1);
        assertTrue(Math.hypot(last[0] - 50, last[1]) < 60);
    }
}
