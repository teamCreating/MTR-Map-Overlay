package com.lx862.mtrmap.network;

import com.lx862.mtrmap.MTRMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fabric transport for the same version-5 snapshot payloads as NeoForge. */
public final class MTRNetwork {
    private MTRNetwork() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(RequestNetworkSync.TYPE, RequestNetworkSync.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(NetworkSyncProbe.TYPE, NetworkSyncProbe.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(NetworkSyncChunk.TYPE, NetworkSyncChunk.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(NetworkProbeResponse.TYPE, NetworkProbeResponse.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestNetworkSync.TYPE, (msg, context) ->
                context.server().execute(() -> ServerNetworkCollector.collectAndSend(
                        context.player(), msg.dimensionFilter())));
        ServerPlayNetworking.registerGlobalReceiver(NetworkSyncProbe.TYPE, (msg, context) ->
                context.server().execute(() -> ServerNetworkCollector.sendProbeResponse(context.player())));
        MTRMap.LOGGER.info("[MTRMap] Fabric full-network sync payloads registered (protocol 5)");
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkSyncChunk.TYPE, (msg, context) ->
                context.client().execute(() -> ClientNetworkSync.onChunkReceived(msg)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkProbeResponse.TYPE, (msg, context) ->
                context.client().execute(() -> ClientNetworkSync.onProbeReceived(msg.hashes())));
    }

    public static boolean canSendToServer() {
        return ClientPlayNetworking.canSend(RequestNetworkSync.TYPE)
                && ClientPlayNetworking.canSend(NetworkSyncProbe.TYPE);
    }

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
