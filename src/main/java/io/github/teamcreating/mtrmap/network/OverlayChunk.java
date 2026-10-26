package io.github.teamcreating.mtrmap.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OverlayChunk(long generation, String dimension, String station, long sequence, int index, int count, int total, byte[] bytes) implements CustomPacketPayload {
    public static final int CHUNK = 40000;
    public static final Type<OverlayChunk> TYPE = new Type<>(MTRNetwork.id("overlay_response_v1"));
    public static final StreamCodec<FriendlyByteBuf, OverlayChunk> STREAM_CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarLong(m.generation); b.writeUtf(m.dimension, 256); b.writeUtf(m.station, 32); b.writeVarLong(m.sequence); b.writeVarInt(m.index); b.writeVarInt(m.count); b.writeVarInt(m.total); b.writeByteArray(m.bytes); },
            b -> new OverlayChunk(b.readVarLong(), b.readUtf(256), b.readUtf(32), b.readVarLong(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
