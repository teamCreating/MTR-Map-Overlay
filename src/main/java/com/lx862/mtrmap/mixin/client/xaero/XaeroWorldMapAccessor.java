package com.lx862.mtrmap.mixin.client.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;

/**
 * Accessor mixin for Xaero's World Map GuiMap to read camera position, scale
 * and the map processor (used to resolve the dimension currently being viewed).
 */
@Mixin(value = GuiMap.class, remap = false)
public interface XaeroWorldMapAccessor {

    @Accessor
    double getCameraX();

    @Accessor
    double getCameraZ();

    @Accessor("cameraX")
    void setCameraX(double value);

    @Accessor("cameraZ")
    void setCameraZ(double value);

    @Accessor
    double getScale();

    @Accessor("scale")
    void setScale(double value);

    @Accessor
    MapProcessor getMapProcessor();
}
