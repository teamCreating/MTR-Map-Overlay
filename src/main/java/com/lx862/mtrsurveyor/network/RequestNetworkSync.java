package com.lx862.mtrsurveyor.network;

import com.lx862.mtrsurveyor.MTRSurveyor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: client asks the server for a full-network map snapshot.
 * The server replies with a sequence of {@link NetworkSyncChunk}s.
 *
 * <p>{@code dimensionFilter} limits the reply to one dimension (used by the
 * hot-update probe flow); {@code null} requests every dimension.</p>
 */
public record RequestNetworkSync(String dimensionFilter) implements CustomPacketPayload {

    public static final RequestNetworkSync ALL = new RequestNetworkSync(null);

    public static final Type<RequestNetworkSync> TYPE =
            new Type<>(MTRNetwork.id("request_network_sync"));

    public static final StreamCodec<FriendlyByteBuf, RequestNetworkSync> STREAM_CODEC =
            StreamCodec.of(RequestNetworkSync::write, RequestNetworkSync::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void write(FriendlyByteBuf buf, RequestNetworkSync msg) {
        buf.writeBoolean(msg.dimensionFilter != null);
        if (msg.dimensionFilter != null) {
            buf.writeUtf(msg.dimensionFilter);
        }
    }

    public static RequestNetworkSync read(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return new RequestNetworkSync(buf.readUtf());
        }
        return ALL;
    }

    public static void handle(RequestNetworkSync msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sender) {
            // Jump to the server thread first; the collector then hops onto each
            // simulator thread for thread-safe MTR data reads.
            ctx.enqueueWork(() -> ServerNetworkCollector.collectAndSend(sender, msg.dimensionFilter));
        } else {
            MTRSurveyor.LOGGER.warn("[MTRSurveyor] Received network sync request from a non-player");
        }
    }
}
