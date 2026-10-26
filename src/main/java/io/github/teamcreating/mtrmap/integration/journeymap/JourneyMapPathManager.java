package io.github.teamcreating.mtrmap.integration.journeymap;

import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.mapdata.PlanarMapScene;
import io.github.teamcreating.mtrmap.mapdata.ClientPlanarScenes;
import io.github.teamcreating.mtrmap.mapdata.MapCar;
import io.github.teamcreating.mtrmap.mapdata.MapDataCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

/** Pure 2D physical rails, route lanes and actual nearby car footprints. */
final class JourneyMapPathManager {
    private JourneyMapPathManager() {
    }

    static PlanarMapScene render(GuiGraphics graphics, MapDataCache.DimensionData data,
            JourneyMapScreenProjection projection, List<MapCar> cars) {
        data = io.github.teamcreating.mtrmap.extension.ViewRenderer.data(data);
        final var config = MTRMapConfig.INSTANCE;
        final var view = projection.visibleWorldBounds(graphics.guiWidth(), graphics.guiHeight(), 16);
        if (view == null || !config.enabled.get()) return null;
        final PlanarMapScene scene = ClientPlanarScenes.JOURNEYMAP.get(data, data.tracks, data.routePalette, data.trackPatterns,
                cars, config.trackLinesEnabled.get(), config.routeLinesEnabled.get(), projection.pixelsPerBlock(),
                view.minX(), view.minZ(), view.maxX(), view.maxZ());
        final var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        final var matrix = graphics.pose().last().pose();
        for (PlanarMapScene.Quad quad : scene.quads()) {
            final int color = quad.color();
            final int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            consumer.vertex(matrix, (float) projection.x(quad.x1(), quad.z1()),
                    (float) projection.y(quad.x1(), quad.z1()), 0).color(r, g, b, quad.vertexAlpha(0)).endVertex();
            consumer.vertex(matrix, (float) projection.x(quad.x2(), quad.z2()),
                    (float) projection.y(quad.x2(), quad.z2()), 0).color(r, g, b, quad.vertexAlpha(1)).endVertex();
            consumer.vertex(matrix, (float) projection.x(quad.x3(), quad.z3()),
                    (float) projection.y(quad.x3(), quad.z3()), 0).color(r, g, b, quad.vertexAlpha(2)).endVertex();
            consumer.vertex(matrix, (float) projection.x(quad.x4(), quad.z4()),
                    (float) projection.y(quad.x4(), quad.z4()), 0).color(r, g, b, quad.vertexAlpha(3)).endVertex();
        }
        RenderSystem.disableCull();
        graphics.bufferSource().endBatch(RenderType.gui());
        RenderSystem.enableCull();
        return scene;
    }
}
