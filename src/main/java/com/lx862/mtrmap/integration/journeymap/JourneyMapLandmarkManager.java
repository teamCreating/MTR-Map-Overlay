package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.MTRDataSummary;
import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.MapDataCache;
import com.lx862.mtrmap.mapdata.MapLandmark;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.MapImage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.mtr.client.MinecraftClientData;
import org.mtr.core.data.Depot;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.data.IGui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds JourneyMap {@link MarkerOverlay} markers from MTR data.
 *
 * <p>Ported from the standalone JourneyMap edition onto the unified mod:
 * a "station" mode (one marker per station), a "platform" mode (one marker
 * per platform, with route &amp; destination info on hover) and optional depot
 * markers, with per-transport-mode icons tinted by MTR color.</p>
 *
 * <p>This class references JourneyMap API types and must only be loaded when
 * JourneyMap is present (see {@link JourneyMapIntegration}).</p>
 */
final class JourneyMapLandmarkManager {

    // Track currently displayed markers so we can clean them up on the next sync
    private static final List<MarkerOverlay> activeMarkers = new ArrayList<>();
    private static MarkerOverlay testMarker;

    private JourneyMapLandmarkManager() {
    }

    static void syncLandmarks(String reason, Level world, MinecraftClientData clientData) {
        final IClientAPI api = getJourneyMapAPI();
        if (api == null) {
            // JourneyMap not loaded or its plugin not initialized yet
            return;
        }

        final MTRMapConfig config = MTRMapConfig.INSTANCE;
        final long startMs = System.currentTimeMillis();

        // Build the full set of markers that should be displayed right now
        final Map<String, MarkerOverlay> desiredMarkers = new LinkedHashMap<>();
        if (config.enabled.get()) {
            final MTRDataSummary dataSummary = MTRDataSummary.of(clientData);
            final String dimensionKey = world.dimension().location().getNamespace() + "/"
                    + world.dimension().location().getPath();
            if (MapDataCache.hasServerData(dimensionKey)) {
                collectNetworkMarkers(desiredMarkers, MapDataCache.get(dimensionKey).landmarks, world);
            } else {
                if ("platform".equalsIgnoreCase(config.waypointMode.get())) {
                    collectPlatformMarkers(desiredMarkers, world);
                } else if ("both".equalsIgnoreCase(config.waypointMode.get())) {
                    collectStationMarkers(desiredMarkers, dataSummary, world);
                    collectPlatformMarkers(desiredMarkers, world);
                } else {
                    collectStationMarkers(desiredMarkers, dataSummary, world);
                }
            }

            if (config.showDepotLandmarks.get()) {
                collectDepotMarkers(desiredMarkers, world);
            }
        }

        // JourneyMap rejects api.show() for an ID that is already displayed, so
        // every sync removes the previous set and re-adds fresh markers
        for (MarkerOverlay marker : activeMarkers) {
            try {
                api.remove(marker);
            } catch (Exception e) {
                MTRMap.LOGGER.warn("[MTRMap] Failed to remove marker during resync: {}", e.getMessage());
            }
        }
        activeMarkers.clear();

        int failures = 0;
        for (Map.Entry<String, MarkerOverlay> entry : desiredMarkers.entrySet()) {
            try {
                api.show(entry.getValue());
                activeMarkers.add(entry.getValue());
            } catch (Exception e) {
                failures++;
                if (failures <= 3) {
                    MTRMap.LOGGER.error("[MTRMap] Failed to show marker for {}", entry.getKey(), e);
                }
            }
        }

        if (config.debugLog.get() || failures > 0) {
            MTRMap.LOGGER.info("[MTRMap] Landmark sync ({}): {} markers active, {} failed (mode: {}, {}ms)",
                    reason, activeMarkers.size(), failures, config.waypointMode.get(),
                    System.currentTimeMillis() - startMs);
        }
    }

