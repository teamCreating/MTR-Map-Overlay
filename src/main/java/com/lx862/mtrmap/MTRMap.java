package com.lx862.mtrmap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrmap.integration.xaero.LegacyXaeroWaypointCleanup;
import com.lx862.mtrmap.network.MTRNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MTRMap.MOD_ID)
public class MTRMap {

    public static final String MOD_ID = "mtrmap";
    public static final String MOD_NAME = "MTR Map Overlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    private static MinecraftServer serverInstance = null;

    public MTRMap(IEventBus modEventBus, ModContainer modContainer) {
        MTRMapConfig.register(modContainer);

        // Mod-bus events
        modEventBus.addListener(this::setup);
        modEventBus.addListener(MTRNetwork::register);

        // Game-bus events
        NeoForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        if (MTRMapConfig.INSTANCE.formalInitLog.get()) {
            LOGGER.info("[{}] Mod loaded!", MOD_NAME);
        } else {
            LOGGER.info("[{}] You get a landmark, you get a landmark, every-nyan gets a landmark! >w<", MOD_NAME);
        }

        LOGGER.info("[{}] JourneyMap {} - landmark integration {}",
                MOD_NAME,
                JourneyMapIntegration.isJourneyMapLoaded() ? "detected" : "not found",
                JourneyMapIntegration.isJourneyMapLoaded() ? "enabled" : "disabled");
        if (ModList.get().isLoaded("xaeroworldmap")) {
            LOGGER.info("[{}] Xaero's World Map detected - map-only landmark icons enabled", MOD_NAME);
        }
    }

    /**
     * Register client-side commands. This fires on the client and works
     * even when connected to a remote server that doesn't have this mod.
     */
    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandRegistration.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (ModList.get().isLoaded("xaerominimap")) {
            LegacyXaeroWaypointCleanup.onClientTick();
        }
        // JourneyMap landmark sync (self-gates on JourneyMap presence)
        JourneyMapIntegration.onClientTick();
    }

    public static MinecraftServer getServerInstance() {
        return serverInstance;
    }
}
