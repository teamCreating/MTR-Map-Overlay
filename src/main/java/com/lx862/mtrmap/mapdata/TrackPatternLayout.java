package com.lx862.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable direction and pattern phase along chains of physical rails, prepared once per snapshot. */
public final class TrackPatternLayout {
    public static final double BLOCK_LENGTH = 16;

    private TrackPatternLayout() {
    }

    public record Join(double normalX, double normalZ, String neighborId) {}

    public record Placement(boolean reversed, double distance, double blockLength, Join start, Join end) {
        public Placement(boolean reversed, double distance, double blockLength) {
            this(reversed, distance, blockLength, null, null);
        }
        public static final Placement DEFAULT = new Placement(false, 0, BLOCK_LENGTH);

        public double normalX(MapTrack track, int point) {
            final Join join = point == 0 ? start : point == track.points.size() - 1 ? end : null;
            return join == null ? track.normalX[point] : join.normalX;
        }

        public double normalZ(MapTrack track, int point) {
            final Join join = point == 0 ? start : point == track.points.size() - 1 ? end : null;
            return join == null ? track.normalZ[point] : join.normalZ;
        }
    }

    private record Endpoint(double x, double y, double z) {
        static Endpoint at(double[] point) {
            // RailMath endpoints have tiny trig rounding differences. Never use raw
            // doubles as graph identity, or every curve can reset lanes and stripes.
            return new Endpoint(snap(point[0]), point.length >= 3 ? snap(point[2]) : Double.NaN, snap(point[1]));
        }

        private static double snap(double value) {
            final double snapped = Math.rint(value * 10000) / 10000;
            return snapped == 0 ? 0 : snapped;
        }
    }

    public static Map<String, Placement> build(List<MapTrack> tracks) {
        return build(tracks, Map.of());
    }

    public static Map<String, Placement> build(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette) {
        final List<MapTrack> ordered = tracks.stream().filter(track -> track.length() > 0)
                .sorted(Comparator.comparing(track -> track.id)).toList();
        final Map<Endpoint, List<MapTrack>> incident = new HashMap<>();
        for (MapTrack track : ordered) {
            incident.computeIfAbsent(start(track), ignored -> new ArrayList<>()).add(track);
            incident.computeIfAbsent(end(track), ignored -> new ArrayList<>()).add(track);
        }
        final Map<Endpoint, List<MapTrack>> continuations = new HashMap<>();
        incident.forEach((point, rails) -> continuations.put(point, continuation(point, rails, palette)));
        final Map<String, Placement> result = new HashMap<>();
        final Set<String> visited = new HashSet<>();
        // Open chains first so an internal rail cannot become an artificial phase reset.
        for (MapTrack track : ordered) {
            if (!continuations.get(start(track)).contains(track) || !continuations.get(end(track)).contains(track)) {
                final Endpoint origin = !continuations.get(start(track)).contains(track) ? start(track) : end(track);
                walk(track, origin, continuations, visited, result);
            }
        }
        for (MapTrack track : ordered) {
            walk(track, start(track), continuations, visited, result);
        }
        // Share the same miter cross-section across physical rail boundaries.
        // Per-rail endpoint tangents otherwise leave cracks and flip adjacent lanes.
        for (var entry : continuations.entrySet()) {
            if (entry.getValue().size() != 2) continue;
            final MapTrack a = entry.getValue().get(0), b = entry.getValue().get(1);
            if (a == b) continue;
            final boolean aStart = start(a).equals(entry.getKey()), bStart = start(b).equals(entry.getKey());
            final double[] ta = outward(a, aStart), tb = outward(b, bStart);
            final double nx = ta[1] - tb[1], nz = tb[0] - ta[0];
            final double length = Math.hypot(nx, nz);
            if (length < 1.0E-8) continue;
            final double dot = (nx * -tb[1] + nz * tb[0]) / length;
            final double factor = Math.min(2, 1 / Math.max(0.5, dot)) / length;
            attach(result, a, aStart, new Join(nx * factor * (aStart ? -1 : 1),
                    nz * factor * (aStart ? -1 : 1), b.id));
            attach(result, b, bStart, new Join(nx * factor * (bStart ? 1 : -1),
                    nz * factor * (bStart ? 1 : -1), a.id));
        }
        return Map.copyOf(result);
    }

