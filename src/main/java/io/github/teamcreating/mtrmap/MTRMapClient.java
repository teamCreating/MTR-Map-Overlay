package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.integration.journeymap.JourneyMapIntegration;
import io.github.teamcreating.mtrmap.integration.xaero.LegacyXaeroWaypointCleanup;
import io.github.teamcreating.mtrmap.network.ClientNetworkSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;

/** Physical-client entry point. Never instantiated by a dedicated server. */
@Mod(value = MTRMap.MOD_ID, dist = Dist.CLIENT)
public final class MTRMapClient {

    public MTRMapClient(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
        MTRMap.LOGGER.info("[{}] JourneyMap {} - landmark integration {}",
                MTRMap.MOD_NAME,
                JourneyMapIntegration.isJourneyMapLoaded() ? "detected" : "not found",
                JourneyMapIntegration.isJourneyMapLoaded() ? "enabled" : "disabled");
        if (ModList.get().isLoaded("xaeroworldmap")) {
            MTRMap.LOGGER.info("[{}] Xaero's World Map detected - map-only landmark icons enabled",
                    MTRMap.MOD_NAME);
        }
    }

    /** Client commands also work when the remote server lacks this mod. */
    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandRegistration.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        ClientNetworkSync.onClientTick();
        if (ModList.get().isLoaded("xaerominimap")) {
            LegacyXaeroWaypointCleanup.onClientTick();
        }
        JourneyMapIntegration.onClientTick();
    }

    @SubscribeEvent
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientNetworkSync.onLoggingIn();
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNetworkSync.onLoggingOut();
    }
}
