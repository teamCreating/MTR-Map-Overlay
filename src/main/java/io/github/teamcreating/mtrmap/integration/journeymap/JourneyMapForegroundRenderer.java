package io.github.teamcreating.mtrmap.integration.journeymap;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.mapdata.MapDataCache;
import io.github.teamcreating.mtrmap.train.ClientTrainStream;
import io.github.teamcreating.mtrmap.mapdata.PlanarMapScene;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.integration.TrainMapTooltip;
import com.mojang.blaze3d.systems.RenderSystem;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Draw station/platform symbols after JourneyMap flushes its map overlay buffers. */
final class JourneyMapForegroundRenderer {

    private static Object mapRenderer;
    private static Method exactPointPixel;
    private static Method windowPosition;
    private static boolean registered;
    private static boolean projectionWarningLogged;

    private JourneyMapForegroundRenderer() {
    }

    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MTRMap.MOD_ID,
                JourneyMapForegroundRenderer::render);
    }

    private static void render(FullscreenRenderEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || event.getFullscreen().getUiState() == null) {
            return;
        }
        try {
            prepareProjection();
            final GuiGraphics graphics = event.getGraphics();
            final double guiScale = minecraft.getWindow().getGuiScale();
            final double dragX = event.getFullscreen().getCenterBlockX(false)
                    - event.getFullscreen().getCenterBlockX(true);
            final double dragZ = event.getFullscreen().getCenterBlockZ(false)
                    - event.getFullscreen().getCenterBlockZ(true);
            final JourneyMapScreenProjection projection = JourneyMapScreenProjection.fromSamples(
                    project(0, 0), project(1, 0), project(0, 1), guiScale).withDrag(dragX, dragZ);
            final var viewedDimension = event.getFullscreen().getUiState().dimension;
            final String dimensionId = viewedDimension.location().getNamespace() + "/" + viewedDimension.location().getPath();
            final var bounds = projection.visibleWorldBounds(graphics.guiWidth(), graphics.guiHeight(), 20);
            graphics.flush();
            graphics.pose().pushPose();
            PlanarMapScene scene = null;
            try {
                graphics.pose().translate(0, 0, 400);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                scene = JourneyMapPathManager.render(graphics, MapDataCache.get(dimensionId), projection,
                        MTRMapConfig.INSTANCE.trainCarsEnabled.get()
                                && bounds != null && MTRMapConfig.INSTANCE.enabled.get()
                                ? ClientTrainStream.cars(dimensionId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(),
                                        projection.pixelsPerBlock()) : java.util.List.of());
                graphics.flush();
                ResourceLocation currentTexture = null;
                int currentColor = 0;
                float currentOpacity = Float.NaN;
                for (MarkerOverlay marker : JourneyMapLandmarkManager.displayedMarkers()) {
                    final MapImage icon = marker.getIcon();
                    if (!viewedDimension.equals(marker.getDimension()) || icon.getImageLocation() == null) {
                        continue;
                    }
                    final int width = (int) Math.round(icon.getDisplayWidth());
                    final int height = (int) Math.round(icon.getDisplayHeight());
                    final int x = (int) Math.round(projection.x(marker.getPoint().getX(), marker.getPoint().getZ())
                            - icon.getAnchorX());
                    final int y = (int) Math.round(projection.y(marker.getPoint().getX(), marker.getPoint().getZ())
                            - icon.getAnchorY());
                    if (x + width < 0 || y + height < 0 || x >= graphics.guiWidth() || y >= graphics.guiHeight()) {
                        continue;
                    }
                    final int color = icon.getColor();
                    final float opacity = icon.getOpacity();
                    final ResourceLocation texture = icon.getImageLocation();
                    if (!texture.equals(currentTexture) || color != currentColor || opacity != currentOpacity) {
                        // Buffered blits use the shader tint at flush time. Keep
                        // adjacent icons with the same state in one batch.
                        graphics.flush();
                        RenderSystem.setShaderColor(((color >> 16) & 255) / 255f,
                                ((color >> 8) & 255) / 255f, (color & 255) / 255f, opacity);
                        currentTexture = texture;
                        currentColor = color;
                        currentOpacity = opacity;
                    }
                    graphics.blit(icon.getImageLocation(), x, y, width, height,
                            (float) icon.getTextureX(), (float) icon.getTextureY(),
                            icon.getTextureWidth(), icon.getTextureHeight(),
                            icon.getTextureWidth(), icon.getTextureHeight());
                }
            } finally {
                graphics.flush();
                graphics.pose().popPose();
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            if (scene != null) {
                ClientTrainStream.drawStatus(graphics, dimensionId);
                final double mouseX = minecraft.mouseHandler.xpos() / guiScale;
                final double mouseY = minecraft.mouseHandler.ypos() / guiScale;
                final double determinant = projection.xStepX() * projection.zStepY()
                        - projection.zStepX() * projection.xStepY();
                if (Math.abs(determinant) > 1.0E-12) {
                    final double dx = mouseX - projection.originX(), dy = mouseY - projection.originY();
                    final double worldX = (dx * projection.zStepY() - dy * projection.zStepX()) / determinant;
                    final double worldZ = (dy * projection.xStepX() - dx * projection.xStepY()) / determinant;
                    final var car = scene.pick(worldX, worldZ);
                    if (car != null) graphics.renderComponentTooltip(minecraft.font, TrainMapTooltip.lines(car),
                            (int) mouseX, (int) mouseY);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!projectionWarningLogged) {
                projectionWarningLogged = true;
                MTRMap.LOGGER.warn("[MTRMap] JourneyMap foreground icons unavailable: {}", e.getMessage());
            }
        }
    }

    private static Point2D.Double project(double x, double z) throws ReflectiveOperationException {
        final Point2D.Double gridPixel = (Point2D.Double) exactPointPixel.invoke(mapRenderer, x, z);
        return (Point2D.Double) windowPosition.invoke(mapRenderer, gridPixel);
    }

    private static void prepareProjection() throws ReflectiveOperationException {
        if (mapRenderer != null) {
            return;
        }
        final Class<?> fullscreenClass = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");
        final Field rendererField = fullscreenClass.getDeclaredField("mapRenderer");
        rendererField.setAccessible(true);
        mapRenderer = rendererField.get(null);
        exactPointPixel = mapRenderer.getClass().getMethod("getBlockPixelInGridExact", double.class, double.class);
        windowPosition = mapRenderer.getClass().getMethod("getWindowPosition", Point2D.Double.class);
    }
}
