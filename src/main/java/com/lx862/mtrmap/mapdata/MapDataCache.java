package com.lx862.mtrmap.mapdata;

import com.lx862.mtrmap.MTRMap;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.SimplifiedRoutePlatform;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.data.Depot;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.client.MinecraftClientData;
import org.mtr.data.VehicleExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central access point for map path-layer data, keyed by dimension id
 * (MTR world-id format, e.g. "minecraft/overworld").
 *
 * <p>Resolution order per dimension:</p>
 * <ol>
 *   <li>Server-synced full-network data ({@code SERVER_DATA}, filled by the
 *       network sync when the server runs this mod) - covers the whole
 *       network, Create-style. Route colors here are painted along MTR's own
 *       generated driving paths.</li>
 *   <li>Fallback: whatever MTR has synced to the client
 *       ({@link MinecraftClientData}), which is limited to the area around
 *       the player (MTR only syncs data within render distance). Route colors
 *       come from running vehicles' real driving paths.</li>
 * </ol>
 */
public class MapDataCache {

    /** Simple per-dimension bundle of render-ready data. */
    public static class DimensionData {

        public final String dimensionId;
        public final List<MapRoute> routes;
        public final List<MapTrack> tracks;
        public final List<MapLandmark> landmarks;
        public final Map<String, List<TrackRoutePalette.Entry>> routePalette;
        /** Monotonic version counter, used to decide when to rebuild caches. */
        public final long version;

        public DimensionData(String dimensionId, List<MapRoute> routes, List<MapTrack> tracks,
                List<MapLandmark> landmarks, long version) {
            this.dimensionId = dimensionId;
            this.routes = routes;
            this.tracks = tracks;
            this.landmarks = landmarks;
            this.routePalette = TrackRoutePalette.build(routes);
            this.version = version;
        }

        public boolean isEmpty() {
            return routes.isEmpty() && tracks.isEmpty() && landmarks.isEmpty();
        }
    }

    private static final DimensionData EMPTY = new DimensionData("", List.of(), List.of(), List.of(), 0);

    private static final Object2ObjectOpenHashMap<String, DimensionData> SERVER_DATA = new Object2ObjectOpenHashMap<>();
    /** Bumped whenever MTR pushes new client data, invalidates the client-side cache. */
    private static volatile long clientDataVersion = 0;
    private static volatile DimensionData clientDataBuilt = null;

    public static void onClientDataSynced() {
        clientDataVersion++;
    }

    /**
     * Store a full-network snapshot received from the (modded) server.
     * Called on the network thread; volatile-safe swap into the map.
     */
    public static void putServerData(String dimension, DimensionData data) {
        synchronized (SERVER_DATA) {
            SERVER_DATA.put(dimension, data);
        }
    }

    public static void clearServerData() {
        synchronized (SERVER_DATA) {
            SERVER_DATA.clear();
        }
    }

    public static boolean hasServerData(String dimension) {
        synchronized (SERVER_DATA) {
            return SERVER_DATA.containsKey(dimension);
        }
    }

    /**
     * Get render data for a dimension. Prefers server-synced full-network
     * data; falls back to the radius-limited MTR client data.
     */
    public static DimensionData get(String dimension) {
        synchronized (SERVER_DATA) {
            final DimensionData serverData = SERVER_DATA.get(dimension);
            if (serverData != null) {
                return serverData;
            }
        }
        return getClientData();
    }

    /**
     * Build (with memoization) route/track data from MTR's client-side data.
     * Note: MTR only syncs data within render distance of the player, so this
     * fallback only covers the area around the player.
     */
    public static DimensionData getClientData() {
        final long version = clientDataVersion;
        DimensionData data = clientDataBuilt;
        if (data != null && data.version == version) {
            return data;
        }
        data = buildClientData(version);
        clientDataBuilt = data;
        return data;
    }

