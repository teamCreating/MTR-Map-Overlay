package io.github.teamcreating.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Both sides of a physical join agree on route boundaries and the actual backing width. */
public record RouteRibbonProfile(Map<String, Band> bands, double width, double backingWidth,
        End start, End end, double taper, List<Double> cuts) {
    public record Band(double left, double right) {}
    public record End(Map<String, Band> bands, double width, double backingWidth) {}

    public static Map<String, RouteRibbonProfile> build(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, double scale) {
        return build(tracks, palette, patterns, scale, true);
    }

    public static Map<String, RouteRibbonProfile> build(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, double scale, boolean showTracks) {
        final Map<String, End> sections = new HashMap<>();
        for (MapTrack track : tracks) {
            final var entries = palette.getOrDefault(track.id, List.of());
            final double half = RouteRibbonGeometry.halfWidth(entries, scale);
            sections.put(track.id, new End(partition(entries, half), half,
                    RailRenderStyle.backingWidth(half, scale, showTracks)));
        }
        final Map<String, RouteRibbonProfile> result = new HashMap<>();
        for (MapTrack track : tracks) {
            final End own = sections.get(track.id);
            final var placement = patterns.getOrDefault(track.id, TrackPatternLayout.Placement.DEFAULT);
            final End start = endpoint(track.id, own, placement.start(), sections, palette, scale, showTracks);
            final End end = endpoint(track.id, own, placement.end(), sections, palette, scale, showTracks);
            final double taper = Math.min(track.length() / 2, Math.max(12 / scale, 6 * own.backingWidth));
            final List<Double> cuts = new ArrayList<>();
            sampleTaper(cuts, own, start, taper, 0, 1, scale);
            sampleTaper(cuts, own, end, taper, track.length(), -1, scale);
            result.put(track.id, new RouteRibbonProfile(own.bands, own.width, own.backingWidth,
                    start, end, taper, cuts.stream().distinct().sorted().toList()));
        }
        return result;
    }

    private static Map<String, Band> partition(List<TrackRoutePalette.Entry> entries, double half) {
        double weight = 0;
        for (var entry : entries) weight += entry.style().width;
        final Map<String, Band> result = new HashMap<>();
        double left = -half;
        for (int i = 0; i < entries.size(); i++) {
            final var entry = entries.get(i);
            final double right = i + 1 == entries.size() ? half : left + 2 * half * entry.style().width / weight;
            result.put(entry.id(), new Band(left, right));
            left = right;
        }
        return Map.copyOf(result);
    }

    private static End endpoint(String id, End own, TrackPatternLayout.Join join, Map<String, End> sections,
            Map<String, List<TrackRoutePalette.Entry>> palette, double scale, boolean showTracks) {
        final End neighbor = join == null ? null : sections.get(join.neighborId());
        if (neighbor == null || own.backingWidth == 0 || neighbor.backingWidth == 0) return own;
        final var entries = palette.getOrDefault(id, List.of());
        final var common = entries.stream().filter(e -> neighbor.bands.containsKey(e.id())).toList();
        if (!common.isEmpty()) {
            // Continuing routes occupy the complete common section. Routes ending
            // here contract to a boundary BEFORE the node, rather than leaving a lip.
            final double half = RouteRibbonGeometry.halfWidth(common, scale);
            final Map<String, Band> shared = new HashMap<>(partition(common, half));
            double boundary = -half;
            for (var entry : entries) {
                final Band band = shared.get(entry.id());
                if (band == null) shared.put(entry.id(), new Band(boundary, boundary));
                else boundary = band.right;
            }
            return new End(Map.copyOf(shared), half, RailRenderStyle.backingWidth(half, scale, showTracks));
        }
        // A colour/style change still has one silhouette. An uncoloured rail has
        // a nonzero physical backing; never add route width to that width again.
        final double backing = Math.min(own.backingWidth, neighbor.backingWidth);
        final double half = own.width > 0 && neighbor.width > 0 ? Math.min(own.width, neighbor.width)
                : Math.max(0, backing - RailRenderStyle.shoulder(scale));
        return new End(partition(entries, Math.min(own.width, half)), Math.min(own.width, half), backing);
    }

    private static void sampleTaper(List<Double> cuts, End own, End target, double taper,
            double origin, int direction, double scale) {
        double delta = Math.abs(own.backingWidth - target.backingWidth);
        for (var entry : own.bands.entrySet()) {
            final Band to = target.bands.getOrDefault(entry.getKey(), entry.getValue());
            delta = Math.max(delta, Math.max(Math.abs(to.left - entry.getValue().left),
                    Math.abs(to.right - entry.getValue().right)));
        }
        if (delta < 1E-9 || taper <= 0) return;
        // Smoothstep interpolation error is at most .75*delta/n² per interval.
        // Bound work even for pathological zoom; these points are static-cache work.
        final int samples = Math.max(4, Math.min(64, (int) Math.ceil(Math.sqrt(.75 * delta * scale / .06))));
        for (int i = 1; i <= samples; i++) cuts.add(origin + direction * taper * i / samples);
    }

    public Band at(String id, double left, double right, double distance, double length) {
        final Band local = new Band(left, right);
        final End target = distance < taper ? start : length - distance < taper ? end : null;
        if (target == null || taper <= 0) return local;
        final Band to = target.bands.getOrDefault(id, local);
        final double blend = blend(Math.min(distance, length - distance));
        return new Band(left + (to.left - left) * blend, right + (to.right - right) * blend);
    }

    public double widthAt(double local, double distance, double length) {
        final End target = distance < taper ? start : length - distance < taper ? end : null;
        return target == null || taper <= 0 ? local
                : local + (target.backingWidth - local) * blend(Math.min(distance, length - distance));
    }

    private double blend(double distance) {
        final double t = Math.max(0, Math.min(1, distance / taper));
        return 1 - t * t * (3 - 2 * t);
    }
}