    private static void collectNetworkMarkers(Map<String, MarkerOverlay> out, List<MapLandmark> landmarks,
            Level world) {
        for (MapLandmark landmark : landmarks) {
            if (!shouldShow(landmark)) {
                continue;
            }
            final boolean depot = landmark.type() == MapLandmark.Type.DEPOT;
            final int size = landmark.type() == MapLandmark.Type.PLATFORM ? 6 : depot ? 10 : 12;
            final ResourceLocation iconLocation = markerIcon("train", depot);
            // The bundled marker PNGs are 32x32; declaring 16x16 samples
            // only a corner of the station/depot icon in JourneyMap too.
            final MapImage icon = new MapImage(iconLocation, 32, 32);
            icon.centerAnchors();
            icon.setDisplayWidth(size);
            icon.setDisplayHeight(size);

            final StringBuilder title = new StringBuilder(landmark.name());
            if (landmark.type() == MapLandmark.Type.PLATFORM) {
                title.append("\nPlatform ").append(landmark.symbol());
            }
            if (!landmark.description().isEmpty()) {
                title.append("\nRoutes:\n- ").append(landmark.description().replace(", ", "\n- "));
            }

            final MarkerOverlay marker = new MarkerOverlay(MTRMap.MOD_ID,
                    new BlockPos(landmark.x(), landmark.y(), landmark.z()), icon);
            marker.setDimension(world.dimension());
            marker.setLabel("");
            marker.setTitle(title.toString());
            marker.setActiveUIs(Context.UI.Fullscreen);
            out.put(landmark.id(), marker);
        }
    }

    private static boolean shouldShow(MapLandmark landmark) {
        if (!landmark.hasRoutes() && !MTRMapConfig.INSTANCE.showEmptyStation.get()
                && landmark.type() != MapLandmark.Type.DEPOT) {
            return false;
        }
        return switch (landmark.type()) {
            case STATION -> MTRMapConfig.INSTANCE.showStationLandmarks.get();
            case PLATFORM -> MTRMapConfig.INSTANCE.showPlatformLandmarks.get();
            case DEPOT -> MTRMapConfig.INSTANCE.showDepotLandmarks.get();
        };
    }

    static void placeTestMarker(BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        final IClientAPI api = getJourneyMapAPI();
        if (api == null) {
            MTRMap.LOGGER.warn("[MTRMap] JourneyMap API not initialized yet - try again after JourneyMap loads");
            return;
        }

        final MapImage icon = new MapImage(markerIcon("train", false), 16, 16);
        icon.setAnchorX(8);
        icon.setAnchorY(8);
        icon.setColor(0xFF00AAFF);
        final MarkerOverlay marker = new MarkerOverlay(MTRMap.MOD_ID, pos, icon);
        marker.setDimension(dimension);
        marker.setActiveUIs(Context.UI.Fullscreen);
        marker.setLabel("[MTR] Test marker");
        marker.setTitle("MTR Map Overlay JourneyMap integration works!");

        if (testMarker != null) {
            try {
                api.remove(testMarker);
            } catch (Exception ignored) {
            }
        }
        try {
            api.show(marker);
        } catch (Exception e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to show test marker", e);
            return;
        }
        testMarker = marker;
        MTRMap.LOGGER.info("[MTRMap] JourneyMap test marker placed at {}", pos.toShortString());
    }

    // Station mode: one marker per station, placed at the station centre
    private static void collectStationMarkers(Map<String, MarkerOverlay> out, MTRDataSummary dataSummary, Level world) {
        if (!MTRMapConfig.INSTANCE.showStationLandmarks.get()) {
            return;
        }

        for (Station station : collectStations()) {
            if (shouldBeFilteredOut(station, dataSummary)) {
                continue;
            }
            out.put(getMarkerId(station), createStationMarker(station, dataSummary, world));
        }
    }

    // Platform mode: one marker per platform, labelled with the platform
    // number and showing the station name plus route/destination info on hover
    private static void collectPlatformMarkers(Map<String, MarkerOverlay> out, Level world) {
        if (!MTRMapConfig.INSTANCE.showPlatformLandmarks.get()) {
            return;
        }

        // Build a map of platformId -> routes passing through it, since
        // platform.routes is not populated on the client side
        Map<Long, List<Route>> platformRouteMap = new HashMap<>();
        for (Route route : collectRoutes()) {
            List<RoutePlatformData> rpList = route.getRoutePlatforms();
            if (rpList == null) {
                continue;
            }
            for (RoutePlatformData rpd : rpList) {
                if (rpd.getPlatform() != null) {
                    platformRouteMap.computeIfAbsent(rpd.getPlatform().getId(), k -> new ArrayList<>()).add(route);
                }
            }
        }

        for (Station station : collectStations()) {
            String stationName = IGui.formatStationName(station.getName());
            if (stationName == null || stationName.isEmpty()) {
                continue;
            }

            for (Object railObj : new ArrayList<>(station.savedRails)) {
                if (!(railObj instanceof Platform platform)) {
                    continue;
                }

                String markerId = getMarkerId(platform);
                Position midPos = platform.getMidPosition();
                BlockPos pos = new BlockPos((int) midPos.getX(), (int) midPos.getY(), (int) midPos.getZ());

                // Label = platform name/number
                String platformName = platform.getName();
                if (platformName == null || platformName.isEmpty()) {
                    platformName = String.valueOf(platform.getId());
                }

                // Hover text = station name + route names with destinations
                StringBuilder title = new StringBuilder(stationName);
                List<String> routeInfos = buildRouteInfos(platform, platformRouteMap.get(platform.getId()));
                if (!routeInfos.isEmpty()) {
                    title.append("\n").append(String.join("\n", routeInfos));
                }

                out.put(markerId, createMarker(markerId, pos, platformName, title.toString(),
                        station.getTransportMode(), false, station.getColor(), world));
            }
        }
    }

