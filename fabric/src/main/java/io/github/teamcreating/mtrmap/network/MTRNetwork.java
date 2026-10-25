package io.github.teamcreating.mtrmap.network;

import io.github.teamcreating.mtrmap.MTRMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fabric transport for the same version-8 snapshot payloads as NeoForge. */
public final class MTRNetwork {
    private MTRNetwork() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(OverlayRequest.TYPE, OverlayRequest.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OverlayChunk.TYPE, OverlayChunk.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OverlayRequest.TYPE, (msg, context) ->
                context.server().execute(() -> io.github.teamcreating.mtrmap.extension.ServerOverlayService.request(context.player(), msg)));
        PayloadTypeRegistry.playC2S().register(RequestNetworkSync.TYPE, RequestNetworkSync.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(NetworkSyncProbe.TYPE, NetworkSyncProbe.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(NetworkSyncChunk.TYPE, NetworkSyncChunk.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(NetworkProbeResponse.TYPE, NetworkProbeResponse.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TrainStreamRequest.TYPE, TrainStreamRequest.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TrainStreamChunk.TYPE, TrainStreamChunk.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TrainStreamStatus.TYPE, TrainStreamStatus.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TrainStreamRequest.TYPE, (msg, context) ->
                context.server().execute(() -> io.github.teamcreating.mtrmap.train.ServerTrainStream.request(context.player(), msg)));

        ServerPlayNetworking.registerGlobalReceiver(RequestNetworkSync.TYPE, (msg, context) ->
                context.server().execute(() -> ServerNetworkCollector.collectAndSend(
                        context.player(), msg.dimensionFilter())));
        ServerPlayNetworking.registerGlobalReceiver(NetworkSyncProbe.TYPE, (msg, context) ->
                context.server().execute(() -> ServerNetworkCollector.sendProbeResponse(context.player())));
        MTRMap.LOGGER.info("[MTRMap] Fabric full-network sync payloads registered (protocol 8)");
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(OverlayChunk.TYPE, (msg, context) ->
                context.client().execute(() -> io.github.teamcreating.mtrmap.extension.ClientOverlayRuntime.INSTANCE.chunk(msg)));
        ClientPlayNetworking.registerGlobalReceiver(TrainStreamChunk.TYPE, (msg, context) ->
                context.client().execute(() -> io.github.teamcreating.mtrmap.train.ClientTrainStream.chunk(msg)));
        ClientPlayNetworking.registerGlobalReceiver(TrainStreamStatus.TYPE, (msg, context) ->
                context.client().execute(() -> io.github.teamcreating.mtrmap.train.ClientTrainStream.status(msg)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkSyncChunk.TYPE, (msg, context) ->
                context.client().execute(() -> ClientNetworkSync.onChunkReceived(msg)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkProbeResponse.TYPE, (msg, context) ->
                context.client().execute(() -> ClientNetworkSync.onProbeReceived(msg.hashes())));
    }

    public static boolean canSendToServer() {
        return ClientPlayNetworking.canSend(RequestNetworkSync.TYPE)
                && ClientPlayNetworking.canSend(NetworkSyncProbe.TYPE);
    }

    public static boolean canSendTrains(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, TrainStreamChunk.TYPE) && ServerPlayNetworking.canSend(player, TrainStreamStatus.TYPE);
    }

    public static boolean canSendOverlay(ServerPlayer player) { return ServerPlayNetworking.canSend(player, OverlayChunk.TYPE); }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MTRMap.MOD_ID, path);
    }
}
