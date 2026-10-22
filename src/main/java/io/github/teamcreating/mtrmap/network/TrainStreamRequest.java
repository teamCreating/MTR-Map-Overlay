package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;

/** Subscribe/renew/unsubscribe, independent of static network-v8 support. */
public record TrainStreamRequest(long generation, String dimension, boolean active) implements MapPacket {
    public static void write(FriendlyByteBuf buf, TrainStreamRequest msg) { buf.writeVarLong(msg.generation); buf.writeUtf(msg.dimension, 256); buf.writeBoolean(msg.active); }
    public static TrainStreamRequest read(FriendlyByteBuf b) { return new TrainStreamRequest(b.readVarLong(), b.readUtf(256), b.readBoolean()); }
}
