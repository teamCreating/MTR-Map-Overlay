package com.lx862.mtrsurveyor.integration.xaero;

import com.lx862.mtrsurveyor.MTRSurveyor;
import com.lx862.mtrsurveyor.config.MTRSurveyorConfig;
import com.lx862.mtrsurveyor.mapdata.MapDataCache;
import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.MapTrack;
import com.lx862.mtrsurveyor.mixin.client.xaero.XaeroWorldMapAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import xaero.map.gui.GuiMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the MTR path layer (route lines & track geometry) on Xaero's World
 * Map. Called from {@code XaeroWorldMapMixin} during GuiMap rendering.
 *
 * <p>Modelled after Create mod's train map integration: the pose is
 * transformed into world coordinates (center -&gt; map scale -&gt; camera
 * offset) so route geometry can be emitted in world units and follows the
 * map while panning/zooming.</p>
 */
public class XaeroRouteRenderer {

    private static final float ROUTE_HALF_WIDTH_PX = 3.0f;
    private static final float TRACK_HALF_WIDTH_PX = 1.25f;
    private static final int ROUTE_ALPHA = 210;
    private static final int TRACK_COLOR = 0x404040;
    private static final int TRACK_ALPHA = 175;
    /** Stop hit radius (in blocks) for hover picking. */
    private static final double STOP_PICK_RADIUS = 20.0;
    /** Segment hit distance (in blocks) for hover picking. */
    private static final double SEGMENT_PICK_RADIUS = 10.0;
    private static final int MAX_TOOLTIP_ROUTES = 4;

    private static final int WIDGET_X = 5;
    private static final int WIDGET_Y = 60;
    private static final int WIDGET_WIDTH = 44;
    private static final int WIDGET_HEIGHT = 14;
    private static final int WIDGET_SPACING = 4;

