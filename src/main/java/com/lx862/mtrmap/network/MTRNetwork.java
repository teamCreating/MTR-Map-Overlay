package com.lx862.mtrmap.network;

import com.lx862.mtrmap.MTRMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.Minecraft;
import com.lx862.mtrmap.mixin.client.ClientCommonListenerAccessor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * NeoForge payload registration for the full-network map sync (Plan C).
 *
 * <p>When this mod is installed on the server, clients can request a snapshot
 * of the whole MTR network (routes + sampled track geometry) per dimension -
 * the same data model Create's train map uses. The snapshot is transferred in
 * chunks so arbitrarily large networks stay within packet size limits.</p>
 *
 * <p>Both payloads are registered as {@code optional()}, so connecting to a
 * NeoForge server without this mod never disconnects the client; presence is
 * probed empirically by {@link ClientNetworkSync}.</p>
 */
public class MTRNetwork {

    private static final String PROTOCOL_VERSION = "5";

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(RequestNetworkSync.TYPE, RequestNetworkSync.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) {
                ctx.enqueueWork(() -> ServerNetworkCollector.collectAndSend(sender, msg.dimensionFilter()));
            }
        });
        registrar.playToServer(NetworkSyncProbe.TYPE, NetworkSyncProbe.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) {
                ctx.enqueueWork(() -> ServerNetworkCollector.sendProbeResponse(sender));
            }
        });
        registrar.playToClient(NetworkSyncChunk.TYPE, NetworkSyncChunk.STREAM_CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> ClientNetworkSync.onChunkReceived(msg)));
        registrar.playToClient(NetworkProbeResponse.TYPE, NetworkProbeResponse.STREAM_CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> ClientNetworkSync.onProbeReceived(msg.hashes())));
        MTRMap.LOGGER.info("[MTRMap] Full-network sync payloads registered (protocol {})", PROTOCOL_VERSION);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MTRMap.MOD_ID, path);
    }

    public static boolean canSendToServer() {
        return Minecraft.getInstance().getConnection() instanceof ClientCommonListenerAccessor accessor
                && accessor.mtrmap$getConnectionType() == ConnectionType.NEOFORGE;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
