package io.github.teamcreating.mtrmap.mixin.client.journeymap;

import io.github.teamcreating.mtrmap.integration.journeymap.JourneyMapForegroundRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JourneyMap 5 has no fullscreen render event. drawMap is unmapped on both loaders. */
@Pseudo
@Mixin(targets = "journeymap.client.ui.fullscreen.Fullscreen", remap = false)
public abstract class JourneyMapFullscreenMixin {
    @Inject(method = "drawMap", at = @At("TAIL"), remap = false)
    private void mtrmap$foreground(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        JourneyMapForegroundRenderer.render(graphics, this);
    }
}
