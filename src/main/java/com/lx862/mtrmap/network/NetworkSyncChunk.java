package com.lx862.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;


/**
 * S2C: one chunk of a full-network map snapshot. The payload is a byte slice
 * of a self-describing binary dump; the last chunk triggers reassembly.
 */
public record NetworkSyncChunk(int transferId, short chunkIndex, short totalChunks, long snapshotHash, byte[] data)
        implements CustomPacketPayload {

    public static final Type<NetworkSyncChunk> TYPE =
            new Type<>(MTRNetwork.id("network_sync_chunk"));

    public static final StreamCodec<FriendlyByteBuf, NetworkSyncChunk> STREAM_CODEC =
            StreamCodec.of(NetworkSyncChunk::write, NetworkSyncChunk::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void write(FriendlyByteBuf buf, NetworkSyncChunk msg) {
        buf.writeVarInt(msg.transferId());
        buf.writeShort(msg.chunkIndex());
        buf.writeShort(msg.totalChunks());
        buf.writeLong(msg.snapshotHash());
        buf.writeByteArray(msg.data());
    }

    public static NetworkSyncChunk read(FriendlyByteBuf buf) {
        final int transferId = buf.readVarInt();
        final short chunkIndex = buf.readShort();
        final short totalChunks = buf.readShort();
        final long snapshotHash = buf.readLong();
        final byte[] data = buf.readByteArray();
        return new NetworkSyncChunk(transferId, chunkIndex, totalChunks, snapshotHash, data);
    }

}