    /** Set once the render hook is known to work, so users can diagnose silent mixin failures. */
    private static boolean renderHookVerified = false;
    private static boolean warnedNoData = false;

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (isHovered(mouseX, mouseY, 0)) {
            MTRSurveyorConfig.INSTANCE.routeLinesEnabled.set(!MTRSurveyorConfig.INSTANCE.routeLinesEnabled.get());
            return true;
        }
        if (isHovered(mouseX, mouseY, 1)) {
            MTRSurveyorConfig.INSTANCE.trackLinesEnabled.set(!MTRSurveyorConfig.INSTANCE.trackLinesEnabled.get());
            return true;
        }
        return false;
    }

    /**
     * Main render entry point, called from the mixin.
     */
    public static void onRender(GuiGraphics graphics, GuiMap screen, int mouseX, int mouseY, float partialTick) {
        if (!MTRSurveyorConfig.INSTANCE.enabled.get()) {
            return;
        }

        if (!renderHookVerified) {
            renderHookVerified = true;
            MTRSurveyor.LOGGER.info("[MTRSurveyor] Path layer render hook into Xaero's World Map is active");
        }

        final Minecraft mc = Minecraft.getInstance();

        // Resolve the dimension currently being viewed on the map, guarded so a
        // Xaero internals change can never break the whole render.
        ResourceKey<Level> viewedDimension = null;
        double cameraX = 0;
        double cameraZ = 0;
        double mapScale = 1;
        try {
            final XaeroWorldMapAccessor accessor = (XaeroWorldMapAccessor) (Object) screen;
            cameraX = accessor.getCameraX();
            cameraZ = accessor.getCameraZ();
            mapScale = accessor.getScale();
            viewedDimension = accessor.getMapProcessor().getMapWorld().getCurrentDimension().getDimId();
        } catch (Throwable e) {
            MTRSurveyor.LOGGER.debug("[MTRSurveyor] Failed to read Xaero map state: {}", e.getMessage());
            if (mc.level != null) {
                viewedDimension = mc.level.dimension();
            }
        }

        // MTR client data belongs to the player's dimension. Without server-synced
        // data we can only draw the dimension the player is actually in.
        // Key format matches MTR's world ids ("minecraft/overworld", slash not colon).
        final String dimensionKey;
        if (viewedDimension == null) {
            dimensionKey = null;
        } else {
            final net.minecraft.resources.ResourceLocation dimLocation = viewedDimension.location();
            dimensionKey = dimLocation.getNamespace() + "/" + dimLocation.getPath();
        }
        final boolean playerDimension = mc.level != null && viewedDimension == mc.level.dimension();
        if (dimensionKey == null || (!playerDimension && !MapDataCache.hasServerData(dimensionKey))) {
            renderToggleWidgets(graphics, mc.font, mouseX, mouseY);
            return;
        }

        final MapDataCache.DimensionData data = MapDataCache.get(dimensionKey);
        if (data.isEmpty()) {
            renderToggleWidgets(graphics, mc.font, mouseX, mouseY);
            if (!warnedNoData && playerDimension) {
                warnedNoData = true;
                MTRSurveyor.LOGGER.info(
                        "[MTRSurveyor] No MTR data available for the path layer yet (MTR syncs data within "
                                + "render distance; travel around or install this mod on the server for full-network view)");
            }
            return;
        }

        // Xaero multiplies the map scale by the total GUI scale factor; dividing
        // gives us a transform where 1 unit = 1 block.
        final double guiScale = (double) mc.getWindow().getWidth() / (double) mc.getWindow().getGuiScaledWidth();
        final double scale = mapScale / guiScale;

        final com.mojang.blaze3d.vertex.PoseStack stack = graphics.pose();
        // GuiMap inherits width/height from Screen; cast so we compile against
        // mapped Screen fields (the Xaero jar itself ships SRG-named members).
        final net.minecraft.client.gui.screens.Screen mcScreen = (net.minecraft.client.gui.screens.Screen) (Object) screen;
        stack.pushPose();
        stack.translate(mcScreen.width / 2.0f, mcScreen.height / 2.0f, 0);
        stack.scale((float) scale, (float) scale, 1);
        stack.translate(-cameraX, -cameraZ, 0);
        final Matrix4f matrix = stack.last().pose();

        // Visible world bounds for segment culling.
        final double halfW = mcScreen.width / 2.0 / scale;
        final double halfH = mcScreen.height / 2.0 / scale;
        final double minX = cameraX - halfW;
        final double minZ = cameraZ - halfH;
        final double maxX = cameraX + halfW;
        final double maxZ = cameraZ + halfH;

        if (MTRSurveyorConfig.INSTANCE.trackLinesEnabled.get()) {
            drawTracks(graphics, matrix, data.tracks, scale, minX, minZ, maxX, maxZ);
        }
        if (MTRSurveyorConfig.INSTANCE.routeLinesEnabled.get()) {
            drawRoutes(graphics, matrix, data.routes, scale, minX, minZ, maxX, maxZ);
        }

        // Flush now so the path layer lands in the map, without being culled away
        // by later GUI batch usage.
        RenderSystem.disableCull();
        graphics.bufferSource().endBatch(RenderType.gui());
        RenderSystem.enableCull();

        stack.popPose();

        renderToggleWidgets(graphics, mc.font, mouseX, mouseY);

        // Hover picking & tooltip (in screen space, drawn after the batch flush).
        if (mc.screen != null && MTRSurveyorConfig.INSTANCE.routeLinesEnabled.get()) {
            final double mouseWorldX = (mouseX - mcScreen.width / 2.0) / scale + cameraX;
            final double mouseWorldZ = (mouseY - mcScreen.height / 2.0) / scale + cameraZ;
            final List<Component> tooltip = pickTooltip(data, mouseWorldX, mouseWorldZ);
            if (!tooltip.isEmpty()) {
                graphics.renderComponentTooltip(mc.font, tooltip, mouseX, mouseY);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Geometry drawing
    // -----------------------------------------------------------------------------------------------------------------

    private static void drawRoutes(GuiGraphics graphics, Matrix4f matrix, List<MapRoute> routes, double scale,
            double minX, double minZ, double maxX, double maxZ) {
        final VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        final float halfWidth = worldLineWidth(ROUTE_HALF_WIDTH_PX, scale);
        // Lateral distance between parallel routes sharing the same track,
        // expressed in world units (one full line width per lane step).
        final float laneWidth = 2 * halfWidth;

        for (MapRoute route : routes) {
            // Cheap rejection: skip routes entirely outside the view.
            if (!routeIntersects(route, minX, minZ, maxX, maxZ)) {
                continue;
            }

            final int argb = route.color;
            final int r = (argb >> 16) & 0xFF;
            final int g = (argb >> 8) & 0xFF;
            final int b = argb & 0xFF;

            if (route.path.size() >= 2) {
                // Snapped track geometry with per-point lane offsets
                drawSnappedRoute(matrix, consumer, route, halfWidth, laneWidth, r, g, b, minX, minZ, maxX, maxZ);
            } else {
                // Straight fallback (client-only mode): stop-to-stop lines
                final int pointCount = route.stops.size() + (route.circular ? 1 : 0);
                for (int i = 0; i < pointCount - 1; i++) {
                    final MapRoute.Stop p1 = route.stops.get(i);
                    final MapRoute.Stop p2 = route.stops.get((i + 1) % route.stops.size());
                    drawSegment(matrix, consumer, p1.x, p1.z, p2.x, p2.z, halfWidth, r, g, b, ROUTE_ALPHA);
                }

                // Square caps at each stop so joints and endpoints look clean.
                for (MapRoute.Stop stop : route.stops) {
                    fillQuad(matrix, consumer, stop.x - halfWidth, stop.z - halfWidth,
                            stop.x + halfWidth, stop.z + halfWidth, r, g, b, ROUTE_ALPHA);
                }
            }
        }
    }

    /**
     * Draw a route whose geometry follows the real rails. Each point carries a
     * lane offset index: the segment is shifted sideways by lane * laneWidth
     * along its own normal, so routes sharing the same track render as
     * parallel lines instead of stacking.
     */
    private static void drawSnappedRoute(Matrix4f matrix, VertexConsumer consumer, MapRoute route,
            float halfWidth, float laneWidth, int r, int g, int b,
            double minX, double minZ, double maxX, double maxZ) {
        final List<MapRoute.PathPoint> path = route.path;
        double prevEndX = Double.NaN;
        double prevEndZ = Double.NaN;

        for (int i = 0; i < path.size() - 1; i++) {
            final MapRoute.PathPoint p1 = path.get(i);
            final MapRoute.PathPoint p2 = path.get(i + 1);
            if (segmentOutsideView(p1.x(), p1.z(), p2.x(), p2.z(), minX, minZ, maxX, maxZ)) {
                prevEndX = Double.NaN;
                continue;
            }

            // Perpendicular unit normal of this segment
            final double dx = p2.x() - p1.x();
            final double dz = p2.z() - p1.z();
            final double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4) {
                continue;
            }
            final double nx = -dz / len;
            final double nz = dx / len;

            final double off1 = p1.lane() * laneWidth;
            final double off2 = p2.lane() * laneWidth;
            final double ax = p1.x() + nx * off1;
            final double az = p1.z() + nz * off1;
            final double bx = p2.x() + nx * off2;
            final double bz = p2.z() + nz * off2;

            drawSegment(matrix, consumer, ax, az, bx, bz, halfWidth, r, g, b, ROUTE_ALPHA);

            // Bridge to the previous segment's offset endpoint (same underlying
            // vertex, but a different lane or curve normal) so the line stays
            // connected through kinks and lane changes.
            if (!Double.isNaN(prevEndX)) {
                drawSegment(matrix, consumer, prevEndX, prevEndZ, ax, az, halfWidth, r, g, b, ROUTE_ALPHA);
            }
            prevEndX = bx;
            prevEndZ = bz;
        }
    }

    private static void drawTracks(GuiGraphics graphics, Matrix4f matrix, List<MapTrack> tracks, double scale,
            double minX, double minZ, double maxX, double maxZ) {
        final VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        final float halfWidth = worldLineWidth(TRACK_HALF_WIDTH_PX, scale);
        final int r = (TRACK_COLOR >> 16) & 0xFF;
        final int g = (TRACK_COLOR >> 8) & 0xFF;
        final int b = TRACK_COLOR & 0xFF;

        for (MapTrack track : tracks) {
            final List<double[]> points = track.points;
            for (int i = 0; i < points.size() - 1; i++) {
                final double[] p1 = points.get(i);
                final double[] p2 = points.get(i + 1);
                if (segmentOutsideView(p1[0], p1[1], p2[0], p2[1], minX, minZ, maxX, maxZ)) {
                    continue;
                }
                drawSegment(matrix, consumer, p1[0], p1[1], p2[0], p2[1], halfWidth, r, g, b, TRACK_ALPHA);
            }
        }
    }

    private static float worldLineWidth(float px, double scale) {
        // Keep the line a constant screen width, but never thinner than a third
        // of a block when zoomed all the way in.
        return (float) Math.max(px / scale, 0.33);
    }

    private static void drawSegment(Matrix4f matrix, VertexConsumer consumer,
            double x1, double z1, double x2, double z2, float halfWidth, int r, int g, int b, int a) {
        final double dx = x2 - x1;
        final double dz = z2 - z1;
        final double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            return;
        }
        final double px = -dz / length * halfWidth;
        final double pz = dx / length * halfWidth;

        consumer.addVertex(matrix, (float) (x1 + px), (float) (z1 + pz), 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) (x2 + px), (float) (z2 + pz), 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) (x2 - px), (float) (z2 - pz), 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) (x1 - px), (float) (z1 - pz), 0).setColor(r, g, b, a);
    }

    private static void fillQuad(Matrix4f matrix, VertexConsumer consumer,
            double x1, double z1, double x2, double z2, int r, int g, int b, int a) {
        consumer.addVertex(matrix, (float) x1, (float) z1, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) x1, (float) z2, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) x2, (float) z2, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) x2, (float) z1, 0).setColor(r, g, b, a);
    }

    private static boolean routeIntersects(MapRoute route, double minX, double minZ, double maxX, double maxZ) {
        for (MapRoute.Stop stop : route.stops) {
            if (stop.x >= minX && stop.x <= maxX && stop.z >= minZ && stop.z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentOutsideView(double x1, double z1, double x2, double z2,
            double minX, double minZ, double maxX, double maxZ) {
        return Math.max(x1, x2) < minX || Math.min(x1, x2) > maxX
                || Math.max(z1, z2) < minZ || Math.min(z1, z2) > maxZ;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Hover picking
    // -----------------------------------------------------------------------------------------------------------------

    private static List<Component> pickTooltip(MapDataCache.DimensionData data, double mouseWorldX, double mouseWorldZ) {
        MapRoute.Stop bestStop = null;
        double bestStopDist = STOP_PICK_RADIUS * STOP_PICK_RADIUS;

        for (MapRoute route : data.routes) {
            for (MapRoute.Stop stop : route.stops) {
                final double dx = stop.x - mouseWorldX;
                final double dz = stop.z - mouseWorldZ;
                final double distSq = dx * dx + dz * dz;
                if (distSq < bestStopDist) {
                    bestStopDist = distSq;
                    bestStop = stop;
                }
            }
        }

        MapRoute bestRoute = null;
        double bestRouteDist = SEGMENT_PICK_RADIUS * SEGMENT_PICK_RADIUS;
        for (MapRoute route : data.routes) {
            final int pointCount = route.stops.size() + (route.circular ? 1 : 0);
            for (int i = 0; i < pointCount - 1; i++) {
                final MapRoute.Stop a = route.stops.get(i);
                final MapRoute.Stop b = route.stops.get((i + 1) % route.stops.size());
                final double distSq = distanceToSegmentSq(mouseWorldX, mouseWorldZ, a, b);
                if (distSq < bestRouteDist) {
                    bestRouteDist = distSq;
                    bestRoute = route;
                }
            }
        }

        final List<Component> tooltip = new ArrayList<>();
        if (bestStop != null) {
            tooltip.add(Component.literal(bestStop.stationName == null ? "?" : bestStop.stationName)
                    .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD));
            if (bestStop.destination != null && !bestStop.destination.isEmpty()) {
                tooltip.add(Component.literal("→ " + bestStop.destination).withStyle(ChatFormatting.GRAY));
            }
        }
        if (bestRoute != null) {
            tooltip.add(Component.literal(bestRoute.name == null ? "Route" : bestRoute.name)
                    .withStyle(ChatFormatting.BOLD));
        }
        if (tooltip.size() > MAX_TOOLTIP_ROUTES) {
            return tooltip.subList(0, MAX_TOOLTIP_ROUTES);
        }
        return tooltip;
    }

    private static double distanceToSegmentSq(double px, double pz, MapRoute.Stop a, MapRoute.Stop b) {
        final double dx = b.x - a.x;
        final double dz = b.z - a.z;
        final double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1.0E-6) {
            final double ex = px - a.x;
            final double ez = pz - a.z;
            return ex * ex + ez * ez;
        }
        double t = ((px - a.x) * dx + (pz - a.z) * dz) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        final double cx = a.x + t * dx;
        final double cz = a.z + t * dz;
        final double ex = px - cx;
        final double ez = pz - cz;
        return ex * ex + ez * ez;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Toggle widgets
    // -----------------------------------------------------------------------------------------------------------------

    private static void renderToggleWidgets(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        drawToggle(graphics, font, mouseX, mouseY, 0, "ROUTES",
                MTRSurveyorConfig.INSTANCE.routeLinesEnabled.get(),
                Component.literal("Toggle MTR route lines"));
        drawToggle(graphics, font, mouseX, mouseY, 1, "TRACKS",
                MTRSurveyorConfig.INSTANCE.trackLinesEnabled.get(),
                Component.literal("Toggle MTR track layer"));
    }

    private static void drawToggle(GuiGraphics graphics, Font font, int mouseX, int mouseY, int index,
            String label, boolean enabled, Component tooltip) {
        final int x = WIDGET_X;
        final int y = WIDGET_Y + index * (WIDGET_HEIGHT + WIDGET_SPACING);
        final int color = enabled ? 0xAA228822 : 0xAA882222;
        final int borderColor = isHovered(mouseX, mouseY, index) ? 0xFFFFFFFF : 0xFF000000;

        graphics.fill(x - 1, y - 1, x + WIDGET_WIDTH + 1, y + WIDGET_HEIGHT + 1, borderColor);
        graphics.fill(x, y, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, color);
        graphics.drawCenteredString(font, label, x + WIDGET_WIDTH / 2,
                y + (WIDGET_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);

        if (isHovered(mouseX, mouseY, index)) {
            graphics.renderComponentTooltip(font, List.of(tooltip), mouseX, mouseY + 12);
        }
    }

    private static boolean isHovered(double mouseX, double mouseY, int index) {
        final int y = WIDGET_Y + index * (WIDGET_HEIGHT + WIDGET_SPACING);
        return mouseX >= WIDGET_X && mouseX <= WIDGET_X + WIDGET_WIDTH
                && mouseY >= y && mouseY <= y + WIDGET_HEIGHT;
    }
}
