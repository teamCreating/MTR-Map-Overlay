package io.github.teamcreating.mtrmap.network;
import io.github.teamcreating.mtrmap.MTRMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 1.20 transport for the shared v8 messages. */
public final class MTRNetwork {
    private static final ResourceLocation OVERLAY_REQUEST=id("overlay_request_v1"), OVERLAY_CHUNK=id("overlay_response_v1");
    public static boolean canSendOverlay(ServerPlayer p) { return ServerPlayNetworking.canSend(p,OVERLAY_CHUNK); }
    public static boolean canSendOverlayToServer() { return ClientPlayNetworking.canSend(OVERLAY_REQUEST); }
    private static final ResourceLocation REQUEST = id("request_network_sync_v8");
    private static final ResourceLocation PROBE = id("network_sync_probe_v8");
    private static final ResourceLocation CHUNK = id("network_sync_chunk_v8");
    private static final ResourceLocation RESPONSE = id("network_probe_response_v8");
    private static final ResourceLocation TRAIN_REQUEST = id("train_request_v1");
    private static final ResourceLocation TRAIN_CHUNK = id("train_chunk_v1");
    private static final ResourceLocation TRAIN_STATUS = id("train_status_v1");
    public static boolean canSendTrains(ServerPlayer player) { return ServerPlayNetworking.canSend(player, TRAIN_CHUNK) && ServerPlayNetworking.canSend(player, TRAIN_STATUS); }
    public static boolean canSendTrainsToServer() { return ClientPlayNetworking.canSend(TRAIN_REQUEST); }
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(OVERLAY_REQUEST,(server,player,handler,buf,sender)->{ var message=OverlayRequest.read(buf);server.execute(()->io.github.teamcreating.mtrmap.extension.ServerOverlayService.request(player,message)); });
        ServerPlayNetworking.registerGlobalReceiver(TRAIN_REQUEST, (server, player, handler, buf, sender) -> {
            TrainStreamRequest msg = TrainStreamRequest.read(buf);
            server.execute(() -> io.github.teamcreating.mtrmap.train.ServerTrainStream.request(player, msg));
        });
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) -> {
            RequestNetworkSync msg = RequestNetworkSync.read(buf);
            server.execute(() -> ServerNetworkCollector.collectAndSend(player, msg.dimensionFilter()));
        });
        ServerPlayNetworking.registerGlobalReceiver(PROBE, (server, player, handler, buf, sender) ->
                server.execute(() -> ServerNetworkCollector.sendProbeResponse(player)));
    }
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(OVERLAY_CHUNK,(client,handler,buf,sender)->{ var message=OverlayChunk.read(buf);client.execute(()->io.github.teamcreating.mtrmap.extension.ClientOverlayRuntime.INSTANCE.chunk(message)); });
        ClientPlayNetworking.registerGlobalReceiver(TRAIN_CHUNK, (client, handler, buf, sender) -> {
            TrainStreamChunk msg = TrainStreamChunk.read(buf);
            client.execute(() -> io.github.teamcreating.mtrmap.train.ClientTrainStream.chunk(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(TRAIN_STATUS, (client, handler, buf, sender) -> {
            TrainStreamStatus msg = TrainStreamStatus.read(buf);
            client.execute(() -> io.github.teamcreating.mtrmap.train.ClientTrainStream.status(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(CHUNK, (client, handler, buf, sender) -> {
            NetworkSyncChunk msg = NetworkSyncChunk.read(buf);
            client.execute(() -> ClientNetworkSync.onChunkReceived(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(RESPONSE, (client, handler, buf, sender) -> {
            NetworkProbeResponse msg = NetworkProbeResponse.read(buf);
            client.execute(() -> ClientNetworkSync.onProbeReceived(msg.hashes()));
        });
    }
    public static boolean canSendToServer() {
        return ClientPlayNetworking.canSend(REQUEST) && ClientPlayNetworking.canSend(PROBE);
    }
    public static void sendToServer(MapPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        if(packet instanceof OverlayRequest msg) { OverlayRequest.write(buf,msg);ClientPlayNetworking.send(OVERLAY_REQUEST,buf); } else if (packet instanceof TrainStreamRequest msg) {
            TrainStreamRequest.write(buf, msg); ClientPlayNetworking.send(TRAIN_REQUEST, buf);
        } else if (packet instanceof RequestNetworkSync msg) {
            RequestNetworkSync.write(buf, msg); ClientPlayNetworking.send(REQUEST, buf);
        } else if (packet instanceof NetworkSyncProbe) {
            ClientPlayNetworking.send(PROBE, buf);
        } else { throw new IllegalArgumentException("Unexpected serverbound packet"); }
    }
    public static void sendToPlayer(ServerPlayer player, MapPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ResourceLocation channel;
        if(packet instanceof OverlayChunk msg) { OverlayChunk.write(buf,msg);channel=OVERLAY_CHUNK; } else if (packet instanceof TrainStreamChunk msg) {
            TrainStreamChunk.write(buf, msg); channel = TRAIN_CHUNK;
        } else if (packet instanceof TrainStreamStatus msg) {
            TrainStreamStatus.write(buf, msg); channel = TRAIN_STATUS;
        } else if (packet instanceof NetworkSyncChunk msg) {
            NetworkSyncChunk.write(buf, msg); channel = CHUNK;
        } else if (packet instanceof NetworkProbeResponse msg) {
            NetworkProbeResponse.write(buf, msg); channel = RESPONSE;
        } else { throw new IllegalArgumentException("Unexpected clientbound packet"); }
        if (ServerPlayNetworking.canSend(player, channel)) ServerPlayNetworking.send(player, channel, buf);
        else buf.release();
    }
    private static ResourceLocation id(String path) { return new ResourceLocation(MTRMap.MOD_ID, path); }
}
