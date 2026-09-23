package com.lx862.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: per-dimension content hashes for the full-network snapshot. The client
 * diffs these against its cache and requests full snapshots only for
 * dimensions that changed.
 */
public record NetworkProbeResponse(List<DimensionHash> hashes) implements CustomPacketPayload {

    public static final Type<NetworkProbeResponse> TYPE =
            new Type<>(MTRNetwork.id("network_probe_response"));

    public static final StreamCodec<FriendlyByteBuf, NetworkProbeResponse> STREAM_CODEC =
            StreamCodec.of(NetworkProbeResponse::write, NetworkProbeResponse::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record DimensionHash(String dimensionId, long hash) {
    }

    public static void write(FriendlyByteBuf buf, NetworkProbeResponse msg) {
        buf.writeVarInt(msg.hashes.size());
        for (DimensionHash entry : msg.hashes) {
            buf.writeUtf(entry.dimensionId());
            buf.writeLong(entry.hash());
        }
    }

    public static NetworkProbeResponse read(FriendlyByteBuf buf) {
        final int count = buf.readVarInt();
        final List<DimensionHash> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hashes.add(new DimensionHash(buf.readUtf(), buf.readLong()));
        }
        return new NetworkProbeResponse(hashes);
    }

}
