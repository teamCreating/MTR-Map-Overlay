package com.lx862.mtrmap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.network.MTRNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MTRMap implements ModInitializer {
    public static final String MOD_ID = "mtrmap";
    public static final String MOD_NAME = "MTR Map Overlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    @Override
    public void onInitialize() {
        MTRMapConfig.load();
        MTRNetwork.registerServer();
        LOGGER.info("[{}] Fabric loaded; JourneyMap {}, Xaero World Map {}", MOD_NAME,
                isModLoaded("journeymap") ? "detected" : "not found",
                isModLoaded("xaeroworldmap") ? "detected" : "not found");
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
