package io.github.teamcreating.mtrmap.network;
import io.github.teamcreating.mtrmap.MTRMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Optional Forge channel with direction-restricted handlers. */
public final class MTRNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MTRMap.MOD_ID, "network_v8"), () -> "8",
            version -> "8".equals(version) || NetworkRegistry.ABSENT.equals(version)
                    || NetworkRegistry.ACCEPTVANILLA.equals(version),
            version -> "8".equals(version) || NetworkRegistry.ABSENT.equals(version)
                    || NetworkRegistry.ACCEPTVANILLA.equals(version));
    public static final SimpleChannel TRAINS = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(MTRMap.MOD_ID, "trains_v1"), () -> "1",
      version -> "1".equals(version) || NetworkRegistry.ABSENT.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version),
      version -> "1".equals(version) || NetworkRegistry.ABSENT.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version));
    public static void register() {
        TRAINS.messageBuilder(TrainStreamRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
          .encoder((msg, buf) -> TrainStreamRequest.write(buf, msg)).decoder(TrainStreamRequest::read)
          .consumerMainThread((msg, ctx) -> { ServerPlayer sender = ctx.get().getSender(); if(sender != null) io.github.teamcreating.mtrmap.train.ServerTrainStream.request(sender, msg); }).add();
        TRAINS.messageBuilder(TrainStreamChunk.class, 1, NetworkDirection.PLAY_TO_CLIENT)
          .encoder((msg, buf) -> TrainStreamChunk.write(buf, msg)).decoder(TrainStreamChunk::read)
          .consumerMainThread((msg, ctx) -> MTRNetworkClient.onTrainChunk(msg)).add();
        TRAINS.messageBuilder(TrainStreamStatus.class, 2, NetworkDirection.PLAY_TO_CLIENT)
          .encoder((msg, buf) -> TrainStreamStatus.write(buf, msg)).decoder(TrainStreamStatus::read)
          .consumerMainThread((msg, ctx) -> MTRNetworkClient.onTrainStatus(msg)).add();

        CHANNEL.messageBuilder(RequestNetworkSync.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> RequestNetworkSync.write(buf, msg)).decoder(RequestNetworkSync::read)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) ServerNetworkCollector.collectAndSend(sender, msg.dimensionFilter());
                }).add();
        CHANNEL.messageBuilder(NetworkSyncProbe.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> NetworkSyncProbe.write(buf, msg)).decoder(NetworkSyncProbe::read)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) ServerNetworkCollector.sendProbeResponse(sender);
                }).add();
        CHANNEL.messageBuilder(NetworkSyncChunk.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> NetworkSyncChunk.write(buf, msg)).decoder(NetworkSyncChunk::read)
                .consumerMainThread((msg, ctx) -> MTRNetworkClient.onChunk(msg)).add();
        CHANNEL.messageBuilder(NetworkProbeResponse.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> NetworkProbeResponse.write(buf, msg)).decoder(NetworkProbeResponse::read)
                .consumerMainThread((msg, ctx) -> MTRNetworkClient.onProbe(msg)).add();
    }
    public static boolean canSendTrains(ServerPlayer player) { return TRAINS.isRemotePresent(player.connection.connection); }
    public static void sendToPlayer(ServerPlayer player, MapPacket packet) {
        if (packet instanceof TrainStreamChunk || packet instanceof TrainStreamStatus) {
            if (canSendTrains(player)) { TRAINS.send(PacketDistributor.PLAYER.with(() -> player), packet); }
            return;
        }
        if (CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
