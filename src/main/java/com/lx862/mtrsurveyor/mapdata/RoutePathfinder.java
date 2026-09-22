package com.lx862.mtrsurveyor.mapdata;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Snaps MTR routes onto the actual rail network.
 *
 * <p>Builds a node graph from the rail set (rail node positions as graph
 * nodes, rails as weighted edges), anchors each platform to its own platform
 * rail (the {@code rail} reference carried by the platform, with a proximity
 * fallback), and runs a multi-source Dijkstra between consecutive platforms
 * so route lines follow the real track geometry (arcs included) instead of
 * straight stop-to-stop lines.</p>
 *
 * <p>Pure mtr-core types - unit-testable without a Minecraft install.</p>
 */
public final class RoutePathfinder {

    private RoutePathfinder() {
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Graph
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * One directed traversal of a rail. {@code reversed} is true when the
     * traversal runs opposite to the rail's own geometry parameterization.
     */
    public static final class Edge {
        public final Position from;
        public final Position to;
        public final Rail rail;
        public final boolean reversed;
        public final double length;
        Edge(Position from, Position to, Rail rail, boolean reversed) {
            this.from = from;
            this.to = to;
            this.rail = rail;
            this.reversed = reversed;
            this.length = rail.railMath.getLength();
        }
    }

    /**
     * Node graph over the rail network. Nodes are rail node positions; edges
     * are directed rail traversals (both directions available).
     */
    public static final class Graph {
        final Map<Position, List<Edge>> adjacency = new HashMap<>();
        final Map<String, Rail> railById = new HashMap<>();
        /** hexId -> [naturalStart, naturalEnd] node positions. */
        final Map<String, Position[]> railEnds = new HashMap<>();

        List<Edge> edgesFrom(Position node, Set<String> skipRailIds) {
            final List<Edge> all = adjacency.get(node);
            if (all == null) {
                return List.of();
            }
            if (skipRailIds == null || skipRailIds.isEmpty()) {
                return all;
            }
            final List<Edge> result = new ArrayList<>(all.size());
            for (Edge edge : all) {
                if (!skipRailIds.contains(edge.rail.getHexId())) {
                    result.add(edge);
                }
            }
            return result;
        }
    }

    /**
     * Build the graph from MTR's rail set and node-to-node rail index. The
     * index may contain one or both directions per rail; missing reverse
     * directions are added so the graph traverses both ways.
     */
    public static Graph buildGraph(Iterable<Rail> rails,
            Map<Position, ? extends Map<? extends Position, ? extends Rail>> positionsToRail) {
        final Graph graph = new Graph();

        if (rails != null) {
            for (Rail rail : rails) {
                if (rail != null && rail.isValid() && rail.getTransportMode() == TransportMode.TRAIN) {
                    graph.railById.put(rail.getHexId(), rail);
                }
            }
        }

        if (positionsToRail != null) {
            for (Map.Entry<Position, ? extends Map<? extends Position, ? extends Rail>> entry : positionsToRail
                    .entrySet()) {
                for (final Map.Entry<? extends Position, ? extends Rail> railEntry : entry.getValue().entrySet()) {
                    addDirected(graph, entry.getKey(), railEntry.getKey(), railEntry.getValue());
                }
            }
        }

        // Add reverse traversals for any rail that only has one direction indexed
        for (Rail rail : new ArrayList<>(graph.railById.values())) {
            final Position[] ends = graph.railEnds.get(rail.getHexId());
            if (ends != null) {
                boolean hasReverse = false;
                for (Edge edge : graph.adjacency.getOrDefault(ends[1], List.of())) {
                    if (edge.rail.getHexId().equals(rail.getHexId())) {
                        hasReverse = true;
                        break;
                    }
                }
                if (!hasReverse) {
                    addDirected(graph, ends[1], ends[0], rail);
                }
            }
        }

        return graph;
    }

    private static void addDirected(Graph graph, Position from, Position to, Rail rail) {
        if (from == null || to == null || rail == null || from.equals(to)) {
            return;
        }
        if (!graph.railById.containsKey(rail.getHexId())) {
            return;
        }

        // Determine the rail's own geometry orientation by comparing the
        // traversal start with the rail's parameterized start point.
        boolean natural = isNaturalOrientation(rail, from, to);

        if (!graph.railEnds.containsKey(rail.getHexId())) {
            graph.railEnds.put(rail.getHexId(),
                    natural ? new Position[]{from, to} : new Position[]{to, from});
        }

        graph.adjacency.computeIfAbsent(from, k -> new ArrayList<>(2))
                .add(new Edge(from, to, rail, !natural));
    }

    private static boolean isNaturalOrientation(Rail rail, Position from, Position to) {
        try {
            final double length = rail.railMath.getLength();
            final org.mtr.core.tool.Vector startSample = rail.railMath.getPosition(0, false);
            final org.mtr.core.tool.Vector endSample = rail.railMath.getPosition(length, false);
            final double dStart = squareDistance(startSample.x(), startSample.z(), from.getX(), from.getZ());
            final double dEnd = squareDistance(endSample.x(), endSample.z(), from.getX(), from.getZ());
            return dStart <= dEnd;
        } catch (Throwable e) {
            return true;
        }
    }

    private static double squareDistance(double x1, double z1, double x2, double z2) {
        final double dx = x1 - x2;
        final double dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private static double squareDistance(Position a, Position b) {
        return squareDistance(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Route path search
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * One edge of a route path: which rail and in which direction.
     */
    public static final class PathEdge {
        public final String hexId;
        public final boolean reversed;
        PathEdge(String hexId, boolean reversed) {
            this.hexId = hexId;
            this.reversed = reversed;
        }
    }

    /**
     * One inter-stop segment: the rail edges to traverse plus the network
     * entry/exit nodes of the two anchor platform rails.
     */
    public static final class SegmentPath {
        public final List<PathEdge> edges;
        /** Endpoint of the from-platform rail where the network path starts. */
        public final Position entryNode;
        /** Endpoint of the to-platform rail where the network path ends. */
        public final Position exitNode;

        SegmentPath(List<PathEdge> edges, Position entryNode, Position exitNode) {
            this.edges = edges;
            this.entryNode = entryNode;
            this.exitNode = exitNode;
        }
    }

    /**
     * Find the rail path for one route through its ordered platforms.
     *
     * @return one {@link SegmentPath} per inter-stop segment (size = stops-1,
     *         plus one extra closure entry for circular routes); an entry is
     *         {@code null} when no rail path was found for that segment. A
     *         caller must not replace a missing segment with a straight chord,
     *         because route color is only valid when it lies on track geometry.
     */
    public static List<SegmentPath> findRoutePath(Graph graph, List<Platform> platforms, boolean circular) {
        final int stopCount = platforms.size();
        if (graph == null || stopCount < 2) {
            return null;
        }

        final List<SegmentPath> segments = new ArrayList<>();
        for (int i = 0; i < stopCount - 1; i++) {
            final SegmentPath segment = findSegmentPath(graph, platforms.get(i), platforms.get(i + 1));
            segments.add(segment);
        }

        if (circular) {
            segments.add(findSegmentPath(graph, platforms.get(stopCount - 1), platforms.get(0)));
        }
        return segments;
    }

    /**
     * Dijkstra between the two platform rails: sources are both endpoints of
     * {@code fromPlatform}'s rail, targets are both endpoints of
     * {@code toPlatform}'s rail; the two anchor rails are not traversed (their
     * geometry is added by the caller).
     */
    static SegmentPath findSegmentPath(Graph graph, Platform fromPlatform, Platform toPlatform) {
        final Rail fromRail = getPlatformRail(graph, fromPlatform);
        final Rail toRail = getPlatformRail(graph, toPlatform);
        if (fromRail == null || toRail == null) {
            return null;
        }

        final Position[] fromEnds = graph.railEnds.get(fromRail.getHexId());
        final Position[] toEnds = graph.railEnds.get(toRail.getHexId());
        if (fromEnds == null || toEnds == null) {
            return null;
        }

        if (fromRail.getHexId().equals(toRail.getHexId())) {
            // Adjacent platforms sharing one platform rail
            return new SegmentPath(List.of(), fromEnds[0], fromEnds[1]);
        }

        final Set<String> skip = Set.of(fromRail.getHexId(), toRail.getHexId());

        final Map<Position, Double> dist = new HashMap<>();
        final Map<Position, Edge> prevEdge = new HashMap<>();
        final PriorityQueue<NodeEntry> queue = new PriorityQueue<>();

        for (Position source : fromEnds) {
            dist.put(source, 0.0);
            queue.add(new NodeEntry(source, 0.0));
        }

        Position settledTarget = null;
        while (!queue.isEmpty()) {
            final NodeEntry entry = queue.poll();
            final Position current = entry.node;
            if (entry.dist > dist.getOrDefault(current, Double.POSITIVE_INFINITY)) {
                continue;
            }

            if (current.equals(toEnds[0]) || current.equals(toEnds[1])) {
                settledTarget = current;
                break;
            }

            for (Edge edge : graph.edgesFrom(current, skip)) {
                final double nextDist = entry.dist + edge.length;
                final Double known = dist.get(edge.to);
                if (known == null || nextDist < known) {
                    dist.put(edge.to, nextDist);
                    prevEdge.put(edge.to, edge);
                    queue.add(new NodeEntry(edge.to, nextDist));
                }
            }
        }

        if (settledTarget == null) {
            return null;
        }

        final List<PathEdge> edges = new ArrayList<>();
        Position node = settledTarget;
        while (prevEdge.containsKey(node)) {
            final Edge edge = prevEdge.get(node);
            edges.add(new PathEdge(edge.rail.getHexId(), edge.reversed));
            node = edge.from;
        }
        java.util.Collections.reverse(edges);
        final Position entryNode = node;

        // When a target endpoint is already a source, the platforms are back
        // to back and no intermediate rail edge is needed.
        return new SegmentPath(edges, entryNode, settledTarget);
    }

    /**
     * Resolve the platform's anchor rail. Prefers the live {@code rail}
     * reference carried by the platform; falls back to the nearest
     * platform-type rail in the graph.
     */
    static Rail getPlatformRail(Graph graph, Platform platform) {
        try {
            final Rail liveRail = platform.rail;
            if (liveRail != null && liveRail.isValid() && graph.railById.containsKey(liveRail.getHexId())) {
                return liveRail;
            }
        } catch (Throwable ignored) {
        }

        // Fallback: nearest platform-type rail to the platform's mid position
        Rail best = null;
        double bestDist = Double.MAX_VALUE;
        final Position mid = platform.getMidPosition();
        for (Rail rail : graph.railById.values()) {
            if (!rail.isPlatform() || !rail.closeTo(mid, 32)) {
                continue;
            }
            final Position[] ends = graph.railEnds.get(rail.getHexId());
            if (ends == null) {
                continue;
            }
            final double dist = Math.min(squareDistance(mid, ends[0]), squareDistance(mid, ends[1]));
            if (dist < bestDist) {
                bestDist = dist;
                best = rail;
            }
        }
        return best;
    }

    private record NodeEntry(Position node, double dist) implements Comparable<NodeEntry> {
        @Override
        public int compareTo(NodeEntry other) {
            return Double.compare(dist, other.dist);
        }
    }

    /**
     * Resolve a complete route to the exact {@link MapTrack} strokes used by
     * the gray TRACK layer. No rails are concatenated and no new sampling is
     * performed; ROUTE therefore renders the same immutable polylines.
     */
    public static List<MapTrack> toTracks(Graph graph, List<Platform> platforms,
            List<SegmentPath> segments, boolean circular) {
        final int expectedSegments = platforms.size() - 1 + (circular ? 1 : 0);
        if (segments == null || segments.size() != expectedSegments || !isComplete(segments)) {
            return List.of();
        }

        final LinkedHashSet<String> railIds = new LinkedHashSet<>();
        for (int i = 0; i < segments.size(); i++) {
            final Platform from = platforms.get(i);
            final Platform to = platforms.get((i + 1) % platforms.size());
            final Rail fromRail = getPlatformRail(graph, from);
            final Rail toRail = getPlatformRail(graph, to);
            if (fromRail == null || toRail == null) {
                return List.of();
            }
            railIds.add(fromRail.getHexId());
            for (PathEdge edge : segments.get(i).edges) {
                railIds.add(edge.hexId);
            }
            railIds.add(toRail.getHexId());
        }

        final List<MapTrack> tracks = new ArrayList<>(railIds.size());
        for (String railId : railIds) {
            final Rail rail = graph.railById.get(railId);
            final List<double[]> points = rail == null ? null : TrackSampler.sample(rail);
            if (points == null || points.size() < 2) {
                return List.of();
            }
            tracks.add(new MapTrack(railId, points));
        }
        return tracks;
    }

    private static boolean isComplete(List<SegmentPath> segments) {
        for (SegmentPath segment : segments) {
            if (segment == null) {
                return false;
            }
        }
        return true;
    }
}
