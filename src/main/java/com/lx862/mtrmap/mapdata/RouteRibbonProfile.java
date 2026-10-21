package com.lx862.mtrmap.mapdata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared endpoint lane boundaries; bundle changes taper over a short screen distance. */
public record RouteRibbonProfile(Map<String, Band> bands, double width, End start, End end, double taper) {
    public record Band(double left, double right) {}
    public record End(Map<String, Band> bands, double width) {}

    public static Map<String, RouteRibbonProfile> build(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, double scale) {
        final Map<String, End> sections = new HashMap<>();
        for (MapTrack track : tracks) {
            final List<TrackRoutePalette.Entry> entries = palette.getOrDefault(track.id, List.of());
            final double half = RouteRibbonGeometry.halfWidth(entries, scale);
            double weight = 0;
            for (var entry : entries) weight += entry.style().width;
            final Map<String, Band> bands = new HashMap<>();
            double left = -half;
            for (var entry : entries) {
                final double right = left + 2 * half * entry.style().width / weight;
                bands.put(entry.id(), new Band(left, right));
                left = right;
            }
            sections.put(track.id, new End(Map.copyOf(bands), half));
        }
        final Map<String, RouteRibbonProfile> result = new HashMap<>();
        for (MapTrack track : tracks) {
            final End own = sections.get(track.id);
            final var placement = patterns.getOrDefault(track.id, TrackPatternLayout.Placement.DEFAULT);
            result.put(track.id, new RouteRibbonProfile(own.bands, own.width,
                    endpoint(own, placement.start(), sections), endpoint(own, placement.end(), sections),
                    Math.min(track.length() / 2, 12 / scale)));
        }
        return result;
    }

    private static End endpoint(End own, TrackPatternLayout.Join join, Map<String, End> sections) {
        final End neighbor = join == null ? null : sections.get(join.neighborId());
        // Anchor the shared route at its position in the wider bundle. Disappearing
        // lanes end there; continuing lanes fan gently back toward their own centre.
        return neighbor != null && neighbor.width > own.width ? neighbor : own;
    }

    public Band at(String id, double left, double right, double distance, double length) {
        final Band local = new Band(left, right);
        if (taper <= 0) return local;
        final Band target;
        final double blend;
        if (distance < taper) {
            target = start.bands.getOrDefault(id, local);
            blend = 1 - distance / taper;
        } else if (length - distance < taper) {
            target = end.bands.getOrDefault(id, local);
            blend = 1 - (length - distance) / taper;
        } else return local;
        return new Band(left + (target.left - left) * blend, right + (target.right - right) * blend);
    }

    public double widthAt(double local, double distance, double length) {
        if (taper <= 0) return local;
        if (distance < taper) return local + Math.max(0, start.width - width) * (1 - distance / taper);
        if (length - distance < taper) return local + Math.max(0, end.width - width) * (1 - (length - distance) / taper);
        return local;
    }
}
