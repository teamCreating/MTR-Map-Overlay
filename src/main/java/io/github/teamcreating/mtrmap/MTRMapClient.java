package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.integration.journeymap.JourneyMapIntegration;
import io.github.teamcreating.mtrmap.integration.xaero.LegacyXaeroWaypointCleanup;
import io.github.teamcreating.mtrmap.network.ClientNetworkSync;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;

/** Physical-client entry point. Never instantiated by a dedicated server. */

public final class MTRMapClient {

    public static void initialize() { new MTRMapClient(); }
    private MTRMapClient() {
        MinecraftForge.EVENT_BUS.register(this);
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
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
