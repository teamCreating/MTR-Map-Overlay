package com.lx862.mtrmap.train;
import com.lx862.mtrmap.MTRMap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid = MTRMap.MOD_ID)
public final class TrainServerEvents {
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) { if(event.phase == TickEvent.Phase.END) ServerTrainStream.tick(event.getServer()); }
 @SubscribeEvent public static void stop(ServerStoppingEvent event) { ServerTrainStream.stop(); }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ServerTrainStream.disconnect(event.getEntity().getUUID()); }
}
