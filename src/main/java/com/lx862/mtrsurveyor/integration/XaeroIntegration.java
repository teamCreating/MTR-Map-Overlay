package com.lx862.mtrsurveyor.integration;

import com.lx862.mtrsurveyor.MTRDataSummary;
import com.lx862.mtrsurveyor.MTRSurveyor;
import com.lx862.mtrsurveyor.config.MTRSurveyorConfig;
import com.lx862.mtrsurveyor.mapdata.MapDataCache;
import com.lx862.mtrsurveyor.mapdata.MapLandmark;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;
import org.mtr.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XaeroIntegration {

    private static final String WAYPOINT_PREFIX = "[MTR] ";
    private static final int STATION_COLOR = 9; // Blue
    private static final int DEPOT_COLOR = 6; // Gold
    private static final int PLATFORM_COLOR = 3; // Light blue

    // Sync state
    public static volatile boolean needsSync = false;
    private static int tickCounter = 0;
    private static final int SYNC_INTERVAL_TICKS = 100; // 5 seconds

    /**
     * Check if Xaero's Minimap mod is loaded (safe to call anywhere).
     */
    public static boolean isXaeroLoaded() {
        return ModList.get().isLoaded("xaerominimap");
    }

    /**
     * Called every client tick. Checks if sync is needed and Xaero is ready.
     */
    public static void onClientTick() {
        if (!needsSync)
            return;
        if (!MTRSurveyorConfig.INSTANCE.enabled.get())
            return;

        tickCounter++;
        if (tickCounter < SYNC_INTERVAL_TICKS)
            return;
        tickCounter = 0;

        try {
            MinecraftClientData clientData = MinecraftClientData.getInstance();
            if (clientData == null) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Client data not available yet, will retry...");
                return;
            }

            final Minecraft mc = Minecraft.getInstance();
            final String dimension = mc.level == null ? null
                    : mc.level.dimension().location().getNamespace() + "/" + mc.level.dimension().location().getPath();
            final boolean success;
            if (dimension != null && MapDataCache.hasServerData(dimension)) {
                success = doFullNetworkSync(MapDataCache.get(dimension).landmarks);
            } else {
                success = doSync(MTRDataSummary.of(clientData));
            }
            if (success) {
                needsSync = false;
                tickCounter = 0;
            }
        } catch (Throwable e) {
            MTRSurveyor.LOGGER.error("[MTRSurveyor] Error during waypoint sync tick", e);
        }
    }

    /**
     * Mark that waypoint sync is needed (called from mixins).
     */
    public static void requestSync() {
        needsSync = true;
        tickCounter = SYNC_INTERVAL_TICKS - 1; // Try on next tick cycle
    }

    /**
     * Perform the actual Xaero waypoint sync. Returns true on success.
     */
    private static boolean doSync(MTRDataSummary data) {
        try {
            return XaeroSyncHelper.performSync(data);
        } catch (NoClassDefFoundError e) {
            MTRSurveyor.LOGGER.warn("[MTRSurveyor] Xaero classes not available: {}", e.getMessage());
            needsSync = false;
            return false;
        }
    }

    private static boolean doFullNetworkSync(List<MapLandmark> landmarks) {
        try {
            return XaeroSyncHelper.performFullNetworkSync(landmarks);
        } catch (NoClassDefFoundError e) {
            MTRSurveyor.LOGGER.warn("[MTRSurveyor] Xaero classes not available: {}", e.getMessage());
            needsSync = false;
            return false;
        }
    }

    /**
     * Inner helper class that contains all Xaero class references.
     * Separated so the outer class can be loaded without triggering Xaero class
     * loading.
     */
    static class XaeroSyncHelper {
        static boolean performFullNetworkSync(List<MapLandmark> landmarks) {
            final List<xaero.common.minimap.waypoints.Waypoint> existingWaypoints = getCurrentWaypoints();
            if (existingWaypoints == null) {
                return false;
            }

            final Map<String, MapLandmark> desired = new LinkedHashMap<>();
            int stationCount = 0;
            int platformCount = 0;
            int depotCount = 0;
            for (MapLandmark landmark : landmarks) {
                if (!shouldShow(landmark)) {
                    continue;
                }
                final String label = switch (landmark.type()) {
                    case STATION -> landmark.name();
                    case PLATFORM -> landmark.name() + (landmark.description().isEmpty()
                            ? " | Platform " + landmark.symbol()
                            : " | " + landmark.symbol() + " | " + landmark.description());
                    case DEPOT -> "Depot: " + landmark.name();
                };
                desired.put(WAYPOINT_PREFIX + label, landmark);
                switch (landmark.type()) {
                    case STATION -> stationCount++;
                    case PLATFORM -> platformCount++;
                    case DEPOT -> depotCount++;
                }
            }

            // Incremental reconciliation: keep unchanged Xaero waypoint objects,
            // update moved/renamed metadata in place, remove only stale managed
            // entries, then append genuinely new landmarks.
            final Set<String> applied = new HashSet<>();
            existingWaypoints.removeIf(waypoint -> {
                final String waypointName = waypoint.getName();
                if (waypointName == null || !waypointName.startsWith(WAYPOINT_PREFIX)) {
                    return false;
                }
                final MapLandmark landmark = desired.get(waypointName);
                if (landmark == null || !applied.add(waypointName)) {
                    return true;
                }
                waypoint.setX(landmark.x());
                waypoint.setY(landmark.y());
                waypoint.setZ(landmark.z());
                waypoint.setSymbol(landmark.symbol());
                waypoint.setColor(colorFor(landmark.type()));
                waypoint.setDisabled(false);
                return false;
            });
            desired.forEach((waypointName, landmark) -> {
                if (applied.contains(waypointName)) {
                    return;
                }
                final xaero.common.minimap.waypoints.Waypoint waypoint =
                        new xaero.common.minimap.waypoints.Waypoint(landmark.x(), landmark.y(), landmark.z(),
                                waypointName, landmark.symbol(), colorFor(landmark.type()), 3, false);
                waypoint.setDisabled(false);
                existingWaypoints.add(waypoint);
            });
            MTRSurveyor.LOGGER.info(
                    "[MTRSurveyor] Full-network waypoint sync: {} stations, {} platforms, {} depots",
                    stationCount, platformCount, depotCount);
            return true;
        }

        private static int colorFor(MapLandmark.Type type) {
            return switch (type) {
                case STATION -> STATION_COLOR;
                case PLATFORM -> PLATFORM_COLOR;
                case DEPOT -> DEPOT_COLOR;
            };
        }

        private static boolean shouldShow(MapLandmark landmark) {
            if (!landmark.hasRoutes() && !MTRSurveyorConfig.INSTANCE.showEmptyStation.get()
                    && landmark.type() != MapLandmark.Type.DEPOT) {
                return false;
            }
            return switch (landmark.type()) {
                case STATION -> MTRSurveyorConfig.INSTANCE.showStationLandmarks.get();
                case PLATFORM -> MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.get();
                case DEPOT -> MTRSurveyorConfig.INSTANCE.showDepotLandmarks.get();
            };
        }

        private static List<xaero.common.minimap.waypoints.Waypoint> getCurrentWaypoints() {
            final xaero.common.XaeroMinimapSession session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null || session.getWaypointsManager() == null
                    || session.getWaypointsManager().getCurrentWorld() == null
                    || session.getWaypointsManager().getCurrentWorld().getCurrentSet() == null) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Xaero waypoint world not ready, will retry...");
                return null;
            }
            return session.getWaypointsManager().getCurrentWorld().getCurrentSet().getList();
        }

        static boolean performSync(MTRDataSummary data) {
            xaero.common.XaeroMinimapSession session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Xaero session not available, will retry...");
                return false;
            }

            xaero.common.minimap.waypoints.WaypointsManager waypointsManager = session.getWaypointsManager();
            if (waypointsManager == null) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Waypoints manager not available, will retry...");
                return false;
            }

            xaero.common.minimap.waypoints.WaypointWorld waypointWorld = waypointsManager.getCurrentWorld();
            if (waypointWorld == null) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Waypoint world not available, will retry...");
                return false;
            }

            xaero.common.minimap.waypoints.WaypointSet currentSet = waypointWorld.getCurrentSet();
            if (currentSet == null) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] No active waypoint set, will retry...");
                return false;
            }

            List<xaero.common.minimap.waypoints.Waypoint> existingWaypoints = currentSet.getList();

            // Radius-limited client data is never authoritative for deletion.
            // Keep saved far-away waypoints until a full server snapshot can
            // reconcile the whole dimension; otherwise login would erase the
            // persistent full-map set before the first network reply arrives.

            String mode = MTRSurveyorConfig.INSTANCE.waypointMode.get();
            if ("platform".equalsIgnoreCase(mode)) {
                return syncPlatformMode(data, existingWaypoints);
            } else if ("both".equalsIgnoreCase(mode)) {
                return syncStationMode(data, existingWaypoints) && syncPlatformMode(data, existingWaypoints);
            } else {
                return syncStationMode(data, existingWaypoints);
            }
        }

        private static boolean syncStationMode(MTRDataSummary data,
                List<xaero.common.minimap.waypoints.Waypoint> existingWaypoints) {
            Set<String> addedNames = new HashSet<>();
            int stationCount = 0;
            int depotCount = 0;

            // Collect all existing MTR waypoint names so we don't duplicate them
            for (xaero.common.minimap.waypoints.Waypoint wp : existingWaypoints) {
                if (wp.getName() != null && wp.getName().startsWith(WAYPOINT_PREFIX)) {
                    addedNames.add(wp.getName());
                }
            }

            // We aggregate stations from BOTH the local streaming instance AND the
            // dashboard instance (if the user opened it)
            java.util.Set<org.mtr.core.data.Station> allStations = new java.util.HashSet<>();
            java.util.Set<org.mtr.core.data.Depot> allDepots = new java.util.HashSet<>();

            try {
                org.mtr.client.MinecraftClientData instance = org.mtr.client.MinecraftClientData.getInstance();
                if (instance != null) {
                    allStations.addAll(instance.stations);
                    allDepots.addAll(instance.depots);
                }
                org.mtr.client.MinecraftClientData dashboard = org.mtr.client.MinecraftClientData
                        .getDashboardInstance();
                if (dashboard != null) {
                    allStations.addAll(dashboard.stations);
                    allDepots.addAll(dashboard.depots);
                }
            } catch (Exception e) {
                MTRSurveyor.LOGGER.error("[MTRSurveyor] Error accessing MTR datasets: ", e);
            }

            // Add station waypoints
            if (MTRSurveyorConfig.INSTANCE.showStationLandmarks.get()) {
                for (org.mtr.core.data.Station station : allStations) {
                    String name = station.getName();
                    if (name == null || name.isEmpty())
                        continue;

                    String wpName = WAYPOINT_PREFIX + name;
                    if (addedNames.contains(wpName))
                        continue;
                    addedNames.add(wpName);

                    // Use station center X/Z, but fix Y using platform positions
                    org.mtr.core.data.Position center = station.getCenter();
                    int x = (int) center.getX();
                    int z = (int) center.getZ();
                    int y = calculateStationY(station);
                    String symbol = name;

                    xaero.common.minimap.waypoints.Waypoint waypoint = new xaero.common.minimap.waypoints.Waypoint(
                            x, y, z, wpName, symbol, STATION_COLOR, 3, false);
                    waypoint.setDisabled(false);
                    existingWaypoints.add(waypoint);
                    stationCount++;
                }
            }

            // Add depot waypoints
            if (MTRSurveyorConfig.INSTANCE.showDepotLandmarks.get()) {
                for (org.mtr.core.data.Depot depot : allDepots) {
                    String name = depot.getName();
                    if (name == null || name.isEmpty())
                        continue;

                    String wpName = WAYPOINT_PREFIX + "Depot: " + name;
                    if (addedNames.contains(wpName))
                        continue;
                    addedNames.add(wpName);

                    org.mtr.core.data.Position center = depot.getCenter();
                    int x = (int) center.getX();
                    int y = (int) depot.getMaxY(); // Use top of depot area
                    int z = (int) center.getZ();

                    xaero.common.minimap.waypoints.Waypoint waypoint = new xaero.common.minimap.waypoints.Waypoint(
                            x, y, z, wpName, "D", DEPOT_COLOR, 3, false);
                    waypoint.setDisabled(false);
                    existingWaypoints.add(waypoint);
                    depotCount++;
                }
            }

            MTRSurveyor.LOGGER.info(
                    "[MTRSurveyor] Station mode sync: added {} new stations, {} new depots",
                    stationCount, depotCount);
            return true;
        }

        /**
         * Platform mode: One waypoint per platform.
         * Symbol = platform name/number, Name = [MTR] StationName | RouteName(s) |
         * Destination.
         * Y = platform mid position Y.
         */
        private static boolean syncPlatformMode(MTRDataSummary data,
                List<xaero.common.minimap.waypoints.Waypoint> existingWaypoints) {
            int platformCount = 0;

            if (!MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.get()) {
                MTRSurveyor.LOGGER.info("[MTRSurveyor] Platform landmarks disabled, skipping");
                return true;
            }

            Set<String> addedNames = new HashSet<>();
            for (xaero.common.minimap.waypoints.Waypoint wp : existingWaypoints) {
                if (wp.getName() != null && wp.getName().startsWith(WAYPOINT_PREFIX)) {
                    addedNames.add(wp.getName());
                }
            }

            // We aggregate stations from BOTH the local streaming instance AND the
            // dashboard instance (if the user opened it)
            java.util.Set<org.mtr.core.data.Station> allStations = new java.util.HashSet<>();
            java.util.Set<org.mtr.core.data.Route> allRoutes = new java.util.HashSet<>();

            try {
                org.mtr.client.MinecraftClientData instance = org.mtr.client.MinecraftClientData.getInstance();
                if (instance != null) {
                    allStations.addAll(instance.stations);
                    allRoutes.addAll(instance.routes);
                }
                org.mtr.client.MinecraftClientData dashboard = org.mtr.client.MinecraftClientData
                        .getDashboardInstance();
                if (dashboard != null) {
                    allStations.addAll(dashboard.stations);
                    allRoutes.addAll(dashboard.routes);
                }
            } catch (Exception e) {
                MTRSurveyor.LOGGER.error("[MTRSurveyor] Error accessing MTR datasets: ", e);
            }

            // Build a map of platformId -> list of routes, since platform.routes
            // is not populated on the client side
            Map<Long, List<Route>> platformRouteMap = new HashMap<>();
            try {
                for (Route route : allRoutes) {
                    List<RoutePlatformData> rpList = route.getRoutePlatforms();
                    if (rpList == null)
                        continue;
                    for (RoutePlatformData rpd : rpList) {
                        Platform rp = rpd.getPlatform();
                        if (rp != null) {
                            platformRouteMap.computeIfAbsent(rp.getId(), k -> new ArrayList<>()).add(route);
                        }
                    }
                }
            } catch (Exception e) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Error building platform route map: {}", e.getMessage());
            }

            for (org.mtr.core.data.Station station : allStations) {
                String stationName = station.getName();
                if (stationName == null || stationName.isEmpty())
                    continue;

                // Iterate over platforms (savedRails) in this station
                for (Object railObj : new ArrayList<>(station.savedRails)) {
                    if (!(railObj instanceof Platform platform))
                        continue;

                    Position midPos = platform.getMidPosition();
                    int x = (int) midPos.getX();
                    int y = (int) midPos.getY();
                    int z = (int) midPos.getZ();

                    // Platform name/number for the symbol
                    String platformName = platform.getName();
                    if (platformName == null || platformName.isEmpty()) {
                        platformName = String.valueOf(platform.getId());
                    }
                    String symbol = platformName;

                    // Build the full waypoint name: [MTR] StationName | Route1(→Dest),
                    // Route2(→Dest)
                    StringBuilder nameBuilder = new StringBuilder(WAYPOINT_PREFIX);
                    nameBuilder.append(stationName);

                    // Find routes using the pre-built map
                    List<String> routeInfos = new ArrayList<>();
                    List<Route> routesForPlatform = platformRouteMap.get(platform.getId());
                    if (routesForPlatform != null) {
                        // Deduplicate by route name
                        Set<String> seenRoutes = new HashSet<>();
                        for (Route route : routesForPlatform) {
                            String routeName = route.getName();
                            if (routeName == null || routeName.isEmpty())
                                continue;
                            if (!seenRoutes.add(routeName))
                                continue;

                            String destination = findDestinationForPlatform(route, platform);

                            StringBuilder routeInfo = new StringBuilder(routeName);
                            if (destination != null && !destination.isEmpty()) {
                                routeInfo.append("→").append(destination);
                            }
                            routeInfos.add(routeInfo.toString());
                        }
                    }

                    if (!routeInfos.isEmpty()) {
                        nameBuilder.append(" | ").append(String.join(", ", routeInfos));
                    }

                    String wpName = nameBuilder.toString();
                    if (addedNames.contains(wpName))
                        continue;
                    addedNames.add(wpName);

                    xaero.common.minimap.waypoints.Waypoint waypoint = new xaero.common.minimap.waypoints.Waypoint(
                            x, y, z, wpName, symbol, PLATFORM_COLOR, 3, false);
                    waypoint.setDisabled(false);
                    existingWaypoints.add(waypoint);
                    platformCount++;

                    if (MTRSurveyorConfig.INSTANCE.debugLog.get()) {
                        MTRSurveyor.LOGGER.info("[MTRSurveyor] Platform waypoint: {} [{}] at ({}, {}, {})",
                                wpName, symbol, x, y, z);
                    }
                }
            }

            MTRSurveyor.LOGGER.info("[MTRSurveyor] Platform mode sync: added {} platforms", platformCount);
            return true;
        }

        /**
         * Find the destination station name for a route from a given platform.
         * Returns the last station name in the route (terminal).
         */
        private static String findDestinationForPlatform(Route route, Platform currentPlatform) {
            try {
                List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
                if (routePlatforms == null || routePlatforms.isEmpty())
                    return null;

                // Find the index of current platform in the route
                int currentIndex = -1;
                for (int i = 0; i < routePlatforms.size(); i++) {
                    Platform rp = routePlatforms.get(i).getPlatform();
                    if (rp != null && rp.getId() == currentPlatform.getId()) {
                        currentIndex = i;
                        break;
                    }
                }

                if (currentIndex < 0)
                    return null;

                // The last platform's station name is the terminal/destination
                RoutePlatformData lastPlatformData = routePlatforms.get(routePlatforms.size() - 1);

                // Check if there's a custom destination set
                String customDest = lastPlatformData.getCustomDestination();
                if (customDest != null && !customDest.isEmpty()
                        && !Route.destinationIsReset(customDest)) {
                    return customDest;
                }

                // Use the last platform's station name
                Platform lastPlatform = lastPlatformData.getPlatform();
                if (lastPlatform != null) {
                    return lastPlatform.getStationName();
                }
            } catch (Exception e) {
                MTRSurveyor.LOGGER.debug("[MTRSurveyor] Error finding destination: {}", e.getMessage());
            }
            return null;
        }

        /**
         * Calculate a better Y coordinate for a station waypoint.
         * Uses the average Y of all platforms in the station.
         * Falls back to station maxY if no platforms exist.
         */
        private static int calculateStationY(Station station) {
            long totalY = 0;
            int count = 0;

            for (Object railObj : new ArrayList<>(station.savedRails)) {
                if (railObj instanceof Platform platform) {
                    Position midPos = platform.getMidPosition();
                    totalY += (long) midPos.getY();
                    count++;
                }
            }

            if (count > 0) {
                return (int) (totalY / count);
            }

            // Fallback: use the top of the station area
            return (int) station.getMaxY();
        }
    }
}
