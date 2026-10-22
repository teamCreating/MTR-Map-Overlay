package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: per-dimension content hashes for the full-network snapshot. The client
 * diffs these against its cache and requests full snapshots only for
 * dimensions that changed.
 */
public record NetworkProbeResponse(List<DimensionHash> hashes) implements MapPacket {



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
        // Each entry needs at least a string length byte plus an eight-byte hash.
        if (count < 0 || count > buf.readableBytes() / 9) {
            throw new IllegalArgumentException("Invalid dimension hash count: " + count);
        }
        final List<DimensionHash> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hashes.add(new DimensionHash(buf.readUtf(), buf.readLong()));
        }
        return new NetworkProbeResponse(hashes);
    }

}
