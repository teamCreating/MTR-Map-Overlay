package com.lx862.mtrmap.mixin.client;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrmap.mapdata.MapDataCache;
import org.mtr.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClientData.class, remap = false)
public class MinecraftClientDataMixin {
    @Inject(method = "sync", at = @At("TAIL"))
    public void onSync(CallbackInfo ci) {
        MTRMap.LOGGER.debug("[MTRMap] MTR client data synced, refreshing map overlays");
        // Refresh JourneyMap landmarks on MTR data changes (self-gating)
        JourneyMapIntegration.requestSync();
        // Invalidate the path-layer cache built from MTR client data
        MapDataCache.onClientDataSynced();
    }
}
