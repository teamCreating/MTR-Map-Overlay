package io.github.teamcreating.mtrmap.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 49 finishes mod loading after vanilla can synchronously open a
 * quick-play world. Move its existing completion guard before that boundary
 * so server lifecycle subscribers run during integrated-server startup.
 * Compiled and registered only for Forge 1.20.4.
 */
@Mixin(Minecraft.class)
public abstract class ForgeInitialLoadMixin {
    // Vanilla has now set gameLoadFinished, which its menu renderer needs.
    // Guard before building initial screens or synchronously connecting a world.
    @Inject(method = "onGameLoadFinished", at = @At("HEAD"),
            require = 1, allow = 1, cancellable = true)
    private void mtrmap$completeForgeBeforeInitialScreens(CallbackInfo callback) {
        if (ClientModLoader.completeModLoading()) {
            // Keep Forge's warning/error screen and do not enter a world.
            callback.cancel();
        }
    }

    // Forge adds this callback with the same name in named and SRG artifacts.
    @Redirect(method = "lambda$new$6", remap = false, require = 1, allow = 1,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraftforge/client/loading/ClientModLoader;completeModLoading()Z"))
    private boolean mtrmap$removeLateCompletion() {
        // The real guard already handled success or its diagnostic screen at
        // the vanilla boundary. Do not invoke completion twice at this late site.
        return false;
    }
}
