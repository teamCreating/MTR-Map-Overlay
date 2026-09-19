package com.lx862.mtrsurveyor.mapdata;

import com.lx862.mtrsurveyor.MTRSurveyor;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * <p>When several routes traverse the same rail, lane indices are assigned
 * per rail and split by traversal direction, so overlapping routes render as
 * parallel offset lines instead of stacking on top of each other.</p>
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
        int lane;

        Edge(Position from, Position to, Rail rail, boolean reversed) {
            this.from = from;
            this.to = to;
            this.rail = rail;
            this.reversed = reversed;
            this.length = rail.railMath.getLength();
            this.lane = 0;
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
                if (rail != null && rail.isValid()) {
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
            if (ends != null && graph.adjacency.get(ends[1]) != null) {
                boolean hasReverse = false;
                for (Edge edge : graph.adjacency.get(ends[1])) {
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
     * One edge of a route path: which rail, in which direction, and the lane
     * offset assigned to it (0 = centerline).
     */
    public static final class PathEdge {
        public final String hexId;
        public final boolean reversed;
        int lane;

        PathEdge(String hexId, boolean reversed) {
            this.hexId = hexId;
            this.reversed = reversed;
            this.lane = 0;
        }

        /** Lane offset index (0 = centerline, signed for parallel routes). */
        public int getLane() {
            return lane;
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
     *         {@code null} when no rail path was found for that segment
     *         (caller falls back to a straight line for it)
     */
    public static List<SegmentPath> findRoutePath(Graph graph, List<Platform> platforms, boolean circular) {
        final int stopCount = platforms.size();
        if (graph == null || stopCount < 2) {
            return null;
        }

        final List<SegmentPath> segments = new ArrayList<>();
        boolean complete = true;
        for (int i = 0; i < stopCount - 1; i++) {
            final SegmentPath segment = findSegmentPath(graph, platforms.get(i), platforms.get(i + 1));
            segments.add(segment);
            complete &= segment != null;
        }

        if (circular && complete) {
            // Close the loop by retracing the whole path backwards; on an
            // undirected graph the closure follows the same rails.
            final List<PathEdge> forward = new ArrayList<>();
            for (SegmentPath segment : segments) {
                forward.addAll(segment.edges);
            }
            final SegmentPath first = segments.get(0);
            final SegmentPath last = segments.get(segments.size() - 1);
            final List<PathEdge> closure = new ArrayList<>(forward.size());
            for (int i = forward.size() - 1; i >= 0; i--) {
                closure.add(new PathEdge(forward.get(i).hexId, !forward.get(i).reversed));
            }
            segments.add(new SegmentPath(closure, last.exitNode, first.entryNode));
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
            return new SegmentPath(List.of(new PathEdge(fromRail.getHexId(), false)), fromEnds[0], fromEnds[1]);
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

        if (edges.isEmpty()) {
            // A target endpoint was already a source - platforms back to back
            edges.add(new PathEdge(toRail.getHexId(), false));
        }
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
     * Assign lane offsets for all routes. Rails used by two or more routes get
     * non-zero lanes (split by traversal direction so opposite services end up
     * on opposite sides); rails used by a single route stay at 0.
     */
    public static void assignLanes(List<List<SegmentPath>> allRoutes) {
        // hexId -> (routeIdx -> reversed)
        final Map<String, Map<Integer, Boolean>> usage = new HashMap<>();
        for (int routeIdx = 0; routeIdx < allRoutes.size(); routeIdx++) {
            final List<SegmentPath> segments = allRoutes.get(routeIdx);
            if (segments == null) {
                continue;
            }
            for (SegmentPath segment : segments) {
                if (segment == null) {
                    continue;
                }
                for (PathEdge edge : segment.edges) {
                    usage.computeIfAbsent(edge.hexId, k -> new HashMap<>()).put(routeIdx, edge.reversed);
                }
            }
        }

        for (Map.Entry<String, Map<Integer, Boolean>> entry : usage.entrySet()) {
            final Map<Integer, Boolean> users = entry.getValue();
            if (users.size() < 2) {
                continue;
            }
            for (int direction = 0; direction < 2; direction++) {
                final List<Integer> group = new ArrayList<>();
                for (Map.Entry<Integer, Boolean> user : users.entrySet()) {
                    if ((user.getValue() ? 1 : 0) == direction) {
                        group.add(user.getKey());
                    }
                }
                group.sort(Integer::compareTo);
                for (int k = 0; k < group.size(); k++) {
                    final int lane = (k + 1) * (direction == 0 ? 1 : -1);
                    setLane(allRoutes.get(group.get(k)), entry.getKey(), lane);
                }
            }
        }
    }

    private static void setLane(List<SegmentPath> route, String hexId, int lane) {
        for (SegmentPath segment : route) {
            if (segment == null) {
                continue;
            }
            for (PathEdge edge : segment.edges) {
                if (edge.hexId.equals(hexId)) {
                    edge.lane = lane;
                }
            }
        }
    }

    /**
     * Flatten a route's path into render points {x, z, lane}. Failed segments
     * fall back to a straight stop-to-stop interpolation (lane 0).
     */
    public static List<double[]> flattenToPoints(Graph graph, List<Platform> platforms,
            List<SegmentPath> segments, boolean circular) {
        final List<double[]> points = new ArrayList<>();
        final int stopCount = platforms.size();
        for (int i = 0; i < stopCount - 1; i++) {
            final SegmentPath segment = segments == null ? null : segments.get(i);
            if (segment == null) {
                // Straight fallback for this segment
                appendPoint(points, platforms.get(i).getMidPosition().getX(),
                        platforms.get(i).getMidPosition().getZ(), 0);
                appendPoint(points, platforms.get(i + 1).getMidPosition().getX(),
                        platforms.get(i + 1).getMidPosition().getZ(), 0);
            } else {
                appendSegmentGeometry(points, graph, segment, platforms.get(i), platforms.get(i + 1));
            }
        }
        if (circular && segments != null && isCircularComplete(segments)) {
            appendPoint(points, platforms.get(stopCount - 1).getMidPosition().getX(),
                    platforms.get(stopCount - 1).getMidPosition().getZ(), 0);
            appendPoint(points, platforms.get(0).getMidPosition().getX(), platforms.get(0).getMidPosition().getZ(), 0);
        }
        return points;
    }

    private static boolean isCircularComplete(List<SegmentPath> segments) {
        for (SegmentPath segment : segments) {
            if (segment == null) {
                return false;
            }
        }
        return true;
    }

    private static void appendSegmentGeometry(List<double[]> points, Graph graph, SegmentPath segment,
            Platform fromStop, Platform toStop) {
        final Rail fromRail = getPlatformRail(graph, fromStop);
        final Rail toRail = getPlatformRail(graph, toStop);
        if (fromRail == null || toRail == null) {
            return;
        }
        final int laneA = segment.edges.isEmpty() ? 0 : segment.edges.get(0).lane;
        final int laneB = segment.edges.isEmpty() ? laneA
                : segment.edges.get(segment.edges.size() - 1).lane;

        // Anchor onto the departure stop
        appendPoint(points, fromStop.getMidPosition().getX(), fromStop.getMidPosition().getZ(), laneA);

        // From-platform rail: from the end nearest the stop to the network entry node
        appendPlatformRail(points, graph, fromRail, fromStop, segment.entryNode, laneA);

        // Network rails in traversal order (each starts at the previous edge's end node)
        for (PathEdge pathEdge : segment.edges) {
            appendRailGeometry(points, graph.railById.get(pathEdge.hexId), pathEdge.reversed, pathEdge.lane, true);
        }

        // To-platform rail: from the network exit node to the end nearest the stop
        appendPlatformRailToStop(points, graph, toRail, toStop, segment.exitNode, laneB);

        // Anchor onto the arrival stop
        appendPoint(points, toStop.getMidPosition().getX(), toStop.getMidPosition().getZ(), laneB);
    }

    /** Platform rail sampled from the end nearest the stop towards {@code towards}. */
    private static void appendPlatformRail(List<double[]> points, Graph graph, Rail rail, Platform stop,
            Position towards, int lane) {
        drawFromNearestTo(points, graph, rail, stop, towards, lane);
    }

    /** Draw the rail from the end nearest the stop's mid position to {@code towards}. */
    private static void drawFromNearestTo(List<double[]> points, Graph graph, Rail rail, Platform stop,
            Position towards, int lane) {
        final Position[] ends = graph.railEnds.get(rail.getHexId());
        if (ends == null) {
            return;
        }
        final Position mid = stop.getMidPosition();
        final Position nearEnd = squareDistance(mid, ends[0]) <= squareDistance(mid, ends[1]) ? ends[0] : ends[1];
        if (towards.equals(nearEnd)) {
            return; // nothing to draw
        }
        // natural traversal runs ends[0] -> ends[1]
        final boolean natural = ends[0].equals(nearEnd) && ends[1].equals(towards);
        final boolean anti = ends[1].equals(nearEnd) && ends[0].equals(towards);
        if (!natural && !anti) {
            return; // towards is not an end of this rail - skip
        }
        appendRailGeometry(points, rail, anti, lane, true);
    }

    /** Platform rail sampled from {@code fromNode} to the end nearest the stop. */
    private static void appendPlatformRailToStop(List<double[]> points, Graph graph, Rail rail, Platform stop,
            Position fromNode, int lane) {
        final Position[] ends = graph.railEnds.get(rail.getHexId());
        if (ends == null) {
            return;
        }
        final Position mid = stop.getMidPosition();
        final Position nearEnd = squareDistance(mid, ends[0]) <= squareDistance(mid, ends[1]) ? ends[0] : ends[1];
        if (fromNode.equals(nearEnd)) {
            return; // nothing to draw
        }
        final boolean natural = ends[0].equals(fromNode) && ends[1].equals(nearEnd);
        final boolean anti = ends[1].equals(fromNode) && ends[0].equals(nearEnd);
        if (!natural && !anti) {
            return;
        }
        appendRailGeometry(points, rail, anti, lane, true);
    }

    /** Sample a full rail traversal; {@code reversed} flips the parameterization. */
    private static void appendRailGeometry(List<double[]> points, Rail rail, boolean reversed, int lane,
            boolean skipFirst) {
        final double length = rail.railMath.getLength();
        if (length <= 0) {
            return;
        }
        final int samples = (int) Math.min(64, Math.max(2, Math.ceil(length / SAMPLE_INTERVAL) + 1));
        for (int i = skipFirst ? 1 : 0; i <= samples; i++) {
            final double d = length * i / samples;
            final org.mtr.core.tool.Vector pos = rail.railMath.getPosition(d, reversed);
            appendPoint(points, pos.x(), pos.z(), lane);
        }
    }

    private static void appendPoint(List<double[]> points, double x, double z, int lane) {
        final double[] last = points.isEmpty() ? null : points.get(points.size() - 1);
        if (last != null && Math.abs(last[0] - x) < 1.0E-3 && Math.abs(last[1] - z) < 1.0E-3) {
            // Merge duplicate consecutive points, but keep the lane change
            last[2] = lane;
            return;
        }
        points.add(new double[]{x, z, lane});
    }

    private static final double SAMPLE_INTERVAL = 8.0;
}
