package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * C2S: client asks the server for a full-network map snapshot.
 * The server replies with a sequence of {@link NetworkSyncChunk}s.
 *
 * <p>{@code dimensionFilter} limits the reply to one dimension (used by the
 * hot-update probe flow); {@code null} requests every dimension.</p>
 */
public record RequestNetworkSync(String dimensionFilter) implements MapPacket {

    public static final RequestNetworkSync ALL = new RequestNetworkSync(null);



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

}
