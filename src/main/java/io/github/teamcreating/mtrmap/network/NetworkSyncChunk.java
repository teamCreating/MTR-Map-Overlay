package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;


/**
 * S2C: one chunk of a full-network map snapshot. The payload is a byte slice
 * of a self-describing binary dump; the last chunk triggers reassembly.
 */
public record NetworkSyncChunk(int transferId, short chunkIndex, short totalChunks, long snapshotHash, byte[] data)
        implements MapPacket {



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
        final byte[] data = buf.readByteArray(NetworkChunkAssembler.CHUNK_SIZE);
        return new NetworkSyncChunk(transferId, chunkIndex, totalChunks, snapshotHash, data);
    }

}
