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

/** Xaero's Fabric jar uses intermediary names for inherited Screen methods. */
@Mixin(value = GuiMap.class, remap = false)
public abstract class XaeroWorldMapMixin {
    @Unique
    private boolean mtrmap$failedToRender;

    @Inject(method = {"render", "method_25394"}, at = @At("TAIL"), remap = false, require = 0)
    private void mtrmap$onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (mtrmap$failedToRender) {
            return;
        }
        try {
            XaeroRouteRenderer.onRender(graphics, (GuiMap) (Object) this, mouseX, mouseY, partialTick);
        } catch (Throwable e) {
            MTRMap.LOGGER.error("[MTRMap] Xaero Fabric map overlay disabled for this session", e);
            mtrmap$failedToRender = true;
        }
    }

}
