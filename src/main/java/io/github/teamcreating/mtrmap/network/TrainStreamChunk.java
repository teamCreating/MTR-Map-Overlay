package io.github.teamcreating.mtrmap.network;

import io.github.teamcreating.mtrmap.train.TrainSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record TrainStreamChunk(long generation, String dimension, UUID epoch, long sequence, long sampleAgeMillis, byte kind,
        int index, int count, int totalBytes, byte[] data) implements CustomPacketPayload {
    public static final Type<TrainStreamChunk> TYPE = new Type<>(MTRNetwork.id("train_chunk_v1"));
    public static final StreamCodec<FriendlyByteBuf, TrainStreamChunk> STREAM_CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarLong(m.generation); b.writeUtf(m.dimension, 256); b.writeUUID(m.epoch);
                b.writeVarLong(m.sequence); b.writeVarLong(m.sampleAgeMillis); b.writeByte(m.kind); b.writeVarInt(m.index); b.writeVarInt(m.count);
                b.writeVarInt(m.totalBytes); b.writeByteArray(m.data); },
            b -> new TrainStreamChunk(b.readVarLong(), b.readUtf(256), b.readUUID(), b.readVarLong(), b.readVarLong(), b.readByte(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(TrainSnapshot.CHUNK_BYTES)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
