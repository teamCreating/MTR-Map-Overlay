package com.lx862.mtrsurveyor.network;

import com.lx862.mtrsurveyor.MTRSurveyor;
import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.MapTrack;
import com.lx862.mtrsurveyor.mapdata.MapLandmark;
import com.lx862.mtrsurveyor.mapdata.RoutePathfinder;
import com.lx862.mtrsurveyor.mapdata.TrackSampler;
import com.lx862.mtrsurveyor.mixin.MainAccessorMixin;
import com.lx862.mtrsurveyor.mixin.MTRAccessorMixin;
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
import net.neoforged.neoforge.network.PacketDistributor;

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

    /** Max bytes of payload per S2C chunk (well under the 1 MiB custom payload limit). */
    private static final int CHUNK_SIZE = 200_000;
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
                final NetworkSyncChunk.PendingDimension dimension = collect(simulator, requestTime, hash);
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
                        PacketDistributor.sendToPlayer(player, new NetworkProbeResponse(List.of(dimensionHash)));
                    }
                });
            });
        }
    }

    private static NetworkSyncChunk.PendingDimension collect(Simulator simulator, long requestTime, long hash) {
        final NetworkSyncChunk.PendingDimension result =
                new NetworkSyncChunk.PendingDimension(simulator.dimension, hash);

        // Route colors painted along MTR's own generated driving paths. Each
        // depot holds the real PathData sequence its trains drive; the color
        // of each stretch is resolved through the platforms the path passes.
        simulator.depots.forEach(depot -> {
            try {
                depot.writePathCache(); // refresh PathData rail references
                collectDepotPath(simulator, depot, result);
            } catch (Throwable e) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Failed to collect depot path on server: {}", e.getMessage());
            }
        });

        // Fallback for routes whose depot path is not (yet) generated: snap
        // them onto the rail network (Dijkstra over positionsToRail) so the
        // color still follows the exact track geometry.
        final RoutePathfinder.Graph graph =
                RoutePathfinder.buildGraph(simulator.rails, simulator.positionsToRail);
        final List<Route> fallbackRoutes = new ArrayList<>();
        final List<List<RoutePathfinder.SegmentPath>> fallbackSegments = new ArrayList<>();
        final Map<Long, List<MapRoute.Stop>> fallbackStops = new HashMap<>();
        final Map<Long, List<Platform>> fallbackPlatforms = new HashMap<>();
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
                fallbackRoutes.add(route);
                fallbackSegments.add(RoutePathfinder.findRoutePath(graph, platforms, circular));
                fallbackStops.put(route.getId(), stops);
                fallbackPlatforms.put(route.getId(), platforms);
            } catch (Throwable e) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Failed to collect route on server: {}", e.getMessage());
            }
        });
        for (int i = 0; i < fallbackRoutes.size(); i++) {
            final Route route = fallbackRoutes.get(i);
            final List<RoutePathfinder.SegmentPath> segments = fallbackSegments.get(i);
            final boolean circular = route.getCircularState() == Route.CircularState.CLOCKWISE
                    || route.getCircularState() == Route.CircularState.ANTICLOCKWISE;
            final List<MapTrack> routeTracks =
                    RoutePathfinder.toTracks(graph, fallbackPlatforms.get(route.getId()), segments, circular);
            if (routeTracks.isEmpty()) {
                MTRSurveyor.LOGGER.warn("[MTRSurveyor] No complete track path for route {}; skipping straight-line fallback",
                        route.getName());
                continue;
            }
            result.routes.add(MapRoute.ofTracks(Long.toHexString(route.getId()), route.getName(), route.getColor(),
                    fallbackStops.getOrDefault(route.getId(), List.of()), routeTracks));
        }

        // Tracks: sample the real rail geometry.
        simulator.rails.forEach(rail -> {
            final List<double[]> points = TrackSampler.sample(rail);
            if (points != null) {
                result.tracks.add(new MapTrack(rail.getHexId(), points));
            }
        });

        collectLandmarks(simulator, result);

        return result;
    }

    /**
     * Walk one depot's driving path and split it into per-route colored
     * stretches. The color of each stretch is resolved through the platforms
     * the path passes: platform.routes ∩ depot.routes gives the serving route.
     */
    private static void collectDepotPath(Simulator simulator, Depot depot,
            NetworkSyncChunk.PendingDimension result) {
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
                final List<double[]> railPoints = TrackSampler.sample(rail);
                if (railPoints != null && railPoints.size() >= 2) {
                    routeTracks.add(new MapTrack(rail.getHexId(), railPoints));
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

    /** Collect complete, dimension-wide waypoint data from the authoritative simulator. */
    private static void collectLandmarks(Simulator simulator, NetworkSyncChunk.PendingDimension result) {
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
            for (Platform platform : station.savedRails) {
                totalY += (long) platform.getMidPosition().getY();
                platformCount++;
                hasRoutes |= !platformRoutes.getOrDefault(platform.getId(), List.of()).isEmpty();

                final Position platformPosition = platform.getMidPosition();
                final String platformName = platform.getName() == null || platform.getName().isEmpty()
                        ? Long.toString(platform.getId()) : platform.getName();
                final List<String> routeLabels = platformRoutes.getOrDefault(platform.getId(), List.of());
                final String description = String.join(", ", new java.util.LinkedHashSet<>(routeLabels));
                result.landmarks.add(new MapLandmark("platform:" + platform.getHexId(),
                        MapLandmark.Type.PLATFORM, (int) platformPosition.getX(), (int) platformPosition.getY(),
                        (int) platformPosition.getZ(), station.getName(), platformName, description,
                        !routeLabels.isEmpty()));
            }

            final Position center = station.getCenter();
            final int y = platformCount == 0 ? (int) station.getMaxY() : (int) (totalY / platformCount);
            result.landmarks.add(new MapLandmark("station:" + station.getHexId(), MapLandmark.Type.STATION,
                    (int) center.getX(), y, (int) center.getZ(), station.getName(), station.getName(), "", hasRoutes));
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

    private static void send(ServerPlayer player, NetworkSyncChunk.PendingDimension dimension, long requestTime,
            int dimensionIndex) {
        try {
            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream(1 << 16);
            final DataOutputStream dataOut = new DataOutputStream(byteOut);
            NetworkSyncChunk.writeDimensionList(dataOut, List.of(dimension));
            dataOut.flush();
            final byte[] payload = byteOut.toByteArray();

            final int totalChunks = (int) Math.max(1, Math.ceil((double) payload.length / CHUNK_SIZE));
            final int transferId = (int) (requestTime ^ (31 * payload.length) ^ (dimensionIndex * 1_000_003))
                    ^ player.getUUID().hashCode();
            for (short chunk = 0; chunk < totalChunks; chunk++) {
                final int from = chunk * CHUNK_SIZE;
                final int to = Math.min(payload.length, from + CHUNK_SIZE);
                final byte[] slice = new byte[to - from];
                System.arraycopy(payload, from, slice, 0, slice.length);
                PacketDistributor.sendToPlayer(player,
                        new NetworkSyncChunk(transferId, chunk, (short) totalChunks, dimension.snapshotHash, slice));
            }

            MTRSurveyor.LOGGER.info("[MTRSurveyor] Sent full-network snapshot for {} to {} ({} routes, {} rails, {} bytes, {} chunk(s))",
                    dimension.dimensionId, player.getGameProfile().getName(),
                    dimension.routes.size(), dimension.tracks.size(), payload.length, totalChunks);
        } catch (Throwable e) {
            MTRSurveyor.LOGGER.error("[MTRSurveyor] Failed to send network snapshot for {}: {}",
                    dimension.dimensionId, e.getMessage(), e);
        }
    }
}