    private static void collectDepotMarkers(Map<String, MarkerOverlay> out, Level world) {
        for (Depot depot : collectDepots()) {
            String depotName = IGui.formatStationName(depot.getName());
            String markerId = getMarkerId(depot);
            BlockPos pos = new BlockPos(
                    (int) depot.getCenter().getX(),
                    (int) depot.getMaxY(), // Use top of depot area
                    (int) depot.getCenter().getZ());
            out.put(markerId, createMarker(markerId, pos, depotName, depotName, depot.getTransportMode(), true,
                    depot.getColor(), world));
        }
    }

    /**
     * Build "RouteName → Destination" strings for a platform. Routes are
     * deduplicated by name; the destination is the last station on the route
     * (or its custom destination, if set).
     */
    private static List<String> buildRouteInfos(Platform platform, List<Route> routes) {
        List<String> routeInfos = new ArrayList<>();
        if (routes == null) {
            return routeInfos;
        }

        Set<String> seenRoutes = new HashSet<>();
        for (Route route : routes) {
            String routeName = route.getName();
            if (routeName == null || routeName.isEmpty()) {
                continue;
            }
            if (!seenRoutes.add(routeName)) {
                continue;
            }

            StringBuilder routeInfo = new StringBuilder(IGui.formatStationName(routeName.split("\\|\\|")[0]));
            String destination = findDestinationForPlatform(route, platform);
            if (destination != null && !destination.isEmpty()) {
                routeInfo.append(" → ").append(destination);
            }
            routeInfos.add(routeInfo.toString());
        }
        return routeInfos;
    }

    private static String findDestinationForPlatform(Route route, Platform currentPlatform) {
        try {
            List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
            if (routePlatforms == null || routePlatforms.isEmpty()) {
                return null;
            }

            RoutePlatformData lastPlatformData = routePlatforms.get(routePlatforms.size() - 1);
            String customDest = lastPlatformData.getCustomDestination();
            if (customDest != null && !customDest.isEmpty() && !Route.destinationIsReset(customDest)) {
                return customDest;
            }

            Platform lastPlatform = lastPlatformData.getPlatform();
            if (lastPlatform != null) {
                return IGui.formatStationName(lastPlatform.getStationName());
            }
        } catch (Exception e) {
            MTRMap.LOGGER.debug("[MTRMap] Error finding destination: {}", e.getMessage());
        }
        return null;
    }

    private static MarkerOverlay createStationMarker(Station station, MTRDataSummary mtrDataSummary, Level world) {
        String markerId = getMarkerId(station);
        String stationName = IGui.formatStationName(station.getName());
        BlockPos pos = new BlockPos(
                (int) station.getCenter().getX(),
                (int) station.getCenter().getY(),
                (int) station.getCenter().getZ());

        StringBuilder desc = new StringBuilder();
        desc.append("Fare zone: ").append(station.getZone1());
        List<MTRDataSummary.BasicRouteInfo> routesInStation = mtrDataSummary.getRoutesInStation(station);
        if (routesInStation != null && !routesInStation.isEmpty()) {
            desc.append("\nRoutes: ");
            for (int i = 0; i < routesInStation.size(); i++) {
                if (i > 0) {
                    desc.append(", ");
                }
                desc.append(IGui.formatStationName(routesInStation.get(i).name()));
            }
        }

        return createMarker(markerId, pos, stationName, desc.toString(), station.getTransportMode(), false,
                station.getColor(), world);
    }

