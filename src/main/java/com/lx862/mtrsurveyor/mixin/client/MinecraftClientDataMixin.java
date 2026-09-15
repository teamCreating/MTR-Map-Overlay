package com.lx862.mtrsurveyor.mixin.client;

import com.lx862.mtrsurveyor.MTRSurveyor;
import com.lx862.mtrsurveyor.config.MTRSurveyorConfig;
import com.lx862.mtrsurveyor.integration.XaeroIntegration;
import com.lx862.mtrsurveyor.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrsurveyor.mapdata.MapDataCache;
import org.mtr.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClientData.class, remap = false)
public class MinecraftClientDataMixin {
    @Inject(method = "sync", at = @At("TAIL"))
    public void onSync(CallbackInfo ci) {
        if (MTRSurveyorConfig.INSTANCE.enabled.get()) {
            MTRSurveyor.LOGGER.debug("[MTRSurveyor] MTR client data synced, requesting waypoint sync");
            XaeroIntegration.requestSync();
        }
        // Refresh JourneyMap landmarks on MTR data changes (self-gating)
        JourneyMapIntegration.requestSync();
        // Invalidate the path-layer cache built from MTR client data
        MapDataCache.onClientDataSynced();
    }
}
