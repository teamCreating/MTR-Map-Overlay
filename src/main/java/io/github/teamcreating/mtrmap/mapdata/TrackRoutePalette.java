package io.github.teamcreating.mtrmap.mapdata;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a deterministic, de-duplicated route-color palette for each physical rail. */
public final class TrackRoutePalette {

    private TrackRoutePalette() {
    }

    public static Map<String, List<Entry>> build(List<MapRoute> routes) {
        // Outbound/return services commonly have different Core route IDs but
        // the same colour/style. Drawing both artificially doubles the width.
        final Map<Appearance, Entry> appearances = new HashMap<>();
        for (MapRoute route : routes) {
            final Entry candidate = new Entry(route.id, route.name, route.color, route.style);
            appearances.merge(new Appearance(route.color, route.style), candidate,
                    (a, b) -> a.id.compareTo(b.id) <= 0 ? a : b);
        }
        final Map<String, Map<String, Entry>> grouped = new HashMap<>();
        for (MapRoute route : routes) {
            final Entry entry = appearances.get(new Appearance(route.color, route.style));
            for (String trackId : route.trackIds) {
                grouped.computeIfAbsent(trackId, ignored -> new LinkedHashMap<>()).putIfAbsent(entry.id, entry);
            }
        }
        final Map<String, List<Entry>> result = new HashMap<>();
        grouped.forEach((trackId, byRoute) -> result.put(trackId, byRoute.values().stream()
                .sorted(Comparator.comparing(Entry::id).thenComparing(Entry::name)).toList()));
        return result;
    }

    public record Entry(String id, String name, int color, RouteLineStyle style) {
    }

    private record Appearance(int color, RouteLineStyle style) {}
}
