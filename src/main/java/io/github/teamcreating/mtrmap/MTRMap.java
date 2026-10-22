package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.network.MTRNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
    }

    private void setup(final FMLCommonSetupEvent event) {
        if (MTRMapConfig.INSTANCE.formalInitLog.get()) {
            LOGGER.info("[{}] Mod loaded!", MOD_NAME);
        } else {
            LOGGER.info("[{}] You get a landmark, you get a landmark, every-nyan gets a landmark! >w<", MOD_NAME);
        }
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static MinecraftServer getServerInstance() {
        return serverInstance;
    }
}
