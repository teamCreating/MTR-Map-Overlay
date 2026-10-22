package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.PlanarMapScene;
import com.lx862.mtrmap.train.ClientTrainStream;
import com.lx862.mtrmap.integration.TrainMapTooltip;
import com.lx862.mtrmap.mapdata.MapDataCache;
import com.mojang.blaze3d.systems.RenderSystem;
import journeymap.client.api.display.MarkerOverlay;
import journeymap.client.api.model.IFullscreen;
import journeymap.client.api.model.MapImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Draw station/platform symbols after JourneyMap 5 draws the map and before it draws toolbar controls. */
public final class JourneyMapForegroundRenderer {

    private static Object mapRenderer;
    private static Method exactPointPixel;
    private static Method windowPosition;
    private static Field scrolling;
    private static Method mouseDrag;
    private static boolean projectionWarningLogged;

    private JourneyMapForegroundRenderer() {
    }

    public static void render(GuiGraphics graphics, Object screen) {
        JourneyMapToolbar.refreshButtons();
        final IFullscreen fullscreen = (IFullscreen) screen;
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || fullscreen.getUiState() == null) {
            return;
        }
        try {
            prepareProjection();
            final double guiScale = minecraft.getWindow().getGuiScale();
            final Point2D.Double drag = Boolean.TRUE.equals(scrolling.get(screen))
                    ? (Point2D.Double) mouseDrag.invoke(screen) : new Point2D.Double();
            final JourneyMapScreenProjection projection = JourneyMapScreenProjection.fromSamples(
                    project(0, 0), project(1, 0), project(0, 1), guiScale).withDrag(drag.x, drag.y);
            final var viewedDimension = fullscreen.getUiState().dimension;
            final String dimensionId = viewedDimension.location().getNamespace() + "/" + viewedDimension.location().getPath();
            final var bounds = projection.visibleWorldBounds(graphics.guiWidth(), graphics.guiHeight(), 20);
            PlanarMapScene scene = null;
            graphics.flush();
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0, 0, 400);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                scene = JourneyMapPathManager.render(graphics, MapDataCache.get(dimensionId), projection,
                        MTRMapConfig.INSTANCE.trainCarsEnabled.get() && bounds != null && MTRMapConfig.INSTANCE.enabled.get()
                        ? ClientTrainStream.cars(dimensionId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), projection.pixelsPerBlock()) : java.util.List.of());
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
        final Field rendererField = fullscreenClass.getDeclaredField("gridRenderer");
        rendererField.setAccessible(true);
        scrolling = fullscreenClass.getDeclaredField("isScrolling");
        scrolling.setAccessible(true);
        mouseDrag = fullscreenClass.getDeclaredMethod("getMouseDrag");
        mouseDrag.setAccessible(true);
        mapRenderer = rendererField.get(null);
        exactPointPixel = mapRenderer.getClass().getMethod("getBlockPixelInGrid", double.class, double.class);
        windowPosition = mapRenderer.getClass().getMethod("getWindowPosition", Point2D.Double.class);
    }
}
