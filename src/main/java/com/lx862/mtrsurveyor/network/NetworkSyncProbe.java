package com.lx862.mtrsurveyor.network;

import com.lx862.mtrsurveyor.MTRSurveyor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: lightweight change-detection probe. The server replies with a
 * {@link NetworkProbeResponse} listing a content hash per dimension; the
 * client pulls full snapshots only for dimensions whose hash changed. This
 * makes periodic hot-update polling cost O(network size) on the server with
 * no transfer when nothing changed.
 */
public record NetworkSyncProbe() implements CustomPacketPayload {

    public static final NetworkSyncProbe INSTANCE = new NetworkSyncProbe();

    public static final Type<NetworkSyncProbe> TYPE =
            new Type<>(MTRNetwork.id("network_sync_probe"));

    public static final StreamCodec<FriendlyByteBuf, NetworkSyncProbe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NetworkSyncProbe msg, IPayloadContext ctx) {
        final ServerPlayer sender = ctx.player() instanceof ServerPlayer player ? player : null;
        if (sender != null) {
            ctx.enqueueWork(() -> ServerNetworkCollector.sendProbeResponse(sender));
        }
    }
}
