package com.lx862.mtrsurveyor;

import com.lx862.mtrsurveyor.mapdata.RoutePathfinder;
import com.lx862.mtrsurveyor.mapdata.MapTrack;
import com.lx862.mtrsurveyor.mapdata.TrackSampler;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless verification for the no-depot route fallback. */
class RoutePathfinderTest {

    private static final ClientData DATA = new ClientData();

    private static Position node(long x, long z) {
        return new Position(x, 64, z);
    }

    private static Angle angleOf(Position from, Position to) {
        final double degrees = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        final int index = ((int) Math.round(degrees / 22.5) + 16) % 16;
        return Angle.values()[index];
    }

    private static Rail rail(Position start, Position end) {
        final Rail rail = Rail.newRail(start, angleOf(start, end), end, angleOf(end, start),
                Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
        assertNotNull(rail);
        return rail;
    }

    private static Platform platform(Rail rail) {
        final double length = rail.railMath.getLength();
        final org.mtr.core.tool.Vector start = rail.railMath.getPosition(0, false);
        final org.mtr.core.tool.Vector end = rail.railMath.getPosition(length, false);
        final Platform platform = new Platform(
                new Position(Math.round(start.x()), Math.round(start.y()), Math.round(start.z())),
                new Position(Math.round(end.x()), Math.round(end.y()), Math.round(end.z())),
                TransportMode.TRAIN, DATA);
        platform.rail = rail;
        return platform;
    }

    private static RoutePathfinder.Graph graph(Rail... rails) {
        final Map<Position, Map<Position, Rail>> positionsToRail = new HashMap<>();
        for (Rail rail : rails) {
            final double length = rail.railMath.getLength();
            final org.mtr.core.tool.Vector a = rail.railMath.getPosition(0, false);
            final org.mtr.core.tool.Vector b = rail.railMath.getPosition(length, false);
            final Position start = new Position(Math.round(a.x()), Math.round(a.y()), Math.round(a.z()));
            final Position end = new Position(Math.round(b.x()), Math.round(b.y()), Math.round(b.z()));
            positionsToRail.computeIfAbsent(start, ignored -> new HashMap<>()).put(end, rail);
        }
        return RoutePathfinder.buildGraph(List.of(rails), positionsToRail);
    }

    private static String key(double[] point) {
        return Double.doubleToLongBits(point[0]) + ":" + Double.doubleToLongBits(point[1]);
    }

    @Test
    void fallbackUsesTheExactTrackLayerSamples() {
        final Rail first = rail(node(0, 0), node(100, 0));
        final Rail middle = rail(node(100, 0), node(200, 0));
        final Rail last = rail(node(200, 0), node(300, 0));
        final RoutePathfinder.Graph graph = graph(first, middle, last);
        final List<Platform> platforms = List.of(platform(first), platform(last));

        final List<RoutePathfinder.SegmentPath> segments =
                RoutePathfinder.findRoutePath(graph, platforms, false);
        final List<MapTrack> routeTracks = RoutePathfinder.toTracks(graph, platforms, segments, false);
        assertFalse(routeTracks.isEmpty());

        final Set<String> grayTrackSamples = new HashSet<>();
        for (Rail rail : List.of(first, middle, last)) {
            TrackSampler.sample(rail).forEach(point -> grayTrackSamples.add(key(point)));
        }
        for (MapTrack routeTrack : routeTracks) {
            for (double[] routePoint : routeTrack.points) {
                assertTrue(grayTrackSamples.contains(key(routePoint)),
                        "route point must be one of the exact gray-track samples");
            }
        }
    }

    @Test
    void disconnectedNetworkNeverProducesAStationChord() {
        final Rail first = rail(node(0, 0), node(100, 0));
        final Rail isolated = rail(node(10_000, 10_000), node(10_100, 10_000));
        final RoutePathfinder.Graph graph = graph(first, isolated);
        final List<Platform> platforms = List.of(platform(first), platform(isolated));

        final List<RoutePathfinder.SegmentPath> segments =
                RoutePathfinder.findRoutePath(graph, platforms, false);
        final List<MapTrack> routeTracks = RoutePathfinder.toTracks(graph, platforms, segments, false);

        assertTrue(routeTracks.isEmpty(), "an unroutable route must be omitted instead of drawn as a straight line");
    }

    @Test
    void reverseTraversalStillUsesTrackSamples() {
        final Rail first = rail(node(0, 0), node(100, 0));
        final Rail last = rail(node(100, 0), node(200, 0));
        final RoutePathfinder.Graph graph = graph(first, last);
        final List<Platform> platforms = List.of(platform(last), platform(first));

        final List<RoutePathfinder.SegmentPath> segments =
                RoutePathfinder.findRoutePath(graph, platforms, false);
        final List<MapTrack> routeTracks = RoutePathfinder.toTracks(graph, platforms, segments, false);

        assertFalse(routeTracks.isEmpty());
        assertTrue(routeTracks.stream().allMatch(track -> track.points.size() >= 2));
    }
}