    private static List<MapTrack> continuation(Endpoint point, List<MapTrack> rails,
            Map<String, List<TrackRoutePalette.Entry>> palette) {
        if (rails.size() == 2) return rails;
        List<MapTrack> best = List.of();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < rails.size(); i++) {
            final MapTrack a = rails.get(i);
            final double[] ta = outward(a, start(a).equals(point));
            for (int j = i + 1; j < rails.size(); j++) {
                final MapTrack b = rails.get(j);
                final double[] tb = outward(b, start(b).equals(point));
                final double dot = ta[0] * tb[0] + ta[1] * tb[1];
                if (dot > -0.5) continue;
                int common = 0;
                for (var route : palette.getOrDefault(a.id, List.of())) {
                    if (palette.getOrDefault(b.id, List.of()).stream().anyMatch(other -> other.id().equals(route.id()))) common++;
                }
                final double score = common * 4 - dot;
                if (score > bestScore) {
                    bestScore = score;
                    best = List.of(a, b);
                }
            }
        }
        return best;
    }

    private static double[] outward(MapTrack track, boolean start) {
        if (track.curve != null) {
            final int i = start ? 0 : track.points.size() - 1;
            return new double[] {track.normalZ[i] * (start ? 1 : -1), track.normalX[i] * (start ? -1 : 1)};
        }
        final double[] p = start ? track.points.get(0) : track.points.get(track.points.size() - 1);
        for (int i = 1; i < track.points.size(); i++) {
            final double[] q = track.points.get(start ? i : track.points.size() - 1 - i);
            final double length = Math.hypot(q[0] - p[0], q[1] - p[1]);
            if (length > 1.0E-8) return new double[] {(q[0] - p[0]) / length, (q[1] - p[1]) / length};
        }
        return new double[] {0, 0};
    }

    private static void attach(Map<String, Placement> result, MapTrack track, boolean start, Join join) {
        final Placement p = result.get(track.id);
        result.put(track.id, new Placement(p.reversed, p.distance, p.blockLength,
                start ? join : p.start, start ? p.end : join));
    }

    private static void walk(MapTrack track, Endpoint origin, Map<Endpoint, List<MapTrack>> incident,
            Set<String> visited, Map<String, Placement> result) {
        Endpoint point = origin;
        double distance = 0;
        final List<String> chain = new ArrayList<>();
        while (visited.add(track.id)) {
            final boolean reversed = !start(track).equals(point);
            result.put(track.id, new Placement(reversed, distance, BLOCK_LENGTH));
            chain.add(track.id);
            distance += track.length();
            point = reversed ? start(track) : end(track);
            final List<MapTrack> next = incident.get(point);
            if (next.size() != 2 || !next.contains(track)) {
                break;
            }
            track = next.get(0).id.equals(track.id) ? next.get(1) : next.get(0);
        }
        if (!chain.isEmpty() && point.equals(origin)) {
            // Fit complete coloured/white pairs to a closed loop, avoiding a seam at its root.
            final double block = distance / (2 * Math.max(1, Math.round(distance / (2 * BLOCK_LENGTH))));
            for (String id : chain) {
                final Placement placement = result.get(id);
                result.put(id, new Placement(placement.reversed, placement.distance, block));
            }
        }
    }

    private static Endpoint start(MapTrack track) {
        return Endpoint.at(track.points.get(0));
    }

    private static Endpoint end(MapTrack track) {
        return Endpoint.at(track.points.get(track.points.size() - 1));
    }
}
