package com.lx862.mtrsurveyor.integration.xaero;

import com.lx862.mtrsurveyor.MTRSurveyor;
import com.lx862.mtrsurveyor.config.MTRSurveyorConfig;
import com.lx862.mtrsurveyor.mapdata.MapDataCache;
import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.MapTrack;
import com.lx862.mtrsurveyor.mapdata.TrackRoutePalette;
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
import java.util.Map;

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

    private static final float TRACK_HALF_WIDTH_PX = 1.25f;
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
            drawRoutes(graphics, matrix, data.tracks, data.routePalette, scale, minX, minZ, maxX, maxZ);
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

    private static void drawRoutes(GuiGraphics graphics, Matrix4f matrix, List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> routeBands, double scale,
            double minX, double minZ, double maxX, double maxZ) {
        final VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());

        // One physical rail, one draw pass. Shared routes divide the same
        // TRACK-shaped ribbon across its width instead of painting over each other.
        for (MapTrack track : tracks) {
            final List<TrackRoutePalette.Entry> bands = routeBands.get(track.id);
            if (bands == null || bands.isEmpty()) {
                continue;
            }
            final float widthMultiplier = 1.0f + Math.min(0.6f, Math.max(0, bands.size() - 3) * 0.15f);
            final float halfWidth = worldLineWidth(TRACK_HALF_WIDTH_PX * widthMultiplier, scale);
            drawRibbon(matrix, consumer, track.points, halfWidth, bands, minX, minZ, maxX, maxZ);
        }
    }

    private static void drawRibbon(Matrix4f matrix, VertexConsumer consumer, List<double[]> points,
            float halfWidth, List<TrackRoutePalette.Entry> bands,
            double minX, double minZ, double maxX, double maxZ) {
        for (int i = 0; i < points.size() - 1; i++) {
            final double[] p1 = points.get(i);
            final double[] p2 = points.get(i + 1);
            if (!segmentOutsideView(p1[0], p1[1], p2[0], p2[1], minX, minZ, maxX, maxZ)) {
                drawRibbonSegment(matrix, consumer, p1[0], p1[1], p2[0], p2[1], halfWidth, bands);
            }
        }
    }

    private static void drawRibbonSegment(Matrix4f matrix, VertexConsumer consumer,
            double x1, double z1, double x2, double z2, float halfWidth,
            List<TrackRoutePalette.Entry> bands) {
        final double dx = x2 - x1;
        final double dz = z2 - z1;
        final double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            return;
        }
        final double nx = -dz / length;
        final double nz = dx / length;
        final double bandWidth = halfWidth * 2.0 / bands.size();
        for (int i = 0; i < bands.size(); i++) {
            final TrackRoutePalette.Entry band = bands.get(i);
            final double left = -halfWidth + bandWidth * i;
            final double right = i == bands.size() - 1 ? halfWidth : left + bandWidth;
            final int color = band.color();
            consumer.addVertex(matrix, (float) (x1 + nx * right), (float) (z1 + nz * right), 0)
                    .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, TRACK_ALPHA);
            consumer.addVertex(matrix, (float) (x2 + nx * right), (float) (z2 + nz * right), 0)
                    .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, TRACK_ALPHA);
            consumer.addVertex(matrix, (float) (x2 + nx * left), (float) (z2 + nz * left), 0)
                    .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, TRACK_ALPHA);
            consumer.addVertex(matrix, (float) (x1 + nx * left), (float) (z1 + nz * left), 0)
                    .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, TRACK_ALPHA);
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
            drawPolyline(matrix, consumer, track.points, halfWidth, r, g, b, TRACK_ALPHA,
                    minX, minZ, maxX, maxZ);
        }
    }

    /** Shared ROUTE/TRACK polyline renderer. */
    private static void drawPolyline(Matrix4f matrix, VertexConsumer consumer, List<double[]> points,
            float halfWidth, int r, int g, int b, int a,
            double minX, double minZ, double maxX, double maxZ) {
        for (int i = 0; i < points.size() - 1; i++) {
            final double[] p1 = points.get(i);
            final double[] p2 = points.get(i + 1);
            if (segmentOutsideView(p1[0], p1[1], p2[0], p2[1], minX, minZ, maxX, maxZ)) {
                continue;
            }
            drawSegment(matrix, consumer, p1[0], p1[1], p2[0], p2[1], halfWidth, r, g, b, a);
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

        List<TrackRoutePalette.Entry> bestRoutes = List.of();
        double bestRouteDist = SEGMENT_PICK_RADIUS * SEGMENT_PICK_RADIUS;
        for (MapTrack track : data.tracks) {
            final List<TrackRoutePalette.Entry> bands = data.routePalette.get(track.id);
            if (bands == null) {
                continue;
            }
            for (int i = 0; i < track.points.size() - 1; i++) {
                final double distSq = distanceToSegmentSq(mouseWorldX, mouseWorldZ,
                        track.points.get(i), track.points.get(i + 1));
                if (distSq < bestRouteDist) {
                    bestRouteDist = distSq;
                    bestRoutes = bands;
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
        for (TrackRoutePalette.Entry route : bestRoutes) {
            tooltip.add(Component.literal(route.name() == null ? "Route" : route.name())
                    .withStyle(ChatFormatting.BOLD).withColor(route.color()));
        }
        if (tooltip.size() > MAX_TOOLTIP_ROUTES) {
            return tooltip.subList(0, MAX_TOOLTIP_ROUTES);
        }
        return tooltip;
    }

    private static double distanceToSegmentSq(double px, double pz, MapRoute.Stop a, MapRoute.Stop b) {
        return distanceToSegmentSq(px, pz, new double[]{a.x, a.z}, new double[]{b.x, b.z});
    }

    private static double distanceToSegmentSq(double px, double pz, double[] a, double[] b) {
        final double dx = b[0] - a[0];
        final double dz = b[1] - a[1];
        final double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1.0E-6) {
            final double ex = px - a[0];
            final double ez = pz - a[1];
            return ex * ex + ez * ez;
        }
        double t = ((px - a[0]) * dx + (pz - a[1]) * dz) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        final double cx = a[0] + t * dx;
        final double cz = a[1] + t * dz;
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