    private static DimensionData buildClientData(long version) {
        final List<MapRoute> routes = new ArrayList<>();
        final List<MapTrack> tracks = new ArrayList<>();
        final List<MapLandmark> landmarks = new ArrayList<>();
        final Map<String, MapTrack> sampledTracks = new HashMap<>();

        try {
            // Aggregate both the live streaming instance and the dashboard instance,
            // mirroring how both map overlays collect their fallback data.
            final Map<Long, Platform> allPlatforms = new HashMap<>();
            final List<SimplifiedRoute> allRoutes = new ArrayList<>();
            final Map<String, Station> allStations = new java.util.LinkedHashMap<>();
            final Map<String, Depot> allDepots = new java.util.LinkedHashMap<>();
            try {
                final MinecraftClientData instance = MinecraftClientData.getInstance();
                if (instance != null) {
                    allPlatforms.putAll(instance.platformIdMap);
                    allRoutes.addAll(instance.simplifiedRoutes);
                    instance.stations.forEach(station -> allStations.put(station.getHexId(), station));
                    instance.depots.forEach(depot -> allDepots.put(depot.getHexId(), depot));
                    collectClientTracks(instance, tracks, sampledTracks);
                }
                final MinecraftClientData dashboard = MinecraftClientData.getDashboardInstance();
                if (dashboard != null) {
                    allPlatforms.putAll(dashboard.platformIdMap);
                    allRoutes.addAll(dashboard.simplifiedRoutes);
                    dashboard.stations.forEach(station -> allStations.put(station.getHexId(), station));
                    dashboard.depots.forEach(depot -> allDepots.put(depot.getHexId(), depot));
                }
            } catch (Throwable e) {
                MTRMap.LOGGER.error("[MTRMap] Error accessing MTR client datasets", e);
            }

            // Pure-client fallback for route colors: running vehicles carry
            // MTR's own real driving paths (immutablePath). Group them by
            // route name/color so the map dyes the rails the trains use.
            try {
                final MinecraftClientData live = MinecraftClientData.getInstance();
                if (live != null) {
                    final Map<String, List<MapTrack>> vehicleTracks = new HashMap<>();
                    final Map<String, java.util.Set<String>> vehicleRailIds = new HashMap<>();
                    for (VehicleExtension vehicle : live.vehicles) {
                        final String routeName = vehicle.vehicleExtraData.getThisRouteName();
                        final int routeColor = vehicle.vehicleExtraData.getThisRouteColor();
                        final String key = routeName + "#" + routeColor;
                        final List<MapTrack> routeTracks = vehicleTracks.computeIfAbsent(key,
                                k -> new ArrayList<>());
                        for (org.mtr.core.data.PathData pathData : vehicle.vehicleExtraData.immutablePath) {
                            final org.mtr.core.data.Rail rail = pathData.getRail();
                            if (rail != null && vehicleRailIds.computeIfAbsent(key, ignored -> new java.util.HashSet<>())
                                    .add(rail.getHexId())) {
                                final MapTrack track = sampledTracks.get(rail.getHexId());
                                if (track != null) {
                                    routeTracks.add(track);
                                }
                            }
                        }
                    }
                    for (Map.Entry<String, List<MapTrack>> entry : vehicleTracks.entrySet()) {
                        final String key = entry.getKey();
                        final int sep = key.lastIndexOf('#');
                        routes.add(MapRoute.ofTracks("vehicle:" + key, key.substring(0, sep),
                                Integer.parseInt(key.substring(sep + 1)), new ArrayList<>(), entry.getValue()));
                    }
                }
            } catch (Throwable e) {
                MTRMap.LOGGER.debug("[MTRMap] Failed to collect vehicle paths: {}", e.getMessage());
            }

            for (SimplifiedRoute route : allRoutes) {
                try {
                    final List<SimplifiedRoutePlatform> routePlatforms = route.getPlatforms();
                    if (routePlatforms == null || routePlatforms.size() < 2) {
                        continue;
                    }

                    final List<MapRoute.Stop> stops = new ArrayList<>(routePlatforms.size());
                    boolean allStopsResolved = true;
                    for (SimplifiedRoutePlatform routePlatform : routePlatforms) {
                        final Platform platform = allPlatforms.get(routePlatform.getPlatformId());
                        if (platform == null) {
                            allStopsResolved = false;
                            break;
                        }
                        final Position pos = platform.getMidPosition();
                        stops.add(new MapRoute.Stop(pos.getX(), pos.getZ(),
                                routePlatform.getStationName(), routePlatform.getDestination()));
                    }

                    if (allStopsResolved) {
                        final boolean circular = route.getCircularState() == Route.CircularState.CLOCKWISE
                                || route.getCircularState() == Route.CircularState.ANTICLOCKWISE;
                        routes.add(new MapRoute(Long.toHexString(route.getId()), route.getName(), route.getColor(),
                                circular, stops, List.of()));
                    }
                } catch (Throwable e) {
                    MTRMap.LOGGER.debug("[MTRMap] Failed to build map data for route: {}", e.getMessage());
                }
            }

            collectClientLandmarks(allStations.values(), allDepots.values(), allRoutes, landmarks);
        } catch (Throwable e) {
            // Never let data collection break the map render
            MTRMap.LOGGER.debug("[MTRMap] Error building client map data: {}", e.getMessage());
        }

        return new DimensionData("", routes, tracks, landmarks, version);
    }

