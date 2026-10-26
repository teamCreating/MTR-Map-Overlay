package io.github.teamcreating.mtrmap.integration.journeymap;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.mapdata.PlanarMapScene;
import io.github.teamcreating.mtrmap.train.ClientTrainStream;
import io.github.teamcreating.mtrmap.integration.TrainMapTooltip;
import io.github.teamcreating.mtrmap.mapdata.MapDataCache;
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
                        MTRMapConfig.INSTANCE.trainCarsEnabled.get() && io.github.teamcreating.mtrmap.extension.ViewRenderer.trains(dimensionId) && bounds != null && MTRMapConfig.INSTANCE.enabled.get()
                        ? ClientTrainStream.cars(dimensionId, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), projection.pixelsPerBlock()) : java.util.List.of());
                graphics.flush();
            } finally {
                graphics.flush();
                graphics.pose().popPose();
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            final double uiMouseX=minecraft.mouseHandler.xpos()/guiScale, uiMouseY=minecraft.mouseHandler.ypos()/guiScale;
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0,0,600);
                io.github.teamcreating.mtrmap.extension.OverlayUi.render(graphics,dimensionId,
                    io.github.teamcreating.mtrmap.extension.ViewRenderer.data(MapDataCache.get(dimensionId)),projection.pixelsPerBlock(),
                    projection::x,projection::y,(int)uiMouseX,(int)uiMouseY,true);
                graphics.flush();
            } finally { graphics.pose().popPose(); }
            if (scene != null && !io.github.teamcreating.mtrmap.extension.OverlayUi.blocks(uiMouseX,uiMouseY) && !io.github.teamcreating.mtrmap.extension.OverlayUi.hasPoint(uiMouseX,uiMouseY)) {
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
