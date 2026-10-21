package com.lx862.mtrmap.train;

import com.lx862.mtrmap.MTRMap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MTRMap.MOD_ID)
public final class TrainServerEvents {
    private TrainServerEvents() { }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { ServerTrainStream.tick(event.getServer()); }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { ServerTrainStream.stop(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ServerTrainStream.disconnect(event.getEntity().getUUID()); }
}
