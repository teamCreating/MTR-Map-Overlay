package io.github.teamcreating.mtrmap.mixin.client.xaero;

import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A live train map needs the integrated server to keep advancing its authoritative simulation. */
@Mixin(Minecraft.class)
public abstract class TrainMapPauseMixin {
    @Redirect(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;isPauseScreen()Z"))
    private boolean mtrmap$keepTrainMapLive(Screen screen) {
        var config = MTRMapConfig.INSTANCE;
        return screen.isPauseScreen() && !(screen.getClass().getName().equals("xaero.map.gui.GuiMap") && config.enabled.get()
                && (config.trainCarsEnabled.get() && config.networkSyncEnabled.get() || io.github.teamcreating.mtrmap.extension.OverlayUi.live()));
    }
}
