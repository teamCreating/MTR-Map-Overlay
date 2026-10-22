package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.network.MTRNetwork;
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
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(io.github.teamcreating.mtrmap.train.ServerTrainStream::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> io.github.teamcreating.mtrmap.train.ServerTrainStream.stop());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                io.github.teamcreating.mtrmap.train.ServerTrainStream.disconnect(handler.player.getUUID()));
        LOGGER.info("[{}] Fabric loaded; JourneyMap {}, Xaero World Map {}", MOD_NAME,
                isModLoaded("journeymap") ? "detected" : "not found",
                isModLoaded("xaeroworldmap") ? "detected" : "not found");
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
