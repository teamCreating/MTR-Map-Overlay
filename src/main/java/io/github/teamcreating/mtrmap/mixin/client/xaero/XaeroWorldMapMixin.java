package io.github.teamcreating.mtrmap.mixin.client.xaero;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.integration.xaero.XaeroRouteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * Mixin into Xaero's World Map render method to draw the MTR path layer
 * (route lines & track geometry) on the map.
 * Modelled after Create mod's XaeroFullscreenMapMixin.
 *
 * <p>Forge 1.20.1 uses SRG names for inherited Screen methods at runtime.
 * Both dev and production names are accepted; custom Xaero fields are not remapped.</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class XaeroWorldMapMixin {

    @Unique
    private boolean mtrmap$failedToRender = false;

    @Inject(method = {"render", "m_88315_"}, at = @At("TAIL"), remap = false, require = 0)
    public void mtrmap$onRenderTail(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        try {
            if (!this.mtrmap$failedToRender) {
                XaeroRouteRenderer.onRender(graphics, (GuiMap) (Object) this, mouseX, mouseY, partialTick);
            }
        } catch (Throwable e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to render the MTR path layer on Xaero's World Map - "
                    + "the layer is disabled for this session (possibly an incompatible Xaero's World Map version):", e);
            this.mtrmap$failedToRender = true;
        }
    }

}
