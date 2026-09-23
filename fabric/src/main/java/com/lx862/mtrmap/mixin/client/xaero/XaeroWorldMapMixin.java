package com.lx862.mtrmap.mixin.client.xaero;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.integration.xaero.XaeroRouteRenderer;
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

    @Inject(method = {"mouseClicked", "method_25402"}, at = @At("HEAD"),
            remap = false, cancellable = true, require = 0)
    private void mtrmap$onClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (XaeroRouteRenderer.onMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
