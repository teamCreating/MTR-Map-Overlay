package io.github.teamcreating.mtrmap.network;
import net.minecraft.network.FriendlyByteBuf;
public record OverlayRequest(long generation, String dimension, String station, boolean active) implements MapPacket {
    public static void write(FriendlyByteBuf b, OverlayRequest m) { b.writeVarLong(m.generation);b.writeUtf(m.dimension,256);b.writeUtf(m.station,32);b.writeBoolean(m.active); }
    public static OverlayRequest read(FriendlyByteBuf b) { return new OverlayRequest(b.readVarLong(),b.readUtf(256),b.readUtf(32),b.readBoolean()); }
}
