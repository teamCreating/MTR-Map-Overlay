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

    public record Placement(boolean reversed, double distance, double blockLength) {
        public static final Placement DEFAULT = new Placement(false, 0, BLOCK_LENGTH);
    }

    private record Endpoint(double x, double y, double z) {
        static Endpoint at(double[] point) {
            return new Endpoint(point[0] == 0 ? 0 : point[0], point.length >= 3 ? point[2] : Double.NaN,
                    point[1] == 0 ? 0 : point[1]);
        }
    }

    public static Map<String, Placement> build(List<MapTrack> tracks) {
        final List<MapTrack> ordered = tracks.stream().filter(track -> track.length() > 0)
                .sorted(Comparator.comparing(track -> track.id)).toList();
        final Map<Endpoint, List<MapTrack>> incident = new HashMap<>();
        for (MapTrack track : ordered) {
            incident.computeIfAbsent(start(track), ignored -> new ArrayList<>()).add(track);
            incident.computeIfAbsent(end(track), ignored -> new ArrayList<>()).add(track);
        }
        final Map<String, Placement> result = new HashMap<>();
        final Set<String> visited = new HashSet<>();
        // Open chains first so an internal rail cannot become an artificial phase reset.
        for (MapTrack track : ordered) {
            if (incident.get(start(track)).size() != 2 || incident.get(end(track)).size() != 2) {
                final Endpoint origin = incident.get(start(track)).size() != 2 ? start(track) : end(track);
                walk(track, origin, incident, visited, result);
            }
        }
        for (MapTrack track : ordered) {
            walk(track, start(track), incident, visited, result);
        }
        return Map.copyOf(result);
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
            if (next.size() != 2) {
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
        return Endpoint.at(track.points.getFirst());
    }

    private static Endpoint end(MapTrack track) {
        return Endpoint.at(track.points.getLast());
    }
}