    private static void collectClientLandmarks(Iterable<Station> stations, Iterable<Depot> depots,
            List<SimplifiedRoute> routes, List<MapLandmark> landmarks) {
        final Map<Long, List<String>> platformRoutes = new HashMap<>();
        for (SimplifiedRoute route : routes) {
            for (SimplifiedRoutePlatform platform : route.getPlatforms()) {
                String label = route.getName();
                if (platform.getDestination() != null && !platform.getDestination().isEmpty()) {
                    label += "→" + platform.getDestination();
                }
                platformRoutes.computeIfAbsent(platform.getPlatformId(), ignored -> new ArrayList<>()).add(label);
            }
        }
        for (Station station : stations) {
            long totalY = 0;
            int count = 0;
            final java.util.Set<String> stationRoutes = new java.util.LinkedHashSet<>();
            for (Platform platform : station.savedRails) {
                final Position pos = platform.getMidPosition();
                final List<String> labels = platformRoutes.getOrDefault(platform.getId(), List.of());
                stationRoutes.addAll(labels);
                totalY += (long) pos.getY();
                count++;
                final String platformName = platform.getName() == null || platform.getName().isEmpty()
                        ? Long.toString(platform.getId()) : platform.getName();
                landmarks.add(new MapLandmark("platform:" + platform.getHexId(), MapLandmark.Type.PLATFORM,
                        (int) pos.getX(), (int) pos.getY(), (int) pos.getZ(), station.getName(), platformName,
                        String.join(", ", new java.util.LinkedHashSet<>(labels)), !labels.isEmpty()));
            }
            final Position center = station.getCenter();
            landmarks.add(new MapLandmark("station:" + station.getHexId(), MapLandmark.Type.STATION,
                    (int) center.getX(), count == 0 ? (int) station.getMaxY() : (int) (totalY / count),
                    (int) center.getZ(), station.getName(), station.getName(), String.join(", ", stationRoutes),
                    !stationRoutes.isEmpty()));
        }
        for (Depot depot : depots) {
            final Position center = depot.getCenter();
            landmarks.add(new MapLandmark("depot:" + depot.getHexId(), MapLandmark.Type.DEPOT,
                    (int) center.getX(), (int) depot.getMaxY(), (int) center.getZ(), depot.getName(), "D", "", true));
        }
    }

    /**
     * Sample the actual rail geometry from MTR's client-side rail set into
     * polylines. Rails follow real curves (arcs, slopes), so each rail is
     * sampled along its length via {@link RailMath#getPosition(double, boolean)}.
     */
    private static void collectClientTracks(MinecraftClientData instance, List<MapTrack> tracks,
            Map<String, MapTrack> sampledTracks) {
        for (Rail rail : instance.rails) {
            final String railId = rail.getHexId();
            if (sampledTracks.containsKey(railId)) {
                continue;
            }
            final List<double[]> points = TrackSampler.sample(rail);
            if (points != null) {
                final MapTrack track = new MapTrack(railId, points);
                tracks.add(track);
                sampledTracks.put(railId, track);
            }
        }
    }
}
