package com.lx862.mtrmap.network;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.mapdata.MapRoute;
import com.lx862.mtrmap.mapdata.MapTrack;
import com.lx862.mtrmap.mapdata.MapLandmark;
import com.lx862.mtrmap.mapdata.RoutePathfinder;
import com.lx862.mtrmap.mapdata.TrackSampler;
import com.lx862.mtrmap.mixin.MainAccessorMixin;
import com.lx862.mtrmap.mixin.MTRAccessorMixin;
import org.mtr.core.Main;
import org.mtr.core.data.Depot;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-side collector for the full-network map snapshot.
 *
 * <p>Route colors come from MTR's own route generation: each {@link Depot
 * depot} holds the real {@link PathData driving path} its trains follow
 * (computed by MTR's route generation, not by this mod). The path is split
 * into stretches colored by the route serving them, so the map looks like the
 * tracks are painted with route colors.</p>
 *
 * <p>Collection runs on each simulator's own thread via
 * {@link Simulator#run(Runnable)}; packets are sent on the server thread.</p>
 */
public final class ServerNetworkCollector {

    private ServerNetworkCollector() {
    }

    public static void collectAndSend(ServerPlayer player, String dimensionFilter) {
        final Main main = MTRAccessorMixin.getMain();
        if (main == null) {
            return;
        }
        final ObjectImmutableList<Simulator> simulators = ((MainAccessorMixin) main).getSimulators();
        if (simulators == null || simulators.isEmpty()) {
            return;
        }

        final MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        final long requestTime = System.currentTimeMillis();
        for (int i = 0; i < simulators.size(); i++) {
            final Simulator simulator = simulators.get(i);
            if (dimensionFilter != null && !dimensionFilter.equals(simulator.dimension)) {
                continue;
            }
            final int dimensionIndex = i;
            // Queue the read onto the simulator thread, then hop back to the
            // server thread to send the packets.
            simulator.run(() -> {
                final long hash = computeHash(simulator);
                final NetworkSnapshotCodec.PendingDimension dimension = collect(simulator, hash);
                server.execute(() -> {
                    if (player.connection != null) {
                        send(player, dimension, requestTime, dimensionIndex);
                    }
                });
            });
        }
    }

    /**
     * Compute a lightweight change-detection hash per dimension. The client
     * compares against its cached hashes and only pulls full snapshots for
     * dimensions that actually changed. Cost is O(network size) with trivial
     * per-element work, so polling is cheap.
     */
    public static void sendProbeResponse(ServerPlayer player) {
        final Main main = MTRAccessorMixin.getMain();
        if (main == null) {
            return;
        }
        final ObjectImmutableList<Simulator> simulators = ((MainAccessorMixin) main).getSimulators();
        if (simulators == null || simulators.isEmpty()) {
            return;
        }
        final MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        final List<NetworkProbeResponse.DimensionHash> hashes = new ArrayList<>();
        for (final Simulator simulator : simulators) {
            simulator.run(() -> {
                final long hash = computeHash(simulator);
                final NetworkProbeResponse.DimensionHash dimensionHash =
                        new NetworkProbeResponse.DimensionHash(simulator.dimension, hash);
                server.execute(() -> {
                    if (player.connection != null) {
                        MTRNetwork.sendToPlayer(player, new NetworkProbeResponse(List.of(dimensionHash)));
                    }
                });
            });
        }
    }

    private static NetworkSnapshotCodec.PendingDimension collect(Simulator simulator, long hash) {
        final NetworkSnapshotCodec.PendingDimension result =
                new NetworkSnapshotCodec.PendingDimension(simulator.dimension, hash);

        // Build the canonical TRACK layer first. Depot paths and fallback
        // routes can then reuse its polylines instead of sampling the same
        // physical rail again for each route or vehicle depot.
        final Map<String, MapTrack> sampledTracks = new HashMap<>();
        simulator.rails.forEach(rail -> {
            final List<double[]> points = TrackSampler.sample(rail);
            if (points != null) {
                final MapTrack track = new MapTrack(rail.getHexId(), points);
                result.tracks.add(track);
                sampledTracks.put(track.id, track);
            }
        });

        // Route colors painted along MTR's own generated driving paths. Each
        // depot holds the real PathData sequence its trains drive; the color
        // of each stretch is resolved through the platforms the path passes.
        simulator.depots.forEach(depot -> {
            try {
                depot.writePathCache(); // refresh PathData rail references
                collectDepotPath(simulator, depot, result, sampledTracks);
            } catch (Throwable e) {
                MTRMap.LOGGER.debug("[MTRMap] Failed to collect depot path on server: {}", e.getMessage());
            }
        });

        // Fallback for routes whose depot path is not (yet) generated: snap
        // them onto the rail network (Dijkstra over positionsToRail) so the
        // color still follows the exact track geometry.
        final List<FallbackRoute> fallbackRoutes = new ArrayList<>();
        simulator.routes.forEach(route -> {
            try {
                if (result.hasRealPath(route.getId())) {
                    return; // covered by a real depot driving path
                }
                final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
                if (routePlatforms == null || routePlatforms.size() < 2) {
                    return;
                }
                final List<MapRoute.Stop> stops = new ArrayList<>(routePlatforms.size());
                final List<Platform> platforms = new ArrayList<>(routePlatforms.size());
                for (RoutePlatformData routePlatform : routePlatforms) {
                    final Platform platform = routePlatform.getPlatform();
                    if (platform == null) {
                        return;
                    }
                    platforms.add(platform);
                    final Position pos = platform.getMidPosition();
                    stops.add(new MapRoute.Stop(pos.getX(), pos.getZ(),
                            platform.getStationName(), routePlatform.getCustomDestination()));
                }
                final boolean circular = route.getCircularState() == Route.CircularState.CLOCKWISE
                        || route.getCircularState() == Route.CircularState.ANTICLOCKWISE;
                fallbackRoutes.add(new FallbackRoute(route, stops, platforms, circular));
            } catch (Throwable e) {
                MTRMap.LOGGER.debug("[MTRMap] Failed to collect route on server: {}", e.getMessage());
            }
        });
        if (!fallbackRoutes.isEmpty()) {
            final RoutePathfinder.Graph graph =
                    RoutePathfinder.buildGraph(simulator.rails, simulator.positionsToRail);
            for (FallbackRoute fallback : fallbackRoutes) {
                final Route route = fallback.route();
                final List<RoutePathfinder.SegmentPath> segments =
                        RoutePathfinder.findRoutePath(graph, fallback.platforms(), fallback.circular());
                final List<MapTrack> routeTracks = RoutePathfinder.toTracks(graph, fallback.platforms(), segments,
                        fallback.circular(), sampledTracks);
                if (routeTracks.isEmpty()) {
                    MTRMap.LOGGER.warn("[MTRMap] No complete track path for route {}; skipping straight-line fallback",
                            route.getName());
                    continue;
                }
                result.routes.add(MapRoute.ofTracks(Long.toHexString(route.getId()), route.getName(), route.getColor(),
                        fallback.stops(), routeTracks));
            }
        }

        collectLandmarks(simulator, result);

        return result;
    }

    private record FallbackRoute(Route route, List<MapRoute.Stop> stops, List<Platform> platforms,
                                 boolean circular) {
    }

    /**
     * Walk one depot's driving path and split it into per-route colored
     * stretches. The color of each stretch is resolved through the platforms
     * the path passes: platform.routes ∩ depot.routes gives the serving route.
     */
    private static void collectDepotPath(Simulator simulator, Depot depot,
            NetworkSnapshotCodec.PendingDimension result, Map<String, MapTrack> sampledTracks) {
        final List<PathData> path = depot.getPath();
        if (path == null || path.isEmpty()) {
            return;
        }

        // Remember which routes got real paths (fallback suppression)
        for (Route route : depot.routes) {
            result.markRealPath(route.getId());
        }

        String currentRouteId = null;
        String currentRouteName = null;
        int currentColor = 0;
        List<MapRoute.Stop> stops = new ArrayList<>();
        List<MapTrack> routeTracks = new ArrayList<>();
        Set<String> routeRailIds = new HashSet<>();
        long lastPlatformId = 0;

        for (PathData pathData : path) {
            final Rail rail = pathData.getRail();
            if (rail == null) {
                continue;
            }
            final double length = rail.railMath.getLength();
            if (length <= 0) {
                continue;
            }

            // Resolve the serving route at the platform this stretch stops at
            final long savedRailId = pathData.getSavedRailBaseId();
            final Platform platform = savedRailId == 0 ? null : simulator.platformIdMap.get(savedRailId);
            if (platform != null && savedRailId != lastPlatformId) {
                final Route serving = resolveServingRoute(depot, platform);
                final String routeId = serving == null ? "depot:" + depot.getHexId()
                        : Long.toHexString(serving.getId());
                final String routeName = serving == null ? depot.getName() : serving.getName();
                final int routeColor = serving == null ? depot.getColor() : serving.getColor();

                if (currentRouteName != null && (!routeName.equals(currentRouteName) || routeColor != currentColor)) {
                    // Color change: flush the finished stretch
                    if (!routeTracks.isEmpty()) {
                        result.routes.add(MapRoute.ofTracks(currentRouteId, currentRouteName, currentColor, stops,
                                routeTracks));
                    }
                    stops = new ArrayList<>();
                    routeTracks = new ArrayList<>();
                    routeRailIds = new HashSet<>();
                }
                currentRouteId = routeId;
                currentRouteName = routeName;
                currentColor = routeColor;
                lastPlatformId = savedRailId;
            }

            // Reuse the exact full-rail MapTrack geometry. Direction and path
            // distances affect train movement, not how the physical rail is drawn.
            if (routeRailIds.add(rail.getHexId())) {
                final MapTrack track = sampledTracks.get(rail.getHexId());
                if (track != null && track.points.size() >= 2) {
                    routeTracks.add(track);
                }
            }

            if (platform != null) {
                final Position pos = platform.getMidPosition();
                appendStop(stops, pos.getX(), pos.getZ(), platform.getStationName());
                lastPlatformId = savedRailId;
            }
        }

        if (!routeTracks.isEmpty()) {
            final String name = currentRouteName == null ? depot.getName() : currentRouteName;
            final String id = currentRouteId == null ? "depot:" + depot.getHexId() : currentRouteId;
            result.routes.add(MapRoute.ofTracks(id, name, currentColor, stops, routeTracks));
        }
    }

    /** Collect complete, dimension-wide map landmark data from the authoritative simulator. */
    private static void collectLandmarks(Simulator simulator, NetworkSnapshotCodec.PendingDimension result) {
        final Map<Long, List<String>> platformRoutes = new HashMap<>();
        for (Route route : simulator.routes) {
            final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
            for (int i = 0; i < routePlatforms.size(); i++) {
                final RoutePlatformData routePlatform = routePlatforms.get(i);
                final Platform platform = routePlatform.getPlatform();
                if (platform == null) {
                    continue;
                }
                String routeLabel = route.getName();
                final String destination = route.getDestination(i);
                if (destination != null && !destination.isEmpty() && !Route.destinationIsReset(destination)) {
                    routeLabel += "→" + destination;
                }
                platformRoutes.computeIfAbsent(platform.getId(), ignored -> new ArrayList<>()).add(routeLabel);
            }
        }

        for (Station station : simulator.stations) {
            if (station.getName() == null || station.getName().isEmpty()) {
                continue;
            }
            long totalY = 0;
            int platformCount = 0;
            boolean hasRoutes = false;
            final Set<String> stationRouteLabels = new java.util.LinkedHashSet<>();
            for (Platform platform : station.savedRails) {
                totalY += (long) platform.getMidPosition().getY();
                platformCount++;
                hasRoutes |= !platformRoutes.getOrDefault(platform.getId(), List.of()).isEmpty();

                final Position platformPosition = platform.getMidPosition();
                final String platformName = platform.getName() == null || platform.getName().isEmpty()
                        ? Long.toString(platform.getId()) : platform.getName();
                final List<String> routeLabels = platformRoutes.getOrDefault(platform.getId(), List.of());
                stationRouteLabels.addAll(routeLabels);
                final String description = String.join(", ", new java.util.LinkedHashSet<>(routeLabels));
                result.landmarks.add(new MapLandmark("platform:" + platform.getHexId(),
                        MapLandmark.Type.PLATFORM, (int) platformPosition.getX(), (int) platformPosition.getY(),
                        (int) platformPosition.getZ(), station.getName(), platformName, description,
                        !routeLabels.isEmpty()));
            }

            final Position center = station.getCenter();
            final int y = platformCount == 0 ? (int) station.getMaxY() : (int) (totalY / platformCount);
            result.landmarks.add(new MapLandmark("station:" + station.getHexId(), MapLandmark.Type.STATION,
                    (int) center.getX(), y, (int) center.getZ(), station.getName(), station.getName(),
                    String.join(", ", stationRouteLabels), hasRoutes));
        }

        for (Depot depot : simulator.depots) {
            if (depot.getName() == null || depot.getName().isEmpty()) {
                continue;
            }
            final Position center = depot.getCenter();
            result.landmarks.add(new MapLandmark("depot:" + depot.getHexId(), MapLandmark.Type.DEPOT,
                    (int) center.getX(), (int) depot.getMaxY(), (int) center.getZ(), depot.getName(), "D", "",
                    !depot.routes.isEmpty()));
        }
    }

    /**
     * The serving route for a platform: the first of the depot's routes that
     * also passes this platform (platform.routes is a sorted set, so this is
     * deterministic).
     */
    private static Route resolveServingRoute(Depot depot, Platform platform) {
        for (Route depotRoute : depot.routes) {
            if (platform.routes.contains(depotRoute)) {
                return depotRoute;
            }
        }
        return null;
    }

    private static void appendStop(List<MapRoute.Stop> stops, double x, double z, String stationName) {
        final MapRoute.Stop last = stops.isEmpty() ? null : stops.get(stops.size() - 1);
        if (last != null && last.stationName != null && last.stationName.equals(stationName)) {
            return; // arrive + depart on the same platform
        }
        stops.add(new MapRoute.Stop(x, z, stationName, ""));
    }

    /** Cheap O(network) content hash for change detection. */
    private static long computeHash(Simulator simulator) {
        long hash = simulator.rails.size();
        for (Rail rail : simulator.rails) {
            hash = hash * 31 + rail.getHexId().hashCode();
        }
        hash = hash * 31 + simulator.routes.size();
        for (Route route : simulator.routes) {
            hash = hash * 31 + (int) route.getId();
            hash = hash * 31 + Objects.hashCode(route.getName());
            hash = hash * 31 + route.getColor();
            for (RoutePlatformData routePlatform : route.getRoutePlatforms()) {
                final Platform platform = routePlatform.getPlatform();
                hash = hash * 31 + (platform == null ? 0 : Long.hashCode(platform.getId()));
                hash = hash * 31 + Objects.hashCode(routePlatform.getCustomDestination());
            }
        }
        hash = hash * 31 + simulator.platforms.size();
        for (Platform platform : simulator.platforms) {
            final Position pos = platform.getMidPosition();
            hash = hash * 31 + platform.getHexId().hashCode();
            hash = hash * 31 + Objects.hashCode(platform.getName());
            hash = hash * 31 + Long.hashCode((long) pos.getX());
            hash = hash * 31 + Long.hashCode((long) pos.getY());
            hash = hash * 31 + Long.hashCode((long) pos.getZ());
        }
        hash = hash * 31 + simulator.stations.size();
        for (Station station : simulator.stations) {
            final Position center = station.getCenter();
            hash = hash * 31 + station.getHexId().hashCode();
            hash = hash * 31 + Objects.hashCode(station.getName());
            hash = hash * 31 + Long.hashCode((long) center.getX());
            hash = hash * 31 + Long.hashCode((long) center.getZ());
        }
        for (Depot depot : simulator.depots) {
            final Position center = depot.getCenter();
            hash = hash * 31 + depot.getHexId().hashCode();
            hash = hash * 31 + Objects.hashCode(depot.getName());
            hash = hash * 31 + Long.hashCode((long) center.getX());
            hash = hash * 31 + Long.hashCode((long) center.getZ());
            hash = hash * 31 + (int) depot.getLastGeneratedMillis();
            final List<PathData> depotPath = depot.getPath();
            hash = hash * 31 + (depotPath == null ? 0 : depotPath.size());
        }
        return hash;
    }

    private static void send(ServerPlayer player, NetworkSnapshotCodec.PendingDimension dimension, long requestTime,
            int dimensionIndex) {
        try {
            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream(1 << 16);
            final DataOutputStream dataOut = new DataOutputStream(byteOut);
            NetworkSnapshotCodec.writeDimensionList(dataOut, List.of(dimension));
            dataOut.flush();
            final byte[] payload = byteOut.toByteArray();
            if (payload.length > NetworkChunkAssembler.MAX_BYTES) {
                throw new IllegalArgumentException("Snapshot exceeds transfer size limit: " + payload.length);
            }

            final int totalChunks = Math.max(1, (payload.length - 1) / NetworkChunkAssembler.CHUNK_SIZE + 1);
            if (totalChunks > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Snapshot requires too many chunks: " + totalChunks);
            }
            final int transferId = (int) (requestTime ^ (31 * payload.length) ^ (dimensionIndex * 1_000_003))
                    ^ player.getUUID().hashCode();
            for (int chunk = 0; chunk < totalChunks; chunk++) {
                final int from = chunk * NetworkChunkAssembler.CHUNK_SIZE;
                final int to = Math.min(payload.length, from + NetworkChunkAssembler.CHUNK_SIZE);
                final byte[] slice = new byte[to - from];
                System.arraycopy(payload, from, slice, 0, slice.length);
                MTRNetwork.sendToPlayer(player,
                        new NetworkSyncChunk(transferId, (short) chunk, (short) totalChunks,
                                dimension.snapshotHash, slice));
            }

            MTRMap.LOGGER.info("[MTRMap] Sent full-network snapshot for {} to {} ({} routes, {} rails, {} bytes, {} chunk(s))",
                    dimension.dimensionId, player.getGameProfile().getName(),
                    dimension.routes.size(), dimension.tracks.size(), payload.length, totalChunks);
        } catch (Throwable e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to send network snapshot for {}: {}",
                    dimension.dimensionId, e.getMessage(), e);
        }
    }
}
