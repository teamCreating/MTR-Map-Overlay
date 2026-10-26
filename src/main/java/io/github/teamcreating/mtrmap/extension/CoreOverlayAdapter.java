package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import org.mtr.core.data.Position;
import org.mtr.core.data.TransportMode;
import org.mtr.core.operation.ArrivalsRequest;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongImmutableList;
import java.util.*;

/** Simulator-thread reads only. No independent timetable, routing or motion simulation. */
public final class CoreOverlayAdapter {
    private CoreOverlayAdapter() {}
    private static String id(long id) { return Long.toUnsignedString(id, 16); }
    private static Point point(Position p) { return new Point(p.getX(), p.getY(), p.getZ()); }
    public static Catalog catalog(Simulator simulator, WorldRef world, long base) {
        var stations = new ArrayList<Station>();
        var platforms = new ArrayList<Platform>();
        var platformStations = new HashMap<Long, String>();
        for (var station : simulator.stations) {
            var ids = new ArrayList<String>();
            for (var platform : station.savedRails) {
                ids.add(id(platform.getId())); platformStations.put(platform.getId(), id(station.getId()));
                platforms.add(new Platform(id(platform.getId()), id(station.getId()), platform.getName(), point(platform.getMidPosition())));
            }
            stations.add(new Station(id(station.getId()), station.getName(), point(station.getCenter()), ids));
        }
        var routes = new ArrayList<io.github.teamcreating.mtrmap.api.MtrMapApi.Route>();
        for (var route : simulator.routes) {
            if (route.getTransportMode() != TransportMode.TRAIN) continue;
            var stops = new ArrayList<Stop>();
            for (var stop : route.getRoutePlatforms()) {
                var platform = stop.getPlatform();
                stops.add(new Stop(stops.size(), platform == null ? "" : id(platform.getId()),
                        platform == null ? "" : platformStations.getOrDefault(platform.getId(), ""), stop.getCustomDestination()));
            }
            routes.add(new io.github.teamcreating.mtrmap.api.MtrMapApi.Route(id(route.getId()), route.getName(), route.getRouteNumber(),
                    route.getColor(), route.getRouteType().name(), stops, paths(route, stops)));
        }
        long revision = base;
        for (Object value : List.of(stations, platforms, routes)) revision = (revision ^ value.hashCode()) * 0x100000001b3L;
        return new Catalog(world, revision, base, Status.READY, stations, platforms, routes);
    }
    /** Match the complete native stop sequence, never an intersection of route/platform memberships. */
    private static List<PathVariant> paths(org.mtr.core.data.Route route, List<Stop> stops) {
        if (stops.size() < 2 || stops.stream().anyMatch(s -> s.platformId().isEmpty())) return List.of();
        var variants = new ArrayList<PathVariant>();
        for (var depot : route.depots) {
            var path = depot.getPath();
            if (path.size() > 20000 || variants.size() >= 128) break;
            var indices = new ArrayList<Integer>();
            for (int i = 0; i < path.size(); i++) {
                var p = path.get(i);
                if (p.getSavedRailBaseId() != 0 && (indices.isEmpty()
                        || path.get(indices.get(indices.size() - 1)).getSavedRailBaseId() != p.getSavedRailBaseId()
                        || path.get(indices.get(indices.size() - 1)).getStopIndex() != p.getStopIndex())) indices.add(i);
            }
            for (int start = 0; start + stops.size() <= indices.size() && variants.size() < 128; start++) {
                boolean matches = true;
                for (int j = 0; j < stops.size(); j++)
                    if (!id(path.get(indices.get(start + j)).getSavedRailBaseId()).equals(stops.get(j).platformId())) { matches = false; break; }
                if (!matches) continue;
                var legs = new ArrayList<Leg>();
                for (int j = 0; j + 1 < stops.size(); j++) {
                    var rails = new ArrayList<RailSpan>();
                    int first = indices.get(start + j), last = indices.get(start + j + 1);
                    for (int k = first; k <= last; k++) {
                        var p = path.get(k); var rail = p.getRail();
                        if (rail == null || !rail.isValid() || rail.railMath == null) { matches = false; break; }
                        double from = k == first ? 0.5 : 0, to = k == last ? 0.5 : 1;
                        rails.add(new RailSpan(rail.getHexId(), p.reversePositions ? 1 - from : from, p.reversePositions ? 1 - to : to));
                    }
                    if (!matches) break;
                    legs.add(new Leg(j, j + 1, rails));
                }
                if (matches) variants.add(new PathVariant(id(depot.getId()) + ":" + start, legs));
            }
        }
        return List.copyOf(variants);
    }
    public static Board board(Simulator simulator, WorldRef world, String stationId, long generation) {
        long now = simulator.getCurrentMillis();
        var station = simulator.stationIdMap.get(Long.parseUnsignedLong(stationId, 16));
        if (station == null) return new Board(world, stationId, generation, Status.NOT_FOUND, now, now, List.of(), List.of());
        if (station.savedRails.size() > 128) return new Board(world, stationId, generation, Status.LIMITED, now, now, List.of(), List.of());
        long[] ids = station.savedRails.stream().mapToLong(p -> p.getId()).toArray();
        var services = new LinkedHashMap<String, Service>();
        for (var platform : station.savedRails) for (var route : platform.routes) {
            if (route.getTransportMode() == TransportMode.TRAIN) services.put(id(route.getId()) + ":" + id(platform.getId()),
                    new Service(id(route.getId()), id(platform.getId()), route.getName(), route.getColor(), platform.getName()));
        }
        var entries = new ArrayList<Arrival>();
        boolean complete = false;
        for (int count : new int[]{10, 40, 160}) {
            var response = new ArrivalsRequest(new LongImmutableList(ids), count, 4097).getArrivals(simulator);
            now = response.getCurrentTime(); entries.clear();
            var seen = new HashSet<String>();
            var groups = new HashSet<String>();
            for (var a : response.getArrivals()) {
                String key = id(a.getRouteId()) + ":" + id(a.getPlatformId()) + ":" + a.getDestination() + ":" + a.getCircularState();
                if (a.getDeparture() < now || !groups.add(key)) continue;
                seen.add(id(a.getRouteId()) + ":" + id(a.getPlatformId()));
                entries.add(new Arrival(id(a.getRouteId()), a.getRouteName(), a.getRouteColor(), id(a.getPlatformId()), a.getPlatformName(),
                        a.getDestination(), a.getArrival(), a.getDeparture(), a.getDeviation(), a.getRealtime(), a.getIsTerminating()));
            }
            complete = seen.containsAll(services.keySet()) && response.getArrivals().size() < 4097;
            if (complete || entries.size() >= 4096) break;
        }
        return new Board(world, stationId, generation, complete ? Status.READY : Status.LIMITED, now, now,
                new ArrayList<>(services.values()), entries);
    }
}
