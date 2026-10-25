package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.mapdata.*;
import java.util.*;

/** Rebuild only on a committed view/source change; never rewrites the source cache. */
public final class ViewRenderer {
    private static MapDataCache.DimensionData source, cached;
    private static long revision = -1;
    private ViewRenderer() {}
    public static boolean trains(String dimension) { return ClientOverlayRuntime.INSTANCE.views().focus(dimension.replaceFirst("/", ":")).map(a -> a.request().showTrains()).orElse(true); }
    public static MapDataCache.DimensionData data(MapDataCache.DimensionData input) {
        var registry = ClientOverlayRuntime.INSTANCE.views();
        if (source == input && cached != null && revision == registry.revision()) return cached;
        var active = registry.active(input.dimensionId.replaceFirst("/", ":"));
        var focus = registry.focus(input.dimensionId.replaceFirst("/", ":")).orElse(null);
        if (active.isEmpty()) return input;
        var catalog = active.isEmpty() ? null : ClientOverlayRuntime.INSTANCE.catalog(active.getFirst().request().world());
        cached = build(input, active, focus, catalog);
        source = input; revision = registry.revision();
        return cached;
    }
    public static MapDataCache.DimensionData build(MapDataCache.DimensionData input, List<ViewRegistry.Active> active, ViewRegistry.Active focus) {
        return build(input, active, focus, null);
    }
    public static MapDataCache.DimensionData build(MapDataCache.DimensionData input, List<ViewRegistry.Active> active, ViewRegistry.Active focus, Catalog catalog) {
        ViewRequest request = focus == null ? null : focus.request();
        var styles = new HashMap<String, Style>(); active.forEach(a -> styles.putAll(a.request().routeStyles()));
        var rails = new ArrayList<MapTrack>();
        var palette = new HashMap<String, List<TrackRoutePalette.Entry>>();
        var offsets = new HashMap<String, TrackPatternLayout.Placement>();
        var byRail = new HashMap<String, List<MapRoute>>();
        input.routes.forEach(r -> r.trackIds.forEach(id -> byRail.computeIfAbsent(id, ignored -> new ArrayList<>()).add(r)));
        // v8 may deduplicate same-colour routes. The native catalog remains authoritative for a requested route.
        if (catalog != null) for (var route : catalog.routes()) {
            var railIds = new HashSet<String>();
            if (request != null) {
                request.spans().stream().filter(s -> s.routeId().equals(route.id())).forEach(s -> railIds.add(s.rail().railId()));
                if (request.wholeRoutes().contains(route.id())) route.paths().forEach(p -> p.legs().forEach(l -> l.rails().forEach(r -> railIds.add(r.railId()))));
            }
            for (String id : railIds) {
                var members = byRail.computeIfAbsent(id, ignored -> new ArrayList<>());
                if (members.stream().noneMatch(r -> r.id.equals(route.id()))) members.add(new MapRoute(route.id(), route.name(), route.color(), RouteLineStyle.fromName(route.type()), false, List.of(), List.of(id)));
            }
        }
        var selections = new HashMap<String, List<SelectedSpan>>();
        if (request != null) request.spans().forEach(s -> selections.computeIfAbsent(s.rail().railId(), ignored -> new ArrayList<>()).add(s));
        for (var rail : input.tracks) {
            var selected = selections.getOrDefault(rail.id, List.of());
            var cuts = new TreeSet<Double>(); cuts.add(0.0); cuts.add(1.0);
            selected.forEach(s -> { cuts.add(Math.min(s.rail().from(), s.rail().to())); cuts.add(Math.max(s.rail().from(), s.rail().to())); });
            var ranges = new ArrayList<>(cuts);
            for (int i = 0; i + 1 < ranges.size(); i++) {
                double from = ranges.get(i), to = ranges.get(i + 1), middle = (from + to) / 2;
                boolean physical = selected.stream().anyMatch(s -> s.routeId().isEmpty() && covers(s, middle));
                var entries = new ArrayList<TrackRoutePalette.Entry>();
                for (var route : byRail.getOrDefault(rail.id, List.of())) {
                    boolean match = request == null || physical || request.wholeRoutes().contains(route.id)
                            || selected.stream().anyMatch(s -> s.routeId().equals(route.id) && covers(s, middle));
                    if (!match && request.unmatched() == Unmatched.HIDE) continue;
                    var style = styles.getOrDefault(route.id, Style.inherit());
                    double alpha = style.opacity() * (!match && request.unmatched() == Unmatched.DIM ? 0.15 : 1);
                    boolean white = style.pattern() == Pattern.COLOUR_WHITE || style.pattern() == Pattern.INHERIT && route.style == RouteLineStyle.HIGH_SPEED;
                    var entry = new TrackRoutePalette.Entry(route.id, route.name, style.color().orElse(route.color), route.style, style.widthScale(), alpha, white);
                    // Preserve same-appearance dedup while keeping selected route identities authoritative.
                    if (entries.stream().noneMatch(e -> e.color() == entry.color() && e.style() == entry.style() && e.opacity() == entry.opacity() && e.widthScale() == entry.widthScale() && e.colourWhite() == entry.colourWhite())) entries.add(entry);
                }
                if (request != null && request.unmatched() == Unmatched.HIDE && entries.isEmpty() && !physical) continue;
                String id = cuts.size() == 2 ? rail.id : rail.id + "@" + from + ":" + to;
                MapTrack part = cuts.size() == 2 ? rail : new SpanMapTrack(rail, from, to, id);
                rails.add(part); palette.put(id, List.copyOf(entries));
                var p = input.trackPatterns.getOrDefault(rail.id, TrackPatternLayout.Placement.DEFAULT);
                offsets.put(id, new TrackPatternLayout.Placement(p.reversed(), p.distance() + (p.reversed() ? 1 - to : from) * rail.length(), p.blockLength(), null, null));
            }
        }
        // Compute shared endpoint normals for the selected geometry, then keep original white-block phase.
        var joins = TrackPatternLayout.build(rails, palette);
        offsets.replaceAll((id, p) -> { var j = joins.get(id); return j == null ? p : new TrackPatternLayout.Placement(p.reversed(), p.distance(), p.blockLength(), j.start(), j.end()); });
        var landmarks = request == null || request.stationIds().isEmpty() ? input.landmarks : input.landmarks.stream().filter(l -> {
            if (!l.id().startsWith("station:")) return false;
            try { return request.stationIds().contains(Long.toUnsignedString(Long.parseUnsignedLong(l.id().substring(8), 16), 16)); }
            catch (NumberFormatException ignored) { return false; }
        }).toList();
        return new MapDataCache.DimensionData(input.dimensionId, input.routes, rails, landmarks, input.version, palette, offsets);
    }
    private static boolean covers(SelectedSpan s, double at) { return at >= Math.min(s.rail().from(), s.rail().to()) && at <= Math.max(s.rail().from(), s.rail().to()); }
    public static void clear() { source = cached = null; revision = -1; }
}
