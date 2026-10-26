package io.github.teamcreating.mtrmap.network;
import net.minecraft.network.FriendlyByteBuf;
public record OverlayChunk(long generation, String dimension, String station, long sequence, int index, int count, int total, byte[] bytes) implements MapPacket {
    public static final int CHUNK=40000;
    public static void write(FriendlyByteBuf b, OverlayChunk m) { b.writeVarLong(m.generation);b.writeUtf(m.dimension,256);b.writeUtf(m.station,32);b.writeVarLong(m.sequence);b.writeVarInt(m.index);b.writeVarInt(m.count);b.writeVarInt(m.total);b.writeByteArray(m.bytes); }
    public static OverlayChunk read(FriendlyByteBuf b) { return new OverlayChunk(b.readVarLong(),b.readUtf(256),b.readUtf(32),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readByteArray(CHUNK)); }
}
