package com.lx862.mtrmap.mapdata;

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
        final Map<String, Map<String, Entry>> grouped = new HashMap<>();
        for (MapRoute route : routes) {
            final Entry entry = new Entry(route.id, route.name, route.color);
            for (String trackId : route.trackIds) {
                grouped.computeIfAbsent(trackId, ignored -> new LinkedHashMap<>()).putIfAbsent(route.id, entry);
            }
        }
        final Map<String, List<Entry>> result = new HashMap<>();
        grouped.forEach((trackId, byRoute) -> result.put(trackId, byRoute.values().stream()
                .sorted(Comparator.comparing(Entry::id).thenComparing(Entry::name)).toList()));
        return result;
    }

    public record Entry(String id, String name, int color) {
    }
}
