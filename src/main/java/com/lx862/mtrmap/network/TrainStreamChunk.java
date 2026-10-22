package com.lx862.mtrmap.network;

import com.lx862.mtrmap.train.TrainSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

public record TrainStreamChunk(long generation, String dimension, UUID epoch, long sequence, long sampleAgeMillis, byte kind,
        int index, int count, int totalBytes, byte[] data) implements MapPacket {
    public static void write(FriendlyByteBuf b, TrainStreamChunk m) { b.writeVarLong(m.generation); b.writeUtf(m.dimension, 256); b.writeUUID(m.epoch);
                b.writeVarLong(m.sequence); b.writeVarLong(m.sampleAgeMillis); b.writeByte(m.kind); b.writeVarInt(m.index); b.writeVarInt(m.count);
                b.writeVarInt(m.totalBytes); b.writeByteArray(m.data); }
    public static TrainStreamChunk read(FriendlyByteBuf b) { return new TrainStreamChunk(b.readVarLong(), b.readUtf(256), b.readUUID(), b.readVarLong(), b.readVarLong(), b.readByte(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(TrainSnapshot.CHUNK_BYTES)); }
}
