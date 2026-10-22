package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.network.MTRNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MTRMap.MOD_ID)
public class MTRMap {

    public static final String MOD_ID = "mtrmap";
    public static final String MOD_NAME = "MTR Map Overlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    private static MinecraftServer serverInstance = null;

    public MTRMap() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        MTRMapConfig.register(modContainer);

        // Mod-bus events
        modEventBus.addListener(this::setup);
        MTRNetwork.register();
        net.minecraftforge.fml.DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> MTRMapClient::initialize);
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
