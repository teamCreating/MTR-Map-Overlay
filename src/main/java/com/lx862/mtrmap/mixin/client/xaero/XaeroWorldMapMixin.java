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

/**
 * Mixin into Xaero's World Map render method to draw the MTR path layer
 * (route lines & track geometry) on the map.
 * Modelled after Create mod's XaeroFullscreenMapMixin.
 *
 * <p>On NeoForge the runtime uses Mojang mappings, so both GuiMap.render and
 * GuiGraphics.blit are referenced by their mapped names.</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class XaeroWorldMapMixin {

    @Unique
    private boolean mtrmap$failedToRender = false;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), remap = false, require = 0)
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

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), remap = false, cancellable = true, require = 0)
    public void mtrmap$onMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (XaeroRouteRenderer.onMouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to handle mouse click on Xaero World Map:", e);
        }
    }
}
