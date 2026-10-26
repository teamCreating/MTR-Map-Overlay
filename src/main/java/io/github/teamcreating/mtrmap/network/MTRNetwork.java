package io.github.teamcreating.mtrmap.network;

import io.github.teamcreating.mtrmap.MTRMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;

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

    private static final String PROTOCOL_VERSION = "8";

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
        // The payload types exist on both sides, but a dedicated server must
        // never resolve their physical-client handlers.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(NetworkSyncChunk.TYPE, NetworkSyncChunk.STREAM_CODEC,
                    MTRNetworkClient::onChunk);
            registrar.playToClient(NetworkProbeResponse.TYPE, NetworkProbeResponse.STREAM_CODEC,
                    MTRNetworkClient::onProbe);
        } else {
            registrar.playToClient(NetworkSyncChunk.TYPE, NetworkSyncChunk.STREAM_CODEC,
                    (msg, ctx) -> {});
            registrar.playToClient(NetworkProbeResponse.TYPE, NetworkProbeResponse.STREAM_CODEC,
                    (msg, ctx) -> {});
        }
        final PayloadRegistrar overlay = event.registrar("overlay-1").optional();
        overlay.playToServer(OverlayRequest.TYPE, OverlayRequest.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender)
                ctx.enqueueWork(() -> io.github.teamcreating.mtrmap.extension.ServerOverlayService.request(sender, msg));
        });
        if (FMLEnvironment.dist == Dist.CLIENT) overlay.playToClient(OverlayChunk.TYPE, OverlayChunk.STREAM_CODEC, MTRNetworkClient::onOverlayChunk);
        else overlay.playToClient(OverlayChunk.TYPE, OverlayChunk.STREAM_CODEC, (msg, ctx) -> {});
        MTRMap.LOGGER.info("[MTRMap] Full-network sync payloads registered (protocol {})", PROTOCOL_VERSION);
        final PayloadRegistrar trains = event.registrar("train-1").optional();
        trains.playToServer(TrainStreamRequest.TYPE, TrainStreamRequest.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender)
                ctx.enqueueWork(() -> io.github.teamcreating.mtrmap.train.ServerTrainStream.request(sender, msg));
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            trains.playToClient(TrainStreamChunk.TYPE, TrainStreamChunk.STREAM_CODEC, MTRNetworkClient::onTrainChunk);
            trains.playToClient(TrainStreamStatus.TYPE, TrainStreamStatus.STREAM_CODEC, MTRNetworkClient::onTrainStatus);
        } else {
            trains.playToClient(TrainStreamChunk.TYPE, TrainStreamChunk.STREAM_CODEC, (msg, ctx) -> {});
            trains.playToClient(TrainStreamStatus.TYPE, TrainStreamStatus.STREAM_CODEC, (msg, ctx) -> {});
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MTRMap.MOD_ID, path);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static boolean canSendTrains(ServerPlayer player) {
        return player.connection.hasChannel(TrainStreamChunk.TYPE) && player.connection.hasChannel(TrainStreamStatus.TYPE);
    }

    public static boolean canSendOverlay(ServerPlayer player) { return player.connection.hasChannel(OverlayChunk.TYPE); }
}
