package io.github.teamcreating.mtrmap.integration.xaero;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.mapdata.MapDataCache;
import io.github.teamcreating.mtrmap.train.ClientTrainStream;
import io.github.teamcreating.mtrmap.mapdata.PlanarMapScene;
import io.github.teamcreating.mtrmap.mapdata.ClientPlanarScenes;
import io.github.teamcreating.mtrmap.mapdata.MapCar;
import io.github.teamcreating.mtrmap.integration.TrainMapTooltip;
import io.github.teamcreating.mtrmap.mapdata.MapLandmark;
import io.github.teamcreating.mtrmap.mapdata.MapRoute;
import io.github.teamcreating.mtrmap.mapdata.MapTrack;
import io.github.teamcreating.mtrmap.mapdata.TrackRoutePalette;
import io.github.teamcreating.mtrmap.mixin.client.xaero.XaeroWorldMapAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

    /** Stop hit radius (in blocks) for hover picking. */
    private static final double STOP_PICK_RADIUS = 20.0;
    /** Segment hit distance (in blocks) for hover picking. */
    private static final double SEGMENT_PICK_RADIUS = 10.0;
    private static final int MAX_TOOLTIP_ROUTES = 4;
    private static final ResourceLocation STATION_ICON = new ResourceLocation(MTRMap.MOD_ID,
            "textures/atlas/marker/train_station.png");
    private static final ResourceLocation DEPOT_ICON = new ResourceLocation(MTRMap.MOD_ID,
            "textures/atlas/marker/train_depot.png");
    private static final int STATION_ICON_SIZE = 12;
    private static final int DEPOT_ICON_SIZE = 10;
    private static final int PLATFORM_ICON_SIZE = 5;
    private static final double PLATFORM_MIN_SCALE = 0.15;

    private static final int WIDGET_X = 5;
    private static final int WIDGET_Y = 60;
    private static final int WIDGET_WIDTH = 78;
    private static final int WIDGET_HEIGHT = 23;
    private static final int WIDGET_SPACING = 5;
    private static final ResourceLocation ROUTE_ICON_ON = toggleIcon("route_on");
    private static final ResourceLocation ROUTE_ICON_OFF = toggleIcon("route_off");
    private static final ResourceLocation TRACK_ICON_ON = toggleIcon("track_on");
    private static final ResourceLocation TRACK_ICON_OFF = toggleIcon("track_off");

    /** Set once the render hook is known to work, so users can diagnose silent mixin failures. */
    private static boolean renderHookVerified = false;
    private static boolean warnedNoData = false;

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (isHovered(mouseX, mouseY, 0)) {
            MTRMapConfig.INSTANCE.routeLinesEnabled.set(!MTRMapConfig.INSTANCE.routeLinesEnabled.get());
            return true;
        }
        if (isHovered(mouseX, mouseY, 1)) {
            MTRMapConfig.INSTANCE.trackLinesEnabled.set(!MTRMapConfig.INSTANCE.trackLinesEnabled.get());
            return true;
        }
        return false;
    }

    /**
     * Main render entry point, called from the mixin.
     */
    public static void onRender(GuiGraphics graphics, GuiMap screen, int mouseX, int mouseY, float partialTick) {
        if (!MTRMapConfig.INSTANCE.enabled.get()) {
            return;
        }

        if (!renderHookVerified) {
            renderHookVerified = true;
            MTRMap.LOGGER.info("[MTRMap] Path layer render hook into Xaero's World Map is active");
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
            MTRMap.LOGGER.debug("[MTRMap] Failed to read Xaero map state: {}", e.getMessage());
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
        if (dimensionKey == null) {
            renderToggleWidgets(graphics, mc.font, mouseX, mouseY);
            return;
        }

        final MapDataCache.DimensionData data = MapDataCache.get(dimensionKey);
        final double guiScale = (double) mc.getWindow().getWidth() / (double) mc.getWindow().getGuiScaledWidth();
        final double scale = mapScale / guiScale;
        final net.minecraft.client.gui.screens.Screen mcScreen = (net.minecraft.client.gui.screens.Screen) (Object) screen;
        final List<MapCar> cars = MTRMapConfig.INSTANCE.trainCarsEnabled.get()
                ? ClientTrainStream.cars(dimensionKey, cameraX - mcScreen.width / 2.0 / scale,
                        cameraZ - mcScreen.height / 2.0 / scale, cameraX + mcScreen.width / 2.0 / scale,
                        cameraZ + mcScreen.height / 2.0 / scale, scale) : List.of();
        if (data.isEmpty() && cars.isEmpty()) {
            renderToggleWidgets(graphics, mc.font, mouseX, mouseY);
            ClientTrainStream.drawStatus(graphics, dimensionKey);
            if (!warnedNoData && playerDimension) {
                warnedNoData = true;
                MTRMap.LOGGER.info(
                        "[MTRMap] No MTR data available for the path layer yet (MTR syncs data within "
                                + "render distance; travel around or install this mod on the server for full-network view)");
            }
            return;
        }

        // Xaero multiplies the map scale by the total GUI scale factor; dividing
        // gives us a transform where 1 unit = 1 block.

        final com.mojang.blaze3d.vertex.PoseStack stack = graphics.pose();
        // GuiMap inherits width/height from Screen; cast so we compile against
        // mapped Screen fields (the Xaero jar itself ships SRG-named members).
        final Matrix4f screenMatrix = new Matrix4f(stack.last().pose());
        stack.pushPose();
        stack.translate(mcScreen.width / 2.0f, mcScreen.height / 2.0f, 0);
        stack.scale((float) scale, (float) scale, 1);
        stack.translate(-cameraX, -cameraZ, 0);
        // Visible world bounds for segment culling.
        final double halfW = mcScreen.width / 2.0 / scale;
        final double halfH = mcScreen.height / 2.0 / scale;
        final double minX = cameraX - halfW;
        final double minZ = cameraZ - halfH;
        final double maxX = cameraX + halfW;
        final double maxZ = cameraZ + halfH;

        final PlanarMapScene scene = ClientPlanarScenes.XAERO.get(data, data.tracks, data.routePalette, data.trackPatterns,
                cars, MTRMapConfig.INSTANCE.trackLinesEnabled.get(), MTRMapConfig.INSTANCE.routeLinesEnabled.get(),
                scale, minX, minZ, maxX, maxZ);
        drawScene(graphics, screenMatrix, scene, scale, cameraX, cameraZ, mcScreen.width, mcScreen.height);

        // Flush now so the path layer lands in the map, without being culled away
        // by later GUI batch usage.
        RenderSystem.disableCull();
        graphics.bufferSource().endBatch(RenderType.gui());
        RenderSystem.enableCull();

        // Landmarks are a map-only overlay. They are deliberately not Xaero
        // waypoints, so they never enter the waypoint list, compass or HUD.
        final MapLandmark hoveredLandmark = drawLandmarks(graphics, data.landmarks, scale, cameraX, cameraZ,
                mcScreen.width, mcScreen.height, mouseX, mouseY);
        graphics.flush();
        stack.popPose();

        renderToggleWidgets(graphics, mc.font, mouseX, mouseY);
        ClientTrainStream.drawStatus(graphics, dimensionKey);

        // Hover picking & tooltip (in screen space, drawn after the batch flush).
        if (mc.screen != null) {
            final double mouseWorldX = (mouseX - mcScreen.width / 2.0) / scale + cameraX;
            final double mouseWorldZ = (mouseY - mcScreen.height / 2.0) / scale + cameraZ;
            final MapCar hoveredCar = hoveredLandmark == null ? scene.pick(mouseWorldX, mouseWorldZ) : null;
            final List<Component> tooltip = hoveredCar != null ? TrainMapTooltip.lines(hoveredCar) : hoveredLandmark == null
                    ? (MTRMapConfig.INSTANCE.routeLinesEnabled.get()
                            ? pickTooltip(data, mouseWorldX, mouseWorldZ) : List.of())
                    : landmarkTooltip(hoveredLandmark);
            if (!tooltip.isEmpty()) {
                graphics.renderComponentTooltip(mc.font, tooltip, mouseX, mouseY);
            }
        }
    }

    private static MapLandmark drawLandmarks(GuiGraphics graphics, List<MapLandmark> landmarks, double scale,
            double cameraX, double cameraZ, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        MapLandmark hovered = null;
        double hoveredDistance = Double.MAX_VALUE;
        for (MapLandmark landmark : landmarks) {
            if (!shouldDrawLandmark(landmark, scale)) {
                continue;
            }
            final double screenX = screenWidth / 2.0 + (landmark.x() - cameraX) * scale;
            final double screenY = screenHeight / 2.0 + (landmark.z() - cameraZ) * scale;
            final int size = landmark.type() == MapLandmark.Type.STATION ? STATION_ICON_SIZE
                    : landmark.type() == MapLandmark.Type.DEPOT ? DEPOT_ICON_SIZE : PLATFORM_ICON_SIZE;
            if (screenX < -size || screenY < -size || screenX > screenWidth + size || screenY > screenHeight + size) {
                continue;
            }

            final com.mojang.blaze3d.vertex.PoseStack stack = graphics.pose();
            stack.pushPose();
            stack.translate(landmark.x(), landmark.z(), 0);
            // Cancel map zoom only for the glyph's size, not its world anchor.
            stack.scale((float) (1.0 / scale), (float) (1.0 / scale), 1);
            if (landmark.type() == MapLandmark.Type.PLATFORM) {
                final int half = size / 2;
                graphics.fill(-half - 1, -half - 1, half + 2, half + 2, 0xDD101010);
                graphics.fill(-half, -half, half + 1, half + 1, 0xFFE6D25A);
            } else {
                final ResourceLocation icon = landmark.type() == MapLandmark.Type.DEPOT ? DEPOT_ICON : STATION_ICON;
                // The atlas source is 32x32. Draw the entire texture, then
                // scale its on-map size; sampling size x size from a 16x16
                // declaration was clipping the station icon.
                stack.scale(size / 32.0f, size / 32.0f, 1);
                graphics.blit(icon, -16, -16, 0, 0, 32, 32, 32, 32);
            }
            stack.popPose();

            final double dx = mouseX - screenX;
            final double dy = mouseY - screenY;
            final double distance = dx * dx + dy * dy;
            final double radius = Math.max(4, size / 2.0 + 2);
            if (distance <= radius * radius && distance < hoveredDistance) {
                hovered = landmark;
                hoveredDistance = distance;
            }
        }
        return hovered;
    }

    private static boolean shouldDrawLandmark(MapLandmark landmark, double scale) {
        if (!landmark.hasRoutes() && !MTRMapConfig.INSTANCE.showEmptyStation.get()
                && landmark.type() != MapLandmark.Type.DEPOT) {
            return false;
        }
        return switch (landmark.type()) {
            case STATION -> MTRMapConfig.INSTANCE.showStationLandmarks.get();
            case PLATFORM -> scale >= PLATFORM_MIN_SCALE
                    && MTRMapConfig.INSTANCE.showPlatformLandmarks.get();
            case DEPOT -> MTRMapConfig.INSTANCE.showDepotLandmarks.get();
        };
    }

    private static List<Component> landmarkTooltip(MapLandmark landmark) {
        final List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(landmark.name()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        if (landmark.type() == MapLandmark.Type.PLATFORM) {
            tooltip.add(Component.literal("Platform " + landmark.symbol()).withStyle(ChatFormatting.GRAY));
        } else if (landmark.type() == MapLandmark.Type.DEPOT) {
            tooltip.add(Component.literal("Depot").withStyle(ChatFormatting.GRAY));
        }
        if (!landmark.description().isEmpty()) {
            tooltip.add(Component.literal("Routes:").withStyle(ChatFormatting.GRAY));
            for (String route : landmark.description().split(", ")) {
                tooltip.add(Component.literal("- " + route));
                if (tooltip.size() >= 8) {
                    break;
                }
            }
        }
        return tooltip;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Geometry drawing
    // -----------------------------------------------------------------------------------------------------------------

    private static void drawScene(GuiGraphics graphics, Matrix4f matrix, PlanarMapScene scene,
            double scale, double cameraX, double cameraZ, int width, int height) {
        final VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (PlanarMapScene.Quad quad : scene.quads()) {
            final int color = quad.color();
            final int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            // Subtract the camera in doubles before converting to screen floats.
            sceneVertex(consumer, matrix, quad.x1(), quad.z1(), scale, cameraX, cameraZ, width, height, r, g, b, quad.vertexAlpha(0));
            sceneVertex(consumer, matrix, quad.x2(), quad.z2(), scale, cameraX, cameraZ, width, height, r, g, b, quad.vertexAlpha(1));
            sceneVertex(consumer, matrix, quad.x3(), quad.z3(), scale, cameraX, cameraZ, width, height, r, g, b, quad.vertexAlpha(2));
            sceneVertex(consumer, matrix, quad.x4(), quad.z4(), scale, cameraX, cameraZ, width, height, r, g, b, quad.vertexAlpha(3));
        }
    }

    private static void sceneVertex(VertexConsumer consumer, Matrix4f matrix, double x, double z,
            double scale, double cameraX, double cameraZ, int width, int height, int r, int g, int b, int alpha) {
        consumer.vertex(matrix, (float) (width / 2.0 + (x - cameraX) * scale),
                (float) (height / 2.0 + (z - cameraZ) * scale), 0).color(r, g, b, alpha).endVertex();
    }

    private static void fillQuad(Matrix4f matrix, VertexConsumer consumer,
            double x1, double z1, double x2, double z2, int r, int g, int b, int a) {
        consumer.vertex(matrix, (float) x1, (float) z1, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x1, (float) z2, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x2, (float) z2, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x2, (float) z1, 0).color(r, g, b, a).endVertex();
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
            if (!track.intersects(mouseWorldX - SEGMENT_PICK_RADIUS, mouseWorldZ - SEGMENT_PICK_RADIUS,
                    mouseWorldX + SEGMENT_PICK_RADIUS, mouseWorldZ + SEGMENT_PICK_RADIUS)) {
                continue;
            }
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
                    .withStyle(ChatFormatting.BOLD).withStyle(style -> style.withColor(route.color())));
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
        drawToggle(graphics, font, mouseX, mouseY, 0,
                MTRMapConfig.INSTANCE.routeLinesEnabled.get(),
                Component.literal("Toggle MTR route lines"));
        drawToggle(graphics, font, mouseX, mouseY, 1,
                MTRMapConfig.INSTANCE.trackLinesEnabled.get(),
                Component.literal("Toggle MTR track layer"));
    }

    private static void drawToggle(GuiGraphics graphics, Font font, int mouseX, int mouseY, int index,
            boolean enabled, Component tooltip) {
        final int x = WIDGET_X;
        final int y = WIDGET_Y + index * (WIDGET_HEIGHT + WIDGET_SPACING);
        final int borderColor = isHovered(mouseX, mouseY, index) ? 0xFFFFFFFF : 0xFF000000;
        final ResourceLocation icon = index == 0
                ? (enabled ? ROUTE_ICON_ON : ROUTE_ICON_OFF)
                : (enabled ? TRACK_ICON_ON : TRACK_ICON_OFF);

        graphics.fill(x - 1, y - 1, x + WIDGET_WIDTH + 1, y + WIDGET_HEIGHT + 1, borderColor);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.blit(icon, 0, 0, 0, 0, 156, 45, 156, 45);
        graphics.pose().popPose();

        if (isHovered(mouseX, mouseY, index)) {
            graphics.renderComponentTooltip(font, List.of(tooltip), mouseX, mouseY + 12);
        }
    }

    private static ResourceLocation toggleIcon(String name) {
        return new ResourceLocation(MTRMap.MOD_ID, "textures/gui/" + name + ".png");
    }

    private static boolean isHovered(double mouseX, double mouseY, int index) {
        final int y = WIDGET_Y + index * (WIDGET_HEIGHT + WIDGET_SPACING);
        return mouseX >= WIDGET_X && mouseX <= WIDGET_X + WIDGET_WIDTH
                && mouseY >= y && mouseY <= y + WIDGET_HEIGHT;
    }
}
