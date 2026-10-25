package io.github.teamcreating.mtrmap.mixin.client;
import io.github.teamcreating.mtrmap.extension.OverlayUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class OverlayPointerMixin {
    @Inject(method = "onMove", at = @At("HEAD"))
    private void mtrmap$pointer(long window, double x, double y, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null) OverlayUi.pointer(x * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(), y * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
    }
}
