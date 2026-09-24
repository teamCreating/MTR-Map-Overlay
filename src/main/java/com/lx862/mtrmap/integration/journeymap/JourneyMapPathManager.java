package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.MapDataCache;
import com.lx862.mtrmap.mapdata.MapTrack;
import com.lx862.mtrmap.mapdata.TrackRibbonGeometry;
import com.lx862.mtrmap.mapdata.TrackRoutePalette;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Static fullscreen-map rail ribbons; never creates JourneyMap waypoints. */
final class JourneyMapPathManager {

    private static final int TRACK_COLOR = 0x404040;
    private static final List<PolygonOverlay> activePaths = new ArrayList<>();
    private static MapDataCache.DimensionData displayedData;
    private static ResourceKey<Level> displayedDimension;
    private static boolean displayedTracks;
    private static boolean displayedRoutes;

    private JourneyMapPathManager() {
    }

    static void sync(IClientAPI api, Level world, MapDataCache.DimensionData data) {
        final MTRMapConfig config = MTRMapConfig.INSTANCE;
        final boolean tracksEnabled = config.enabled.get() && config.trackLinesEnabled.get();
        final boolean routesEnabled = config.enabled.get() && config.routeLinesEnabled.get();
        if (displayedData == data && displayedDimension == world.dimension()
                && displayedTracks == tracksEnabled && displayedRoutes == routesEnabled) {
            return;
        }

        for (PolygonOverlay path : activePaths) {
            try {
                api.remove(path);
            } catch (Exception e) {
                MTRMap.LOGGER.debug("[MTRMap] Could not remove an old JourneyMap path: {}", e.getMessage());
            }
        }
        activePaths.clear();
        displayedData = data;
        displayedDimension = world.dimension();
        displayedTracks = tracksEnabled;
        displayedRoutes = routesEnabled;
        if (!tracksEnabled && !routesEnabled) {
            return;
        }

        int failures = 0;
        for (MapTrack track : data.tracks) {
            if (track.points.size() < 2) {
                continue;
            }
            final List<TrackRoutePalette.Entry> bands = data.routePalette.getOrDefault(track.id, List.of());
            final double halfWidth = Math.max(2.0, bands.size());
            if (tracksEnabled) {
                failures += showBand(api, world.dimension(), track, -halfWidth - 0.75, halfWidth + 0.75,
                        TRACK_COLOR, 0.7f, -20, "MTR track");
            }
            if (routesEnabled && !bands.isEmpty()) {
                final double bandWidth = 2 * halfWidth / bands.size();
                for (int i = 0; i < bands.size(); i++) {
                    final TrackRoutePalette.Entry band = bands.get(i);
                    final double left = -halfWidth + i * bandWidth;
                    failures += showBand(api, world.dimension(), track, left, left + bandWidth,
                            band.color(), 0.85f, -10, band.name());
                }
            }
        }
        MTRMap.LOGGER.info("[MTRMap] JourneyMap paths: {} rail/route bands, {} failed in {}",
                activePaths.size(), failures, world.dimension().location());
    }

    private static int showBand(IClientAPI api, ResourceKey<Level> dimension, MapTrack track,
            double left, double right, int color, float opacity, int order, String title) {
        final List<double[]> outline = TrackRibbonGeometry.band(track.points, left, right);
        if (outline.isEmpty()) {
            return 0;
        }
        final List<BlockPos> vertices = new ArrayList<>(outline.size());
        for (double[] point : outline) {
            final BlockPos vertex = new BlockPos((int) Math.round(point[0]), 64, (int) Math.round(point[1]));
            if (vertices.isEmpty() || !vertices.getLast().equals(vertex)) {
                vertices.add(vertex);
            }
        }
        if (vertices.size() > 1 && vertices.getFirst().equals(vertices.getLast())) {
            vertices.removeLast();
        }
        if (vertices.stream().distinct().limit(3).count() < 3) {
            return 0;
        }

        final ShapeProperties style = new ShapeProperties().setFillColor(color & 0xFFFFFF)
                .setFillOpacity(opacity).setStrokeOpacity(0);
        final PolygonOverlay overlay = new PolygonOverlay(MTRMap.MOD_ID, dimension, style, new MapPolygon(vertices));
        overlay.setActiveUIs(Context.UI.Fullscreen);
        overlay.setDisplayOrder(order);
        overlay.setLabel("");
        overlay.setTitle(title);
        try {
            api.show(overlay);
            activePaths.add(overlay);
            return 0;
        } catch (Exception e) {
            MTRMap.LOGGER.debug("[MTRMap] JourneyMap path overlay failed for rail {}: {}", track.id, e.getMessage());
            return 1;
        }
    }
}
