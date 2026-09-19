package com.lx862.mtrsurveyor.network;

import com.lx862.mtrsurveyor.MTRSurveyor;
import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.MapTrack;
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
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

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
    /** World-block distance between samples along a driving path. */
    private static final double SAMPLE_INTERVAL = 8.0;

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
                long hash = simulator.rails.size();
                for (final Rail rail : simulator.rails) {
                    hash = hash * 31 + rail.getHexId().hashCode();
                }
                hash = hash * 31 + simulator.routes.size();
                for (final Route route : simulator.routes) {
                    hash = hash * 31 + (int) route.getId();
                    hash = hash * 31 + route.getName().hashCode();
                }
                hash = hash * 31 + simulator.platforms.size();
                for (final Depot depot : simulator.depots) {
                    hash = hash * 31 + (int) depot.getLastGeneratedMillis();
                    final List<PathData> depotPath = depot.getPath();
                    hash = hash * 31 + (depotPath == null ? 0 : depotPath.size());
                }
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

        // Fallback for routes whose depot path is not (yet) generated: plain
        // stop-to-stop polylines so every saved route still appears on the map.
        simulator.routes.forEach(route -> {
            try {
                if (result.hasRealPath(route.getId())) {
                    return;
                }
                final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
                if (routePlatforms == null || routePlatforms.size() < 2) {
                    return;
                }
                final List<MapRoute.Stop> stops = new ArrayList<>(routePlatforms.size());
                for (RoutePlatformData routePlatform : routePlatforms) {
                    final Platform platform = routePlatform.getPlatform();
                    if (platform == null) {
                        return;
                    }
                    final Position pos = platform.getMidPosition();
                    stops.add(new MapRoute.Stop(pos.getX(), pos.getZ(),
                            platform.getStationName(), routePlatform.getCustomDestination()));
                }
                result.routes.add(MapRoute.ofStops(route.getName(), route.getColor(), false, stops));
            } catch (Throwable e) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Failed to collect route on server: {}", e.getMessage());
            }
        });

        // Tracks: sample the real rail geometry.
        simulator.rails.forEach(rail -> {
            final List<double[]> points = TrackSampler.sample(rail);
            if (points != null) {
                result.tracks.add(new MapTrack(points));
            }
        });

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

        String currentRouteName = null;
        int currentColor = 0;
        List<MapRoute.Stop> stops = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
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
                final String routeName = serving == null ? depot.getName() : serving.getName();
                final int routeColor = serving == null ? depot.getColor() : serving.getColor();

                if (currentRouteName != null && (!routeName.equals(currentRouteName) || routeColor != currentColor)) {
                    // Color change: flush the finished stretch
                    result.routes.add(MapRoute.ofPath(currentRouteName, currentColor, stops, points));
                    stops = new ArrayList<>();
                    points = new ArrayList<>();
                }
                currentRouteName = routeName;
                currentColor = routeColor;
                lastPlatformId = savedRailId;
            }

            // Sample the rail stretch between the two distances
            final double d1 = pathData.getStartDistance();
            final double d2 = pathData.getEndDistance();
            final double from = Math.max(0, Math.min(d1, d2));
            final double to = Math.min(length, Math.max(d1, d2));
            final int samples = (int) Math.min(64, Math.max(2, Math.ceil((to - from) / SAMPLE_INTERVAL) + 1));
            for (int i = 0; i <= samples; i++) {
                final double d = from + (to - from) * i / samples;
                final org.mtr.core.tool.Vector pos = rail.railMath.getPosition(d, false);
                appendPoint(points, pos.x(), pos.z());
            }

            if (platform != null) {
                final Position pos = platform.getMidPosition();
                appendStop(stops, pos.getX(), pos.getZ(), platform.getStationName());
                lastPlatformId = savedRailId;
            }
        }

        if (points.size() >= 2) {
            final String name = currentRouteName == null ? depot.getName() : currentRouteName;
            result.routes.add(MapRoute.ofPath(name, currentColor, stops, points));
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

    private static void appendPoint(List<double[]> points, double x, double z) {
        final double[] last = points.isEmpty() ? null : points.get(points.size() - 1);
        if (last != null && Math.abs(last[0] - x) < 1.0E-3 && Math.abs(last[1] - z) < 1.0E-3) {
            return;
        }
        points.add(new double[]{x, z});
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
            hash = hash * 31 + route.getName().hashCode();
        }
        hash = hash * 31 + simulator.platforms.size();
        for (Depot depot : simulator.depots) {
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
