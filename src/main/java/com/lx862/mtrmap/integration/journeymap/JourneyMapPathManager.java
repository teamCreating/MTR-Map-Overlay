package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.MapDataCache;
import com.lx862.mtrmap.mapdata.MapTrack;
import com.lx862.mtrmap.mapdata.RailRenderStyle;
import com.lx862.mtrmap.mapdata.TrackRoutePalette;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Draws JourneyMap tracks and routes in pixel space, like the Xaero layer. */
final class JourneyMapPathManager {

    private static MapDataCache.DimensionData preparedData;
    private static List<PreparedTrack> preparedTracks = List.of();

    private JourneyMapPathManager() {
    }

    static void render(GuiGraphics graphics, MapDataCache.DimensionData data,
            JourneyMapScreenProjection projection) {
        final MTRMapConfig config = MTRMapConfig.INSTANCE;
        final boolean tracksEnabled = config.enabled.get() && config.trackLinesEnabled.get();
        final boolean routesEnabled = config.enabled.get() && config.routeLinesEnabled.get();
        if ((!tracksEnabled && !routesEnabled) || projection.pixelsPerBlock() <= 0) {
            return;
        }

        final double scale = projection.pixelsPerBlock();
        final int width = graphics.guiWidth();
        final int height = graphics.guiHeight();
        final JourneyMapScreenProjection.WorldBounds view = projection.visibleWorldBounds(width, height, 16);
        if (view == null) {
            return;
        }
        prepare(data);

        final VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        final Matrix4f matrix = graphics.pose().last().pose();

        // One RenderType and one flush give an unambiguous order on every map:
        // all physical rails first, all coloured route lanes second.
        if (tracksEnabled) {
            for (PreparedTrack prepared : preparedTracks) {
                if (!prepared.visible(view)) {
                    continue;
                }
                final MapTrack track = prepared.track;
                final List<TrackRoutePalette.Entry> bands = prepared.bands;
                float halfWidth = RailRenderStyle.screenHalfWidth(RailRenderStyle.TRACK_HALF_WIDTH_PX, scale);
                if (routesEnabled && !bands.isEmpty()) {
                    final float routeWidth = routeHalfWidth(bands.size(), scale);
                    halfWidth = Math.max(halfWidth, routeWidth + RailRenderStyle.TRACK_SHOULDER_PX);
                }
                drawTrack(matrix, consumer, track, projection, halfWidth, width, height);
            }
        }
        if (routesEnabled) {
            for (PreparedTrack prepared : preparedTracks) {
                if (!prepared.visible(view)) {
                    continue;
                }
                final MapTrack track = prepared.track;
                final List<TrackRoutePalette.Entry> bands = prepared.bands;
                if (!bands.isEmpty()) {
                    drawRoutes(matrix, consumer, track, bands, projection,
                            routeHalfWidth(bands.size(), scale), width, height);
                }
            }
        }
        RenderSystem.disableCull();
        graphics.bufferSource().endBatch(RenderType.gui());
        RenderSystem.enableCull();
    }

    private static void prepare(MapDataCache.DimensionData data) {
        if (preparedData == data) {
            return;
        }
        final List<PreparedTrack> next = new ArrayList<>(data.tracks.size());
        for (MapTrack track : data.tracks) {
            if (track.points.size() < 2) {
                continue;
            }
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (double[] point : track.points) {
                minX = Math.min(minX, point[0]);
                maxX = Math.max(maxX, point[0]);
                minZ = Math.min(minZ, point[1]);
                maxZ = Math.max(maxZ, point[1]);
            }
            next.add(new PreparedTrack(track, data.routePalette.getOrDefault(track.id, List.of()),
                    minX, minZ, maxX, maxZ));
        }
        preparedTracks = next;
        preparedData = data;
    }

    private record PreparedTrack(MapTrack track, List<TrackRoutePalette.Entry> bands,
            double minX, double minZ, double maxX, double maxZ) {
        boolean visible(JourneyMapScreenProjection.WorldBounds view) {
            return view.intersects(minX, minZ, maxX, maxZ);
        }
    }

    private static float routeHalfWidth(int routeCount, double scale) {
        return RailRenderStyle.screenHalfWidth(
                RailRenderStyle.TRACK_HALF_WIDTH_PX * RailRenderStyle.routeWidthMultiplier(routeCount), scale);
    }

    private static void drawTrack(Matrix4f matrix, VertexConsumer consumer, MapTrack track,
            JourneyMapScreenProjection projection, float halfWidth, int viewWidth, int viewHeight) {
        final int color = RailRenderStyle.TRACK_COLOR;
        for (int i = 0; i + 1 < track.points.size(); i++) {
            final double[] start = track.points.get(i);
            final double[] end = track.points.get(i + 1);
            final double x1 = projection.x(start[0], start[1]);
            final double y1 = projection.y(start[0], start[1]);
            final double x2 = projection.x(end[0], end[1]);
            final double y2 = projection.y(end[0], end[1]);
            if (outsideView(x1, y1, x2, y2, viewWidth, viewHeight)) {
                continue;
            }
            drawSegment(matrix, consumer, x1, y1, x2, y2, -halfWidth, halfWidth, color);
        }
    }

    private static void drawRoutes(Matrix4f matrix, VertexConsumer consumer, MapTrack track,
            List<TrackRoutePalette.Entry> bands, JourneyMapScreenProjection projection,
            float halfWidth, int viewWidth, int viewHeight) {
        final double bandWidth = 2.0 * halfWidth / bands.size();
        for (int i = 0; i + 1 < track.points.size(); i++) {
            final double[] start = track.points.get(i);
            final double[] end = track.points.get(i + 1);
            final double x1 = projection.x(start[0], start[1]);
            final double y1 = projection.y(start[0], start[1]);
            final double x2 = projection.x(end[0], end[1]);
            final double y2 = projection.y(end[0], end[1]);
            if (outsideView(x1, y1, x2, y2, viewWidth, viewHeight)) {
                continue;
            }
            for (int lane = 0; lane < bands.size(); lane++) {
                final double left = -halfWidth + lane * bandWidth;
                final double right = lane == bands.size() - 1 ? halfWidth : left + bandWidth;
                drawSegment(matrix, consumer, x1, y1, x2, y2, left, right, bands.get(lane).color());
            }
        }
    }

    private static boolean outsideView(double x1, double y1, double x2, double y2, int width, int height) {
        final double margin = 8;
        return !Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)
                || (x1 < -margin && x2 < -margin) || (x1 > width + margin && x2 > width + margin)
                || (y1 < -margin && y2 < -margin) || (y1 > height + margin && y2 > height + margin);
    }

    private static void drawSegment(Matrix4f matrix, VertexConsumer consumer,
            double x1, double y1, double x2, double y2, double left, double right, int color) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        final double length = Math.hypot(dx, dy);
        if (length < 1.0E-4) {
            return;
        }
        final double nx = -dy / length;
        final double ny = dx / length;
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        final int a = RailRenderStyle.TRACK_ALPHA;
        consumer.addVertex(matrix, (float) (x1 + nx * right), (float) (y1 + ny * right), 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) (x2 + nx * right), (float) (y2 + ny * right), 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) (x2 + nx * left), (float) (y2 + ny * left), 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) (x1 + nx * left), (float) (y1 + ny * left), 0).setColor(r, g, b, a);
    }
}
