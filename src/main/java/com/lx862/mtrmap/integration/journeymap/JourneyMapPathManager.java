package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.HeightMapScene;
import com.lx862.mtrmap.mapdata.MapCar;
import com.lx862.mtrmap.mapdata.MapDataCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

/** Height-ordered physical rails, route lanes and actual nearby car poses. */
final class JourneyMapPathManager {
    private JourneyMapPathManager() {
    }

    static HeightMapScene render(GuiGraphics graphics, MapDataCache.DimensionData data,
            JourneyMapScreenProjection projection, List<MapCar> cars) {
        final var config = MTRMapConfig.INSTANCE;
        final var view = projection.visibleWorldBounds(graphics.guiWidth(), graphics.guiHeight(), 16);
        if (view == null || !config.enabled.get()) return null;
        final HeightMapScene scene = HeightMapScene.build(data.tracks, data.routePalette, data.trackPatterns,
                cars, config.trackLinesEnabled.get(), config.routeLinesEnabled.get(), projection.pixelsPerBlock(),
                view.minX(), view.minZ(), view.maxX(), view.maxZ());
        final var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        final var matrix = graphics.pose().last().pose();
        for (HeightMapScene.Quad quad : scene.quads()) {
            final int color = quad.color();
            final int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            for (var point : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                consumer.addVertex(matrix, (float) projection.x(point.x(), point.z()),
                        (float) projection.y(point.x(), point.z()), 0).setColor(r, g, b, quad.alpha());
            }
        }
        RenderSystem.disableCull();
        graphics.bufferSource().endBatch(RenderType.gui());
        RenderSystem.enableCull();
        return scene;
    }
}