    private static MarkerOverlay createMarker(String markerId, BlockPos pos, String label, String title,
            TransportMode transportMode, boolean isDepot, int color, Level world) {
        ResourceLocation iconRL = getMarkerIcon(transportMode, isDepot);
        MapImage icon = new MapImage(iconRL, 16, 16);
        icon.setAnchorX(8);
        icon.setAnchorY(8);
        icon.setColor(color | 0xFF000000); // ensure alpha
        icon.setDisplayWidth(isDepot ? 10 : 12);
        icon.setDisplayHeight(isDepot ? 10 : 12);

        // v2 API generates the marker id internally; markerId is kept for logging only
        MarkerOverlay marker = new MarkerOverlay(MTRMap.MOD_ID, pos, icon);
        marker.setDimension(world.dimension());
        marker.setLabel("");
        marker.setTitle(title);
        marker.setActiveUIs(Context.UI.Fullscreen);
        return marker;
    }

    private static ResourceLocation getMarkerIcon(TransportMode transportMode, boolean isDepot) {
        return markerIcon(getTransportModeName(transportMode), isDepot);
    }

    private static ResourceLocation markerIcon(String modeName, boolean isDepot) {
        String type = isDepot ? "depot" : "station";
        return ResourceLocation.fromNamespaceAndPath(MTRMap.MOD_ID,
                "textures/atlas/marker/" + modeName + "_" + type + ".png");
    }

    private static String getTransportModeName(TransportMode transportMode) {
        return transportMode.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static String getMarkerId(NameColorDataBase data) {
        String type = (data instanceof Station) ? "station"
                : (data instanceof Depot) ? "depot" : "platform";
        return getTransportModeName(data.getTransportMode()) + "_" + type + "_"
                + data.getHexId().toLowerCase();
    }

    private static boolean shouldBeFilteredOut(Station station, MTRDataSummary dataSummary) {
        List<MTRDataSummary.BasicRouteInfo> routes = dataSummary.getRoutesInStation(station);
        return !MTRMapConfig.INSTANCE.showEmptyStation.get() && (routes == null || routes.isEmpty());
    }

    /**
     * Aggregate stations from BOTH the local streaming instance AND the
     * dashboard instance (if the user opened it).
     */
    private static Set<Station> collectStations() {
        Set<Station> allStations = new HashSet<>();
        try {
            MinecraftClientData instance = MinecraftClientData.getInstance();
            if (instance != null) {
                allStations.addAll(instance.stations);
            }
            MinecraftClientData dashboard = MinecraftClientData.getDashboardInstance();
            if (dashboard != null) {
                allStations.addAll(dashboard.stations);
            }
        } catch (Exception e) {
            MTRMap.LOGGER.error("[MTRMap] Error accessing MTR station datasets: ", e);
        }
        return allStations;
    }

    private static Set<Depot> collectDepots() {
        Set<Depot> allDepots = new HashSet<>();
        try {
            MinecraftClientData instance = MinecraftClientData.getInstance();
            if (instance != null) {
                allDepots.addAll(instance.depots);
            }
            MinecraftClientData dashboard = MinecraftClientData.getDashboardInstance();
            if (dashboard != null) {
                allDepots.addAll(dashboard.depots);
            }
        } catch (Exception e) {
            MTRMap.LOGGER.error("[MTRMap] Error accessing MTR depot datasets: ", e);
        }
        return allDepots;
    }

    private static Set<Route> collectRoutes() {
        Set<Route> allRoutes = new HashSet<>();
        try {
            MinecraftClientData instance = MinecraftClientData.getInstance();
            if (instance != null) {
                allRoutes.addAll(instance.routes);
            }
            MinecraftClientData dashboard = MinecraftClientData.getDashboardInstance();
            if (dashboard != null) {
                allRoutes.addAll(dashboard.routes);
            }
        } catch (Exception e) {
            MTRMap.LOGGER.error("[MTRMap] Error accessing MTR route datasets: ", e);
        }
        return allRoutes;
    }

    /**
     * Gets the JourneyMap API via reflection so this class never needs a
     * direct reference to the plugin class.
     */
    private static IClientAPI getJourneyMapAPI() {
        try {
            Class<?> pluginClass = Class.forName("com.lx862.mtrmap.integration.journeymap.MTRJourneyMapPlugin");
            return (IClientAPI) pluginClass.getMethod("getAPI").invoke(null);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to get JourneyMap API", e);
            return null;
        }
    }
}
